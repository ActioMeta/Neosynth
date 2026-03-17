package com.example.neosynth.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.togetherWith

@Composable
fun MiniPlayer(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    mediaId: String?,
    title: String,
    artist: String,
    artworkUri: String?,
    isPlaying: Boolean,
    hasPrevious: Boolean = true,
    hasNext: Boolean = true,
    onPlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onClick: () -> Unit
) {
    if (title.isEmpty()) return

    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "miniplayer_scale"
    )

    Surface(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .height(72.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = colorScheme.surfaceVariant.copy(alpha = 0.95f),
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Artwork with Shared Element Transition
            with(sharedTransitionScope) {
                 AsyncImage(
                    model = artworkUri,
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .sharedElement(
                    sharedContentState = rememberSharedContentState(key = "artwork-$mediaId"),
                    animatedVisibilityScope = animatedVisibilityScope
                ),
                    contentScale = ContentScale.Crop
                )
            }

            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    text = title,
                    style = typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = artist,
                    style = typography.bodySmall,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1
                )
            }
            
            // Controls Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                // Previous Button
                val prevAlpha by animateFloatAsState(targetValue = if (hasPrevious) 1f else 0.5f, label = "mini_prev_alpha")
                
                IconButton(
                    onClick = onSkipPrevious,
                    enabled = true, // Always enabled to allow restart if implemented in lambda
                    modifier = Modifier
                        .size(36.dp)
                        .graphicsLayer { alpha = prevAlpha }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "Previous",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Play/Pause Button with Shape Animation
                val cornerRadiusPercent by androidx.compose.animation.core.animateIntAsState(
                    targetValue = if (isPlaying) 20 else 50, // 20% for rounded square, 50% for circle
                    animationSpec = spring(stiffness = Spring.StiffnessLow),
                    label = "mini_shape_radius"
                )

                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .clip(RoundedCornerShape(percent = cornerRadiusPercent.coerceIn(0, 100)))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true)
                        ) { onPlayPause() }
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.animation.AnimatedContent(
                        targetState = isPlaying,
                        transitionSpec = {
                            (androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.8f))
                                .togetherWith(androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(targetScale = 0.8f))
                        },
                        label = "play_pause_anim"
                    ) { playing ->
                        Icon(
                            imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Next Button
                val nextAlpha by animateFloatAsState(targetValue = if (hasNext) 1f else 0.5f, label = "mini_next_alpha")
                
                IconButton(
                    onClick = onSkipNext, 
                    enabled = hasNext,
                    modifier = Modifier
                        .size(36.dp)
                        .graphicsLayer { alpha = nextAlpha }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Next",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}