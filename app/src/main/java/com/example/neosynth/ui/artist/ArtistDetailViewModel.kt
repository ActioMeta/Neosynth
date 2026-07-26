package com.example.neosynth.ui.artist

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.example.neosynth.data.local.ServerDao
import com.example.neosynth.data.local.buildCoverArtUrl
import com.example.neosynth.data.local.entities.ServerEntity
import com.example.neosynth.data.remote.DynamicUrlInterceptor
import com.example.neosynth.data.remote.NavidromeApiService
import com.example.neosynth.data.remote.responses.AlbumDto
import com.example.neosynth.data.remote.responses.ArtistDto
import com.example.neosynth.data.remote.responses.ArtistInfo
import com.example.neosynth.data.remote.responses.SongDto
import com.example.neosynth.player.MusicController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.example.neosynth.data.preferences.SettingsPreferences
import com.example.neosynth.utils.NetworkHelper
import com.example.neosynth.utils.ConnectionType
import com.example.neosynth.utils.StreamUrlBuilder
import com.example.neosynth.data.preferences.StreamQuality
import kotlinx.coroutines.flow.first
import androidx.core.net.toUri

import com.example.neosynth.data.repository.MusicRepository
import com.example.neosynth.data.repository.MusicBrainzRepository

@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    private val api: NavidromeApiService,
    private val serverDao: ServerDao,
    private val urlInterceptor: DynamicUrlInterceptor,
    private val musicRepository: MusicRepository,
    private val musicBrainzRepository: MusicBrainzRepository,
    val musicController: MusicController,
    private val settingsPreferences: SettingsPreferences,
    private val networkHelper: NetworkHelper,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _artist = MutableStateFlow<ArtistDto?>(null)
    val artist: StateFlow<ArtistDto?> = _artist

    private val _artistInfo = MutableStateFlow<ArtistInfo?>(null)
    val artistInfo: StateFlow<ArtistInfo?> = _artistInfo

    private val _albums = MutableStateFlow<List<AlbumDto>>(emptyList())
    val albums: StateFlow<List<AlbumDto>> = _albums

    private val _topSongs = MutableStateFlow<List<SongDto>>(emptyList())
    val topSongs: StateFlow<List<SongDto>> = _topSongs

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private var cachedServer: ServerEntity? = null

    fun loadArtist(artistId: String, artistName: String) {
        viewModelScope.launch {
            _isLoading.value = _artist.value == null
            var loadedFromNetwork = false

            // Cargar datos sin conexión/caché inmediatamente
            loadOfflineArtist(artistId, artistName)
            if (_artist.value != null) {
                _isLoading.value = false
            }

            if (!networkHelper.isCurrentConnectionOffline) {
                try {
                    val server = cachedServer ?: serverDao.getActiveServer()
                    if (server != null) {
                        cachedServer = server
                        urlInterceptor.setBaseUrl(server.url)

                        val artistResponse = kotlinx.coroutines.withTimeoutOrNull(3000L) {
                            api.getArtist(
                                artistId = artistId,
                                u = server.username,
                                t = server.token,
                                s = server.salt
                            )
                        }

                        if (artistResponse?.response?.artist != null) {
                            _artist.value = artistResponse.response.artist
                            _albums.value = artistResponse.response.artist?.album ?: emptyList()
                            loadedFromNetwork = true

                            // Get artist info (biography, images) via Navidrome API or MusicBrainz fallback
                            try {
                                val infoResponse = api.getArtistInfo(
                                    artistId = artistId,
                                    u = server.username,
                                    t = server.token,
                                    s = server.salt
                                )
                                val navInfo = infoResponse?.response?.artistInfo
                                if (navInfo != null && !navInfo.biography.isNullOrBlank() && !navInfo.largeImageUrl.isNullOrBlank()) {
                                    _artistInfo.value = navInfo
                                } else {
                                    val mbInfo = musicBrainzRepository.getArtistInfo(artistName)
                                    _artistInfo.value = mbInfo ?: navInfo
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                val mbInfo = musicBrainzRepository.getArtistInfo(artistName)
                                if (mbInfo != null) _artistInfo.value = mbInfo
                            }

                            // Get top songs by searching for artist name
                            try {
                                val songsResponse = api.searchSongs(
                                    query = artistName,
                                    user = server.username,
                                    token = server.token,
                                    salt = server.salt
                                )
                                _topSongs.value = songsResponse.response.searchResult3?.song
                                    ?.filter { it.artistId == artistId || it.artist.equals(artistName, ignoreCase = true) }
                                    ?.take(10) ?: emptyList()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (!loadedFromNetwork && _artist.value == null) {
                loadOfflineArtist(artistId, artistName)
            }

            if (_artistInfo.value?.biography.isNullOrBlank() && !networkHelper.isCurrentConnectionOffline) {
                try {
                    val mbInfo = musicBrainzRepository.getArtistInfo(artistName)
                    if (mbInfo != null) _artistInfo.value = mbInfo
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            _isLoading.value = false
        }
    }

    private suspend fun loadOfflineArtist(artistId: String, artistName: String) {
        try {
            val allDownloaded = musicRepository.getDownloadedSongs().first()
            val artistSongs = allDownloaded.filter { song ->
                song.artistID == artistId || song.artist.equals(artistName, ignoreCase = true)
            }

            if (artistSongs.isNotEmpty()) {
                val resolvedName = artistSongs.first().artist.ifEmpty { artistName }

                val albumsMap = artistSongs.groupBy { if (it.albumID.isNotEmpty()) it.albumID else it.album }
                val albumDtos = albumsMap.map { (albId, songs) ->
                    val firstSong = songs.first()
                    AlbumDto(
                        id = albId,
                        title = firstSong.album,
                        artist = firstSong.artist,
                        artistId = firstSong.artistID.ifEmpty { artistId },
                        coverArt = firstSong.imageUrl?.takeIf { !isAudioFilePath(it) },
                        songCount = songs.size,
                        year = firstSong.year
                    )
                }

                val songDtos = artistSongs.map { s ->
                    val durationSec = if (s.duration > 10_000L) (s.duration / 1000L).toInt() else s.duration.toInt()
                    SongDto(
                        id = s.id,
                        title = s.title,
                        artist = s.artist,
                        artistId = s.artistID.ifEmpty { artistId },
                        album = s.album,
                        albumId = s.albumID,
                        duration = durationSec,
                        coverArt = s.imageUrl?.takeIf { !isAudioFilePath(it) },
                        path = s.path,
                        year = s.year
                    )
                }

                _artist.value = ArtistDto(
                    id = artistId,
                    name = resolvedName,
                    albumCount = albumDtos.size,
                    album = albumDtos
                )
                _albums.value = albumDtos
                _topSongs.value = songDtos
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun isAudioFilePath(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        val lower = path.lowercase()
        return lower.endsWith(".mp3") || lower.endsWith(".flac") || lower.endsWith(".m4a") ||
               lower.endsWith(".wav") || lower.endsWith(".ogg") || lower.endsWith(".aac") ||
               lower.endsWith(".opus") || lower.endsWith(".wma")
    }

    private suspend fun buildMediaItem(s: SongDto): MediaItem {
        val localSong = musicRepository.getSongById(s.id)
        val server = cachedServer ?: serverDao.getActiveServer()
        val isLocal = (localSong != null && localSong.isDownloaded && localSong.path.isNotBlank()) ||
                (s.path != null && (s.path.startsWith("/") || s.path.startsWith("file:") || s.path.startsWith("content:"))) ||
                networkHelper.isCurrentConnectionOffline ||
                server == null

        val rawCover = s.coverArt?.takeIf { it.isNotBlank() && !isAudioFilePath(it) }
            ?: localSong?.imageUrl?.takeIf { it.isNotBlank() && !isAudioFilePath(it) }

        val artworkUri = when {
            rawCover.isNullOrBlank() -> null
            rawCover.startsWith("/") || rawCover.startsWith("file:") || rawCover.startsWith("content:") -> rawCover.toUri()
            rawCover.startsWith("http") -> rawCover.toUri()
            server != null -> buildCoverArtUrl(server, rawCover)?.toUri()
            else -> null
        }

        if (isLocal) {
            val uriPath = localSong?.path ?: s.path ?: ""
            return MediaItem.Builder()
                .setMediaId(s.id)
                .setUri(uriPath.toUri())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(s.title)
                        .setArtist(s.artist)
                        .setAlbumTitle(s.album)
                        .setArtworkUri(artworkUri)
                        .setExtras(
                            android.os.Bundle().apply {
                                putString("path", uriPath)
                                putLong("duration", s.duration * 1000L)
                                putBoolean("isDownloaded", true)
                            }
                        )
                        .build()
                )
                .build()
        }

        if (server == null) {
            val uriPath = s.path ?: ""
            return MediaItem.Builder()
                .setMediaId(s.id)
                .setUri(uriPath.toUri())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(s.title)
                        .setArtist(s.artist)
                        .setAlbumTitle(s.album)
                        .setArtworkUri(artworkUri)
                        .build()
                )
                .build()
        }

        val connectionType = networkHelper.getConnectionType()
        val audioSettings = settingsPreferences.audioSettings.first()
        val streamQuality = when (connectionType) {
            ConnectionType.WIFI -> audioSettings.streamWifiQuality
            ConnectionType.MOBILE -> audioSettings.streamMobileQuality
            ConnectionType.NONE -> StreamQuality.MEDIUM
        }

        val effectiveBitrate = if (streamQuality != StreamQuality.LOSSLESS) streamQuality.bitrate else s.bitRate ?: 0
        val effectiveFormat = if (streamQuality != StreamQuality.LOSSLESS) streamQuality.format.uppercase() else s.suffix?.uppercase() ?: "MP3"

        val streamUrl = StreamUrlBuilder.buildStreamUrl(server, s.id, streamQuality)

        return MediaItem.Builder()
            .setMediaId(s.id)
            .setUri(streamUrl)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(s.title)
                    .setArtist(s.artist)
                    .setAlbumTitle(s.album)
                    .setArtworkUri(artworkUri)
                    .setExtras(
                        android.os.Bundle().apply {
                            putInt("bitRate", effectiveBitrate)
                            putString("suffix", effectiveFormat)
                            putString("metadata", """{"bitRate":$effectiveBitrate,"format":"$effectiveFormat","suffix":"$effectiveFormat"}""")
                            putLong("duration", s.duration * 1000L)
                            putInt("originalBitRate", s.bitRate ?: 0)
                            putString("originalSuffix", s.suffix ?: "MP3")
                        }
                    )
                    .build()
            )
            .build()
    }

    fun playSong(song: SongDto) {
        viewModelScope.launch {
            val mediaItems = _topSongs.value.map { s ->
                buildMediaItem(s)
            }

            val startIndex = _topSongs.value.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
            musicController.playQueue(mediaItems, startIndex)
        }
    }

    fun shufflePlay() {
        viewModelScope.launch {
            val shuffledSongs = _topSongs.value.shuffled()
            if (shuffledSongs.isEmpty()) return@launch

            val mediaItems = shuffledSongs.map { s ->
                buildMediaItem(s)
            }

            musicController.playQueue(mediaItems, 0)
        }
    }

    fun getCoverUrl(coverArt: String?): String? {
        if (coverArt.isNullOrBlank() || isAudioFilePath(coverArt)) return null
        if (coverArt.startsWith("/") || coverArt.startsWith("file:") || coverArt.startsWith("content:")) {
            return coverArt
        }
        val server = cachedServer ?: return coverArt
        return buildCoverArtUrl(server, coverArt) ?: coverArt
    }
}
