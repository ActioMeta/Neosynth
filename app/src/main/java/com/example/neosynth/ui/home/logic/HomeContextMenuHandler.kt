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
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.example.neosynth.R

class HomeContextMenuHandler @Inject constructor(
    private val musicController: MusicController,
    private val playerHandler: HomePlayerHandler,
    @ApplicationContext private val context: Context
) {
    fun onPlayNext(album: Album, scope: CoroutineScope, uiEvent: MutableSharedFlow<UiEvent>) {
        scope.launch {
            try {
                val isLocal = album.sourceType == com.example.neosynth.domain.model.MusicSourceType.LOCAL_FILES
                val songs = playerHandler.getAlbumSongs(album.id, isLocal)
                if (songs.isNotEmpty()) {
                    musicController.addAfterCurrent(songs)
                    uiEvent.emit(UiEvent.ShowSnackbar(context.getString(R.string.feedback_playing_next, album.name)))
                } else {
                    uiEvent.emit(UiEvent.ShowSnackbar(context.getString(R.string.error_no_songs_found)))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                uiEvent.emit(UiEvent.ShowSnackbar(context.getString(R.string.error_fetching_songs_failed)))
            }
        }
    }

    fun onAddToQueue(album: Album, scope: CoroutineScope, uiEvent: MutableSharedFlow<UiEvent>) {
        scope.launch {
            try {
                val isLocal = album.sourceType == com.example.neosynth.domain.model.MusicSourceType.LOCAL_FILES
                val songs = playerHandler.getAlbumSongs(album.id, isLocal)
                if (songs.isNotEmpty()) {
                    musicController.addToQueue(songs)
                    uiEvent.emit(UiEvent.ShowSnackbar(context.getString(R.string.feedback_added_to_queue, album.name)))
                } else {
                    uiEvent.emit(UiEvent.ShowSnackbar(context.getString(R.string.error_no_songs_found)))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                uiEvent.emit(UiEvent.ShowSnackbar(context.getString(R.string.error_fetching_songs_failed)))
            }
        }
    }
}
