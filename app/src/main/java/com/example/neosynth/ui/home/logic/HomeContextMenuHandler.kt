package com.example.neosynth.ui.home.logic

import com.example.neosynth.domain.model.Album
import com.example.neosynth.player.MusicController
import com.example.neosynth.ui.home.HomeViewModel.UiEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class HomeContextMenuHandler @Inject constructor(
    private val musicController: MusicController,
    private val playerHandler: HomePlayerHandler
) {
    fun onPlayNext(album: Album, scope: CoroutineScope, uiEvent: MutableSharedFlow<UiEvent>) {
        scope.launch {
            try {
                val songs = playerHandler.getAlbumSongs(album.id)
                if (songs.isNotEmpty()) {
                    musicController.addAfterCurrent(songs)
                    uiEvent.emit(UiEvent.ShowSnackbar("Playing next: ${album.name}"))
                } else {
                    uiEvent.emit(UiEvent.ShowSnackbar("No songs found for album"))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                uiEvent.emit(UiEvent.ShowSnackbar("Error fetching songs"))
            }
        }
    }

    fun onAddToQueue(album: Album, scope: CoroutineScope, uiEvent: MutableSharedFlow<UiEvent>) {
        scope.launch {
            try {
                val songs = playerHandler.getAlbumSongs(album.id)
                if (songs.isNotEmpty()) {
                    musicController.addToQueue(songs)
                    uiEvent.emit(UiEvent.ShowSnackbar("Added to queue: ${album.name}"))
                } else {
                    uiEvent.emit(UiEvent.ShowSnackbar("No songs found for album"))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                uiEvent.emit(UiEvent.ShowSnackbar("Error fetching songs"))
            }
        }
    }
}
