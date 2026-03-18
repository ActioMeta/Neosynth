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
import com.example.neosynth.data.local.ServerDao
import com.example.neosynth.data.remote.NavidromeApiService
import com.example.neosynth.ui.playlist.logic.PlaylistDownloadHandler
import com.example.neosynth.ui.playlist.logic.PlaylistManagementHandler
import com.example.neosynth.data.remote.responses.SongDto
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    val musicController: MusicController,
    private val serverDao: ServerDao,
    private val api: NavidromeApiService,
    private val downloadHandler: PlaylistDownloadHandler,
    private val managementHandler: PlaylistManagementHandler,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _bitrateText = MutableStateFlow("MP3 • 320 kbps")
    val bitrateText: StateFlow<String> = _bitrateText

    private val _hasAudioPermission = MutableStateFlow(false)
    val hasAudioPermission: StateFlow<Boolean> = _hasAudioPermission

    fun updateBitrate(song: MediaItem?) {
        try {
            val extras = song?.mediaMetadata?.extras
            var bitrate = extras?.getInt("bitRate") ?: 0
            var format = extras?.getString("suffix")?.uppercase() ?: extras?.getString("format")?.uppercase() ?: ""

            val json = extras?.getString("metadata")
            if (!json.isNullOrEmpty()) {
                val jsonObj = JSONObject(json)
                if (bitrate == 0) bitrate = jsonObj.optInt("bitRate", 0)
                if (format.isEmpty()) format = jsonObj.optString("suffix", jsonObj.optString("format", "")).uppercase()
            }
            
            if (bitrate > 0 && format.isNotEmpty()) {
                _bitrateText.value = "$format • $bitrate kbps"
            } else if (bitrate > 0) {
                _bitrateText.value = "MP3 • $bitrate kbps"
            } else if (format.isNotEmpty() && format != "MP3") {
                // Para FLAC o WAV que usualmente reportan 0 kbps desde el servidor
                _bitrateText.value = format
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

    private val _isProcessingQueueAction = MutableStateFlow(false)
    val isProcessingQueueAction: StateFlow<Boolean> = _isProcessingQueueAction

    fun saveQueueAsPlaylist(name: String, onComplete: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            _isProcessingQueueAction.value = true
            try {
                val server = serverDao.getActiveServer()
                if (server == null) {
                    onError("No hay un servidor activo")
                    return@launch
                }
                
                // Get current queue songs IDs
                val currentQueue = musicController.currentQueue.value
                val songIds = currentQueue.map { it.mediaId }
                
                if (songIds.isEmpty()) {
                    onError("La cola está vacía")
                    return@launch
                }
                
                // Construct comma separated list
                val songIdParams = songIds.take(100).joinToString(",") // Navidrome might have limits, truncate to 100 or send in batches (the API accepts them as params, we will use a comma joined string)
                
                val response = api.createPlaylist(
                    name = name,
                    songId = songIds, // The API accepts a List<String>. Retrofit will pass multiple ?songId= variables
                    u = server.username,
                    t = server.token,
                    s = server.salt
                )
                
                if (response.response.status == "ok") {
                    onComplete()
                } else {
                    onError("Error al crear la playlist")
                }
            } catch (e: Exception) {
                onError(e.message ?: "Error desconocido al crear playlist")
            } finally {
                _isProcessingQueueAction.value = false
            }
        }
    }

    fun downloadQueue() {
        viewModelScope.launch {
            val server = serverDao.getActiveServer() ?: return@launch
            val queue = musicController.currentQueue.value
            if (queue.isEmpty()) return@launch
            
            // Map exoPlayer MediaItems back to SongDto mock structures for the download handler
            val songsToDownload = queue.map { item ->
                val extras = item.mediaMetadata.extras
                SongDto(
                    id = item.mediaId,
                    title = item.mediaMetadata.title?.toString() ?: "Unknown",
                    artist = item.mediaMetadata.artist?.toString() ?: "Unknown",
                    album = item.mediaMetadata.albumTitle?.toString() ?: "Unknown",
                    duration = (extras?.getLong("duration") ?: 0L).toInt() / 1000,
                    coverArt = item.mediaMetadata.artworkUri?.toString()?.substringAfterLast("id=")?.substringBefore("&") ?: item.mediaId
                )
            }
            
            downloadHandler.downloadPlaylist(
                allSongs = songsToDownload,
                server = server,
                playlistId = "queue_${System.currentTimeMillis()}", // Mock ID since it's an ad-hoc queue
                playlistName = "Cola de reproducción",
                scope = viewModelScope
            )
        }
    }
}
