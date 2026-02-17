package com.example.neosynth.ui.album.logic

import com.example.neosynth.data.local.ServerDao
import com.example.neosynth.data.local.entities.ServerEntity
import com.example.neosynth.data.remote.DynamicUrlInterceptor
import com.example.neosynth.data.remote.NavidromeApiService
import com.example.neosynth.data.repository.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlbumFavoritesHandler @Inject constructor(
    private val api: NavidromeApiService,
    private val serverDao: ServerDao,
    private val musicRepository: MusicRepository,
    private val urlInterceptor: DynamicUrlInterceptor
) {

    fun addToFavorites(
        songIds: Set<String>,
        albumId: String,
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
                    android.util.Log.e("AlbumFavoritesHandler", "Failed to add to favorites: $songId", e)
                    e.printStackTrace()
                }
            }

            // Sync with Navidrome server (batch operation)
            if (songIds.isNotEmpty()) {
                try {
                    android.util.Log.d("AlbumFavoritesHandler", "Starring songs on server - IDs: ${songIds.joinToString(", ")}")
                    val response = api.star(
                        id = songIds.toList(),
                        u = server.username,
                        t = server.token,
                        s = server.salt
                    )
                    android.util.Log.d("AlbumFavoritesHandler", "Starred ${songIds.size} songs on server - Status: ${response.response.status}")

                    // Callback to reload album data
                    onComplete()
                } catch (e: Exception) {
                    android.util.Log.e("AlbumFavoritesHandler", "Failed to star songs on server", e)
                }
            }

            android.util.Log.d("AlbumFavoritesHandler", "Successfully added ${songIds.size} songs to favorites")
        }
    }
}
