package com.example.neosynth.ui.playlist.logic

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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistDownloadHandler @Inject constructor(
    private val musicRepository: MusicRepository,
    @ApplicationContext private val appContext: Context
) {

    val downloadedSongIds: StateFlow<Set<String>> = musicRepository.getDownloadedSongs()
        .map { songs -> songs.map { it.id }.toSet() }
        .stateIn(
            scope = kotlinx.coroutines.CoroutineScope(kotlinx. coroutines.Dispatchers.Default),
            started = SharingStarted.Lazily,
            initialValue = emptySet()
        )

    fun downloadPlaylist(
        allSongs: List<SongDto>,
        server: ServerEntity,
        playlistId: String?,
        playlistName: String?,
        scope: CoroutineScope
    ) {
        scope.launch {
            val songsToDownload = allSongs.filter { song -> song.id !in downloadedSongIds.value }

            android.util.Log.d("PlaylistDownload", "═══════════════════════════════════════")
            android.util.Log.d("PlaylistDownload", "Iniciando descarga de playlist: $playlistName")
            android.util.Log.d("PlaylistDownload", "Total canciones en playlist: ${allSongs.size}")
            android.util.Log.d("PlaylistDownload", "Ya descargadas: ${allSongs.size - songsToDownload.size}")
            android.util.Log.d("PlaylistDownload", "A descargar: ${songsToDownload.size}")
            android.util.Log.d("PlaylistDownload", "═══════════════════════════════════════")

            if (songsToDownload.isEmpty()) {
                android.util.Log.d("PlaylistDownload", "⚠️ Todas las canciones ya están descargadas")
                return@launch
            }

            // Save playlist entity
            try {
                val playlistEntity = com.example.neosynth.data.local.entities.PlaylistEntity(
                    id = playlistId ?: "",
                    name = playlistName ?: "",
                    serverId = server.id,
                    coverArt = allSongs.firstOrNull()?.coverArt,
                    songCount = allSongs.size
                )
                musicRepository.insertPlaylist(playlistEntity)

                var newSongsCount = 0
                var preservedSongsCount = 0

                allSongs.forEach { song ->
                    val existingSong = musicRepository.getSongById(song.id)
                    if (existingSong == null) {
                        val songEntity = com.example.neosynth.data.local.entities.SongEntity(
                            id = song.id,
                            title = song.title,
                            serverID = 0L,
                            sourceType = "SUBSONIC",
                            sourceId = server.id.toString(),
                            artistID = song.artistId ?: "",
                            artist = song.artist ?: "Unknown Artist",
                            albumID = song.albumId ?: "",
                            album = song.album ?: "Unknown Album",
                            duration = song.duration.toLong(),
                            imageUrl = song.coverArt,
                            path = "",
                            isDownloaded = false
                        )
                        musicRepository.insertSong(songEntity)
                        newSongsCount++
                    } else {
                        preservedSongsCount++
                        android.util.Log.d("PlaylistDownload", "  ✓ Preservada: ${song.title} (downloaded: ${existingSong.isDownloaded})")
                    }
                }

                android.util.Log.d("PlaylistDownload", "Canciones nuevas insertadas: $newSongsCount")
                android.util.Log.d("PlaylistDownload", "Canciones preservadas: $preservedSongsCount")

                val crossRefs = allSongs.mapIndexed { index, song ->
                    com.example.neosynth.data.local.entities.PlaylistSongCrossRef(
                        playlistId = playlistId ?: "",
                        songId = song.id,
                        position = index
                    )
                }
                musicRepository.insertPlaylistSongCrossRefs(crossRefs)

                android.util.Log.d("PlaylistDownload", "Playlist guardada en Room")
            } catch (e: Exception) {
                android.util.Log.e("PlaylistDownload", "❌ Error guardando playlist: ${e.message}", e)
                e.printStackTrace()
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
                        .putString("album", song.album ?: "Unknown Album")
                        .putString("albumId", song.albumId ?: "")
                        .putInt("duration", song.duration)
                        .putString("coverArt", song.coverArt)
                        .putLong("serverId", server.id)
                        .putString("serverUrl", server.url)
                        .putString("username", server.username)
                        .putString("token", server.token)
                        .putString("salt", server.salt)
                        .putString("playlist_id", playlistId)
                        .putString("playlist_name", playlistName)
                        .putInt("total_songs", songsToDownload.size)
                        .putInt("current_index", globalIndex)
                        .build()

                    androidx.work.OneTimeWorkRequestBuilder<com.example.neosynth.data.worker.DownloadWorker>()
                        .setInputData(inputData)
                        .setConstraints(constraints)
                        .addTag("playlist_$playlistId")
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

            android.util.Log.d("PlaylistDownload", "✅ ${songsToDownload.size} canciones encoladas en ${batches.size} batches de $parallelSize")
            android.util.Log.d("PlaylistDownload", "⏱️ Tiempo estimado: ~${(songsToDownload.size * 8) / 60} minutos ($parallelSize workers paralelos)")
        }
    }

    fun downloadSongs(
        songIds: Set<String>,
        allSongs: List<SongDto>,
        server: ServerEntity,
        scope: CoroutineScope
    ) {
        scope.launch {
            val songsToDownload = allSongs.filter { it.id in songIds && it.id !in downloadedSongIds.value }
            if (songsToDownload.isEmpty()) return@launch

            songsToDownload.forEach { song ->
                val inputData = androidx.work.Data.Builder()
                    .putString("songId", song.id)
                    .putString("title", song.title)
                    .putString("artist", song.artist)
                    .putString("album", song.album)
                    .putInt("duration", song.duration)
                    .putString("coverArt", song.coverArt)
                    .putLong("serverId", server.id)
                    .putString("serverUrl", server.url)
                    .putString("username", server.username)
                    .putString("token", server.token)
                    .putString("salt", server.salt)
                    .build()

                val downloadRequest = androidx.work.OneTimeWorkRequestBuilder<com.example.neosynth.data.worker.DownloadWorker>()
                    .setInputData(inputData)
                    .build()

                androidx.work.WorkManager.getInstance(appContext).enqueue(downloadRequest)
            }
        }
    }
}
