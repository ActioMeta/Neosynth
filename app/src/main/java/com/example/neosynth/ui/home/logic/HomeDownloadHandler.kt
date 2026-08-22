package com.example.neosynth.ui.home.logic

import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.neosynth.data.local.ServerDao
import com.example.neosynth.data.local.buildCoverArtUrl
import com.example.neosynth.data.local.entities.SongEntity
import com.example.neosynth.data.remote.DynamicUrlInterceptor
import com.example.neosynth.data.remote.NavidromeApiService
import com.example.neosynth.data.remote.responses.SongDto
import com.example.neosynth.data.repository.MusicRepository
import com.example.neosynth.data.worker.DownloadWorker
import com.example.neosynth.player.MusicController
import com.example.neosynth.ui.home.HomeViewModel.UiEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.example.neosynth.R
import javax.inject.Inject

class HomeDownloadHandler @Inject constructor(
    private val api: NavidromeApiService,
    private val serverDao: ServerDao,
    private val musicRepository: MusicRepository,
    private val musicController: MusicController,
    private val urlInterceptor: DynamicUrlInterceptor,
    @ApplicationContext private val appContext: Context
) {

    val downloadedSongIds = musicRepository.getDownloadedSongs()
        .map { songs -> songs.map { it.id }.toSet() }

    fun downloadAlbum(albumId: String, scope: CoroutineScope) {
        Log.d("HomeDownloadHandler", "downloadAlbum called with albumId: $albumId")
        scope.launch {
            val server = serverDao.getActiveServer()
            if (server == null) {
                Log.e("HomeDownloadHandler", "No active server found")
                return@launch
            }
            urlInterceptor.setBaseUrl(server.url)
            try {
                Log.d("HomeDownloadHandler", "Fetching album from API...")
                val response = api.getAlbum(
                    albumId = albumId,
                    u = server.username,
                    t = server.token,
                    s = server.salt
                )

                val albumDetails = response.response.albumDetails
                val songs = albumDetails?.song.orEmpty()
                val albumName = albumDetails?.name ?: "Álbum"
                Log.d("HomeDownloadHandler", "Found ${songs.size} songs in album $albumName")

                // Asegurar que las canciones existen en Room
                songs.forEach { songDto ->
                    val existing = musicRepository.getSongById(songDto.id)
                    if (existing == null) {
                        val songEntity = SongEntity(
                            id = songDto.id,
                            title = songDto.title,
                            serverID = server.id,
                            sourceType = "SUBSONIC",
                            sourceId = server.id.toString(),
                            artistID = songDto.artistId ?: "",
                            artist = songDto.artist ?: "Unknown Artist",
                            albumID = songDto.albumId ?: albumId,
                            album = songDto.album ?: albumName,
                            duration = songDto.duration.toLong(),
                            imageUrl = songDto.coverArt,
                            path = "",
                            isDownloaded = false
                        )
                        musicRepository.insertSong(songEntity)
                    }
                }

                val batchId = "album_$albumId"
                val constraints = androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                    .build()

                val inputData = Data.Builder()
                    .putString("batch_id", batchId)
                    .putString("batch_type", "ALBUM")
                    .putString("batch_name", albumName)
                    .putString("album_id", albumId)
                    .putStringArray("song_ids", songs.map { it.id }.toTypedArray())
                    .putLong("serverId", server.id)
                    .putString("serverUrl", server.url)
                    .putString("username", server.username)
                    .putString("token", server.token)
                    .putString("salt", server.salt)
                    .build()

                val workRequest = OneTimeWorkRequestBuilder<com.example.neosynth.data.worker.BatchDownloadWorker>()
                    .setInputData(inputData)
                    .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .setConstraints(constraints)
                    .addTag("batch_download")
                    .build()

                WorkManager.getInstance(appContext).enqueueUniqueWork(
                    batchId,
                    androidx.work.ExistingWorkPolicy.REPLACE,
                    workRequest
                )
            } catch (e: Exception) {
                Log.e("HomeDownloadHandler", "Error downloading album: ${e.message}", e)
                e.printStackTrace()
            }
        }
    }

    fun downloadCurrentSong(scope: CoroutineScope, uiEvent: MutableSharedFlow<UiEvent>) {
        scope.launch {
            try {
                val server = serverDao.getActiveServer()
                if (server == null) {
                    Log.e("HomeDownloadHandler", "No active server found")
                    uiEvent.emit(UiEvent.ShowSnackbar(appContext.getString(R.string.error_no_active_server)))
                    return@launch
                }
                
                val currentItem = musicController.currentMediaItem.value
                if (currentItem == null) {
                    Log.e("HomeDownloadHandler", "No current song playing")
                    uiEvent.emit(UiEvent.ShowSnackbar(appContext.getString(R.string.msg_no_song_playing)))
                    return@launch
                }

                val songId = currentItem.mediaId
                val songTitle = currentItem.mediaMetadata.title?.toString() ?: appContext.getString(R.string.song_label)
                
                val existingSong = musicRepository.getSongById(songId)
                if (existingSong != null && existingSong.isDownloaded) {
                    Log.d("HomeDownloadHandler", "Song already downloaded: $songTitle")
                    uiEvent.emit(UiEvent.ShowSnackbar(appContext.getString(R.string.msg_already_downloaded, songTitle)))
                    return@launch
                }

                Log.d("HomeDownloadHandler", "Starting download for: $songTitle ($songId)")

                val inputData = Data.Builder()
                    .putString("songId", songId)
                    .putString("title", currentItem.mediaMetadata.title?.toString() ?: "Unknown")
                    .putString("artist", currentItem.mediaMetadata.artist?.toString() ?: "Unknown")
                    .putString("artistId", "")
                    .putString("album", currentItem.mediaMetadata.albumTitle?.toString() ?: "Unknown")
                    .putString("albumId", "") 
                    .putInt("duration", 0)
                    .putInt("originalBitRate", currentItem.mediaMetadata.extras?.getInt("originalBitRate") ?: currentItem.mediaMetadata.extras?.getInt("bitRate") ?: 0)
                    .putString("originalSuffix", currentItem.mediaMetadata.extras?.getString("originalSuffix") ?: currentItem.mediaMetadata.extras?.getString("suffix") ?: "MP3")
                    .putString("coverArt", currentItem.mediaMetadata.extras?.getString("coverArtId"))
                    .putString("artworkUri", currentItem.mediaMetadata.artworkUri?.toString())
                    .putLong("serverId", server.id)
                    .putString("serverUrl", server.url)
                    .putString("username", server.username)
                    .putString("token", server.token)
                    .putString("salt", server.salt)
                    .build()

                val downloadRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
                    .setInputData(inputData)
                    .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .addTag("download_$songId")
                    .build()

                WorkManager.getInstance(appContext).enqueue(downloadRequest)
                Log.d("HomeDownloadHandler", "Download request enqueued for: $songTitle")
                
                if (existingSong == null) {
                    val newSong = SongEntity(
                        id = songId,
                        title = currentItem.mediaMetadata.title?.toString() ?: "Unknown",
                        serverID = 0L,
                        sourceType = "SUBSONIC",
                        sourceId = server.id.toString(),
                        artistID = "",
                        artist = currentItem.mediaMetadata.artist?.toString() ?: "Unknown",
                        albumID = "",
                        album = currentItem.mediaMetadata.albumTitle?.toString() ?: "Unknown",
                        duration = 0L,
                        imageUrl = currentItem.mediaMetadata.artworkUri?.toString(),
                        path = "",
                        isDownloaded = false, // Set false initially until worker updates it? Or maybe worker handles it entirely?
                        isFavorite = false
                    )
                    musicRepository.insertSong(newSong)
                }
                
                uiEvent.emit(UiEvent.ShowSnackbar("${appContext.getString(R.string.action_downloading)} $songTitle"))
                
            } catch (e: Exception) {
                Log.e("HomeDownloadHandler", "Error downloading song", e)
                uiEvent.emit(UiEvent.ShowSnackbar("${appContext.getString(R.string.notification_download_error)}: ${e.message}"))
            }
        }
    }

    private fun enqueueSongDownload(
        songDto: SongDto,
        server: com.example.neosynth.data.local.entities.ServerEntity
    ) {
        val inputData = Data.Builder()
            .putString("songId", songDto.id)
            .putString("title", songDto.title)
            .putString("artist", songDto.artist)
            .putString("artistId", songDto.artistId ?: "")
            .putString("album", songDto.album)
            .putString("albumId", songDto.albumId ?: "")
            .putInt("duration", songDto.duration)
            .putInt("originalBitRate", songDto.bitRate ?: 0)
            .putString("originalSuffix", songDto.suffix ?: "MP3")
            .putString("coverArt", songDto.coverArt)
            .putLong("serverId", server.id)
            .putString("serverUrl", server.url)
            .putString("username", server.username)
            .putString("token", server.token)
            .putString("salt", server.salt)
            .build()

        val downloadRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(inputData)
            .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag("download_${songDto.id}")
            .build()

        WorkManager.getInstance(appContext).enqueue(downloadRequest)
    }
}
