package com.example.neosynth.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import android.media.audiofx.AudioEffect
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.example.neosynth.MainActivity
import com.example.neosynth.data.preferences.SettingsPreferences
import com.example.neosynth.player.audio.CrossfeedAudioProcessor
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private lateinit var exoPlayer: ExoPlayer
    
    @Inject
    lateinit var settingsPreferences: SettingsPreferences

    @Inject
    lateinit var statsRepository: com.example.neosynth.data.repository.StatsRepository
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private val crossfeedProcessor = CrossfeedAudioProcessor()
    private var preloadJob: Job? = null
    private var playbackTrackerJob: Job? = null
    private var currentTrackLogged = false

    override fun onCreate() {
        super.onCreate()
        
        // Configure audio attributes for music playback
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
            
        // Custom LoadControl for better streaming and seeking behavior
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15000,  // Min buffer: 15s
                50000,  // Max buffer: 50s
                2500,   // Playback buffer: 2.5s
                5000    // Playback rebuffer: 5s
            )
            .build()

        // Setup cache datasource factory for gapless & preloading
        val cache = MediaCacheManager.getCache(this)
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
        val defaultDataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(defaultDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            
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
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(cacheDataSourceFactory))
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setLoadControl(loadControl)
            .setSeekParameters(SeekParameters.EXACT)
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
                // Preload the next song in the queue
                val nextIndex = exoPlayer.currentMediaItemIndex + 1
                if (nextIndex < exoPlayer.mediaItemCount) {
                    val nextItem = exoPlayer.getMediaItemAt(nextIndex)
                    preloadMediaItem(nextItem)
                }

                // Track stats
                playbackTrackerJob?.cancel()
                currentTrackLogged = false
                
                if (mediaItem != null) {
                    val songId = mediaItem.mediaId
                    val title = mediaItem.mediaMetadata.title?.toString() ?: ""
                    val artist = mediaItem.mediaMetadata.artist?.toString() ?: ""
                    
                    playbackTrackerJob = serviceScope.launch {
                        var duration = exoPlayer.duration
                        // Wait for duration to load (up to 5 seconds)
                        var waitCount = 0
                        while ((duration <= 0 || duration == C.TIME_UNSET) && waitCount < 10) {
                            kotlinx.coroutines.delay(500)
                            duration = exoPlayer.duration
                            waitCount++
                        }
                        
                        val targetTimeMs = if (duration > 0 && duration != C.TIME_UNSET) {
                            minOf(30_000L, duration / 2)
                        } else {
                            30_000L
                        }
                        
                        var elapsedMs = 0L
                        val checkIntervalMs = 1000L
                        var historyId: Long? = null
                        
                        try {
                            while (exoPlayer.currentMediaItem?.mediaId == songId) {
                                kotlinx.coroutines.delay(checkIntervalMs)
                                if (exoPlayer.isPlaying && exoPlayer.currentMediaItem?.mediaId == songId) {
                                    elapsedMs += checkIntervalMs
                                    
                                    if (elapsedMs >= targetTimeMs && historyId == null) {
                                        historyId = statsRepository.recordPlayback(
                                            songId = songId,
                                            title = title,
                                            artist = artist,
                                            durationListened = elapsedMs
                                        )
                                        currentTrackLogged = true
                                    } else if (historyId != null && elapsedMs % 5000L == 0L) {
                                        statsRepository.updateDurationListened(historyId, elapsedMs)
                                    }
                                }
                            }
                        } finally {
                            historyId?.let { id ->
                                if (elapsedMs > 0) {
                                    statsRepository.updateDurationListened(id, elapsedMs)
                                }
                            }
                        }
                    }
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

    private fun preloadMediaItem(mediaItem: androidx.media3.common.MediaItem) {
        preloadJob?.cancel()
        
        val uri = mediaItem.localConfiguration?.uri ?: return
        val scheme = uri.scheme
        if (scheme != "http" && scheme != "https") return
        
        preloadJob = serviceScope.launch(Dispatchers.IO) {
            try {
                // Wait 2s to not compete with the active player's initial load bandwidth
                kotlinx.coroutines.delay(2000)
                
                val cache = MediaCacheManager.getCache(this@PlaybackService)
                val httpDataSource = DefaultHttpDataSource.Factory()
                    .setAllowCrossProtocolRedirects(true)
                    .createDataSource()
                
                val cacheDataSource = CacheDataSource(
                    cache,
                    httpDataSource,
                    CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
                )
                
                val dataSpec = DataSpec.Builder()
                    .setUri(uri)
                    .setPosition(0)
                    .setLength(2 * 1024 * 1024) // 2MB preload limit
                    .build()
                    
                val buffer = ByteArray(128 * 1024)
                val cacheWriter = CacheWriter(
                    cacheDataSource,
                    dataSpec,
                    buffer,
                    null
                )
                
                cacheWriter.cache()
                android.util.Log.d("PlaybackService", "Successfully preloaded next track: ${mediaItem.mediaId}")
            } catch (e: Exception) {
                android.util.Log.e("PlaybackService", "Failed to preload next track: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        preloadJob?.cancel()
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