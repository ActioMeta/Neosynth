package com.example.neosynth.ui.album.logic

import com.example.neosynth.data.local.ServerDao
import com.example.neosynth.data.local.entities.ServerEntity
import com.example.neosynth.data.remote.NavidromeApiService
import com.example.neosynth.data.remote.responses.PlaylistDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Singleton
import javax.inject.Inject

@Singleton
class AlbumPlaylistHandler @Inject constructor(
    private val api: NavidromeApiService,
    private val serverDao: ServerDao
) {

    private val _playlists = MutableStateFlow<List<PlaylistDto>>(emptyList())
    val playlists: StateFlow<List<PlaylistDto>> = _playlists

    fun loadPlaylists(
        cachedServer: ServerEntity?,
        scope: CoroutineScope
    ) {
        scope.launch {
            try {
                val server = cachedServer ?: serverDao.getActiveServer() ?: return@launch
                val response = api.getPlaylists(
                    user = server.username,
                    token = server.token,
                    salt = server.salt
                )
                _playlists.value = response.response.playlistsContainer?.playlist ?: emptyList()
            } catch (e: Exception) {
                android.util.Log.e("AlbumPlaylistHandler", "Failed to load playlists", e)
                e.printStackTrace()
            }
        }
    }

    fun addToPlaylist(
        songIds: Set<String>,
        playlistId: String,
        cachedServer: ServerEntity?,
        scope: CoroutineScope
    ) {
        scope.launch {
            val server = cachedServer ?: serverDao.getActiveServer() ?: return@launch

            try {
                api.addToPlaylist(
                    playlistId = playlistId,
                    songIds = songIds.toList(),
                    u = server.username,
                    t = server.token,
                    s = server.salt
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
