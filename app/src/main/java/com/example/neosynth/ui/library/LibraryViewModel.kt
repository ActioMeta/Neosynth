package com.example.neosynth.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.neosynth.data.local.ServerDao
import com.example.neosynth.data.local.buildCoverArtUrl
import com.example.neosynth.data.local.entities.ServerEntity
import com.example.neosynth.data.remote.DynamicUrlInterceptor
import com.example.neosynth.data.remote.NavidromeApiService
import com.example.neosynth.data.remote.responses.AlbumDto
import com.example.neosynth.data.remote.responses.ArtistDto
import com.example.neosynth.data.remote.responses.PlaylistDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val api: NavidromeApiService,
    private val serverDao: ServerDao,
    private val urlInterceptor: DynamicUrlInterceptor
) : ViewModel() {

    private val _playlists = MutableStateFlow<List<PlaylistDto>>(emptyList())
    val playlists: StateFlow<List<PlaylistDto>> = _playlists

    private val _artists = MutableStateFlow<List<ArtistDto>>(emptyList())
    val artists: StateFlow<List<ArtistDto>> = _artists

    private val _albums = MutableStateFlow<List<AlbumDto>>(emptyList())
    val albums: StateFlow<List<AlbumDto>> = _albums

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private var cachedServer: ServerEntity? = null

    fun loadLibrary() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val server = serverDao.getActiveServer() ?: return@launch
                cachedServer = server
                urlInterceptor.setBaseUrl(server.url)

                // Load playlists
                try {
                    val playlistsResponse = api.getPlaylists(
                        user = server.username,
                        token = server.token,
                        salt = server.salt
                    )
                    _playlists.value = playlistsResponse.response.playlistsContainer?.playlist ?: emptyList()
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // Load all artists
                try {
                    val artistsResponse = api.getArtists(
                        user = server.username,
                        token = server.token,
                        salt = server.salt
                    )
                    val allArtists = artistsResponse.response.artistsContainer?.indices
                        ?.flatMap { it.artist ?: emptyList() } ?: emptyList()
                    _artists.value = allArtists
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // Load all albums
                try {
                    val albumsResponse = api.getAlbumList(
                        type = "alphabeticalByName",
                        user = server.username,
                        token = server.token,
                        salt = server.salt
                    )
                    _albums.value = albumsResponse.response.albumList?.album 
                        ?: albumsResponse.response.albumList2?.album ?: emptyList()
                } catch (e: Exception) {
                    e.printStackTrace()
                }

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            try {
                val server = cachedServer ?: serverDao.getActiveServer() ?: return@launch
                urlInterceptor.setBaseUrl(server.url)
                
                api.createPlaylist(
                    name = name,
                    u = server.username,
                    t = server.token,
                    s = server.salt
                )
                
                // Reload playlists
                loadPlaylists()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updatePlaylist(playlistId: String, newName: String) {
        viewModelScope.launch {
            try {
                val server = cachedServer ?: serverDao.getActiveServer() ?: return@launch
                urlInterceptor.setBaseUrl(server.url)
                
                api.updatePlaylist(
                    playlistId = playlistId,
                    name = newName,
                    u = server.username,
                    t = server.token,
                    s = server.salt
                )
                
                // Reload playlists
                loadPlaylists()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            try {
                val server = cachedServer ?: serverDao.getActiveServer() ?: return@launch
                urlInterceptor.setBaseUrl(server.url)
                
                api.deletePlaylist(
                    id = playlistId,
                    u = server.username,
                    t = server.token,
                    s = server.salt
                )
                
                // Reload playlists
                loadPlaylists()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun loadPlaylists() {
        viewModelScope.launch {
            try {
                val server = cachedServer ?: serverDao.getActiveServer() ?: return@launch
                val playlistsResponse = api.getPlaylists(
                    user = server.username,
                    token = server.token,
                    salt = server.salt
                )
                _playlists.value = playlistsResponse.response.playlistsContainer?.playlist ?: emptyList()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getCoverUrl(coverArt: String?): String? {
        val server = cachedServer ?: return null
        return buildCoverArtUrl(server, coverArt)
    }
}
