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
import kotlinx.coroutines.launch

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

    private var originalQueue: List<MediaItem>? = null

    init {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        browserFuture = MediaBrowser.Builder(context, sessionToken).buildAsync()
        browserFuture?.addListener({
            val player = browser ?: return@addListener

            _isPlaying.value = player.isPlaying
            _currentMediaItem.value = player.currentMediaItem
            _shuffleModeEnabled.value = _shuffleModeEnabled.value // Manage shuffle internally
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
                
                // We no longer rely on ExoPlayer's internal shuffle reporting since we do manual queue rewriting
                // override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                //    _shuffleModeEnabled.value = shuffleModeEnabled
                //    updateNavStates()
                // }

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
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
                        try {
                            val palette = Palette.from(bitmap).generate()
                            val swatch = palette.vibrantSwatch 
                                ?: palette.darkVibrantSwatch 
                                ?: palette.lightVibrantSwatch 
                                ?: palette.mutedSwatch 
                                ?: palette.dominantSwatch
                            
                            swatch?.rgb?.let { colorValue ->
                                val hsv = FloatArray(3)
                                android.graphics.Color.colorToHSV(colorValue, hsv)
                                if (hsv[1] < 0.5f) hsv[1] = (hsv[1] + 0.4f).coerceAtMost(0.9f)
                                if (hsv[2] < 0.3f) hsv[2] = 0.4f
                                val finalColor = android.graphics.Color.HSVToColor(hsv)
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    _dominantColorInt.value = finalColor
                                }
                            } ?: run {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    _dominantColorInt.value = android.graphics.Color.DKGRAY
                                }
                            }
                        } catch (e: Exception) {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                _dominantColorInt.value = android.graphics.Color.DKGRAY
                            }
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
            
            // If we start a new queue, the "original" ordering is the one we just passed in.
            // Even if shuffle is on, we consider this new queue as the baseline original.
            originalQueue = mediaItems
            
            // If shuffle is already turned on globally, re-apply the shuffle immediately 
            // over this new queue to avoid it playing linearly.
            if (_shuffleModeEnabled.value) {
                applyManualShuffle(player, mediaItems, startIndex)
            }
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
        val newValue = !_shuffleModeEnabled.value
        _shuffleModeEnabled.value = newValue
        
        val currentQueueContent = _currentQueue.value
        if (currentQueueContent.isEmpty()) return

        if (newValue) {
            // Enable shuffle
            // Important: do not overwrite originalQueue if a valid one exists, unless it's missing
            if (originalQueue == null || originalQueue!!.size != currentQueueContent.size) {
                 originalQueue = currentQueueContent
            }
            applyManualShuffle(player, currentQueueContent, player.currentMediaItemIndex)
        } else {
            // Disable shuffle: Restore original queue
            originalQueue?.let { origQueue ->
                // Apply the original order by moving items back to their original positions
                val currentQueueItems = _currentQueue.value
                if (currentQueueItems.size == origQueue.size) {
                    // Start from index 1, moving each item to its correct original position
                    for (i in 0 until origQueue.size) {
                        val originalItem = origQueue[i]
                        val currentIndex = player.currentMediaItemIndex
                        // Find where this original item is currently sitting
                        var currentPos = -1
                        for (j in 0 until player.mediaItemCount) {
                            if (player.getMediaItemAt(j).mediaId == originalItem.mediaId) {
                                currentPos = j
                                break
                            }
                        }
                        
                        if (currentPos != -1 && currentPos != i) {
                            player.moveMediaItem(currentPos, i)
                        }
                    }
                }
            }
        }
    }

    private fun applyManualShuffle(player: Player, baseQueue: List<MediaItem>, currentIndex: Int) {
        if (baseQueue.size <= 1) return
        
        // We use moveMediaItem to shift all items *after* the current index into a shuffled order.
        // We do this by swapping each item at i (from currentIndex+1 to end) with another random index.
        val targetIndices = (0 until baseQueue.size).toMutableList()
        targetIndices.remove(currentIndex)
        targetIndices.shuffle()
        
        // Now targetIndices contains the new randomized positions for all items except the current one.
        // But the easiest way to shuffle the remaining queue in ExoPlayer without cutting audio
        // is to bring all of them to the front (right after current index) or back in a random order.
        
        // Let's create a list of the media IDs we want to put sequentially *after* the current index.
        val remainingIds = baseQueue.map { it.mediaId }.toMutableList()
        val playingId = remainingIds.removeAt(currentIndex)
        remainingIds.shuffle()
        
        // Now we just move each of these remainingIds to position (currentIndex + 1 + ... )
        var insertPosition = currentIndex + 1
        
        for (id in remainingIds) {
            // find where 'id' currently is
            var foundPos = -1
            for (j in 0 until player.mediaItemCount) {
                if (player.getMediaItemAt(j).mediaId == id) {
                    foundPos = j
                    break
                }
            }
            
            if (foundPos != -1) {
                if (foundPos != insertPosition) {
                    player.moveMediaItem(foundPos, insertPosition)
                }
                insertPosition++
            }
        }
        
        // If the current playing index wasn't 0, we can also shift the playing item and all its followers to the top
        if (currentIndex > 0) {
            // Move the playing item to 0
            player.moveMediaItem(player.currentMediaItemIndex, 0)
            
            // Move the rest of the items up sequentially
            var currentPosOfShuffled = 1
            for (i in 0 until remainingIds.size) {
                // Where is the next shuffled item now?
                val id = remainingIds[i]
                var foundPos = -1
                for (j in 0 until player.mediaItemCount) {
                    if (player.getMediaItemAt(j).mediaId == id) {
                        foundPos = j
                        break
                    }
                }
                if (foundPos != -1 && foundPos != currentPosOfShuffled) {
                    player.moveMediaItem(foundPos, currentPosOfShuffled)
                }
                currentPosOfShuffled++
            }
        }
        
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
            if (_shuffleModeEnabled.value && originalQueue != null) {
                originalQueue = originalQueue!! + mediaItems
            }
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
            
            if (_shuffleModeEnabled.value && originalQueue != null) {
                val origPlayingItem = player.currentMediaItem
                val origIndex = originalQueue!!.indexOfFirst { it.mediaId == origPlayingItem?.mediaId }.takeIf { it >= 0 } ?: -1
                originalQueue = if (origIndex >= 0 && origIndex < originalQueue!!.size) {
                    val mutated = originalQueue!!.toMutableList()
                    mutated.addAll(origIndex + 1, mediaItems)
                    mutated
                } else {
                    originalQueue!! + mediaItems
                }
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
            originalQueue = null
            _shuffleModeEnabled.value = false
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