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
import com.example.neosynth.data.preferences.SettingsPreferences
import com.example.neosynth.utils.NetworkHelper
import com.example.neosynth.utils.ConnectionType
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val musicRepository: MusicRepository,
    private val settingsPreferences: SettingsPreferences,
    private val networkHelper: NetworkHelper
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

    private fun showProgressNotification(title: String, progress: Int = -1, playlistName: String? = null, current: Int = 0, total: Int = 0) {
        if (!hasNotificationPermission()) return

        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)

        // Notificación consolidada para playlists
        if (playlistName != null && total > 0) {
            builder.setContentTitle("Descargando $playlistName")
            builder.setContentText("$current de $total canciones")
            val percentage = (current * 100) / total
            builder.setProgress(100, percentage, false)
        } else {
            // Notificación individual para canciones sueltas
            builder.setContentTitle("Descargando")
            builder.setContentText(title)
            if (progress >= 0) {
                builder.setProgress(100, progress, false)
            } else {
                builder.setProgress(0, 0, true)
            }
        }

        notificationManager.notify(notificationId, builder.build())
    }

    private fun showCompleteNotification(title: String, playlistName: String? = null, total: Int = 0) {
        if (!hasNotificationPermission()) return

        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)

        if (playlistName != null && total > 0) {
            builder.setContentTitle("Descarga completada")
            builder.setContentText("$playlistName - $total canciones descargadas")
        } else {
            builder.setContentTitle("Descarga completa")
            builder.setContentText(title)
        }

        notificationManager.notify(notificationId, builder.build())
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
        val artistId = inputData.getString("artistId") ?: ""
        val album = inputData.getString("album") ?: "Unknown"
        val albumId = inputData.getString("albumId") ?: ""
        val duration = inputData.getInt("duration", 0).toLong()
        val coverArt = inputData.getString("coverArt")
        val serverId = inputData.getLong("serverId", 0L)
        
        // Parámetros para notificación consolidada (playlists)
        val playlistId = inputData.getString("playlist_id")
        val playlistName = inputData.getString("playlist_name")
        val totalSongs = inputData.getInt("total_songs", 0)
        val currentIndex = inputData.getInt("current_index", 0)
        val isPartOfBatch = playlistId != null && totalSongs > 0
        
        // Usar playlistId para consolidar notificaciones
        if (isPartOfBatch && playlistId != null) {
            notificationId = playlistId.hashCode()
        }
        
        // Parámetros del servidor para construir la URL
        val serverUrl = inputData.getString("serverUrl") ?: return@withContext Result.failure()
        val username = inputData.getString("username") ?: return@withContext Result.failure()
        val token = inputData.getString("token") ?: return@withContext Result.failure()
        val salt = inputData.getString("salt") ?: return@withContext Result.failure()
        
        // Obtener configuración de calidad según tipo de conexión
        val connectionType = networkHelper.getConnectionType()
        val audioSettings = settingsPreferences.audioSettings.first()
        
        val downloadQuality = when (connectionType) {
            ConnectionType.WIFI -> audioSettings.downloadWifiQuality
            ConnectionType.MOBILE -> audioSettings.downloadMobileQuality
            ConnectionType.NONE -> audioSettings.downloadMobileQuality // Fallback
        }
        
        // Construir URL de descarga con parámetros de transcodificación
        val url = buildString {
            append(serverUrl)
            if (!serverUrl.endsWith("/")) append("/")
            append("rest/download")
            append("?id=$songId")
            append("&u=$username")
            append("&t=$token")
            append("&s=$salt")
            append("&v=1.16.1")
            append("&c=NeoSynth")
            
            // Agregar parámetros de transcodificación si no es LOSSLESS
            if (downloadQuality != com.example.neosynth.data.preferences.DownloadQuality.LOSSLESS) {
                append("&maxBitRate=${downloadQuality.bitrate}")
                append("&format=${downloadQuality.format}")
            }
        }
        
        val imageUrl = coverArt

        Log.d(TAG, "Iniciando descarga: $title - $artist")
        if (isPartOfBatch) {
            Log.d(TAG, "Parte de playlist: $playlistName ($currentIndex/$totalSongs)")
        }
        Log.d(TAG, "URL: $url")

        // No mostrar notificación al inicio, solo cuando termine
        // Esto evita actualizaciones desordenadas

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
            if (coverArt != null && coverArt.isNotBlank()) {
                // Usar el ID del coverArt para el nombre del archivo, así se comparte entre canciones del mismo álbum
                val coverFile = File(coversDir, "${coverArt.replace("/", "_")}.jpg")
                
                // Solo descargar si no existe ya
                if (coverFile.exists()) {
                    localCoverPath = coverFile.absolutePath
                    Log.d(TAG, "♻️ Cover reutilizado (ya existe): ${coverFile.name}")
                } else {
                    var retries = 3
                    var downloaded = false
                    
                    while (retries > 0 && !downloaded) {
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
                            
                            Log.d(TAG, "Descargando cover art (intento ${4 - retries}/3): $coverUrl")
                            downloadFile(coverUrl, coverFile)
                            localCoverPath = coverFile.absolutePath
                            downloaded = true
                            Log.d(TAG, "Cover art descargado: ${coverFile.absolutePath} (${coverFile.length()} bytes)")
                        } catch (e: Exception) {
                            retries--
                            Log.e(TAG, "Error descargando cover art (intentos restantes: $retries): ${e.message}", e)
                            if (retries > 0) {
                                kotlinx.coroutines.delay(1000) // Esperar 1 segundo antes de reintentar
                            }
                        }
                    }
                    
                    if (!downloaded) {
                        Log.w(TAG, "No se pudo descargar el cover art después de 3 intentos")
                    }
                }
            }

            // Registrar en Room una vez descargado
            // IMPORTANTE: La canción ya existe en Room (insertada desde PlaylistDetailViewModel)
            // Solo actualizamos path, imageUrl y isDownloaded
            val entity = SongEntity(
                id = songId,
                title = title,
                serverID = 0L, // DEPRECATED
                sourceType = "SUBSONIC", // NOTE: Currently only SUBSONIC source is supported. Will be parameterized when LOCAL_FILES or other sources are added.
                sourceId = serverId.toString(),
                artistID = artistId,
                artist = artist,
                albumID = albumId,
                album = album,
                duration = duration,
                imageUrl = localCoverPath ?: imageUrl, // Usar ruta local si está disponible
                path = outputFile.absolutePath, // Actualizar con la ruta local
                isDownloaded = true // Marcar como descargada
            )
            musicRepository.insertSong(entity) // insertSong usa REPLACE, así que actualiza
            Log.d(TAG, "Canción guardada en Room: $title")

            // Usar contador atómico para progreso real (evita race conditions)
            if (isPartOfBatch && playlistId != null) {
                val actualProgress = DownloadProgress.increment(playlistId)
                
                // Actualizar notificación cada 10 canciones o al final
                if (actualProgress % 10 == 0 || actualProgress == totalSongs) {
                    if (actualProgress == totalSongs) {
                        // Última canción: mostrar completado y limpiar contador
                        showCompleteNotification(
                            title = "$title - $artist",
                            playlistName = playlistName,
                            total = totalSongs
                        )
                        DownloadProgress.reset(playlistId)
                    } else {
                        // Progreso intermedio
                        showProgressNotification(
                            title = "$title - $artist",
                            playlistName = playlistName,
                            current = actualProgress,
                            total = totalSongs
                        )
                    }
                }
                Log.d(TAG, "✅ [$actualProgress/$totalSongs] $title - $artist")
            } else {
                // Descarga individual
                showCompleteNotification("$title - $artist")
                Log.d(TAG, "✅ Descarga individual completada: $title - $artist")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error descargando '$title - $artist': ${e.message}", e)
            
            // Log detallado para playlists
            if (isPartOfBatch && playlistId != null) {
                Log.e(TAG, "❌ Playlist: $playlistName | Index: $currentIndex/$totalSongs")
                Log.e(TAG, "❌ Song ID: $songId | Intentos: ${runAttemptCount + 1}/3")
            }
            
            showErrorNotification("$title - $artist")
            if (runAttemptCount < 3) {
                Log.w(TAG, "⚠️ Reintentando descarga (intento ${runAttemptCount + 1}/3)")
                Result.retry()
            } else {
                Log.e(TAG, "❌ FALLO DEFINITIVO después de 3 intentos: $songId - $title")
                Result.failure()
            }
        }
    }

    private fun downloadFile(urlString: String, outputFile: File) {
        val client = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url(urlString)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP error: ${response.code} - ${response.message}")
            }

            val body = response.body ?: throw Exception("Empty response body")
            val contentLength = body.contentLength()
            
            Log.d(TAG, "Descargando archivo (${contentLength / 1024} KB)...")
            
            body.byteStream().use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead = 0L
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        
                        // Log de progreso cada 100KB
                        if (totalBytesRead % (100 * 1024) == 0L) {
                            val progress = if (contentLength > 0) {
                                (totalBytesRead * 100 / contentLength).toInt()
                            } else {
                                -1
                            }
                            if (progress >= 0) {
                                Log.d(TAG, "Progreso: $progress%")
                            }
                        }
                    }
                }
            }
            
            Log.d(TAG, "Archivo descargado completamente: ${outputFile.absolutePath}")
        }
    }
}