package com.example.neosynth.ui.album.logic

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
class AlbumPlayerHandler @Inject constructor(
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
        server: ServerEntity?,
        albumName: String?,
        albumCoverArt: String?
    ): MediaItem {
        val localSong = musicRepository.getSongById(song.id)
        val isLocal = (localSong != null && localSong.isDownloaded && localSong.path.isNotBlank()) ||
                (song.path != null && (song.path.startsWith("/") || song.path.startsWith("file:") || song.path.startsWith("content:"))) ||
                networkHelper.isCurrentConnectionOffline ||
                server == null

        val rawCover = song.coverArt?.takeIf { it.isNotBlank() && !isAudioFilePath(it) }
            ?: albumCoverArt?.takeIf { it.isNotBlank() && !isAudioFilePath(it) }
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
                        .setAlbumTitle(albumName ?: song.album)
                        .setArtworkUri(artworkUri)
                        .setExtras(
                            android.os.Bundle().apply {
                                putString("path", uriPath)
                                putLong("duration", song.duration * 1000L)
                                putBoolean("isDownloaded", true)
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

        val streamUrl = if (server != null) StreamUrlBuilder.buildStreamUrl(server, song.id, streamQuality) else (song.path ?: "")

        return MediaItem.Builder()
            .setMediaId(song.id)
            .setUri(streamUrl.toUri())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(albumName ?: song.album)
                    .setArtworkUri(artworkUri)
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

    fun playSong(
        song: SongDto,
        allSongs: List<SongDto>,
        albumName: String?,
        albumCoverArt: String?,
        cachedServer: ServerEntity?,
        scope: CoroutineScope
    ) {
        scope.launch {
            val server = cachedServer ?: serverDao.getActiveServer()

            val mediaItems = allSongs.map { s ->
                buildMediaItem(s, server, albumName, albumCoverArt)
            }

            val startIndex = allSongs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
            musicController.playQueue(mediaItems, startIndex)
        }
    }

    fun playAlbum(
        allSongs: List<SongDto>,
        albumName: String?,
        albumCoverArt: String?,
        cachedServer: ServerEntity?,
        scope: CoroutineScope
    ) {
        scope.launch {
            val server = cachedServer ?: serverDao.getActiveServer()
            if (allSongs.isEmpty()) return@launch

            val mediaItems = allSongs.map { s ->
                buildMediaItem(s, server, albumName, albumCoverArt)
            }

            musicController.playQueue(mediaItems, 0)
        }
    }

    fun shufflePlay(
        allSongs: List<SongDto>,
        albumName: String?,
        albumCoverArt: String?,
        cachedServer: ServerEntity?,
        scope: CoroutineScope
    ) {
        scope.launch {
            val server = cachedServer ?: serverDao.getActiveServer()
            val shuffledSongs = allSongs.shuffled()
            if (shuffledSongs.isEmpty()) return@launch

            val mediaItems = shuffledSongs.map { s ->
                buildMediaItem(s, server, albumName, albumCoverArt)
            }

            musicController.playQueue(mediaItems, 0)
        }
    }

    fun playSongs(
        songIds: Set<String>,
        allSongs: List<SongDto>,
        albumName: String?,
        albumCoverArt: String?,
        cachedServer: ServerEntity?,
        scope: CoroutineScope
    ) {
        scope.launch {
            val server = cachedServer ?: serverDao.getActiveServer()
            val songsMap = allSongs.associateBy { it.id }
            val selectedSongs = songIds.mapNotNull { id -> songsMap[id] }
            if (selectedSongs.isEmpty()) return@launch

            val mediaItems = selectedSongs.map { s ->
                buildMediaItem(s, server, albumName, albumCoverArt)
            }

            musicController.playQueue(mediaItems, 0)
        }
    }

    fun playSongsNext(
        songIds: Set<String>,
        allSongs: List<SongDto>,
        albumName: String?,
        albumCoverArt: String?,
        cachedServer: ServerEntity?,
        scope: CoroutineScope
    ) {
        scope.launch {
            val server = cachedServer ?: serverDao.getActiveServer()
            val songsMap = allSongs.associateBy { it.id }
            val selectedSongs = songIds.mapNotNull { id -> songsMap[id] }
            if (selectedSongs.isEmpty()) return@launch

            val mediaItems = selectedSongs.map { s ->
                buildMediaItem(s, server, albumName, albumCoverArt)
            }

            musicController.addAfterCurrent(mediaItems)
        }
    }

    fun addSongsToQueue(
        songIds: Set<String>,
        allSongs: List<SongDto>,
        albumName: String?,
        albumCoverArt: String?,
        cachedServer: ServerEntity?,
        scope: CoroutineScope
    ) {
        scope.launch {
            val server = cachedServer ?: serverDao.getActiveServer() ?: return@launch
            val songsMap = allSongs.associateBy { it.id }
            val selectedSongs = songIds.mapNotNull { id -> songsMap[id] }
            if (selectedSongs.isEmpty()) return@launch

            val mediaItems = selectedSongs.map { s ->
                buildMediaItem(s, server, albumName, albumCoverArt)
            }

            musicController.addToQueue(mediaItems)
        }
    }
}
