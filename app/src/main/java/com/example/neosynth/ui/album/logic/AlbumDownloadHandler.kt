package com.example.neosynth.ui.album.logic

import android.content.Context
import com.example.neosynth.data.local.entities.ServerEntity
import com.example.neosynth.data.remote.responses.SongDto
import com.example.neosynth.data.repository.MusicRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Singleton
import javax.inject.Inject

@Singleton
class AlbumDownloadHandler @Inject constructor(
    private val musicRepository: MusicRepository,
    @ApplicationContext private val appContext: Context
) {

    val downloadedSongIds: StateFlow<Set<String>> = musicRepository.getDownloadedSongs()
        .map { songs -> songs.map { it.id }.toSet() }
        .stateIn(
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default),
            started = SharingStarted.Lazily,
            initialValue = emptySet()
        )

    fun downloadSong(
        song: SongDto,
        server: ServerEntity,
        albumName: String?,
        albumId: String?,
        albumCoverArt: String?,
        scope: CoroutineScope
    ) {
        scope.launch {
            if (song.id in downloadedSongIds.value) return@launch

            val inputData = androidx.work.Data.Builder()
                .putString("songId", song.id)
                .putString("title", song.title)
                .putString("artist", song.artist ?: "Unknown Artist")
                .putString("artistId", song.artistId ?: "")
                .putString("album", albumName ?: song.album)
                .putString("albumId", albumId ?: song.albumId ?: "")
                .putInt("duration", song.duration)
                .putInt("originalBitRate", song.bitRate ?: 0)
                .putString("originalSuffix", song.suffix ?: "MP3")
                .putString("coverArt", song.coverArt?.takeIf { it.isNotBlank() } ?: albumCoverArt)
                .putLong("serverId", server.id)
                .putString("serverUrl", server.url)
                .putString("username", server.username)
                .putString("token", server.token)
                .putString("salt", server.salt)
                .build()

            val downloadRequest = androidx.work.OneTimeWorkRequestBuilder<com.example.neosynth.data.worker.DownloadWorker>()
                .setInputData(inputData)
                .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            androidx.work.WorkManager.getInstance(appContext).enqueue(downloadRequest)
        }
    }

    fun downloadAlbum(
        allSongs: List<SongDto>,
        server: ServerEntity,
        albumId: String?,
        albumName: String?,
        albumCoverArt: String?,
        scope: CoroutineScope
    ) {
        scope.launch {
            val songsToDownload = allSongs.filter { it.id !in downloadedSongIds.value }

            android.util.Log.d("AlbumDownload", "═══════════════════════════════════════")
            android.util.Log.d("AlbumDownload", "Iniciando descarga de álbum: $albumName")
            android.util.Log.d("AlbumDownload", "Total canciones: ${allSongs.size}")
            android.util.Log.d("AlbumDownload", "A descargar: ${songsToDownload.size}")
            android.util.Log.d("AlbumDownload", "═══════════════════════════════════════")

            if (songsToDownload.isEmpty()) {
                android.util.Log.d("AlbumDownload", "⚠️ Todas las canciones ya están descargadas")
                return@launch
            }

            val parallelSize = com.example.neosynth.utils.DownloadOptimizer.getOptimalBatchSize(appContext)
            val workManager = androidx.work.WorkManager.getInstance(appContext)

            val constraints = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(false)
                .build()

            val batches = songsToDownload.chunked(parallelSize)
            var workContinuation: androidx.work.WorkContinuation? = null

            batches.forEachIndexed { batchIndex, batch ->
                val parallelWorkers = batch.mapIndexed { indexInBatch, song ->
                    val globalIndex = batchIndex * parallelSize + indexInBatch + 1

                    val inputData = androidx.work.Data.Builder()
                        .putString("songId", song.id)
                        .putString("title", song.title)
                        .putString("artist", song.artist ?: "Unknown Artist")
                        .putString("artistId", song.artistId ?: "")
                        .putString("album", albumName ?: song.album)
                        .putString("albumId", albumId ?: song.albumId ?: "")
                        .putInt("duration", song.duration)
                        .putInt("originalBitRate", song.bitRate ?: 0)
                        .putString("originalSuffix", song.suffix ?: "MP3")
                        .putString("coverArt", song.coverArt?.takeIf { it.isNotBlank() } ?: albumCoverArt)
                        .putLong("serverId", server.id)
                        .putString("serverUrl", server.url)
                        .putString("username", server.username)
                        .putString("token", server.token)
                        .putString("salt", server.salt)
                        .putString("playlist_id", albumId)
                        .putString("playlist_name", albumName)
                        .putInt("total_songs", songsToDownload.size)
                        .putInt("current_index", globalIndex)
                        .putBoolean("is_album", true)
                        .build()

                    androidx.work.OneTimeWorkRequestBuilder<com.example.neosynth.data.worker.DownloadWorker>()
                        .setInputData(inputData)
                        .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .setConstraints(constraints)
                        .addTag("album_$albumId")
                        .addTag("download_worker")
                        .setBackoffCriteria(
                            androidx.work.BackoffPolicy.EXPONENTIAL,
                            10000L,
                            java.util.concurrent.TimeUnit.MILLISECONDS
                        )
                        .build()
                }

                workContinuation = if (workContinuation == null) {
                    workManager.beginWith(parallelWorkers)
                } else {
                    workContinuation!!.then(parallelWorkers)
                }
            }

            workContinuation?.enqueue()

            android.util.Log.d("AlbumDownload", "✅ ${songsToDownload.size} canciones encoladas en ${batches.size} batches de $parallelSize")
        }
    }

    fun downloadSongs(
        songIds: Set<String>,
        allSongs: List<SongDto>,
        server: ServerEntity,
        albumId: String?,
        albumName: String?,
        albumCoverArt: String?,
        scope: CoroutineScope
    ) {
        scope.launch {
            val songsToDownload = allSongs.filter { it.id in songIds && it.id !in downloadedSongIds.value }

            if (songsToDownload.isEmpty()) return@launch

            val parallelSize = com.example.neosynth.utils.DownloadOptimizer.getOptimalBatchSize(appContext)
            val workManager = androidx.work.WorkManager.getInstance(appContext)

            val constraints = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(false)
                .build()

            val batches = songsToDownload.chunked(parallelSize)
            var workContinuation: androidx.work.WorkContinuation? = null

            batches.forEachIndexed { batchIndex, batch ->
                val parallelWorkers = batch.map { song ->
                    val inputData = androidx.work.Data.Builder()
                        .putString("songId", song.id)
                        .putString("title", song.title)
                        .putString("artist", song.artist ?: "Unknown Artist")
                        .putString("artistId", song.artistId ?: "")
                        .putString("album", albumName ?: song.album)
                        .putString("albumId", albumId ?: song.albumId ?: "")
                        .putInt("duration", song.duration)
                        .putInt("originalBitRate", song.bitRate ?: 0)
                        .putString("originalSuffix", song.suffix ?: "MP3")
                        .putString("coverArt", song.coverArt?.takeIf { it.isNotBlank() } ?: albumCoverArt)
                        .putLong("serverId", server.id)
                        .putString("serverUrl", server.url)
                        .putString("username", server.username)
                        .putString("token", server.token)
                        .putString("salt", server.salt)
                        .putString("playlist_id", albumId)
                        .putString("playlist_name", albumName)
                        .putInt("total_songs", songsToDownload.size)
                        .putBoolean("is_album", true)
                        .build()

                    androidx.work.OneTimeWorkRequestBuilder<com.example.neosynth.data.worker.DownloadWorker>()
                        .setInputData(inputData)
                        .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .setConstraints(constraints)
                        .addTag("album_$albumId")
                        .addTag("download_worker")
                        .setBackoffCriteria(
                            androidx.work.BackoffPolicy.EXPONENTIAL,
                            10000L,
                            java.util.concurrent.TimeUnit.MILLISECONDS
                        )
                        .build()
                }

                workContinuation = if (workContinuation == null) {
                    workManager.beginWith(parallelWorkers)
                } else {
                    workContinuation!!.then(parallelWorkers)
                }
            }

            workContinuation?.enqueue()
        }
    }
}
