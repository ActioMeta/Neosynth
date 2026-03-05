package com.example.neosynth.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import com.google.common.util.concurrent.MoreExecutors
import androidx.media3.common.Player
import androidx.media3.common.C
import kotlin.math.abs
import android.graphics.drawable.BitmapDrawable
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest

@Singleton
class MusicController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _shuffleModeEnabled = mutableStateOf(false)
    val shuffleModeEnabled: State<Boolean> = _shuffleModeEnabled

    private val _repeatMode = mutableStateOf(Player.REPEAT_MODE_OFF)
    val repeatMode: State<Int> = _repeatMode

    private val _currentMediaItem = mutableStateOf<MediaItem?>(null)
    val currentMediaItem: State<MediaItem?> = _currentMediaItem
    
    private val _dominantColorInt = mutableStateOf(android.graphics.Color.DKGRAY)
    val dominantColorInt: State<Int> = _dominantColorInt
    
    private val _currentPosition = mutableStateOf(0L)
    val currentPosition: State<Long> = _currentPosition

    private val _currentQueue = mutableStateOf<List<MediaItem>>(emptyList())
    val currentQueue: State<List<MediaItem>> = _currentQueue
    
    private val _currentIndex = mutableStateOf(0)
    val currentIndex: State<Int> = _currentIndex

    private val _duration = mutableStateOf(0L)
    val duration: State<Long> = _duration

    private val _isPlaying = mutableStateOf(false)
    val isPlaying: State<Boolean> = _isPlaying

    private val _hasNext = mutableStateOf(false)
    val hasNext: State<Boolean> = _hasNext

    private val _hasPrevious = mutableStateOf(false)
    val hasPrevious: State<Boolean> = _hasPrevious

    private val _audioSessionId = mutableStateOf(0)
    val audioSessionId: State<Int> = _audioSessionId

    private var browserFuture: ListenableFuture<MediaBrowser>? = null
    val browser: MediaBrowser?
        get() = if (browserFuture?.isDone == true) browserFuture?.get() else null

    init {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        browserFuture = MediaBrowser.Builder(context, sessionToken).buildAsync()
        browserFuture?.addListener({
            val player = browser ?: return@addListener

            _isPlaying.value = player.isPlaying
            _currentMediaItem.value = player.currentMediaItem
            _shuffleModeEnabled.value = player.shuffleModeEnabled
            _repeatMode.value = player.repeatMode
            _shuffleModeEnabled.value = player.shuffleModeEnabled
            _repeatMode.value = player.repeatMode
            _duration.value = getDuration(player)
            
            // Try to get existing session ID if possible, though it might not be exposed directly on Player interface in all versions without the listener.
            // But we rely on the listener update mostly.

            player.addListener(object : androidx.media3.common.Player.Listener {
                // ... existing callbacks ...
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    _currentMediaItem.value = mediaItem
                    _currentIndex.value = player.currentMediaItemIndex
                    _duration.value = getDuration(player)
                    _currentPosition.value = player.currentPosition
                    updateQueue()
                    updateNavStates()
                    updateDominantColor(mediaItem)
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                    if (isPlaying){
                        updateProgress()
                    }
                }
                override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                    _shuffleModeEnabled.value = shuffleModeEnabled
                    updateNavStates()
                }

                override fun onRepeatModeChanged(repeatMode: Int) {
                    _repeatMode.value = repeatMode
                    updateNavStates()
                }
                
                override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                    updateQueue()
                    updateNavStates()
                }

                override fun onAudioSessionIdChanged(audioSessionId: Int) {
                    _audioSessionId.value = audioSessionId
                }
                
                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int
                ) {
                    // Detect when seek actually completes
                    if (reason == Player.DISCONTINUITY_REASON_SEEK || 
                        reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) {
                        // Seek completed successfully - this is the authoritative position update
                        _currentPosition.value = newPosition.positionMs
                        isSeeking.value = false
                        pendingSeekPosition = null
                    }
                }
            })
        }, MoreExecutors.directExecutor())
    }
    
    private fun updateNavStates() {
        browser?.let { player ->
            _hasNext.value = player.hasNextMediaItem()
            _hasPrevious.value = player.hasPreviousMediaItem()
        }
    }

    private fun updateDominantColor(mediaItem: MediaItem?) {
        val uri = mediaItem?.mediaMetadata?.artworkUri
        if (uri == null) {
            _dominantColorInt.value = android.graphics.Color.DKGRAY
            return
        }
        
        val loader = ImageLoader(context)
        val request = ImageRequest.Builder(context)
            .data(uri)
            .allowHardware(false)
            .target { result ->
                val bitmap = (result as? BitmapDrawable)?.bitmap
                if (bitmap != null) {
                    Palette.from(bitmap).generate { palette ->
                        val swatch = palette?.vibrantSwatch 
                            ?: palette?.darkVibrantSwatch 
                            ?: palette?.lightVibrantSwatch 
                            ?: palette?.mutedSwatch 
                            ?: palette?.dominantSwatch
                        
                        swatch?.rgb?.let { colorValue ->
                            val hsv = FloatArray(3)
                            android.graphics.Color.colorToHSV(colorValue, hsv)
                            if (hsv[1] < 0.5f) hsv[1] = (hsv[1] + 0.4f).coerceAtMost(0.9f)
                            if (hsv[2] < 0.3f) hsv[2] = 0.4f
                            _dominantColorInt.value = android.graphics.Color.HSVToColor(hsv)
                        } ?: run {
                            _dominantColorInt.value = android.graphics.Color.DKGRAY
                        }
                    }
                }
            }
            .build()
        loader.enqueue(request)
    }

    fun togglePlayPause() {
        browser?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    fun skipNext() { 
        isSeeking.value = true
        pendingSeekPosition = 0L
        _currentPosition.value = 0L
        browser?.seekToNext()
        resetSeekingStateDelayed()
    }
    fun skipPrevious() { 
        isSeeking.value = true
        pendingSeekPosition = 0L
        _currentPosition.value = 0L
        browser?.seekToPrevious() 
        resetSeekingStateDelayed()
    }
    
    fun skipPreviousOrRestart() {
        browser?.let { player ->
            if (player.currentPosition > 3000) {
                player.seekTo(0)
            } else {
                if (player.hasPreviousMediaItem()) {
                    player.seekToPrevious()
                } else {
                    player.seekTo(0)
                }
            }
        }
    }
    fun playQueue(mediaItems: List<MediaItem>, startIndex: Int) {
        browser?.let { player ->
            player.stop()
            player.clearMediaItems()
            player.setMediaItems(mediaItems, startIndex, 0L)
            player.prepare()
            player.play()
            _currentQueue.value = mediaItems
            _currentIndex.value = startIndex
        }
    }
    
    private fun updateQueue() {
        val player = browser ?: return
        val queue = mutableListOf<MediaItem>()
        for (i in 0 until player.mediaItemCount) {
            queue.add(player.getMediaItemAt(i))
        }
        _currentQueue.value = queue
        _currentIndex.value = player.currentMediaItemIndex
    }
    
    fun playFromQueue(index: Int) {
        browser?.let { player ->
            if (index in 0 until player.mediaItemCount) {
                isSeeking.value = true
                pendingSeekPosition = 0L
                _currentPosition.value = 0L
                player.seekTo(index, 0L)
                player.play()
                resetSeekingStateDelayed()
            }
        }
    }

    private fun resetSeekingStateDelayed() {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (isSeeking.value) {
                isSeeking.value = false
                pendingSeekPosition = null
            }
        }, 10000)
    }
    
    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        browser?.let { player ->
            if (fromIndex in 0 until player.mediaItemCount && 
                toIndex in 0 until player.mediaItemCount &&
                fromIndex != toIndex) {
                player.moveMediaItem(fromIndex, toIndex)
                updateQueue()
            }
        }
    }
    
    fun removeFromQueue(index: Int) {
        browser?.let { player ->
            if (index in 0 until player.mediaItemCount) {
                player.removeMediaItem(index)
                updateQueue()
            }
        }
    }

    private var lastSeekTime = 0L
    private var isSeeking = mutableStateOf(false)
    private var pendingSeekPosition: Long? = null

    private fun updateProgress() {
        val player = browser ?: return
        
        if (!isSeeking.value) {
            val playerPosition = player.currentPosition
            val pending = pendingSeekPosition
            
            // CRITICAL FIX: During streaming, player.currentPosition may return stale data
            // while buffering the new seek position. Only update if:
            // 1. No pending seek, OR
            // 2. Player position is close to pending position (within 3 seconds tolerance)
            if (pending == null || kotlin.math.abs(playerPosition - pending) < 3000) {
                _currentPosition.value = playerPosition
                _duration.value = getDuration(player)
            }
            // Otherwise: Keep showing the pending seek position until ExoPlayer confirms via onPositionDiscontinuity
        }

        if (player.isPlaying) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                updateProgress()
            }, 1000)
        }
    }

    fun seekTo(position: Long) {
        isSeeking.value = true
        pendingSeekPosition = position
        _currentPosition.value = position
        browser?.seekTo(position)
        
        // Failsafe: reset seeking flag after 10 seconds if onPositionDiscontinuity doesn't fire
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (isSeeking.value) {
                isSeeking.value = false
                pendingSeekPosition = null
            }
        }, 10000)
    }
    fun toggleShuffle() {
        val player = browser ?: return
        val newValue = !player.shuffleModeEnabled
        player.shuffleModeEnabled = newValue
        _shuffleModeEnabled.value = newValue
    }

    fun toggleRepeat() {
        val player = browser ?: return
        val nextMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        player.repeatMode = nextMode
        _repeatMode.value = nextMode
    }

    fun play() {
        browser?.play()
    }

    fun addToQueue(mediaItems: List<MediaItem>) {
        browser?.let { player ->
            player.addMediaItems(mediaItems)
            updateQueue()
        }
    }

    fun addAfterCurrent(mediaItems: List<MediaItem>) {
        browser?.let { player ->
            var index = player.currentMediaItemIndex + 1
            if (index < 0) index = 0 // Safety check
            if (index <= player.mediaItemCount) {
                player.addMediaItems(index, mediaItems)
            } else {
                player.addMediaItems(mediaItems)
            }
            updateQueue()
        }
    }
    
    fun clearQueue() {
        browser?.let { player ->
            player.stop()
            player.clearMediaItems()
            _currentQueue.value = emptyList()
            _currentIndex.value = 0
            _currentMediaItem.value = null
            _isPlaying.value = false
        }
    }
    
    private fun getDuration(player: Player): Long {
        val duration = player.duration
        if (duration == C.TIME_UNSET || duration <= 0) {
            val metadataDuration = player.currentMediaItem?.mediaMetadata?.extras?.getLong("duration") ?: 0L
            return if (metadataDuration > 0) metadataDuration else 0L
        }
        return duration
    }
}