package com.example.neosynth.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.example.neosynth.player.MusicController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimatedPlayerSlider(musicController: MusicController) {
    val currentPosition by musicController.currentPosition
    val duration by musicController.duration

    val interactionSource = remember { MutableInteractionSource() }
    val isDragging by interactionSource.collectIsDraggedAsState()
    
    // Animación de scale cuando se arrastra
    val thumbScale by animateFloatAsState(
        targetValue = if (isDragging) 1.2f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "thumb_scale"
    )

    // Local state for dragging to prevent jumping
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var isUserSeeking by remember { mutableStateOf(false) }

    // Sync sliderPosition with currentPosition when NOT interacting
    LaunchedEffect(currentPosition, isDragging, isUserSeeking) {
        if (!isDragging && !isUserSeeking) {
            sliderPosition = currentPosition.toFloat()
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = sliderPosition,
            onValueChange = { 
                isUserSeeking = true
                sliderPosition = it
            },
            onValueChangeFinished = {
                // Execute seek
                musicController.seekTo(sliderPosition.toLong())
                
                // Reset user seeking flag after a delay to allow seek to complete
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    isUserSeeking = false
                }, 500)
            },
            valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
            interactionSource = interactionSource,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            ),
            thumb = {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(30.dp)
                        .graphicsLayer {
                            scaleX = thumbScale
                            scaleY = thumbScale
                        }
                        .background(
                            MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(2.dp)
                        )
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
        )

        // 4. Espaciado y Tiempos
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime(currentPosition),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatTime(duration),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
fun formatTime(ms: Long): String {
    val seconds = (ms / 1000) % 60
    val minutes = (ms / (1000 * 60)) % 60
    return "%d:%02d".format(minutes, seconds)
}