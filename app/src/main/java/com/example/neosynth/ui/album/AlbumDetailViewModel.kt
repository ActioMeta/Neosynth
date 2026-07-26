package com.example.neosynth.ui.album

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.neosynth.data.local.ServerDao
import com.example.neosynth.data.local.buildCoverArtUrl
import com.example.neosynth.data.local.entities.ServerEntity
import com.example.neosynth.data.remote.DynamicUrlInterceptor
import com.example.neosynth.data.remote.NavidromeApiService
import com.example.neosynth.data.remote.responses.AlbumDetails
import com.example.neosynth.data.remote.responses.PlaylistDto
import com.example.neosynth.data.remote.responses.SongDto
import com.example.neosynth.data.repository.MusicRepository
import com.example.neosynth.player.MusicController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    private val api: NavidromeApiService,
    private val serverDao: ServerDao,
    private val urlInterceptor: DynamicUrlInterceptor,
    private val musicRepository: MusicRepository,
    val musicController: MusicController,
    private val playerHandler: com.example.neosynth.ui.album.logic.AlbumPlayerHandler,
    private val downloadHandler: com.example.neosynth.ui.album.logic.AlbumDownloadHandler,
    private val favoritesHandler: com.example.neosynth.ui.album.logic.AlbumFavoritesHandler,
    private val playlistHandler: com.example.neosynth.ui.album.logic.AlbumPlaylistHandler,
    private val networkHelper: com.example.neosynth.utils.NetworkHelper,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _album = MutableStateFlow<AlbumDetails?>(null)
    val album: StateFlow<AlbumDetails?> = _album

    private val _songs = MutableStateFlow<List<SongDto>>(emptyList())
    val songs: StateFlow<List<SongDto>> = _songs


    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private var cachedServer: ServerEntity? = null

    val downloadedSongIds = downloadHandler.downloadedSongIds

    fun loadAlbum(albumId: String) {
        viewModelScope.launch {
            _isLoading.value = _album.value == null
            
            // Cargar caché/local inmediatamente para tiempo de respuesta instantáneo
            loadOfflineAlbum(albumId)
            if (_album.value != null) {
                _isLoading.value = false
            }

            if (!networkHelper.isCurrentConnectionOffline) {
                try {
                    val server = cachedServer ?: serverDao.getActiveServer()
                    if (server != null) {
                        cachedServer = server
                        urlInterceptor.setBaseUrl(server.url)

                        val response = kotlinx.coroutines.withTimeoutOrNull(3000L) {
                            api.getAlbum(
                                albumId = albumId,
                                u = server.username,
                                t = server.token,
                                s = server.salt
                            )
                        }

                        val details = response?.response?.albumDetails
                        if (details != null) {
                            _album.value = details
                            _songs.value = details.song ?: emptyList()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            _isLoading.value = false
        }
    }

    private suspend fun loadOfflineAlbum(albumId: String) {
        try {
            var localSongs = musicRepository.getDownloadedSongsByAlbum(albumId)
            if (localSongs.isEmpty()) {
                val allDownloaded = musicRepository.getDownloadedSongs().first()
                localSongs = allDownloaded.filter { song ->
                    song.albumID == albumId || song.id == albumId || song.album.equals(albumId, ignoreCase = true)
                }
            }

            if (localSongs.isNotEmpty()) {
                val firstSong = localSongs.first()
                val songDtos = localSongs.map { songEntity ->
                    val durationSec = if (songEntity.duration > 10_000L) {
                        (songEntity.duration / 1000L).toInt()
                    } else {
                        songEntity.duration.toInt()
                    }

                    SongDto(
                        id = songEntity.id,
                        title = songEntity.title,
                        artist = songEntity.artist,
                        artistId = songEntity.artistID,
                        album = songEntity.album,
                        albumId = songEntity.albumID.ifEmpty { albumId },
                        duration = durationSec,
                        coverArt = songEntity.imageUrl?.takeIf { !it.endsWith(".mp3") && !it.endsWith(".flac") && !it.endsWith(".m4a") },
                        path = songEntity.path,
                        year = songEntity.year
                    )
                }
                val totalDurationSec = songDtos.sumOf { it.duration }
                val albumDetails = AlbumDetails(
                    id = firstSong.albumID.ifEmpty { albumId },
                    name = firstSong.album.ifEmpty { firstSong.title },
                    artist = firstSong.artist,
                    artistId = firstSong.artistID,
                    coverArt = firstSong.imageUrl?.takeIf { !it.endsWith(".mp3") && !it.endsWith(".flac") && !it.endsWith(".m4a") },
                    year = firstSong.year,
                    genre = firstSong.genre,
                    songCount = localSongs.size,
                    duration = totalDurationSec,
                    song = songDtos
                )
                _album.value = albumDetails
                _songs.value = songDtos
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playSong(song: SongDto) {
        playerHandler.playSong(
            song = song,
            allSongs = _songs.value,
            albumName = _album.value?.name,
            albumCoverArt = _album.value?.coverArt,
            cachedServer = cachedServer,
            scope = viewModelScope
        )
    }

    fun playAlbum() {
        playerHandler.playAlbum(
            allSongs = _songs.value,
            albumName = _album.value?.name,
            albumCoverArt = _album.value?.coverArt,
            cachedServer = cachedServer,
            scope = viewModelScope
        )
    }

    fun shufflePlay() {
        playerHandler.shufflePlay(
            allSongs = _songs.value,
            albumName = _album.value?.name,
            albumCoverArt = _album.value?.coverArt,
            cachedServer = cachedServer,
            scope = viewModelScope
        )
    }

    fun getCoverUrl(coverArt: String?): String? {
        if (coverArt.isNullOrBlank()) return null
        if (coverArt.startsWith("/") || coverArt.startsWith("file:") || coverArt.startsWith("content:")) {
            return coverArt
        }
        val server = cachedServer ?: return coverArt
        return buildCoverArtUrl(server, coverArt) ?: coverArt
    }

    fun downloadSong(song: SongDto) {
        viewModelScope.launch {
            val server = cachedServer ?: serverDao.getActiveServer() ?: return@launch
            downloadHandler.downloadSong(
                song = song,
                server = server,
                albumName = _album.value?.name,
                albumId = _album.value?.id,
                albumCoverArt = _album.value?.coverArt,
                scope = viewModelScope
            )
        }
    }

    fun downloadAlbum() {
        viewModelScope.launch {
            val server = cachedServer ?: serverDao.getActiveServer() ?: return@launch
            downloadHandler.downloadAlbum(
                allSongs = _songs.value,
                server = server,
                albumId = _album.value?.id,
                albumName = _album.value?.name,
                albumCoverArt = _album.value?.coverArt,
                scope = viewModelScope
            )
        }
    }

    // Multi-selection methods
    fun playSongs(songIds: Set<String>) {
        playerHandler.playSongs(
            songIds = songIds,
            allSongs = _songs.value,
            albumName = _album.value?.name,
            albumCoverArt = _album.value?.coverArt,
            cachedServer = cachedServer,
            scope = viewModelScope
        )
    }

    fun playSongsNext(songIds: Set<String>) {
        playerHandler.playSongsNext(
            songIds = songIds,
            allSongs = _songs.value,
            albumName = _album.value?.name,
            albumCoverArt = _album.value?.coverArt,
            cachedServer = cachedServer,
            scope = viewModelScope
        )
    }

    fun addSongsToQueue(songIds: Set<String>) {
        playerHandler.addSongsToQueue(
            songIds = songIds,
            allSongs = _songs.value,
            albumName = _album.value?.name,
            albumCoverArt = _album.value?.coverArt,
            cachedServer = cachedServer,
            scope = viewModelScope
        )
    }

    fun downloadSongs(songIds: Set<String>) {
        viewModelScope.launch {
            val server = cachedServer ?: serverDao.getActiveServer() ?: return@launch
            downloadHandler.downloadSongs(
                songIds = songIds,
                allSongs = _songs.value,
                server = server,
                albumId = _album.value?.id,
                albumName = _album.value?.name,
                albumCoverArt = _album.value?.coverArt,
                scope = viewModelScope
            )
        }
    }

    fun addToFavorites(songIds: Set<String>) {
        val albumId = _album.value?.id ?: return
        favoritesHandler.addToFavorites(
            songIds = songIds,
            albumId = albumId,
            cachedServer = cachedServer,
            scope = viewModelScope,
            onComplete = { loadAlbum(albumId) }
        )
    }

    private val _playlists = playlistHandler.playlists
    val playlists: StateFlow<List<PlaylistDto>> = _playlists

    fun loadPlaylists() {
        playlistHandler.loadPlaylists(
            cachedServer = cachedServer,
            scope = viewModelScope
        )
    }

    fun addToPlaylist(songIds: Set<String>, playlistId: String) {
        playlistHandler.addToPlaylist(
            songIds = songIds,
            playlistId = playlistId,
            cachedServer = cachedServer,
            scope = viewModelScope
        )
    }
}
