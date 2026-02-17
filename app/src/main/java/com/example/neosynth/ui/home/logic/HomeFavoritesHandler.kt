package com.example.neosynth.ui.home.logic

import android.util.Log
import com.example.neosynth.data.local.ServerDao
import com.example.neosynth.data.local.entities.SongEntity
import com.example.neosynth.data.remote.NavidromeApiService
import com.example.neosynth.data.repository.MusicRepository
import com.example.neosynth.player.MusicController
import com.example.neosynth.ui.home.HomeViewModel.UiEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class HomeFavoritesHandler @Inject constructor(
    private val api: NavidromeApiService,
    private val serverDao: ServerDao,
    private val musicRepository: MusicRepository,
    private val musicController: MusicController
) {

    private val _isCurrentSongFavorite = MutableStateFlow(false)
    val isCurrentSongFavorite: StateFlow<Boolean> = _isCurrentSongFavorite.asStateFlow()

    fun updateCurrentSongFavoriteStatus(scope: CoroutineScope) {
        scope.launch {
            val mediaItem = musicController.currentMediaItem.value
            _isCurrentSongFavorite.value = if (mediaItem != null) {
                musicRepository.isFavorite(mediaItem.mediaId)
            } else {
                false
            }
        }
    }

    fun toggleFavorite(scope: CoroutineScope, uiEvent: MutableSharedFlow<UiEvent>) {
        scope.launch {
            val server = serverDao.getActiveServer() ?: run {
                Log.e("HomeFavoritesHandler", "No active server found")
                uiEvent.emit(UiEvent.ShowSnackbar("No hay servidor activo"))
                return@launch
            }
            
            val currentItem = musicController.currentMediaItem.value ?: run {
                Log.e("HomeFavoritesHandler", "No current song")
                uiEvent.emit(UiEvent.ShowSnackbar("No hay canción reproduciéndose"))
                return@launch
            }
            
            val songId = currentItem.mediaId

            try {
                val isFavorite = musicRepository.isFavorite(songId)
                
                if (isFavorite) {
                    musicRepository.removeFromFavorites(songId)
                    try {
                        api.unstar(
                            id = listOf(songId),
                            u = server.username,
                            t = server.token,
                            s = server.salt
                        )
                        Log.d("HomeFavoritesHandler", "Removed from favorites: $songId")
                        uiEvent.emit(UiEvent.ShowSnackbar("Eliminado de favoritos"))
                    } catch (e: Exception) {
                        Log.e("HomeFavoritesHandler", "Failed to unstar on server: $songId", e)
                        uiEvent.emit(UiEvent.ShowSnackbar("Error al sincronizar con servidor"))
                    }
                } else {
                    val existingSong = musicRepository.getSongById(songId)
                    if (existingSong == null) {
                        val newSong = SongEntity(
                            id = songId,
                            title = currentItem.mediaMetadata.title?.toString() ?: "Unknown",
                            serverID = 0L,
                            sourceType = "SUBSONIC",
                            sourceId = server.id.toString(),
                            artistID = "",
                            artist = currentItem.mediaMetadata.artist?.toString() ?: "Unknown",
                            albumID = "",
                            album = currentItem.mediaMetadata.albumTitle?.toString() ?: "Unknown",
                            duration = 0L,
                            imageUrl = currentItem.mediaMetadata.artworkUri?.toString(),
                            path = "",
                            isDownloaded = false,
                            isFavorite = false
                        )
                        musicRepository.insertSong(newSong)
                        Log.d("HomeFavoritesHandler", "Created song entity for favoriting: $songId")
                    }
                    
                    musicRepository.addToFavorites(songId)
                    
                    try {
                        api.star(
                            id = listOf(songId),
                            u = server.username,
                            t = server.token,
                            s = server.salt
                        )
                        Log.d("HomeFavoritesHandler", "Added to favorites: $songId")
                        uiEvent.emit(UiEvent.ShowSnackbar("Agregado a favoritos"))
                    } catch (e: Exception) {
                        Log.e("HomeFavoritesHandler", "Failed to star on server: $songId", e)
                        uiEvent.emit(UiEvent.ShowSnackbar("Error al sincronizar con servidor"))
                    }
                }
                
                updateCurrentSongFavoriteStatus(scope)
                
            } catch (e: Exception) {
                Log.e("HomeFavoritesHandler", "Error toggling favorite for $songId", e)
                uiEvent.emit(UiEvent.ShowSnackbar("Error al cambiar favorito"))
            }
        }
    }
}
