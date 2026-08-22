package com.example.neosynth.data.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.example.neosynth.MainActivity
import com.example.neosynth.R
import com.example.neosynth.data.local.entities.ServerEntity
import com.example.neosynth.data.local.entities.SongEntity
import com.example.neosynth.data.preferences.DownloadQuality
import com.example.neosynth.data.preferences.SettingsPreferences
import com.example.neosynth.data.repository.MusicRepository
import com.example.neosynth.utils.ConnectionType
import com.example.neosynth.utils.DownloadOptimizer
import com.example.neosynth.utils.NetworkHelper
import com.example.neosynth.utils.StreamUrlBuilder
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

@HiltWorker
class BatchDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val musicRepository: MusicRepository,
    private val settingsPreferences: SettingsPreferences,
    private val networkHelper: NetworkHelper
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "BatchDownloadWorker"
        const val CHANNEL_ID = "download_channel"

        val downloadHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(30L, TimeUnit.SECONDS)
                .readTimeout(180L, TimeUnit.SECONDS)
                .writeTimeout(60L, TimeUnit.SECONDS)
                .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
                .retryOnConnectionFailure(true)
                .build()
        }
    }

    private val notificationManager = NotificationManagerCompat.from(applicationContext)
    private var notificationId = 0
    private val lastNotificationTime = AtomicLong(0L)

    init {
        createNotificationChannel()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        createNotificationChannel()
        val batchName = inputData.getString("batch_name") ?: applicationContext.getString(R.string.notification_downloading)
        val initialId = (inputData.getString("batch_id") ?: id.toString()).hashCode()
        if (notificationId == 0) notificationId = initialId

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(applicationContext.getString(R.string.notification_downloading_short))
            .setContentText(batchName)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .build()
        return ForegroundInfo(notificationId, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.downloads),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = applicationContext.getString(R.string.notification_channel_downloads)
                setShowBadge(false)
            }
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun updateProgressNotification(
        batchName: String,
        currentSongTitle: String,
        completedCount: Int,
        totalCount: Int,
        force: Boolean = false
    ) {
        if (!hasNotificationPermission()) return

        val now = System.currentTimeMillis()
        if (!force && (now - lastNotificationTime.get() < 350L) && completedCount < totalCount) {
            return
        }
        lastNotificationTime.set(now)

        val progressPercent = if (totalCount > 0) (completedCount * 100) / totalCount else 0
        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentTitle("$batchName ($completedCount/$totalCount - $progressPercent%)")
            .setContentText(currentSongTitle)
            .setProgress(totalCount, completedCount, false)

        notificationManager.notify(notificationId, builder.build())
    }

    private fun showCompleteNotification(
        batchName: String,
        successCount: Int,
        failCount: Int,
        totalCount: Int
    ) {
        if (!hasNotificationPermission()) return

        notificationManager.cancel(notificationId)
        val terminalNotificationId = ("terminal_" + notificationId).hashCode()

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            terminalNotificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = if (failCount == 0) {
            applicationContext.getString(R.string.notification_playlist_download_complete, batchName, successCount)
        } else {
            "$batchName: $successCount/$totalCount descargadas ($failCount errores)"
        }

        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setOngoing(false)
            .setContentIntent(pendingIntent)
            .setContentTitle(applicationContext.getString(R.string.notification_download_complete))
            .setContentText(text)

        notificationManager.notify(terminalNotificationId, builder.build())
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val batchId = inputData.getString("batch_id") ?: return@withContext Result.failure()
        val batchType = inputData.getString("batch_type") ?: "PLAYLIST"
        val batchName = inputData.getString("batch_name") ?: "Descargas"
        val playlistId = inputData.getString("playlist_id")
        val albumId = inputData.getString("album_id")
        val songIdsArray = inputData.getStringArray("song_ids")

        val serverId = inputData.getLong("serverId", 0L)
        val serverUrl = inputData.getString("serverUrl") ?: return@withContext Result.failure()
        val username = inputData.getString("username") ?: return@withContext Result.failure()
        val token = inputData.getString("token") ?: return@withContext Result.failure()
        val salt = inputData.getString("salt") ?: return@withContext Result.failure()

        notificationId = batchId.hashCode()
        setForeground(getForegroundInfo())

        val serverEntity = ServerEntity(
            id = serverId,
            url = serverUrl,
            username = username,
            token = token,
            salt = salt,
            name = "Active Server",
            isActive = true
        )

        val connectionType = networkHelper.getConnectionType()
        val audioSettings = settingsPreferences.audioSettings.first()
        val downloadQuality = when (connectionType) {
            ConnectionType.WIFI -> audioSettings.downloadWifiQuality
            ConnectionType.MOBILE -> audioSettings.downloadMobileQuality
            ConnectionType.NONE -> audioSettings.downloadMobileQuality
        }

        // Obtener canciones pendientes desde Room
        val candidateSongs: List<SongEntity> = when (batchType) {
            "PLAYLIST" -> {
                if (playlistId.isNullOrBlank()) emptyList()
                else musicRepository.getSongsInPlaylist(playlistId).first()
            }
            "ALBUM" -> {
                if (albumId.isNullOrBlank()) emptyList()
                else {
                    val all = musicRepository.getAllSongs().first()
                    all.filter { it.albumID == albumId }
                }
            }
            "SONG_IDS" -> {
                val ids = songIdsArray?.toList() ?: emptyList()
                ids.mapNotNull { musicRepository.getSongById(it) }
            }
            else -> emptyList()
        }

        val pendingSongs = candidateSongs.filter { song ->
            !song.isDownloaded || song.path.isBlank() || !File(song.path).exists()
        }

        val totalCount = pendingSongs.size
        if (totalCount == 0) {
            Log.d(TAG, "Todas las canciones de '$batchName' ya están descargadas.")
            showCompleteNotification(batchName, candidateSongs.size, 0, candidateSongs.size)
            return@withContext Result.success()
        }

        Log.d(TAG, "Iniciando descarga masiva para '$batchName': $totalCount canciones pendientes")

        val concurrency = DownloadOptimizer.getOptimalBatchSize(applicationContext).coerceIn(2, 6)
        val semaphore = Semaphore(concurrency)

        val completedCount = AtomicInteger(0)
        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)

        val musicDir = File(applicationContext.filesDir, "music").apply { if (!exists()) mkdirs() }
        val coversDir = File(applicationContext.filesDir, "covers").apply { if (!exists()) mkdirs() }

        updateProgressNotification(batchName, pendingSongs.first().title, 0, totalCount, force = true)

        coroutineScope {
            pendingSongs.forEach { song ->
                launch(Dispatchers.IO) {
                    semaphore.withPermit {
                        try {
                            downloadSongInternal(
                                song = song,
                                server = serverEntity,
                                downloadQuality = downloadQuality,
                                musicDir = musicDir,
                                coversDir = coversDir
                            )
                            successCount.incrementAndGet()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error al descargar canción '${song.title}': ${e.message}", e)
                            failCount.incrementAndGet()
                        } finally {
                            val done = completedCount.incrementAndGet()
                            updateProgressNotification(
                                batchName = batchName,
                                currentSongTitle = song.title,
                                completedCount = done,
                                totalCount = totalCount
                            )
                        }
                    }
                }
            }
        }

        val finalSuccess = successCount.get()
        val finalFail = failCount.get()
        Log.d(TAG, "Descarga masiva terminada: $finalSuccess exitosas, $finalFail fallidas de $totalCount")

        showCompleteNotification(batchName, finalSuccess, finalFail, totalCount)
        Result.success()
    }

    private suspend fun downloadSongInternal(
        song: SongEntity,
        server: ServerEntity,
        downloadQuality: DownloadQuality,
        musicDir: File,
        coversDir: File
    ) {
        val url = StreamUrlBuilder.buildDownloadUrl(
            server = server,
            songId = song.id,
            quality = downloadQuality
        )

        val outputFile = File(musicDir, "${song.id}.mp3")
        downloadFileWithClient(url, outputFile)

        // Manejo de Cover Art
        var localCoverPath: String? = null
        val coverId = song.imageUrl
        if (!coverId.isNullOrBlank() && !coverId.startsWith("file:") && !coverId.startsWith("/")) {
            val coverFileName = "${coverId.replace("/", "_")}.jpg"
            val coverFile = File(coversDir, coverFileName)

            if (coverFile.exists() && coverFile.length() > 0) {
                localCoverPath = "file://${coverFile.absolutePath}"
            } else {
                val coverUrl = buildString {
                    append(server.url)
                    if (!server.url.endsWith("/")) append("/")
                    append("rest/getCoverArt")
                    append("?id=$coverId")
                    append("&u=${server.username}")
                    append("&t=${server.token}")
                    append("&s=${server.salt}")
                    append("&v=1.16.1")
                    append("&c=NeoSynth")
                    append("&size=500")
                }
                try {
                    downloadFileWithClient(coverUrl, coverFile)
                    if (coverFile.exists() && coverFile.length() > 0) {
                        localCoverPath = "file://${coverFile.absolutePath}"
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "No se pudo descargar cover para ${song.title}: ${e.message}")
                }
            }
        }

        val effectiveBitrate = if (downloadQuality != DownloadQuality.LOSSLESS) downloadQuality.bitrate else 320
        val effectiveFormat = if (downloadQuality != DownloadQuality.LOSSLESS) downloadQuality.format.uppercase() else "MP3"
        val metadataJson = """{"bitRate":$effectiveBitrate,"format":"$effectiveFormat","suffix":"$effectiveFormat"}"""

        val finalImage = localCoverPath ?: song.imageUrl

        musicRepository.updateSongDownloadState(
            songId = song.id,
            path = outputFile.absolutePath,
            imageUrl = finalImage,
            isDownloaded = true,
            downloadedAt = System.currentTimeMillis(),
            metadata = metadataJson
        )
    }

    private fun downloadFileWithClient(urlString: String, outputFile: File) {
        val request = Request.Builder().url(urlString).build()
        var attempts = 0
        val maxAttempts = 3
        var lastException: Exception? = null

        while (attempts < maxAttempts) {
            try {
                downloadHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw Exception("HTTP error ${response.code}: ${response.message}")
                    }
                    val body = response.body ?: throw Exception("Empty body")
                    val tempFile = File(outputFile.parentFile, "${outputFile.name}.tmp")

                    body.byteStream().use { input ->
                        FileOutputStream(tempFile).use { output ->
                            val buffer = ByteArray(16384)
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                            }
                        }
                    }

                    if (tempFile.renameTo(outputFile) || (outputFile.delete() && tempFile.renameTo(outputFile))) {
                        return
                    } else {
                        tempFile.copyTo(outputFile, overwrite = true)
                        tempFile.delete()
                        return
                    }
                }
            } catch (e: Exception) {
                lastException = e
                attempts++
                if (attempts < maxAttempts) {
                    Thread.sleep(1000L * attempts)
                }
            }
        }
        throw lastException ?: Exception("Download failed after $maxAttempts attempts")
    }
}
