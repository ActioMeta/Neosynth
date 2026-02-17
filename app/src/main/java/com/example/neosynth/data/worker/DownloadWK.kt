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
            builder.setContentTitle("$playlistName ($current/$total)")
            builder.setContentText(title)
            // Usar el progreso de la canción actual para que la barra se mueva
            if (progress >= 0) {
                builder.setProgress(100, progress, false)
            } else {
                builder.setProgress(0, 0, true)
            }
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

        // Cancelar la notificación de progreso antes de mostrar la de completado
        notificationManager.cancel(notificationId)

        // Crear PendingIntent para abrir MainActivity al hacer click
        val intent = Intent(applicationContext, com.example.neosynth.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setOngoing(false) // Asegurar que no sea persistente
            .setContentIntent(pendingIntent) // Hacer clickeable

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

        // Cancelar la notificación de progreso antes de mostrar la de error
        notificationManager.cancel(notificationId)

        // Crear PendingIntent para abrir MainActivity al hacer click
        val intent = Intent(applicationContext, com.example.neosynth.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Error de descarga")
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent) // Hacer clickeable
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
        
        // Construir URL de descarga usando StreamUrlBuilder
        val serverEntity = com.example.neosynth.data.local.entities.ServerEntity(
            id = serverId,
            url = serverUrl,
            username = username,
            token = token,
            salt = salt,
            name = "Active Server", // Placeholder
            isActive = true
        )
        
        val url = com.example.neosynth.utils.StreamUrlBuilder.buildDownloadUrl(
            server = serverEntity,
            songId = songId,
            quality = downloadQuality
        )
        
        val imageUrl = coverArt

        Log.d(TAG, "Iniciando descarga: $title - $artist")
        if (isPartOfBatch) {
            Log.d(TAG, "Parte de playlist: $playlistName ($currentIndex/$totalSongs)")
        }
        Log.d(TAG, "URL: $url")

        // Mostrar notificación de inicio inmediatamente (0%)
        // Para playlists, consultamos el progreso real de la DB si es posible, o usamos el input
        // Pero para UI inmediata, usamos lo que tenemos
        if (isPartOfBatch) {
             showProgressNotification(
                title = "$title - $artist",
                progress = 0,
                playlistName = playlistName,
                current = currentIndex, // Usamos currentIndex para mostrar "X de Y"
                total = totalSongs
            )
        } else {
             showProgressNotification(
                title = "$title - $artist",
                progress = 0
            )
        }

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

            // Callback para progreso de descarga (bytes)
            val onProgress: (Int) -> Unit = { progress ->
                if (isPartOfBatch) {
                    showProgressNotification(
                        title = "$title - $artist",
                        progress = progress,
                        playlistName = playlistName,
                        current = currentIndex,
                        total = totalSongs
                    )
                } else {
                    showProgressNotification(
                        title = "$title - $artist",
                        progress = progress
                    )
                }
            }

            // Descargar el archivo de audio con OkHttp y progreso
            val outputFile = File(musicDir, "$songId.mp3")
            downloadFile(url, outputFile, onProgress)

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
                            // No necesitamos callback para el cover art (es rápido y pequeño)
                            downloadFile(coverUrl, coverFile) { } 
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
            // IMPORTANTE: Usamos UPDATE para no romper relaciones (PlaylistSongCrossRef)
            // Si usamos insertSong (REPLACE), se borra la fila y se vuelve a crear,
            // lo que dispara el CASCADE DELETE en la tabla de playlist_song_cross_ref.
            musicRepository.updateSongDownloadState(
                songId = songId,
                path = outputFile.absolutePath,
                imageUrl = localCoverPath ?: imageUrl,
                isDownloaded = true,
                downloadedAt = System.currentTimeMillis()
            )
            Log.d(TAG, "Canción actualizada en Room: $title")

            // Notifiación final
            if (isPartOfBatch && playlistId != null) {
                // Consultar DB para cuenta exacta final
                val actualProgress = musicRepository.getPlaylistDownloadedCount(playlistId)
                
                 if (actualProgress >= totalSongs) {
                    // Última canción: mostrar completado de playlist
                    showCompleteNotification(
                        title = "$title - $artist",
                        playlistName = playlistName,
                        total = totalSongs
                    )
                } else {
                    // Progreso intermedio completado (preparando para siguiente)
                     // Opcional: Podríamos dejar el 100% visible o simplemente esperar al siguiente worker
                     // Pero para consistencia, mostramos completado de esta canción
                    Log.d(TAG, "✅ [$actualProgress/$totalSongs] $title - $artist")
                }
            } else {
                // Descarga individual completada
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

    private fun downloadFile(urlString: String, outputFile: File, onProgress: (Int) -> Unit) {
        // Configuración adaptativa según API level
        val isOlderDevice = Build.VERSION.SDK_INT < Build.VERSION_CODES.R // Android < 11
        
        val connectTimeout = if (isOlderDevice) 60L else 30L
        val readTimeout = if (isOlderDevice) 120L else 60L
        val writeTimeout = if (isOlderDevice) 120L else 60L
        
        val client = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(connectTimeout, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(300L, java.util.concurrent.TimeUnit.SECONDS) // Aumentado a 5 minutos para transcoding lento
            .writeTimeout(writeTimeout, java.util.concurrent.TimeUnit.SECONDS)
            .retryOnConnectionFailure(true) // Reintentar en fallos de conexión
            .build()

        val request = Request.Builder()
            .url(urlString)
            .build()

        // Retry logic con backoff exponencial
        var attempts = 0
        val maxAttempts = 3
        var lastException: Exception? = null
        
        while (attempts < maxAttempts) {
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw Exception("HTTP error: ${response.code} - ${response.message}")
                    }

                    val body = response.body ?: throw Exception("Empty response body")
                    val contentLength = body.contentLength()
                    
                    Log.d(TAG, "Descargando archivo (${contentLength / 1024} KB)... Intento ${attempts + 1}/$maxAttempts")
                    
                    body.byteStream().use { input ->
                        FileOutputStream(outputFile).use { output ->
                            // Buffer más grande para dispositivos antiguos (reduce llamadas al sistema)
                            val bufferSize = if (isOlderDevice) 16384 else 8192
                            val buffer = ByteArray(bufferSize)
                            var bytesRead: Int
                            var totalBytesRead = 0L
                            var lastProgress = -1
                            
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                totalBytesRead += bytesRead
                                
                                // Calcular y notificar progreso
                                if (contentLength > 0) {
                                    val progress = (totalBytesRead * 100 / contentLength).toInt()
                                    // Notificar solo si cambia el porcentaje (evita spam de notificaciones)
                                    if (progress > lastProgress) {
                                        onProgress(progress)
                                        lastProgress = progress
                                    }
                                }
                            }
                        }
                    }
                    
                    Log.d(TAG, "Archivo descargado completamente: ${outputFile.absolutePath}")
                    return // Éxito, salir de la función
                }
            } catch (e: Exception) {
                lastException = e
                attempts++
                
                if (attempts < maxAttempts) {
                    // Backoff exponencial: 2s, 4s, 8s...
                    val delayMs = (1000L * Math.pow(2.0, (attempts - 1).toDouble())).toLong()
                    Log.w(TAG, "Error en descarga (intento $attempts/$maxAttempts): ${e.message}. Reintentando en ${delayMs}ms...")
                    Thread.sleep(delayMs)
                } else {
                    Log.e(TAG, "Falló descarga después de $maxAttempts intentos", e)
                }
            }
        }
        
        // Si llegamos aquí, todos los intentos fallaron
        throw lastException ?: Exception("Download failed after $maxAttempts attempts")
    }
}