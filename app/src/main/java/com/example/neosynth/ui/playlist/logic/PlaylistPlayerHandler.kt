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
    private val networkHelper: NetworkHelper,
    private val musicRepository: com.example.neosynth.data.repository.MusicRepository
) {

    private suspend fun getStreamQuality(): StreamQuality {
        val settings = settingsPreferences.audioSettings.first()
        return when (networkHelper.getConnectionType()) {
            ConnectionType.WIFI -> settings.streamWifiQuality
            ConnectionType.MOBILE -> settings.streamMobileQuality
            else -> settings.streamMobileQuality
        }
    }

    private fun isAudioFilePath(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        val lower = path.lowercase()
        return lower.endsWith(".mp3") || lower.endsWith(".flac") || lower.endsWith(".m4a") ||
               lower.endsWith(".wav") || lower.endsWith(".ogg") || lower.endsWith(".aac") ||
               lower.endsWith(".opus") || lower.endsWith(".wma")
    }

    private suspend fun buildMediaItem(
        song: SongDto,
        server: ServerEntity?
    ): MediaItem {
        val localSong = musicRepository.getSongById(song.id)
        val isLocal = (localSong != null && localSong.isDownloaded && localSong.path.isNotBlank()) ||
                (song.path != null && (song.path.startsWith("/") || song.path.startsWith("file:") || song.path.startsWith("content:"))) ||
                networkHelper.isCurrentConnectionOffline ||
                server == null

        val rawCover = song.coverArt?.takeIf { it.isNotBlank() && !isAudioFilePath(it) }
            ?: localSong?.imageUrl?.takeIf { it.isNotBlank() && !isAudioFilePath(it) }

        val artworkUri = when {
            rawCover.isNullOrBlank() -> null
            rawCover.startsWith("/") || rawCover.startsWith("file:") || rawCover.startsWith("content:") -> rawCover.toUri()
            rawCover.startsWith("http") -> rawCover.toUri()
            server != null -> buildCoverArtUrl(server, rawCover)?.toUri()
            else -> null
        }

        if (isLocal) {
            val uriPath = localSong?.path ?: song.path ?: ""
            return MediaItem.Builder()
                .setMediaId(song.id)
                .setUri(uriPath.toUri())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .setAlbumTitle(song.album)
                        .setArtworkUri(artworkUri)
                        .setExtras(
                            android.os.Bundle().apply {
                                putString("path", uriPath)
                                putString("coverArtId", song.coverArt ?: localSong?.imageUrl)
                                putLong("duration", (localSong?.duration ?: (song.duration * 1000L)))
                                putBoolean("isDownloaded", true)
                                putInt("bitRate", song.bitRate ?: 0)
                                putString("suffix", song.suffix ?: "MP3")
                            }
                        )
                        .build()
                )
                .build()
        }

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

        val streamUrl = server?.let { StreamUrlBuilder.buildStreamUrl(it, song.id, streamQuality) } ?: song.path ?: ""
        val coverUrl = server?.let { buildCoverArtUrl(it, song.coverArt) } ?: song.coverArt

        return MediaItem.Builder()
            .setMediaId(song.id)
            .setUri(streamUrl.toUri())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .setArtworkUri(artworkUri ?: coverUrl?.toUri())
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
            val server = cachedServer ?: serverDao.getActiveServer()
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
            val server = cachedServer ?: serverDao.getActiveServer()
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
            val server = cachedServer ?: serverDao.getActiveServer()

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
            val server = cachedServer ?: serverDao.getActiveServer()
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
            val server = cachedServer ?: serverDao.getActiveServer()
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
            val server = cachedServer ?: serverDao.getActiveServer()
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
