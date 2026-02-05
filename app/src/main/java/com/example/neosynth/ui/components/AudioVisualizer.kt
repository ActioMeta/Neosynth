package com.example.neosynth.ui.components

import android.media.audiofx.Visualizer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

@Composable
fun AudioVisualizerSlider(
    modifier: Modifier = Modifier,
    audioSessionId: Int,
    progress: Float, // 0f to 1f
    onProgressChange: (Float) -> Unit,
    color: Color = Color.White
) {
    var bytes by remember { mutableStateOf<ByteArray?>(null) }
    
    // Manage Visualizer
    DisposableEffect(audioSessionId) {
        if (audioSessionId == 0) return@DisposableEffect onDispose { }

        val visualizer = try {
            Visualizer(audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1] // Max size
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                        if (waveform != null) {
                            bytes = waveform
                        }
                    }
                    override fun onFftDataCapture(visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                        // Not used
                    }
                }, Visualizer.getMaxCaptureRate() / 2, true, false)
                enabled = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }

        onDispose {
            visualizer?.enabled = false
            visualizer?.release()
        }
    }

    var draggingProgress by remember { mutableFloatStateOf(-1f) }
    val currentDisplayProgress = if (draggingProgress >= 0f) draggingProgress else progress

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val newProgress = (offset.x / size.width).coerceIn(0f, 1f)
                    onProgressChange(newProgress)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        draggingProgress = (offset.x / size.width).coerceIn(0f, 1f)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        draggingProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                        // Optional: Call onProgressChange immediately if you want live seeking
                    },
                    onDragEnd = {
                        onProgressChange(draggingProgress)
                        draggingProgress = -1f
                    },
                    onDragCancel = {
                        draggingProgress = -1f
                    }
                )
            }
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        
        // Draw track (background)
        drawLine(
            color = color.copy(alpha = 0.3f),
            start = Offset(0f, centerY),
            end = Offset(width, centerY),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Draw progress
        val progressX = width * currentDisplayProgress
        drawLine(
            color = color,
            start = Offset(0f, centerY),
            end = Offset(progressX, centerY),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Draw Visualizer (Waveform) overlay
        bytes?.let { waveform ->
            val path = Path()
            val points = waveform.size
            // We want to draw the waveform across the active part? Or the whole part?
            // "Real-time audio feedback" -> Usually the current instant's wave.
            // Let's draw it centered on the thumb or across the whole bar?
            // If it replaces the progress bar, maybe the *whole bar* vibrates?
            
            // Let's draw the waveform across the whole width, centered vertically.
            // But usually Visualizer gives a small buffer (e.g. 1024 bytes).
            // We map x from 0 to width.
            
            // Draw a mirrored waveform
            val baseAmplitude = height / 2f
            
            path.moveTo(0f, centerY)
            
            for (i in 0 until points step 4) { // Step to reduce points
                val x = (i.toFloat() / points) * width
                // Waveform values are 0..255 (unsigned byte). 128 is silence.
                // Convert to -128..127
                val raw = (waveform[i].toInt() and 0xFF) - 128
                val amplitude = (raw / 128f) * baseAmplitude // Scale to height
                
                // Smooth modulation by progress? No, it's real time.
                path.lineTo(x, centerY - amplitude)
            }
            
            drawPath(
                path = path,
                color = color.copy(alpha = 0.6f),
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // Draw Thumb
        drawCircle(
            color = color,
            radius = 8.dp.toPx(),
            center = Offset(progressX, centerY)
        )
    }
}
