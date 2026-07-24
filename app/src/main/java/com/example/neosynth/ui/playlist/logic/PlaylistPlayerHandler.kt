package com.example.neosynth.ui.playlist.logic

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.example.neosynth.data.local.ServerDao
import com.example.neosynth.data.local.buildCoverArtUrl
import com.example.neosynth.data.local.entities.ServerEntity
import com.example.neosynth.data.preferences.SettingsPreferences
import com.example.neosynth.data.preferences.StreamQuality
import com.example.neosynth.data.remote.responses.SongDto
import com.example.neosynth.player.MusicController
import com.example.neosynth.utils.ConnectionType
import com.example.neosynth.utils.NetworkHelper
import com.example.neosynth.utils.StreamUrlBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.net.toUri

@Singleton
class PlaylistPlayerHandler @Inject constructor(
    private val serverDao: ServerDao,
    private val musicController: MusicController,
    private val settingsPreferences: SettingsPreferences,
    private val networkHelper: NetworkHelper
) {

    private suspend fun getStreamQuality(): StreamQuality {
        val settings = settingsPreferences.audioSettings.first()
        return when (networkHelper.getConnectionType()) {
            ConnectionType.WIFI -> settings.streamWifiQuality
            ConnectionType.MOBILE -> settings.streamMobileQuality
            else -> settings.streamMobileQuality
        }
    }

    private suspend fun buildMediaItem(
        song: SongDto,
        server: ServerEntity
    ): MediaItem {
        val streamQuality = getStreamQuality()

        val effectiveBitrate = if (streamQuality != StreamQuality.LOSSLESS) {
            streamQuality.bitrate
        } else {
            song.bitRate ?: 0
        }

        val effectiveFormat = if (streamQuality != StreamQuality.LOSSLESS) {
            streamQuality.format.uppercase()
        } else {
            song.suffix?.uppercase() ?: "MP3"
        }

        val streamUrl = StreamUrlBuilder.buildStreamUrl(server, song.id, streamQuality)
        val coverUrl = buildCoverArtUrl(server, song.coverArt)

        return MediaItem.Builder()
            .setMediaId(song.id)
            .setUri(streamUrl)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .setArtworkUri(coverUrl?.toUri())
                    .setExtras(
                        android.os.Bundle().apply {
                            putInt("bitRate", effectiveBitrate)
                            putString("suffix", effectiveFormat)
                            putString("metadata", """{"bitRate":$effectiveBitrate,"format":"$effectiveFormat","suffix":"$effectiveFormat"}""")
                            putLong("duration", song.duration * 1000L)
                            putInt("originalBitRate", song.bitRate ?: 0)
                            putString("originalSuffix", song.suffix ?: "MP3")
                        }
                    )
                    .build()
            )
            .build()
    }

    fun playPlaylist(
        allSongs: List<SongDto>,
        cachedServer: ServerEntity?,
        scope: CoroutineScope
    ) {
        scope.launch {
            val server = cachedServer ?: serverDao.getActiveServer() ?: return@launch
            if (allSongs.isEmpty()) return@launch

            val mediaItems = allSongs.map { song ->
                buildMediaItem(song, server)
            }

            musicController.playQueue(mediaItems, 0)
        }
    }

    fun shufflePlay(
        allSongs: List<SongDto>,
        cachedServer: ServerEntity?,
        scope: CoroutineScope
    ) {
        scope.launch {
            val server = cachedServer ?: serverDao.getActiveServer() ?: return@launch
            val shuffledSongs = allSongs.shuffled()
            if (shuffledSongs.isEmpty()) return@launch

            val mediaItems = shuffledSongs.map { song ->
                buildMediaItem(song, server)
            }

            musicController.playQueue(mediaItems, 0)
        }
    }

    fun playSong(
        song: SongDto,
        allSongs: List<SongDto>,
        cachedServer: ServerEntity?,
        scope: CoroutineScope
    ) {
        scope.launch {
            val server = cachedServer ?: serverDao.getActiveServer() ?: return@launch

            val mediaItems = allSongs.map { s ->
                buildMediaItem(s, server)
            }

            val startIndex = allSongs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
            musicController.playQueue(mediaItems, startIndex)
        }
    }

    fun playSongs(
        songIds: Set<String>,
        allSongs: List<SongDto>,
        cachedServer: ServerEntity?,
        scope: CoroutineScope
    ) {
        scope.launch {
            val server = cachedServer ?: serverDao.getActiveServer() ?: return@launch
            val songsMap = allSongs.associateBy { it.id }
            val selectedSongs = songIds.mapNotNull { id -> songsMap[id] }
            if (selectedSongs.isEmpty()) return@launch

            val mediaItems = selectedSongs.map { s ->
                buildMediaItem(s, server)
            }

            musicController.playQueue(mediaItems, 0)
        }
    }

    fun playSongsNext(
        songIds: Set<String>,
        allSongs: List<SongDto>,
        cachedServer: ServerEntity?,
        scope: CoroutineScope
    ) {
        scope.launch {
            val server = cachedServer ?: serverDao.getActiveServer() ?: return@launch
            val songsMap = allSongs.associateBy { it.id }
            val selectedSongs = songIds.mapNotNull { id -> songsMap[id] }
            if (selectedSongs.isEmpty()) return@launch

            val mediaItems = selectedSongs.map { s ->
                buildMediaItem(s, server)
            }

            musicController.addAfterCurrent(mediaItems)
        }
    }

    fun addSongsToQueue(
        songIds: Set<String>,
        allSongs: List<SongDto>,
        cachedServer: ServerEntity?,
        scope: CoroutineScope
    ) {
        scope.launch {
            val server = cachedServer ?: serverDao.getActiveServer() ?: return@launch
            val songsMap = allSongs.associateBy { it.id }
            val selectedSongs = songIds.mapNotNull { id -> songsMap[id] }
            if (selectedSongs.isEmpty()) return@launch

            val mediaItems = selectedSongs.map { s ->
                buildMediaItem(s, server)
            }

            musicController.addToQueue(mediaItems)
        }
    }
}
