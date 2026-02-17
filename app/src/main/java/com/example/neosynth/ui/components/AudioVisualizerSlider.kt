package com.example.neosynth.ui.components

import android.media.audiofx.Visualizer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.hypot

@Composable
fun AudioVisualizerSlider(
    modifier: Modifier = Modifier,
    audioSessionId: Int,
    progress: Float, // 0f to 1f
    onProgressChange: (Float) -> Unit,
    color: Color = Color.White
) {
    var rawWaveform by remember { mutableStateOf(ByteArray(0)) }
    
    // Safety check for session ID - REMOVED EARLY RETURN
    // if (audioSessionId == 0) return

    DisposableEffect(audioSessionId) {
        if (audioSessionId == 0) return@DisposableEffect onDispose { }

        var visualizer: Visualizer? = null
        try {
            visualizer = Visualizer(audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1] // Max size
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int
                    ) {
                        waveform?.let { rawWaveform = it }
                    }

                    override fun onFftDataCapture(
                        visualizer: Visualizer?,
                        fft: ByteArray?,
                        samplingRate: Int
                    ) {
                        // FFT not used for now
                    }
                }, Visualizer.getMaxCaptureRate() / 2, true, false)
                enabled = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        onDispose {
            visualizer?.release()
        }
    }
    
    // Interaction state
    var isDragging by remember { mutableStateOf(false) }
    var isUserSeeking by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    
    // Sync with progress only when not interacting
    val currentProgress = if (isDragging || isUserSeeking) {
        dragProgress
    } else {
        progress
    }

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val newProgress = (offset.x / size.width).coerceIn(0f, 1f)
                    isUserSeeking = true
                    dragProgress = newProgress
                    onProgressChange(newProgress)
                    
                    // Reset seeking flag after delay
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        isUserSeeking = false
                    }, 500)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        isUserSeeking = true
                        dragProgress = (offset.x / size.width).coerceIn(0f, 1f)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        dragProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        isDragging = false
                        onProgressChange(dragProgress)
                        
                        // Reset seeking flag after delay
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            isUserSeeking = false
                        }, 500)
                    },
                    onDragCancel = {
                        isDragging = false
                        isUserSeeking = false
                    }
                )
            }
    ) {
        val width = size.width
        val height = size.height
        val barCount = 60 // Number of bars to draw
        val barWidth = width / barCount
        val centerY = height / 2
        
        // Draw Track (Inactive / Background)
        drawLine(
            color = color.copy(alpha = 0.3f),
            start = Offset(0f, centerY),
            end = Offset(width, centerY),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round
        )

        val thumbX = currentProgress * width

        // Draw active track (Standard progress bar fallback)
        drawLine(
            color = color,
            start = Offset(0f, centerY),
            end = Offset(thumbX, centerY),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Draw Waveform
        if (rawWaveform.isNotEmpty()) {
            val step = rawWaveform.size / barCount
            
            for (i in 0 until barCount) {
                val index = i * step
                if (index < rawWaveform.size) {
                    // Visualizer provides 8-bit unsigned PCM data (0..255).
                    // In Kotlin/Java, byte is signed (-128..127).
                    // 128 (unsigned) roughly corresponds to 0 (silence).
                    // We interpret 0x80 (-128 signed) as the center.
                    
                    val rawByte = rawWaveform[index]
                    val unsignedByte = rawByte.toInt() and 0xFF // Convert to 0..255
                    
                    // Center is 128. Silence is ~128.
                    // Amplitude is distance from 128.
                    val amplitude = abs(unsignedByte - 128) / 128f
                    
                    // Boost amplitude and set minimum visibility
                    val boostedAmp = (amplitude * 5f).coerceAtMost(1f) 
                    
                    // Lower threshold significantly to catch potential low volume
                    if (boostedAmp > 0.02f) { // Lowered form 0.05f to 0.02f
                         val barHeight = (height * 0.8f) * boostedAmp
                         val x = i * barWidth + (barWidth / 2)
                         
                         // Determine color based on progress (Played vs Unplayed)
                         val isPast = x <= thumbX
                         val barColor = if (isPast) color else color.copy(alpha = 0.5f)
                         
                         drawLine(
                            color = barColor,
                            start = Offset(x, centerY - barHeight / 2),
                            end = Offset(x, centerY + barHeight / 2),
                            strokeWidth = barWidth * 0.6f,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
        }
        
        // Draw Thumb
        drawCircle(
            color = color,
            radius = if (isDragging) 18f else 12f,
            center = Offset(thumbX, centerY)
        )
    }
}
