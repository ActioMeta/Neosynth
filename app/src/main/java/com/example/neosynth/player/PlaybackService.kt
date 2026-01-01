package com.example.neosynth.player

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.neosynth.data.preferences.SettingsPreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private lateinit var exoPlayer: ExoPlayer
    
    @Inject
    lateinit var settingsPreferences: SettingsPreferences
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        
        // Configure audio attributes for music playback
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
        
        exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            
        mediaSession = MediaSession.Builder(this, exoPlayer).build()
        
        // Observe audio settings and apply them
        observeAudioSettings()
    }
    
    private fun observeAudioSettings() {
        serviceScope.launch {
            settingsPreferences.audioSettings.collectLatest { settings ->
                // Apply volume normalization
                if (settings.normalizeVolume) {
                    exoPlayer.volume = 0.85f // Slightly lower to prevent clipping
                } else {
                    exoPlayer.volume = 1.0f
                }
                
                // Note: ExoPlayer doesn't have built-in crossfade
                // We'll implement it manually in the listener
                if (settings.crossfadeEnabled) {
                    setupCrossfadeListener(settings.crossfadeDuration)
                } else {
                    removeCrossfadeListener()
                }
            }
        }
    }
    
    private var crossfadeListener: Player.Listener? = null
    
    private fun setupCrossfadeListener(durationSeconds: Int) {
        removeCrossfadeListener()
        
        // This is a simplified crossfade - real implementation would need
        // two players or more advanced audio mixing
        crossfadeListener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    // Fade in new track
                    animateVolume(from = 0.3f, to = if (exoPlayer.volume > 0.9f) 1.0f else 0.85f, durationMs = (durationSeconds * 500).toLong())
                }
            }
        }
        
        exoPlayer.addListener(crossfadeListener!!)
    }
    
    private fun removeCrossfadeListener() {
        crossfadeListener?.let { 
            exoPlayer.removeListener(it)
            crossfadeListener = null
        }
    }
    
    private fun animateVolume(from: Float, to: Float, durationMs: Long) {
        val steps = 20
        val stepDelay = durationMs / steps
        val stepSize = (to - from) / steps
        
        serviceScope.launch {
            var currentVolume = from
            repeat(steps) {
                currentVolume += stepSize
                exoPlayer.volume = currentVolume.coerceIn(0f, 1f)
                kotlinx.coroutines.delay(stepDelay)
            }
            exoPlayer.volume = to
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession
    
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player?.playWhenReady == false) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        removeCrossfadeListener()
        serviceScope.launch { } // Cancel scope
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}