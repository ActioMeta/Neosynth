package com.example.neosynth.ui.playlist.logic

import com.example.neosynth.data.local.ServerDao
import com.example.neosynth.data.local.entities.ServerEntity
import com.example.neosynth.data.remote.NavidromeApiService
import com.example.neosynth.data.repository.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistFavoritesHandler @Inject constructor(
    private val api: NavidromeApiService,
    private val serverDao: ServerDao,
    private val musicRepository: MusicRepository
) {

    fun addToFavorites(
        songIds: Set<String>,
        playlistId: String,
        cachedServer: ServerEntity?,
        scope: CoroutineScope,
        onComplete: suspend () -> Unit
    ) {
        scope.launch {
            val server = cachedServer ?: serverDao.getActiveServer() ?: return@launch

            // Add to local database
            songIds.forEach { songId ->
                try {
                    musicRepository.addToFavorites(songId)
                } catch (e: Exception) {
                    android.util.Log.e("PlaylistFavoritesHandler", "Failed to add to favorites: $songId", e)
                    e.printStackTrace()
                }
            }

            // Sync with Navidrome server (batch operation)
            if (songIds.isNotEmpty()) {
                try {
                    android.util.Log.d("PlaylistFavoritesHandler", "Starring songs on server - IDs: ${songIds.joinToString(", ")}")
                    val response = api.star(
                        id = songIds.toList(),
                        u = server.username,
                        t = server.token,
                        s = server.salt
                    )
                    android.util.Log.d("PlaylistFavoritesHandler", "Starred ${songIds.size} songs on server - Status: ${response.response.status}")

                    // Callback to reload playlist data
                    onComplete()
                } catch (e: Exception) {
                    android.util.Log.e("PlaylistFavoritesHandler", "Failed to star songs on server", e)
                }
            }

            android.util.Log.d("PlaylistFavoritesHandler", "Successfully added ${songIds.size} songs to favorites")
        }
    }

    fun addToPlaylist(
        songIds: Set<String>,
        targetPlaylistId: String,
        cachedServer: ServerEntity?,
        scope: CoroutineScope
    ) {
        scope.launch {
            val server = cachedServer ?: serverDao.getActiveServer() ?: return@launch

            try {
                api.addToPlaylist(
                    playlistId = targetPlaylistId,
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
