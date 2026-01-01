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
            _duration.value = player.duration.coerceAtLeast(0L)

            player.addListener(object : androidx.media3.common.Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    _currentMediaItem.value = mediaItem
                    _currentIndex.value = player.currentMediaItemIndex
                    updateQueue()
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                    if (isPlaying){
                        updateProgress()
                    }
                }
                override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                    _shuffleModeEnabled.value = shuffleModeEnabled
                }

                override fun onRepeatModeChanged(repeatMode: Int) {
                    _repeatMode.value = repeatMode
                }
            })
        }, MoreExecutors.directExecutor())
    }

    fun togglePlayPause() {
        browser?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    fun skipNext() { browser?.seekToNext() }
    fun skipPrevious() { browser?.seekToPrevious() }
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
                player.seekTo(index, 0L)
                player.play()
            }
        }
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

    private fun updateProgress() {
        val player = browser ?: return
        _currentPosition.value = player.currentPosition
        _duration.value = player.duration.coerceAtLeast(0L)

        if (player.isPlaying) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                updateProgress()
            }, 1000)
        }
    }

    fun seekTo(position: Long) {
        browser?.seekTo(position)
        _currentPosition.value = position
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
}