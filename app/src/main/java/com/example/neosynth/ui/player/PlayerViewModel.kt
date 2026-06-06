package com.example.neosynth.ui.player

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import com.example.neosynth.data.local.ServerDao
import com.example.neosynth.data.remote.NavidromeApiService
import com.example.neosynth.data.remote.responses.SongDto
import com.example.neosynth.player.MusicController
import com.example.neosynth.ui.playlist.logic.PlaylistDownloadHandler
import com.example.neosynth.ui.playlist.logic.PlaylistManagementHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject
import com.example.neosynth.R

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
                    onError(context.getString(R.string.error_no_active_server))
                    return@launch
                }
                
                val currentQueue = musicController.currentQueue.value
                val songIds = currentQueue.map { it.mediaId }
                
                if (songIds.isEmpty()) {
                    onError(context.getString(R.string.error_queue_empty))
                    return@launch
                }
                
                val response = api.createPlaylist(
                    name = name,
                    songId = songIds,
                    u = server.username,
                    t = server.token,
                    s = server.salt
                )
                
                if (response.response.status == "ok") {
                    onComplete()
                } else {
                    onError(context.getString(R.string.error_create_playlist_failed))
                }
            } catch (e: Exception) {
                onError(e.message ?: context.getString(R.string.error_create_playlist_unknown))
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
            
            val songsToDownload = queue.map { item ->
                val extras = item.mediaMetadata.extras
                SongDto(
                    id = item.mediaId,
                    title = item.mediaMetadata.title?.toString() ?: context.getString(R.string.unknown_title),
                    artist = item.mediaMetadata.artist?.toString() ?: context.getString(R.string.unknown_artist),
                    album = item.mediaMetadata.albumTitle?.toString() ?: context.getString(R.string.unknown_album),
                    duration = (extras?.getLong("duration") ?: 0L).toInt() / 1000,
                    coverArt = item.mediaMetadata.artworkUri?.toString()?.substringAfterLast("id=")?.substringBefore("&") ?: item.mediaId
                )
            }
            
            downloadHandler.downloadPlaylist(
                allSongs = songsToDownload,
                server = server,
                playlistId = "queue_${System.currentTimeMillis()}",
                playlistName = context.getString(R.string.queue_playing_name),
                scope = viewModelScope
            )
        }
    }

    fun startSleepTimer(durationMs: Long) {
        musicController.startSleepTimer(durationMs)
    }

    fun startSleepTimerAtEndOfSong() {
        musicController.startSleepTimerAtEndOfSong()
    }

    fun cancelSleepTimer() {
        musicController.cancelSleepTimer()
    }
}
