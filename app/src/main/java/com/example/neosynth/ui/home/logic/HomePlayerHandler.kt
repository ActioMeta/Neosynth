package com.example.neosynth.ui.home.logic

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.example.neosynth.R
import com.example.neosynth.data.local.ServerDao
import com.example.neosynth.data.local.buildCoverArtUrl
import com.example.neosynth.data.local.entities.ServerEntity
import com.example.neosynth.data.local.entities.SongEntity
import com.example.neosynth.data.remote.DynamicUrlInterceptor
import com.example.neosynth.data.remote.NavidromeApiService
import com.example.neosynth.ui.home.HomeViewModel.UiEvent
import com.example.neosynth.utils.NetworkHelper
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private val settingsPreferences: SettingsPreferences,
    @ApplicationContext private val context: Context
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
                playOfflineShuffle(uiEvent, updateRandomCoverArts)
                return@launch
            }

            val server = serverDao.getActiveServer() ?: return@launch
            urlInterceptor.setBaseUrl(server.url)
            
            // Get Quality
            val quality = getStreamQuality()
            
            try {
                val response = kotlinx.coroutines.withTimeout(3000L) {
                    api.getRandomSongs(
                        size = 20,
                        u = server.username,
                        t = server.token,
                        s = server.salt,
                        v = "1.16.1",
                        c = "NeoSynth"
                    )
                }

                val songsDto = response.response.randomSongs?.song.orEmpty()
                updateRandomCoverArts(
                    songsDto.take(3).mapNotNull { buildCoverArtUrl(server, it.coverArt) }
                )

                val mediaItems = songsDto.map { songDtoToMediaItem(it, server, quality) }
                musicController.playQueue(mediaItems, 0)

            } catch (e: Exception) {
                e.printStackTrace()
                playOfflineShuffle(uiEvent, updateRandomCoverArts)
            }
        }
    }

    private suspend fun playOfflineShuffle(uiEvent: MutableSharedFlow<UiEvent>, updateRandomCoverArts: (List<String>) -> Unit) {
        try {
            val randomSongs = musicRepository.getRandomDownloadedSongs(50)
            if (randomSongs.isNotEmpty()) {
                val mediaItems = kotlinx.coroutines.Dispatchers.IO.let {
                    kotlinx.coroutines.withContext(it) {
                        randomSongs.map { songEntityToMediaItem(it) }
                    }
                }
                updateRandomCoverArts(randomSongs.take(3).mapNotNull { it.imageUrl })
                musicController.playQueue(mediaItems, 0)
            } else {
                uiEvent.emit(UiEvent.ShowSnackbar(context.getString(R.string.error_no_songs_downloaded)))
            }
        } catch (e: Exception) {
            Log.e("HomePlayerHandler", "Error playing offline shuffle", e)
        }
    }

    suspend fun getAlbumSongs(albumId: String, isLocal: Boolean = false): List<MediaItem> {
         if (networkHelper.isCurrentConnectionOffline || isLocal) {
             var albumSongs = musicRepository.getDownloadedSongsByAlbum(albumId)
             // Fallback: if no album match, treat albumId as a songId (offline carousel uses song.id)
             if (albumSongs.isEmpty()) {
                 val song = musicRepository.getSongById(albumId)
                 if (song != null && song.isDownloaded) {
                     albumSongs = listOf(song)
                 }
             }
             return if (albumSongs.isNotEmpty()) {
                 kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                     albumSongs.map { songEntityToMediaItem(it) }
                 }
             } else {
                 emptyList()
             }
        }
        
        val server = serverDao.getActiveServer() ?: return emptyList()
        urlInterceptor.setBaseUrl(server.url)
        
        // Get Quality
        val quality = getStreamQuality()

        return try {
            val response = kotlinx.coroutines.withTimeout(3000L) {
                api.getAlbum(
                    albumId = albumId,
                    u = server.username,
                    t = server.token,
                    s = server.salt
                )
            }

            val songs = response.response.albumDetails?.song.orEmpty()
            songs.map { songDto ->
                songDtoToMediaItem(songDto, server, quality)
            }
        } catch (e: Exception) {
            e.printStackTrace()
             var albumSongs = musicRepository.getDownloadedSongsByAlbum(albumId)
             if (albumSongs.isEmpty()) {
                 val song = musicRepository.getSongById(albumId)
                 if (song != null && song.isDownloaded) {
                     albumSongs = listOf(song)
                 }
             }
             return if (albumSongs.isNotEmpty()) {
                 kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                     albumSongs.map { songEntityToMediaItem(it) }
                 }
             } else {
                 emptyList()
             }
        }
    }

    fun playAlbum(albumId: String, shuffle: Boolean = false, scope: CoroutineScope, isLocal: Boolean = false) {
        scope.launch {
            val mediaItems = getAlbumSongs(albumId, isLocal)
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
                            putLong("duration", songDto.duration.toLong() * 1000L)
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
