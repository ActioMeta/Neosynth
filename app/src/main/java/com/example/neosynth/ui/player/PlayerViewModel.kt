package com.example.neosynth.ui.player

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import com.example.neosynth.player.MusicController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    val musicController: MusicController,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _bitrateText = MutableStateFlow("MP3 • 320 kbps")
    val bitrateText: StateFlow<String> = _bitrateText

    private val _hasAudioPermission = MutableStateFlow(false)
    val hasAudioPermission: StateFlow<Boolean> = _hasAudioPermission

    fun updateBitrate(song: MediaItem?) {
        try {
            val extras = song?.mediaMetadata?.extras
            val json = extras?.getString("metadata")
            
            if (!json.isNullOrEmpty()) {
                val jsonObj = JSONObject(json)
                val bitrate = jsonObj.optInt("bitRate", 0)
                val format = jsonObj.optString("suffix", "").uppercase()
                
                if (bitrate > 0) {
                    _bitrateText.value = "$format • $bitrate kbps"
                } else {
                    val path = extras?.getString("path") ?: ""
                    val lowerPath = path.lowercase()
                    _bitrateText.value = when {
                        lowerPath.endsWith(".flac") -> "FLAC"
                        lowerPath.endsWith(".wav") -> "WAV"
                        lowerPath.endsWith(".m4a") -> "AAC • 256 kbps" 
                        else -> "MP3 • 320 kbps"
                    }
                }
            } else {
                 val path = extras?.getString("path") ?: ""
                 val lowerPath = path.lowercase()
                 _bitrateText.value = when {
                     lowerPath.endsWith(".flac") -> "FLAC"
                     lowerPath.endsWith(".wav") -> "WAV"
                     else -> "MP3 • 320 kbps"
                 }
            }
        } catch (e: Exception) {
            _bitrateText.value = "MP3 • 320 kbps"
        }
    }

    fun checkAudioPermission() {
        _hasAudioPermission.value = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun onPermissionResult(isGranted: Boolean) {
        _hasAudioPermission.value = isGranted
    }
}
