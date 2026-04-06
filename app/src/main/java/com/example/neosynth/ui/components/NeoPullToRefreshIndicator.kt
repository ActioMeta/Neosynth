package com.example.neosynth.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeoPullToRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
    loaderColor: Color = MaterialTheme.colorScheme.primary,
    backgroundCircleColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
    indicatorSize: Dp = 44.dp,
    pullDropDistance: Dp = 36.dp,
    dragRotationMax: Float = 150f,
    refreshSpinDurationMs: Int = 1800,
    minDragScale: Float = 0.86f,
    maxDragScale: Float = 1f
) {
    val clampedProgress = state.distanceFraction.coerceIn(0f, 1f)
    val density = LocalDensity.current
    val pullDropDistancePx = with(density) { pullDropDistance.toPx() }
    val transition = rememberInfiniteTransition(label = "neo_pull_refresh")
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = refreshSpinDurationMs, easing = LinearEasing)
        ),
        label = "neo_pull_refresh_spin"
    )

    val alpha by animateFloatAsState(
        targetValue = if (isRefreshing) 1f else clampedProgress,
        label = "neo_pull_refresh_alpha"
    )

    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = LinearEasing)
        ),
        label = "neo_pull_refresh_pulse"
    )

    val scale by animateFloatAsState(
        targetValue = if (isRefreshing) maxDragScale else (minDragScale + ((maxDragScale - minDragScale) * clampedProgress)),
        label = "neo_pull_refresh_scale"
    )

    val pullTranslationY by animateFloatAsState(
        targetValue = if (isRefreshing) pullDropDistancePx else (clampedProgress * pullDropDistancePx),
        label = "neo_pull_refresh_translation"
    )

    Box(
        modifier = modifier
            .size(indicatorSize)
            .graphicsLayer {
                this.alpha = alpha
                val effectiveScale = if (isRefreshing) scale * pulse else scale
                scaleX = effectiveScale
                scaleY = effectiveScale
                rotationZ = if (isRefreshing) spin else (clampedProgress * dragRotationMax)
                translationY = pullTranslationY
            },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.size(indicatorSize * 1.18f),
            shape = CircleShape,
            color = backgroundCircleColor
        ) {}

        ExpressiveLoadingIndicator(
            isRefreshing = isRefreshing,
            progress = clampedProgress,
            color = loaderColor,
            modifier = Modifier.size(indicatorSize)
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpressiveLoadingIndicator(
    isRefreshing: Boolean,
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    if (isRefreshing) {
        LoadingIndicator(
            modifier = modifier,
            color = color
        )
    } else {
        LoadingIndicator(
            progress = { progress },
            modifier = modifier,
            color = color
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxScope.NeoPullToRefreshOverlayIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
    topPadding: Dp = 8.dp,
    zIndex: Float = 20f,
    loaderColor: Color = MaterialTheme.colorScheme.primary,
    backgroundCircleColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
    indicatorSize: Dp = 44.dp,
    pullDropDistance: Dp = 36.dp,
    dragRotationMax: Float = 150f,
    refreshSpinDurationMs: Int = 1800,
    minDragScale: Float = 0.86f,
    maxDragScale: Float = 1f
) {
    NeoPullToRefreshIndicator(
        state = state,
        isRefreshing = isRefreshing,
        loaderColor = loaderColor,
        backgroundCircleColor = backgroundCircleColor,
        indicatorSize = indicatorSize,
        pullDropDistance = pullDropDistance,
        dragRotationMax = dragRotationMax,
        refreshSpinDurationMs = refreshSpinDurationMs,
        minDragScale = minDragScale,
        maxDragScale = maxDragScale,
        modifier = modifier
            .align(Alignment.TopCenter)
            .statusBarsPadding()
            .padding(top = topPadding)
            .zIndex(zIndex)
    )
}
