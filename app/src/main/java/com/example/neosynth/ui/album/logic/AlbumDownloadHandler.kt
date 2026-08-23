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

            // Asegurar que la canción existe en Room
            val existing = musicRepository.getSongById(song.id)
            val metadataJson = """{"bitRate":${song.bitRate ?: 0},"format":"${song.suffix ?: "MP3"}","suffix":"${song.suffix ?: "MP3"}"}"""
            if (existing == null) {
                val songEntity = com.example.neosynth.data.local.entities.SongEntity(
                    id = song.id,
                    title = song.title,
                    serverID = server.id,
                    sourceType = "SUBSONIC",
                    sourceId = server.id.toString(),
                    artistID = song.artistId ?: "",
                    artist = song.artist ?: "Unknown Artist",
                    albumID = albumId ?: song.albumId ?: "",
                    album = albumName ?: song.album ?: "Unknown Album",
                    duration = song.duration.toLong(),
                    imageUrl = song.coverArt?.takeIf { it.isNotBlank() } ?: albumCoverArt,
                    path = "",
                    isDownloaded = false,
                    metadata = metadataJson
                )
                musicRepository.insertSong(songEntity)
            }

            val constraints = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .build()

            val inputData = androidx.work.Data.Builder()
                .putString("batch_id", "song_${song.id}")
                .putString("batch_type", "SONG_IDS")
                .putString("batch_name", song.title)
                .putStringArray("song_ids", arrayOf(song.id))
                .putLong("serverId", server.id)
                .putString("serverUrl", server.url)
                .putString("username", server.username)
                .putString("token", server.token)
                .putString("salt", server.salt)
                .build()

            val downloadRequest = androidx.work.OneTimeWorkRequestBuilder<com.example.neosynth.data.worker.BatchDownloadWorker>()
                .setInputData(inputData)
                .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(constraints)
                .addTag("batch_download")
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

            // Asegurar que las canciones existen en Room
            allSongs.forEach { song ->
                val existing = musicRepository.getSongById(song.id)
                if (existing == null) {
                    val metadataJson = """{"bitRate":${song.bitRate ?: 0},"format":"${song.suffix ?: "MP3"}","suffix":"${song.suffix ?: "MP3"}"}"""
                    val songEntity = com.example.neosynth.data.local.entities.SongEntity(
                        id = song.id,
                        title = song.title,
                        serverID = server.id,
                        sourceType = "SUBSONIC",
                        sourceId = server.id.toString(),
                        artistID = song.artistId ?: "",
                        artist = song.artist ?: "Unknown Artist",
                        albumID = albumId ?: song.albumId ?: "",
                        album = albumName ?: song.album ?: "Unknown Album",
                        duration = song.duration.toLong(),
                        imageUrl = song.coverArt?.takeIf { it.isNotBlank() } ?: albumCoverArt,
                        path = "",
                        isDownloaded = false,
                        metadata = metadataJson
                    )
                    musicRepository.insertSong(songEntity)
                }
            }

            val constraints = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(false)
                .build()

            val inputData = androidx.work.Data.Builder()
                .putString("batch_id", "album_$albumId")
                .putString("batch_type", "ALBUM")
                .putString("batch_name", albumName ?: "Álbum")
                .putString("album_id", albumId)
                .putStringArray("song_ids", songsToDownload.map { it.id }.toTypedArray())
                .putLong("serverId", server.id)
                .putString("serverUrl", server.url)
                .putString("username", server.username)
                .putString("token", server.token)
                .putString("salt", server.salt)
                .build()

            val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.example.neosynth.data.worker.BatchDownloadWorker>()
                .setInputData(inputData)
                .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(constraints)
                .addTag("album_$albumId")
                .addTag("batch_download")
                .setBackoffCriteria(
                    androidx.work.BackoffPolicy.EXPONENTIAL,
                    10000L,
                    java.util.concurrent.TimeUnit.MILLISECONDS
                )
                .build()

            val workManager = androidx.work.WorkManager.getInstance(appContext)
            workManager.enqueueUniqueWork(
                "album_$albumId",
                androidx.work.ExistingWorkPolicy.REPLACE,
                workRequest
            )

            android.util.Log.d("AlbumDownload", "✅ Descarga masiva de álbum encolada con BatchDownloadWorker: $albumName (${songsToDownload.size} canciones)")
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

            // Asegurar que las canciones existen en Room
            songsToDownload.forEach { song ->
                val existing = musicRepository.getSongById(song.id)
                if (existing == null) {
                    val metadataJson = """{"bitRate":${song.bitRate ?: 0},"format":"${song.suffix ?: "MP3"}","suffix":"${song.suffix ?: "MP3"}"}"""
                    val songEntity = com.example.neosynth.data.local.entities.SongEntity(
                        id = song.id,
                        title = song.title,
                        serverID = server.id,
                        sourceType = "SUBSONIC",
                        sourceId = server.id.toString(),
                        artistID = song.artistId ?: "",
                        artist = song.artist ?: "Unknown Artist",
                        albumID = albumId ?: song.albumId ?: "",
                        album = albumName ?: song.album ?: "Unknown Album",
                        duration = song.duration.toLong(),
                        imageUrl = song.coverArt?.takeIf { it.isNotBlank() } ?: albumCoverArt,
                        path = "",
                        isDownloaded = false,
                        metadata = metadataJson
                    )
                    musicRepository.insertSong(songEntity)
                }
            }

            val batchId = "batch_album_songs_${System.currentTimeMillis()}"
            val constraints = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .build()

            val inputData = androidx.work.Data.Builder()
                .putString("batch_id", batchId)
                .putString("batch_type", "SONG_IDS")
                .putString("batch_name", albumName ?: "Canciones (${songsToDownload.size})")
                .putStringArray("song_ids", songsToDownload.map { it.id }.toTypedArray())
                .putLong("serverId", server.id)
                .putString("serverUrl", server.url)
                .putString("username", server.username)
                .putString("token", server.token)
                .putString("salt", server.salt)
                .build()

            val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.example.neosynth.data.worker.BatchDownloadWorker>()
                .setInputData(inputData)
                .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(constraints)
                .addTag("batch_download")
                .build()

            androidx.work.WorkManager.getInstance(appContext).enqueue(workRequest)
        }
    }
}
