package com.example.neosynth.ui.home.logic

import android.util.Log
import com.example.neosynth.data.model.LyricsResult
import com.example.neosynth.data.repository.LyricsRepository
import com.example.neosynth.player.MusicController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class HomeLyricsHandler @Inject constructor(
    private val lyricsRepository: LyricsRepository,
    private val musicController: MusicController
) {

    private val _currentLyrics = MutableStateFlow<String?>(null)
    val currentLyrics: StateFlow<String?> = _currentLyrics.asStateFlow()
    
    private val _lyricsOptions = MutableStateFlow<List<LyricsResult>>(emptyList())
    val lyricsOptions: StateFlow<List<LyricsResult>> = _lyricsOptions.asStateFlow()
    
    private val _showLyricsSelection = MutableStateFlow(false)
    val showLyricsSelection: StateFlow<Boolean> = _showLyricsSelection.asStateFlow()
    
    private val _isLoadingLyrics = MutableStateFlow(false)
    val isLoadingLyrics: StateFlow<Boolean> = _isLoadingLyrics.asStateFlow()
    
    private val _lyricsError = MutableStateFlow<String?>(null)
    val lyricsError: StateFlow<String?> = _lyricsError.asStateFlow()

    fun loadLyrics(scope: CoroutineScope) {
        scope.launch {
            val mediaItem = musicController.currentMediaItem.value
            if (mediaItem == null) {
                clearLyrics()
                return@launch
            }
            
            _isLoadingLyrics.value = true
            _lyricsError.value = null
            _lyricsOptions.value = emptyList()
            
            try {
                val artist = mediaItem.mediaMetadata.artist?.toString() ?: ""
                val title = mediaItem.mediaMetadata.title?.toString() ?: ""
                val album = mediaItem.mediaMetadata.albumTitle?.toString()
                val duration = mediaItem.mediaMetadata.extras?.getLong("duration")?.toInt()?.div(1000)
                
                Log.d("HomeLyricsHandler", "Fetching lyrics options: $artist - $title")
                
                val results = lyricsRepository.searchLyricsOptions(
                    artist = artist,
                    title = title,
                    album = album,
                    duration = duration
                )
                
                _lyricsOptions.value = results
                
                if (results.isNotEmpty()) {
                    _showLyricsSelection.value = true
                    _lyricsError.value = null
                } else {
                    _showLyricsSelection.value = false
                    _lyricsError.value = "No se encontraron letras para esta canción"
                }
            } catch (e: Exception) {
                Log.e("HomeLyricsHandler", "Error loading lyrics", e)
                _lyricsOptions.value = emptyList()
                _showLyricsSelection.value = false
                _lyricsError.value = "Error al buscar letras: ${e.message}"
            } finally {
                _isLoadingLyrics.value = false
            }
        }
    }
    
    fun selectLyric(result: LyricsResult) {
        _currentLyrics.value = result.lyric
        _showLyricsSelection.value = false
    }
    
    fun dismissLyricsSelection() {
        _showLyricsSelection.value = false
    }
    
    fun clearLyrics() {
        _currentLyrics.value = null
        _lyricsOptions.value = emptyList()
        _showLyricsSelection.value = false
        _lyricsError.value = null
    }
}
