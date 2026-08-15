package com.example.neosynth.ui.playlist

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.neosynth.data.local.ServerDao
import com.example.neosynth.data.local.buildCoverArtUrl
import com.example.neosynth.data.local.entities.ServerEntity
import com.example.neosynth.data.remote.DynamicUrlInterceptor
import com.example.neosynth.data.remote.NavidromeApiService
import com.example.neosynth.data.remote.responses.PlaylistDto
import com.example.neosynth.data.remote.responses.SongDto
import com.example.neosynth.data.repository.MusicRepository
import com.example.neosynth.player.MusicController
import com.example.neosynth.utils.NetworkHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    private val api: NavidromeApiService,
    private val serverDao: ServerDao,
    private val urlInterceptor: DynamicUrlInterceptor,
    private val musicRepository: MusicRepository,
    val musicController: MusicController,
    private val networkHelper: NetworkHelper,
    private val playerHandler: com.example.neosynth.ui.playlist.logic.PlaylistPlayerHandler,
    private val downloadHandler: com.example.neosynth.ui.playlist.logic.PlaylistDownloadHandler,
    private val favoritesHandler: com.example.neosynth.ui.playlist.logic.PlaylistFavoritesHandler,
    private val managementHandler: com.example.neosynth.ui.playlist.logic.PlaylistManagementHandler,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _playlist = MutableStateFlow<PlaylistDto?>(null)
    val playlist: StateFlow<PlaylistDto?> = _playlist

    private val _songs = MutableStateFlow<List<SongDto>>(emptyList())
    val songs: StateFlow<List<SongDto>> = _songs

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing

    private var cachedServer: ServerEntity? = null
    private var currentPlaylistId: String? = null

    val downloadedSongIds = downloadHandler.downloadedSongIds
    val allPlaylists = managementHandler.availablePlaylists

    fun loadPlaylist(playlistId: String) {
        currentPlaylistId = playlistId
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val server = serverDao.getActiveServer() ?: return@launch
                cachedServer = server
                urlInterceptor.setBaseUrl(server.url)

                if (networkHelper.isCurrentConnectionOffline) {
                    loadPlaylistFromLocal(playlistId)
                    return@launch
                }

                try {
                    val response = api.getPlaylist(
                        playlistId = playlistId,
                        u = server.username,
                        t = server.token,
                        s = server.salt
                    )
                    val playlistDetails = response.response.playlistDetails
                    if (playlistDetails != null) {
                        _playlist.value = PlaylistDto(
                            id = playlistDetails.id,
                            name = playlistDetails.name,
                            songCount = playlistDetails.entry?.size ?: 0,
                            duration = playlistDetails.entry?.sumOf { it.duration } ?: 0,
                            coverArt = playlistDetails.entry?.firstOrNull()?.coverArt
                        )
                        _songs.value = playlistDetails.entry ?: emptyList()
                    }
                } catch (e: Exception) {
                    // API failed, fallback to local DB
                    loadPlaylistFromLocal(playlistId)
                }

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun loadPlaylistFromLocal(playlistId: String) {
        try {
            val localData = musicRepository.getPlaylistWithSongs(playlistId)
            if (localData != null) {
                val entity = localData.playlist
                val songCover = localData.songs.firstOrNull { !it.imageUrl.isNullOrBlank() }?.imageUrl
                _playlist.value = PlaylistDto(
                    id = entity.id,
                    name = entity.name,
                    songCount = entity.songCount,
                    duration = 0,
                    coverArt = entity.coverArt ?: songCover
                )
                _songs.value = localData.songs.map { song ->
                    SongDto(
                        id = song.id,
                        title = song.title,
                        artist = song.artist,
                        artistId = song.artistID,
                        album = song.album,
                        albumId = song.albumID,
                        duration = song.duration.toInt(),
                        coverArt = song.imageUrl,
                        path = song.path
                    )
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PlaylistDetailViewModel", "Failed to load playlist from local DB", e)
        }
    }

    fun playPlaylist() {
        playerHandler.playPlaylist(
            allSongs = _songs.value,
            cachedServer = cachedServer,
            scope = viewModelScope
        )
    }

    fun syncPlaylist() {
        _isSyncing.value = true
        val playlistId = currentPlaylistId ?: run {
            _isSyncing.value = false
            return
        }
        managementHandler.syncPlaylist(
            playlistId = playlistId,
            cachedServer = cachedServer,
            scope = viewModelScope,
            onComplete = { songs ->
                _songs.value = songs
                _isSyncing.value = false
            }
        )
    }

    fun shufflePlay() {
        playerHandler.shufflePlay(
            allSongs = _songs.value,
            cachedServer = cachedServer,
            scope = viewModelScope
        )
    }

    fun playSong(song: SongDto) {
        playerHandler.playSong(
            song = song,
            allSongs = _songs.value,
            cachedServer = cachedServer,
            scope = viewModelScope
        )
    }

    fun removeSongFromPlaylist(songIndex: Int) {
        val playlistId = currentPlaylistId ?: return
        managementHandler.removeSongFromPlaylist(
            songIndex = songIndex,
            playlistId = playlistId,
            cachedServer = cachedServer,
            scope = viewModelScope,
            onComplete = { loadPlaylist(playlistId) }
        )
    }

    fun downloadPlaylist() {
        val playlist = _playlist.value ?: return
        val songs = _songs.value
        val server = cachedServer ?: return
        downloadHandler.downloadPlaylist(
            allSongs = songs,
            server = server,
            playlistId = playlist.id,
            playlistName = playlist.name,
            scope = viewModelScope
        )
    }

    fun getCoverUrl(coverArt: String?): String? {
        val effectiveCover = coverArt?.takeIf { it.isNotBlank() } 
            ?: _songs.value.firstOrNull { !it.coverArt.isNullOrBlank() }?.coverArt

        if (effectiveCover.isNullOrBlank()) return null
        if (effectiveCover.startsWith("/") || effectiveCover.startsWith("file:") || effectiveCover.startsWith("content:") || effectiveCover.startsWith("http")) {
            return effectiveCover
        }
        val server = cachedServer ?: return _songs.value.firstOrNull { !it.coverArt.isNullOrBlank() }?.coverArt
        return buildCoverArtUrl(server, effectiveCover) ?: _songs.value.firstOrNull { !it.coverArt.isNullOrBlank() }?.coverArt
    }

    fun playSongs(songIds: Set<String>) {
        playerHandler.playSongs(
            songIds = songIds,
            allSongs = _songs.value,
            cachedServer = cachedServer,
            scope = viewModelScope
        )
    }

    fun playSongsNext(songIds: Set<String>) {
        playerHandler.playSongsNext(
            songIds = songIds,
            allSongs = _songs.value,
            cachedServer = cachedServer,
            scope = viewModelScope
        )
    }

    fun addSongsToQueue(songIds: Set<String>) {
        playerHandler.addSongsToQueue(
            songIds = songIds,
            allSongs = _songs.value,
            cachedServer = cachedServer,
            scope = viewModelScope
        )
    }

    fun downloadSongs(songIds: Set<String>) {
        val server = cachedServer ?: return
        downloadHandler.downloadSongs(
            songIds = songIds,
            allSongs = _songs.value,
            server = server,
            scope = viewModelScope
        )
    }

    fun addToFavorites(songIds: Set<String>) {
        val playlistId = currentPlaylistId ?: return
        favoritesHandler.addToFavorites(
            songIds = songIds,
            playlistId = playlistId,
            cachedServer = cachedServer,
            scope = viewModelScope,
            onComplete = { loadPlaylist(playlistId) }
        )
    }

    fun loadAllPlaylists() {
        managementHandler.loadAvailablePlaylists(
            cachedServer = cachedServer,
            scope = viewModelScope
        )
    }

    fun addToPlaylist(songIds: Set<String>, playlistId: String) {
        favoritesHandler.addToPlaylist(
            songIds = songIds,
            targetPlaylistId = playlistId,
            cachedServer = cachedServer,
            scope = viewModelScope
        )
    }
}
