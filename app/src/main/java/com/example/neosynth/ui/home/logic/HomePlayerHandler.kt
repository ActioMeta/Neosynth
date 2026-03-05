package com.example.neosynth.ui.home.logic

import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.example.neosynth.data.local.ServerDao
import com.example.neosynth.data.local.buildCoverArtUrl
import com.example.neosynth.data.local.entities.ServerEntity
import com.example.neosynth.data.local.entities.SongEntity
import com.example.neosynth.data.remote.DynamicUrlInterceptor
import com.example.neosynth.data.remote.NavidromeApiService
import com.example.neosynth.ui.home.HomeViewModel.UiEvent
import com.example.neosynth.utils.NetworkHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.example.neosynth.data.preferences.SettingsPreferences
import com.example.neosynth.data.preferences.StreamQuality
import com.example.neosynth.data.remote.responses.SongDto
import com.example.neosynth.data.repository.MusicRepository
import com.example.neosynth.player.MusicController

class HomePlayerHandler @Inject constructor(
    private val api: NavidromeApiService,
    private val serverDao: ServerDao,
    private val musicRepository: MusicRepository,
    private val musicController: MusicController,
    private val urlInterceptor: DynamicUrlInterceptor,
    private val networkHelper: NetworkHelper,
    private val settingsPreferences: SettingsPreferences
) {

    private suspend fun getStreamQuality(): StreamQuality {
        val settings = settingsPreferences.audioSettings.first()
        return when (networkHelper.getConnectionType()) {
            com.example.neosynth.utils.ConnectionType.WIFI -> settings.streamWifiQuality
            com.example.neosynth.utils.ConnectionType.MOBILE -> settings.streamMobileQuality
            else -> settings.streamMobileQuality // Default to mobile/conservative if unknown
        }
    }

    fun playShuffle(scope: CoroutineScope, uiEvent: MutableSharedFlow<UiEvent>, updateRandomCoverArts: (List<String>) -> Unit) {
        scope.launch {
            if (networkHelper.isCurrentConnectionOffline) {
                playOfflineShuffle(uiEvent)
                return@launch
            }

            val server = serverDao.getActiveServer() ?: return@launch
            urlInterceptor.setBaseUrl(server.url)
            
            // Get Quality
            val quality = getStreamQuality()
            
            try {
                val response = api.getRandomSongs(
                    size = 20,
                    u = server.username,
                    t = server.token,
                    s = server.salt,
                    v = "1.16.1",
                    c = "NeoSynth"
                )

                val songsDto = response.response.randomSongs?.song.orEmpty()
                updateRandomCoverArts(
                    songsDto.take(3).mapNotNull { buildCoverArtUrl(server, it.coverArt) }
                )

                val mediaItems = songsDto.map { songDtoToMediaItem(it, server, quality) }
                musicController.playQueue(mediaItems, 0)

            } catch (e: Exception) {
                e.printStackTrace()
                playOfflineShuffle(uiEvent)
            }
        }
    }

    private suspend fun playOfflineShuffle(uiEvent: MutableSharedFlow<UiEvent>) {
        try {
            val downloadedSongs = musicRepository.getDownloadedSongs().first()
            if (downloadedSongs.isNotEmpty()) {
                val mediaItems = downloadedSongs.map { songEntityToMediaItem(it) }
                musicController.playQueue(mediaItems.shuffled(), 0)
            } else {
                uiEvent.emit(UiEvent.ShowSnackbar("No hay canciones descargadas"))
            }
        } catch (e: Exception) {
            Log.e("HomePlayerHandler", "Error playing offline shuffle", e)
        }
    }

    suspend fun getAlbumSongs(albumId: String): List<MediaItem> {
         if (networkHelper.isCurrentConnectionOffline) {
             val downloadedSongs = musicRepository.getDownloadedSongs().first()
             // First, try to filter by albumID (normal album playback)
             var albumSongs = downloadedSongs.filter { it.albumID == albumId }
             // Fallback: if no album match, treat albumId as a songId (offline carousel uses song.id)
             if (albumSongs.isEmpty()) {
                 albumSongs = downloadedSongs.filter { it.id == albumId }
             }
             if (albumSongs.isNotEmpty()) {
                  return albumSongs.map { songEntityToMediaItem(it) }
             }
             return emptyList()
        }
        
        val server = serverDao.getActiveServer() ?: return emptyList()
        urlInterceptor.setBaseUrl(server.url)
        
        // Get Quality
        val quality = getStreamQuality()

        return try {
            val response = api.getAlbum(
                albumId = albumId,
                u = server.username,
                t = server.token,
                s = server.salt
            )

            val songs = response.response.albumDetails?.song.orEmpty()
            songs.map { songDto ->
                songDtoToMediaItem(songDto, server, quality)
            }
        } catch (e: Exception) {
            e.printStackTrace()
             val downloadedSongs = musicRepository.getDownloadedSongs().first()
             val albumSongs = downloadedSongs.filter { it.albumID == albumId }
             if (albumSongs.isNotEmpty()) {
                  albumSongs.map { songEntityToMediaItem(it) }
             } else {
                 emptyList()
             }
        }
    }

    fun playAlbum(albumId: String, shuffle: Boolean = false, scope: CoroutineScope) {
        scope.launch {
            val mediaItems = getAlbumSongs(albumId)
            if (mediaItems.isNotEmpty()) {
                 if (shuffle) {
                     musicController.playQueue(mediaItems.shuffled(), 0)
                 } else {
                     musicController.playQueue(mediaItems, 0)
                 }
            }
        }
    }

    private fun songEntityToMediaItem(song: SongEntity): MediaItem {
        // Extraer metadatos si existen
        var bitRate = 0
        var format = "MP3"
        
        try {
            song.metadata?.let { metadataStr ->
                val json = org.json.JSONObject(metadataStr)
                if (json.has("bitRate")) bitRate = json.getInt("bitRate")
                if (json.has("format")) format = json.getString("format")
            }
        } catch (e: Exception) {
            Log.e("HomePlayerHandler", "Error parsing metadata for offline song", e)
        }

        return MediaItem.Builder()
            .setMediaId(song.id)
            .setUri(song.path)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .setArtworkUri(if (song.imageUrl != null) android.net.Uri.parse(song.imageUrl) else null)
                    .setExtras(
                        android.os.Bundle().apply {
                            putString("path", song.path)
                            putString("coverArtId", song.imageUrl)
                            putLong("duration", song.duration)
                            putBoolean("isDownloaded", true)
                            putInt("bitRate", bitRate)
                            putString("suffix", format)
                        }
                    )
                    .build()
            )
            .build()
    }

    private fun songDtoToMediaItem(songDto: SongDto, server: ServerEntity, quality: StreamQuality): MediaItem {
        val streamUrl = com.example.neosynth.utils.StreamUrlBuilder.buildStreamUrl(server, songDto.id, quality)
        val coverUrl = buildCoverArtUrl(server, songDto.coverArt)
        
        val effectiveBitrate = if (quality != StreamQuality.LOSSLESS) {
            quality.bitrate
        } else {
            songDto.bitRate ?: 0
        }
        
        val effectiveFormat = if (quality != StreamQuality.LOSSLESS) {
            quality.format.uppercase()
        } else {
            songDto.suffix?.uppercase() ?: "MP3"
        }
        
        return MediaItem.Builder()
            .setMediaId(songDto.id)
            .setUri(streamUrl)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(songDto.title)
                    .setArtist(songDto.artist)
                    .setAlbumTitle(songDto.album)
                    .setArtworkUri(if (coverUrl != null) android.net.Uri.parse(coverUrl) else null)
                    .setExtras(
                        android.os.Bundle().apply {
                            putString("coverArtId", songDto.coverArt)
                            putString("artistId", songDto.artistId)
                            putLong("duration", songDto.duration.toLong())
                            putBoolean("isDownloaded", false)
                            putInt("bitRate", effectiveBitrate)
                            putString("suffix", effectiveFormat)
                            putString("metadata", """{"bitRate":$effectiveBitrate,"format":"$effectiveFormat","suffix":"$effectiveFormat"}""")
                            putInt("originalBitRate", songDto.bitRate ?: 0)
                            putString("originalSuffix", songDto.suffix ?: "MP3")
                        }
                    )
                    .build()
            )
            .build()
    }
}
