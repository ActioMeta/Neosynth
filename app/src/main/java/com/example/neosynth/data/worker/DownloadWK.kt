package com.example.neosynth.data.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
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
import com.example.neosynth.data.local.entities.SongEntity
import com.example.neosynth.data.repository.MusicRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val musicRepository: MusicRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "DownloadWorker"
        private const val CHANNEL_ID = "download_channel"
        private const val CHANNEL_NAME = "Descargas"
    }

    private val notificationManager = NotificationManagerCompat.from(applicationContext)
    private var notificationId = id.hashCode()

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificaciones de descarga de música"
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

    private fun showProgressNotification(title: String, progress: Int = -1) {
        if (!hasNotificationPermission()) return

        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Descargando")
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)

        if (progress >= 0) {
            builder.setProgress(100, progress, false)
        } else {
            builder.setProgress(0, 0, true)
        }

        notificationManager.notify(notificationId, builder.build())
    }

    private fun showCompleteNotification(title: String) {
        if (!hasNotificationPermission()) return

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Descarga completa")
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    private fun showErrorNotification(title: String) {
        if (!hasNotificationPermission()) return

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Error de descarga")
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // Leer parámetros enviados por los ViewModels
        val songId = inputData.getString("songId") ?: return@withContext Result.failure()
        val title = inputData.getString("title") ?: "Unknown"
        val artist = inputData.getString("artist") ?: "Unknown"
        val album = inputData.getString("album") ?: "Unknown"
        val duration = inputData.getInt("duration", 0).toLong()
        val coverArt = inputData.getString("coverArt")
        val serverId = inputData.getLong("serverId", 0L)
        
        // Parámetros del servidor para construir la URL
        val serverUrl = inputData.getString("serverUrl") ?: return@withContext Result.failure()
        val username = inputData.getString("username") ?: return@withContext Result.failure()
        val token = inputData.getString("token") ?: return@withContext Result.failure()
        val salt = inputData.getString("salt") ?: return@withContext Result.failure()
        
        // Construir URL de descarga usando la API de Subsonic
        val url = buildString {
            append(serverUrl)
            if (!serverUrl.endsWith("/")) append("/")
            append("rest/stream")
            append("?id=$songId")
            append("&u=$username")
            append("&t=$token")
            append("&s=$salt")
            append("&v=1.16.1")
            append("&c=NeoSynth")
        }
        
        // IDs opcionales (para compatibilidad)
        val artistId = ""
        val albumId = ""
        val imageUrl = coverArt

        Log.d(TAG, "Iniciando descarga: $title - $artist")
        Log.d(TAG, "URL: $url")

        // Mostrar notificación de progreso
        showProgressNotification("$title - $artist")

        try {
            // Crear directorio de música si no existe
            val musicDir = File(applicationContext.filesDir, "music")
            if (!musicDir.exists()) {
                musicDir.mkdirs()
                Log.d(TAG, "Directorio creado: ${musicDir.absolutePath}")
            }
            
            // Crear directorio para covers si no existe
            val coversDir = File(applicationContext.filesDir, "covers")
            if (!coversDir.exists()) {
                coversDir.mkdirs()
                Log.d(TAG, "Directorio de covers creado: ${coversDir.absolutePath}")
            }

            // Descargar el archivo de audio con OkHttp
            val outputFile = File(musicDir, "$songId.mp3")
            downloadFile(url, outputFile)

            Log.d(TAG, "Archivo descargado: ${outputFile.absolutePath}")
            Log.d(TAG, "Tamaño: ${outputFile.length()} bytes")
            
            // Descargar cover art si existe
            var localCoverPath: String? = null
            if (coverArt != null) {
                try {
                    val coverUrl = buildString {
                        append(serverUrl)
                        if (!serverUrl.endsWith("/")) append("/")
                        append("rest/getCoverArt")
                        append("?id=$coverArt")
                        append("&u=$username")
                        append("&t=$token")
                        append("&s=$salt")
                        append("&v=1.16.1")
                        append("&c=NeoSynth")
                        append("&size=500")
                    }
                    
                    val coverFile = File(coversDir, "$songId.jpg")
                    downloadFile(coverUrl, coverFile)
                    localCoverPath = coverFile.absolutePath
                    Log.d(TAG, "Cover art descargado: ${coverFile.absolutePath}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error descargando cover art: ${e.message}", e)
                    // No fallar la descarga completa si solo falla el cover
                }
            }

            // Registrar en Room una vez descargado
            val entity = SongEntity(
                id = songId,
                title = title,
                serverID = serverId,
                artistID = artistId,
                artist = artist,
                albumID = albumId,
                album = album,
                duration = duration,
                imageUrl = localCoverPath ?: imageUrl, // Usar ruta local si está disponible
                path = outputFile.absolutePath,
                isDownloaded = true
            )
            musicRepository.insertSong(entity)
            Log.d(TAG, "Canción guardada en Room: $title")

            // Mostrar notificación de completado
            showCompleteNotification("$title - $artist")

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error descargando: ${e.message}", e)
            showErrorNotification("$title - $artist")
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    private fun downloadFile(urlString: String, outputFile: File) {
        val client = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        val request = Request.Builder()
            .url(urlString)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP error: ${response.code}")
            }

            response.body?.byteStream()?.use { input ->
                FileOutputStream(outputFile).use { output ->
                    input.copyTo(output)
                }
            } ?: throw Exception("Empty response body")
        }
    }
}