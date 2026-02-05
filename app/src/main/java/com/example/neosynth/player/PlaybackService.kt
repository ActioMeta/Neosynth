package com.example.neosynth.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import android.media.audiofx.AudioEffect
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.example.neosynth.MainActivity
import com.example.neosynth.data.preferences.SettingsPreferences
import com.example.neosynth.player.audio.CrossfeedAudioProcessor
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
    
    private val crossfeedProcessor = CrossfeedAudioProcessor()

    override fun onCreate() {
        super.onCreate()
        
        // Configure audio attributes for music playback
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
            
        // Custom RenderersFactory to inject AudioProcessors
        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: android.content.Context, 
                enableFloatOutput: Boolean, 
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink? {
                // Determine if we should use default construction or custom
                // Note: The signature of buildAudioSink varies by Media3 version.
                // We'll use a safer approach: creating DefaultAudioSink with our processor.
                return DefaultAudioSink.Builder()
                    .setAudioProcessors(arrayOf(crossfeedProcessor))
                    .build()
            }
        }
        
        exoPlayer = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        
        // Crear PendingIntent para abrir MainActivity al hacer click en la notificación
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
            
        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(pendingIntent) // Configurar acción al hacer click
            .build()
        
        // Observe audio settings and apply them
        observeAudioSettings()
        
        // Permanent Listener for Queue Cleanup and Audio Session
        exoPlayer.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                // Queue Optimization: Keep only the 3 most recent previous songs
                val keepHistory = 3
                val currentIndex = exoPlayer.currentMediaItemIndex
                if (currentIndex > keepHistory) {
                    // Remove current 0 to (index - keep)
                    // Example: Index 4. Keep 3. Remove 0 to 1 (Item 0).
                    // This shifts indices, keeping the specific "previous" items we want.
                    exoPlayer.removeMediaItems(0, currentIndex - keepHistory)
                }
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                    broadcastAudioSessionId(audioSessionId, AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
                }
            }
        })
        
        // Initial broadcast if session is already ready
        if (exoPlayer.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
            broadcastAudioSessionId(exoPlayer.audioSessionId, AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
        }
    }
    
    private fun broadcastAudioSessionId(sessionId: Int, action: String) {
        val intent = Intent(action)
        intent.putExtra("android.media.audiofx.extra.AUDIO_SESSION_ID", sessionId)
        intent.putExtra("android.media.audiofx.extra.PACKAGE_NAME", packageName)
        intent.putExtra("android.media.audiofx.extra.CONTENT_TYPE", 0) // CONTENT_TYPE_MUSIC = 0
        sendBroadcast(intent)
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
                
                // Update Crossfeed
                crossfeedProcessor.setEnabled(settings.crossfeedEnabled)
                crossfeedProcessor.setStrength(settings.crossfeedStrength)
                
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
                    animateVolume(from = 0.3f, to = if (exoPlayer.volume > 0.9f) 1.0f else 0.85f, durationMs = (durationSeconds * 1000).toLong())
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
        if (::exoPlayer.isInitialized && exoPlayer.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
             broadcastAudioSessionId(exoPlayer.audioSessionId, AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        }
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