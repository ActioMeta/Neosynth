package com.example.neosynth.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CornerSize
import androidx.media3.common.MediaItem
import coil.compose.AsyncImage
import com.example.neosynth.player.MusicController
import com.example.neosynth.ui.components.AlphabetScrollbar
import com.example.neosynth.ui.components.AnimatedPlayerSlider
import com.example.neosynth.ui.components.bounceClick
import com.example.neosynth.ui.stats.rememberBounceScale
import kotlinx.coroutines.launch
import androidx.media3.common.Player
import androidx.compose.ui.res.stringResource
import com.example.neosynth.R

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.togetherWith

import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    viewModel: PlayerViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onDownload: () -> Unit = {},
    onLyricsClick: () -> Unit = {},
    isCurrentSongDownloaded: Boolean = false,
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    visualizerEnabled: Boolean = false
) {
    val musicController = viewModel.musicController
    
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    val currentSong by musicController.currentMediaItem
    val isPlaying by musicController.isPlaying
    val duration by musicController.duration
    val queue by musicController.currentQueue
    val currentIndex by musicController.currentIndex

    val bitrateText by viewModel.bitrateText.collectAsStateWithLifecycle()
    val hasAudioPermission by viewModel.hasAudioPermission.collectAsStateWithLifecycle()

    var showQueueSheet by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val song = currentSong
    
    // Pager State synchronized with queue
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = currentIndex,
        pageCount = { queue.size.coerceAtLeast(1) }
    )
    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()
    val artworkUriCache = remember { mutableStateMapOf<String, Any>() }
    var lastVisibleArtworkUri by remember { mutableStateOf<Any?>(null) }

    var pendingSwipeIndex by remember { mutableIntStateOf(-1) }

    // Sync from Controller -> Pager (Button skips, auto-skips)
    LaunchedEffect(currentIndex, queue.size) {
        if (currentIndex in 0 until queue.size) {
            if (currentIndex == pendingSwipeIndex) {
                // El reproductor ya completó el cambio que propusimos al hacer el swipe
                pendingSwipeIndex = -1
            } else if (pagerState.currentPage != currentIndex && pendingSwipeIndex == -1 && !isDragged) {
                // Un cambio desde el MusicController (botón, final de canción)
                // Cambio instantáneo sin animación, la animación es solo para gestos manuales
                pagerState.scrollToPage(currentIndex)
            }
        }
    }

    // Sync from Pager -> Controller (User swipes)
    LaunchedEffect(pagerState, isDragged) {
        androidx.compose.runtime.snapshotFlow { pagerState.settledPage }
            .collect { settledPage ->
                // Play song only when Pager is settled AND user is not dragging
                // This guarantees the swipe gesture has fully finished cleanly
                if (!isDragged && settledPage != currentIndex && settledPage != pendingSwipeIndex) {
                    pendingSwipeIndex = settledPage
                    musicController.playFromQueue(settledPage)
                }
            }
    }
    
    // Si el usuario vuelve a arrastrar mientras había un cambio pendiente, lo limpiamos
    LaunchedEffect(isDragged) {
        if (isDragged) {
            pendingSwipeIndex = -1
        }
    }
    
    // Update bitrate when song changes
    LaunchedEffect(song) {
        viewModel.updateBitrate(song)
    }

    // Queue Bottom Sheet
    if (showQueueSheet) {
        QueueBottomSheet(
            queue = queue,
            currentIndex = currentIndex,
            onDismiss = { showQueueSheet = false },
            sheetState = sheetState,
            musicController = musicController,
            viewModel = viewModel,
            onPlayFromQueue = { index ->
                musicController.playFromQueue(index)
                showQueueSheet = false
            },
            onMoveItem = { from, to ->
                musicController.moveQueueItem(from, to)
            },
            onRemoveItem = { index ->
                musicController.removeFromQueue(index)
            },
            onShowSnackbar = { message ->
                scope.launch {
                    snackbarHostState.showSnackbar(message)
                }
            }
        )
    }

    // Snackbar Host placed in Box
    Box(modifier = Modifier.fillMaxSize()) {

    // Color dominante unificado directo del MusicController
    val dominantColorInt by musicController.dominantColorInt
    val dominantColor = Color(dominantColorInt)

    if (showSleepTimerDialog) {
        SleepTimerDialog(
            remainingMs = musicController.sleepTimerRemaining.value,
            primaryColor = dominantColor,
            onStartTimer = { duration -> viewModel.startSleepTimer(duration) },
            onStartAtEnd = { viewModel.startSleepTimerAtEndOfSong() },
            onCancel = { viewModel.cancelSleepTimer() },
            onDismiss = { showSleepTimerDialog = false }
        )
    }
    
    // Animación suave del color de fondo
    val animatedColor by animateColorAsState(
        targetValue = dominantColor,
        animationSpec = tween(durationMillis = 1000),
        label = "bg_color_anim"
    )

    // Dynamic Background (Color sólido animado con gradiente radial premium)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black) // Fondo base negro
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(
                            animatedColor.copy(alpha = 0.8f), // Color vibrante en el centro/arriba
                            animatedColor.copy(alpha = 0.4f), // Desvanecimiento medio
                            Color.Black.copy(alpha = 0.9f)    // Casi negro en los bordes
                        ),
                        center = Offset.Unspecified, // Centro default
                        radius = 2500f // Radio grande para cubrir bien
                    )
                )
        )
        // Capa extra de oscurecimiento abajo para asegurar legibilidad de controles
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.8f)
                        ),
                        startY = 500f
                    )
                )
        )
    } // End of Dynamic Background Box

    // Global Swipe to dismiss logic
    var totalDrag by remember { mutableStateOf(0f) }
    val dismissOffset by animateFloatAsState(
        targetValue = totalDrag,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "dismiss_offset"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = dismissOffset.coerceAtLeast(0f) 
                val scale = 1f - (dismissOffset.coerceAtLeast(0f) / 4000f)
                scaleX = scale
                scaleY = scale
                alpha = 1f - (dismissOffset.coerceAtLeast(0f) / 2000f)
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onDragEnd = {
                        if (totalDrag > 250f) {
                            onBack()
                        } else if (totalDrag < -150f) {
                            showQueueSheet = true
                            totalDrag = 0f
                        } else {
                            totalDrag = 0f
                        }
                    },
                    onDrag = { change, dragAmount ->
                        val y = dragAmount.y
                        val x = dragAmount.x
                        if (abs(y) > abs(x)) { 
                            change.consume()
                            totalDrag += dragAmount.y
                        }
                    }
                )
            }
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Barra Superior (Volver + Sleep Timer)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.action_back),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(32.dp)
                )
            }
            
            val sleepRemaining = musicController.sleepTimerRemaining.value
            IconButton(onClick = { showSleepTimerDialog = true }) {
                Icon(
                    imageVector = Icons.Rounded.Timer,
                    contentDescription = "Temporizador",
                    tint = if (sleepRemaining > 0 || sleepRemaining == -1L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.weight(0.08f))

        // Artwork con overlay de controles (click para mostrar/ocultar)
        var showControls by remember { mutableStateOf(false) }
        
        // Reset color when song changes is removed smoothly
        
        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            key = { index -> queue.getOrNull(index)?.mediaId ?: index },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f) // Takes the space between top spacer and the slider
        ) { page ->
            val pageSong = queue.getOrNull(page)
            var showSkipForward by remember { mutableStateOf(false) }
            var showSkipBackward by remember { mutableStateOf(false) }
            val coroutineScope = rememberCoroutineScope()

            val pageMediaId = pageSong?.mediaId
            val liveArtworkUri = pageSong?.mediaMetadata?.artworkUri
            val currentMediaId = song?.mediaId
            val currentSongArtworkUri = song?.mediaMetadata?.artworkUri
            val fallbackCurrentArtworkUri = if (pageMediaId != null && pageMediaId == currentMediaId) {
                currentSongArtworkUri
            } else {
                null
            }

            val resolvedArtworkUri = liveArtworkUri ?: fallbackCurrentArtworkUri

            if (pageMediaId != null && resolvedArtworkUri != null) {
                artworkUriCache[pageMediaId] = resolvedArtworkUri
            }

            val stableArtworkUri = when {
                resolvedArtworkUri != null -> resolvedArtworkUri
                pageMediaId != null -> artworkUriCache[pageMediaId]
                else -> null
            }
            if (page == currentIndex && stableArtworkUri != null) {
                lastVisibleArtworkUri = stableArtworkUri
            }
            val displayArtworkUri = if (page == currentIndex) {
                stableArtworkUri ?: lastVisibleArtworkUri
            } else {
                stableArtworkUri
            }
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // 3D Cover Flow Effect
                        val pageOffset = (
                            (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                        ).absoluteValue
                        
                        val scale = androidx.compose.ui.util.lerp(
                            start = 0.85f,
                            stop = 1f,
                            fraction = 1f - pageOffset.coerceIn(0f, 1f)
                        )
                        
                        scaleX = scale
                        scaleY = scale
                        alpha = androidx.compose.ui.util.lerp(
                            start = 0.5f,
                            stop = 1f,
                            fraction = 1f - pageOffset.coerceIn(0f, 1f)
                        )
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .aspectRatio(1f)
                ) {
                    var isPressed by remember { mutableStateOf(false) }
                    val coverScale by animateFloatAsState(
                        targetValue = if (isPressed) 0.95f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "cover_spring"
                    )

                    val coverShape = RoundedCornerShape(20.dp)
                    with(sharedTransitionScope) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (pageSong != null) {
                                        Modifier.sharedElement(
                                            sharedContentState = rememberSharedContentState(key = "cover_${pageSong.mediaId ?: "current"}"),
                                            animatedVisibilityScope = animatedVisibilityScope,
                                            boundsTransform = { _, _ ->
                                                spring(
                                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                                    stiffness = Spring.StiffnessLow
                                                )
                                            }
                                        )
                                    } else Modifier
                                )
                                .clip(coverShape)
                                .graphicsLayer {
                                    scaleX = coverScale
                                    scaleY = coverScale
                                }
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onPress = {
                                            isPressed = true
                                            tryAwaitRelease()
                                            isPressed = false
                                        }
                                    )
                                }
                        ) {
                            AsyncImage(
                                model = androidx.compose.ui.platform.LocalContext.current.let { context ->
                                    coil.request.ImageRequest.Builder(context)
                                        .data(displayArtworkUri)
                                        .allowHardware(false)
                                        .crossfade(true)
                                        .build()
                                },
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )

                            // Overlay para salto hacia atrás (-10s)
                            androidx.compose.animation.AnimatedVisibility(
                                visible = showSkipBackward,
                                enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(150)) + 
                                        androidx.compose.animation.scaleIn(initialScale = 0.8f),
                                exit = androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(150)) + 
                                       androidx.compose.animation.scaleOut(targetScale = 1.2f),
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = 24.dp)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier
                                        .size(72.dp)
                                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.FastRewind,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Text(
                                        text = "-10s",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Overlay para salto hacia adelante (+10s)
                            androidx.compose.animation.AnimatedVisibility(
                                visible = showSkipForward,
                                enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(150)) + 
                                        androidx.compose.animation.scaleIn(initialScale = 0.8f),
                                exit = androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(150)) + 
                                       androidx.compose.animation.scaleOut(targetScale = 1.2f),
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 24.dp)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier
                                        .size(72.dp)
                                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.FastForward,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Text(
                                        text = "+10s",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                } // Fin Box del CoverArt
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Audio Quality Badge (Bitrate Real y Dinámico)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.animation.AnimatedContent(
                        targetState = if (page == currentIndex) bitrateText else null,
                        label = "bitrate_anim"
                    ) { targetText ->
                        if (targetText != null) {
                            Text(
                                text = targetText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.GraphicEq,
                                contentDescription = stringResource(R.string.content_desc_audio_quality),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
        
                Spacer(modifier = Modifier.weight(0.08f))
                
                // Row de Información y botones de Favorito y Descarga
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = pageSong?.mediaMetadata?.title?.toString() ?: stringResource(R.string.unknown_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(
                                iterations = Int.MAX_VALUE,
                                spacing = MarqueeSpacing(24.dp),
                                initialDelayMillis = 2000,
                                repeatDelayMillis = 2000
                            )
                        )
            
                        Text(
                            text = pageSong?.mediaMetadata?.artist?.toString() ?: stringResource(R.string.unknown_artist),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(
                                iterations = Int.MAX_VALUE,
                                spacing = MarqueeSpacing(24.dp),
                                initialDelayMillis = 2000,
                                repeatDelayMillis = 2000
                            )
                        )
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(onClick = onToggleFavorite) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = "Favorito",
                                tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        IconButton(onClick = onDownload) {
                            Icon(
                                imageVector = if (isCurrentSongDownloaded) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
                                contentDescription = "Descargar",
                                tint = if (isCurrentSongDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
        
        Spacer(modifier = Modifier.height(10.dp))
        
        LaunchedEffect(visualizerEnabled) {
            viewModel.checkAudioPermission()
        }
        
        // Permission request launcher
        val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            viewModel.onPermissionResult(isGranted)
        }
        
        LaunchedEffect(visualizerEnabled, hasAudioPermission) {
             if (visualizerEnabled && !hasAudioPermission) {
                 launcher.launch(android.Manifest.permission.RECORD_AUDIO)
             }
        }
        
        val activeCoverUrl = song?.mediaMetadata?.artworkUri?.toString()
        val coverPalette = com.example.neosynth.ui.album.rememberAlbumPalette(activeCoverUrl)

        if (visualizerEnabled && hasAudioPermission) {
            val audioSessionId by musicController.audioSessionId
            com.example.neosynth.ui.components.AudioVisualizerSlider(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                audioSessionId = audioSessionId,
                progress = if (duration > 0) musicController.currentPosition.value.toFloat() / duration.toFloat() else 0f,
                onProgressChange = { newProgress ->
                    musicController.seekTo((newProgress * duration).toLong())
                },
                color = coverPalette.accent
            )
        } else {
             AnimatedPlayerSlider(
                musicController = musicController,
                accentColor = coverPalette.accent
            )
        }
        
        Spacer(modifier = Modifier.weight(0.1f))

        // Botones de control con animaciones más visibles (incluyendo Shuffle y Repeat)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Shuffle
            val shuffleActive = musicController.shuffleModeEnabled.value
            IconButton(onClick = { musicController.toggleShuffle() }) {
                Icon(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = "Aleatorio",
                    tint = if (shuffleActive) coverPalette.accent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(24.dp)
                )
            }

            // Botón anterior con animación
            val hasPrevious by musicController.hasPrevious
            // Dimmed if !hasPrevious, but still visible and clickable for restart
            val prevAlpha by animateFloatAsState(targetValue = if (hasPrevious) 1f else 0.5f, label = "prev_alpha")
            
            val interactionSourcePrev = remember { MutableInteractionSource() }
            val isPressedPrev by interactionSourcePrev.collectIsPressedAsState()
            val scalePrev by animateFloatAsState(
                targetValue = if (isPressedPrev) 0.8f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "prev_scale"
            )
            
            IconButton(
                onClick = { musicController.skipPreviousOrRestart() },
                enabled = true, // Always enabled to allow restart
                interactionSource = interactionSourcePrev,
                modifier = Modifier.graphicsLayer {
                    scaleX = scalePrev
                    scaleY = scalePrev
                    alpha = prevAlpha
                }
            ) {
                Icon(
                    Icons.Rounded.SkipPrevious, 
                    stringResource(R.string.previous), 
                    modifier = Modifier.size(46.dp),
                    tint = coverPalette.accent
                )
            }

            // Botón Play/Pause con animación prominente
            val interactionSourcePlay = remember { MutableInteractionSource() }
            val isPressedPlay by interactionSourcePlay.collectIsPressedAsState()
            val scalePlay by animateFloatAsState(
                targetValue = if (isPressedPlay) 0.85f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "play_scale"
            )
            
            val cornerRadiusPercent by animateIntAsState(
                targetValue = if (isPlaying) 20 else 50, // 20% for rounded square, 50% for circle
                animationSpec = spring(stiffness = Spring.StiffnessLow),
                label = "shape_radius"
            )
            
            Surface(
                onClick = { musicController.togglePlayPause() },
                interactionSource = interactionSourcePlay,
                modifier = Modifier
                    .size(76.dp)
                    .graphicsLayer {
                        scaleX = scalePlay
                        scaleY = scalePlay
                    },
                shape = RoundedCornerShape(percent = cornerRadiusPercent.coerceIn(0, 100)), // Using percent for square->circle
                color = coverPalette.accent,
                shadowElevation = if (isPressedPlay) 4.dp else 12.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    androidx.compose.animation.AnimatedContent(
                        targetState = isPlaying,
                        transitionSpec = {
                            (androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.8f))
                                .togetherWith(androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(targetScale = 0.8f))
                        },
                        label = "play_pause_anim_main"
                    ) { playing ->
                        Icon(
                            imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (playing) stringResource(R.string.action_pause) else stringResource(R.string.action_play),
                            tint = coverPalette.onAccent,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }

            // Botón siguiente con animación
            val hasNext by musicController.hasNext
            val nextAlpha by animateFloatAsState(targetValue = if (hasNext) 1f else 0.5f, label = "next_alpha")

            val interactionSourceNext = remember { MutableInteractionSource() }
            val isPressedNext by interactionSourceNext.collectIsPressedAsState()
            val scaleNext by animateFloatAsState(
                targetValue = if (isPressedNext) 0.8f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "next_scale"
            )

            IconButton(
                onClick = { if (hasNext) musicController.skipNext() },
                enabled = hasNext,
                interactionSource = interactionSourceNext,
                modifier = Modifier.graphicsLayer {
                    scaleX = scaleNext
                    scaleY = scaleNext
                    alpha = nextAlpha
                }
            ) {
                Icon(
                    Icons.Rounded.SkipNext, 
                    stringResource(R.string.next), 
                    modifier = Modifier.size(46.dp),
                    tint = coverPalette.accent
                )
            }

            // Repeat
            val repeatMode = musicController.repeatMode.value
            val repeatActive = repeatMode != Player.REPEAT_MODE_OFF
            IconButton(onClick = { musicController.toggleRepeat() }) {
                Icon(
                    imageVector = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    contentDescription = "Repetir",
                    tint = if (repeatActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.weight(0.08f))

        // Barra Inferior (Letras y Cola)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onLyricsClick) {
                Icon(
                    imageVector = Icons.Rounded.Lyrics,
                    contentDescription = "Letras",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            IconButton(onClick = { showQueueSheet = true }) {
                Icon(
                    imageVector = Icons.Rounded.QueueMusic,
                    contentDescription = "Cola de reproducción",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
    
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 120.dp)
    )
    } // End of outer Box for snackbar
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun QueueBottomSheet(
    queue: List<MediaItem>,
    currentIndex: Int,
    onDismiss: () -> Unit,
    sheetState: SheetState,
    musicController: MusicController,
    viewModel: PlayerViewModel,
    onPlayFromQueue: (Int) -> Unit,
    onMoveItem: (Int, Int) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onShowSnackbar: (String) -> Unit
) {
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var showQueueOptionsSheet by remember { mutableStateOf(false) }
    var showSavePlaylistDialog by remember { mutableStateOf(false) }
    
    // Estado para drag & drop
    var displayQueue by remember { mutableStateOf(queue) }
    var draggedItemId by remember { mutableStateOf<String?>(null) }
    var dragStartOriginalIndex by remember { mutableIntStateOf(-1) }
    var hoveredIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val itemSpacingPx = 4f * density
    val draggedIndex = remember(displayQueue, draggedItemId) {
        draggedItemId?.let { id -> displayQueue.indexOfFirst { it.mediaId == id } } ?: -1
    }
    val currentSongId = queue.getOrNull(currentIndex)?.mediaId

    LaunchedEffect(queue, draggedItemId) {
        if (draggedItemId == null) {
            displayQueue = queue
        }
    }

    LaunchedEffect(Unit) {
        val targetIndex = displayQueue.indexOfFirst { it.mediaId == currentSongId }
        if (targetIndex >= 0) {
            listState.scrollToItem(targetIndex)
        }
    }

    fun moveInDisplayQueue(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        if (fromIndex !in displayQueue.indices || toIndex !in displayQueue.indices) return

        val mutable = displayQueue.toMutableList()
        val movedItem = mutable.removeAt(fromIndex)
        mutable.add(toIndex, movedItem)
        displayQueue = mutable
    }

    fun maybeReorderDraggedItem() {
        val currentDraggedId = draggedItemId ?: return
        val currentDisplayIndex = displayQueue.indexOfFirst { it.mediaId == currentDraggedId }
        if (currentDisplayIndex < 0) return

        val visibleItems = listState.layoutInfo.visibleItemsInfo
        val currentInfo = visibleItems.find { it.index == currentDisplayIndex } ?: return
        
        val itemHeight = currentInfo.size
        val threshold = itemHeight + itemSpacingPx

        // Dragging down (moving to a higher index)
        if (dragOffset > threshold && currentDisplayIndex < displayQueue.lastIndex) {
            moveInDisplayQueue(currentDisplayIndex, currentDisplayIndex + 1)
            dragOffset -= threshold
            hoveredIndex = currentDisplayIndex + 1
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            return
        }

        // Dragging up (moving to a lower index)
        if (dragOffset < -threshold && currentDisplayIndex > 0) {
            moveInDisplayQueue(currentDisplayIndex, currentDisplayIndex - 1)
            dragOffset += threshold
            hoveredIndex = currentDisplayIndex - 1
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            return
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                // Action Controls Bar sobre la cola de reproducción (M3 Expressive)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Botón Aleatorio / Shuffle (Píldora oscura)
                    val isShuffleOn = musicController.shuffleModeEnabled.value
                    Surface(
                        onClick = { musicController.toggleShuffle() },
                        shape = CircleShape,
                        color = if (isShuffleOn) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.height(38.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shuffle,
                                contentDescription = "Aleatorio",
                                tint = if (isShuffleOn) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Aleatorio",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isShuffleOn) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Contador / Hint de reordenar
                    Text(
                        text = if (draggedIndex >= 0) "Soltar para reordenar" else "${queue.size} canciones",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (draggedIndex >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Botones Limpiar Queue y Opciones
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = {
                                musicController.clearQueue()
                                onShowSnackbar("Cola de reproducción limpiada")
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteSweep,
                                contentDescription = "Limpiar cola",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                showQueueOptionsSheet = true
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "Opciones",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true), // Take all space above FAB
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                itemsIndexed(
                    items = displayQueue,
                    key = { _: Int, item: MediaItem -> item.mediaId }
                ) { index, item ->
                    val isCurrentSong = item.mediaId == currentSongId
                    val isDragged = draggedItemId == item.mediaId
                    val isHovered = hoveredIndex == index
                    
                    QueueItem(
                        item = item,
                        index = index,
                        isCurrentSong = isCurrentSong,
                        isDragged = isDragged,
                        isHovered = isHovered,
                        dragOffset = if (isDragged) dragOffset else 0f,
                        onPlay = {
                            if (draggedIndex < 0) {
                                val actualIndex = queue.indexOfFirst { it.mediaId == item.mediaId }
                                if (actualIndex >= 0) onPlayFromQueue(actualIndex)
                            }
                        },
                        onRemove = {
                            val actualIndex = queue.indexOfFirst { it.mediaId == item.mediaId }
                            if (actualIndex >= 0) onRemoveItem(actualIndex)
                        },
                        onDragStart = {
                            draggedItemId = item.mediaId
                            dragStartOriginalIndex = queue.indexOfFirst { it.mediaId == item.mediaId }
                            hoveredIndex = index
                            dragOffset = 0f
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDrag = { change, dragAmount ->
                            if (draggedItemId == item.mediaId) {
                                dragOffset += dragAmount.y
                                maybeReorderDraggedItem()
                            }
                        },
                        onDragEnd = {
                            val movedItemId = draggedItemId
                            val finalDisplayIndex = movedItemId?.let { id ->
                                displayQueue.indexOfFirst { it.mediaId == id }
                            } ?: -1

                            draggedItemId = null
                            val startIndex = dragStartOriginalIndex
                            dragStartOriginalIndex = -1
                            hoveredIndex = -1
                            dragOffset = 0f

                            if (startIndex >= 0 && finalDisplayIndex >= 0 && startIndex != finalDisplayIndex) {
                                onMoveItem(startIndex, finalDisplayIndex)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        }
                    )
                }
            }

            // --- Auto-scroll Logic ---
            LaunchedEffect(draggedItemId) {
                if (draggedItemId != null) {
                    while (draggedItemId != null) {
                        val currentDraggedId = draggedItemId ?: break
                        val currentDraggedIndex = displayQueue.indexOfFirst { it.mediaId == currentDraggedId }
                        if (currentDraggedIndex < 0) break

                        val layoutInfo = listState.layoutInfo
                        val draggedItem = layoutInfo.visibleItemsInfo.find { it.index == currentDraggedIndex }
                        if (draggedItem != null) {
                            val viewportStart = layoutInfo.viewportStartOffset
                            val viewportEnd = layoutInfo.viewportEndOffset
                            val itemTop = draggedItem.offset + dragOffset
                            val itemBottom = itemTop + draggedItem.size
                            
                            val threshold = 150f * density // Distancia al borde donde empieza a hacer scroll
                            
                            var scrollAmount = 0f
                            // Si estamos cerca del borde superior
                            if (itemTop < viewportStart + threshold) {
                                val distance = (viewportStart + threshold) - itemTop
                                // Velocidad proporcional a la distancia, max 20px
                                scrollAmount = -(distance / threshold) * 20f * density 
                            } 
                            // Si estamos cerca del borde inferior
                            else if (itemBottom > viewportEnd - threshold) {
                                val distance = itemBottom - (viewportEnd - threshold)
                                scrollAmount = (distance / threshold) * 20f * density
                            }

                            if (scrollAmount != 0f) {
                                // Aplica el scroll y obtiene cuánto se movió realmente
                                val consumed = listState.scrollBy(scrollAmount)
                                // Compensa visualmente el desplazamiento solo con lo que se scrolleó
                                // Así evitamos que el elemento desaparezca si llegamos al límite de la lista
                                dragOffset += consumed
                                maybeReorderDraggedItem()
                            }
                        }
                        kotlinx.coroutines.delay(16) // ~60fps
                    }
                }
            }
        } // End of Column inner content

        if (showQueueOptionsSheet) {
            val qSheetState = rememberModalBottomSheetState()
            ModalBottomSheet(
                onDismissRequest = { showQueueOptionsSheet = false },
                sheetState = qSheetState,
                containerColor = MaterialTheme.colorScheme.surface,
                dragHandle = { BottomSheetDefaults.DragHandle() }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = context.getString(R.string.action_options),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Option 1: Download Queue
                        val dlInteraction = remember { MutableInteractionSource() }
                        val dlScale by rememberBounceScale(dlInteraction)
                        Surface(
                            onClick = {
                                showQueueOptionsSheet = false
                                viewModel.downloadQueue()
                                onShowSnackbar(context.getString(R.string.queue_downloading))
                            },
                            interactionSource = dlInteraction,
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                            modifier = Modifier
                                .weight(1f)
                                .height(88.dp)
                                .graphicsLayer {
                                    scaleX = dlScale
                                    scaleY = dlScale
                                }
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = context.getString(R.string.action_download),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = context.getString(R.string.action_download),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Option 2: Save to Playlist
                        val saveInteraction = remember { MutableInteractionSource() }
                        val saveScale by rememberBounceScale(saveInteraction)
                        Surface(
                            onClick = {
                                showQueueOptionsSheet = false
                                showSavePlaylistDialog = true
                            },
                            interactionSource = saveInteraction,
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                            modifier = Modifier
                                .weight(1f)
                                .height(88.dp)
                                .graphicsLayer {
                                    scaleX = saveScale
                                    scaleY = saveScale
                                }
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlaylistAdd,
                                    contentDescription = context.getString(R.string.action_save),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = context.getString(R.string.action_save),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
        
        if (showSavePlaylistDialog) {
            var playlistName by remember { mutableStateOf("") }
            val isProcessing by viewModel.isProcessingQueueAction.collectAsStateWithLifecycle()
            val okMessage = stringResource(R.string.playlist_created_success)
            
            AlertDialog(
                onDismissRequest = { if (!isProcessing) showSavePlaylistDialog = false },
                title = { Text(context.getString(R.string.save_as_playlist)) },
                text = {
                    OutlinedTextField(
                        value = playlistName,
                        onValueChange = { playlistName = it },
                        label = { Text(context.getString(R.string.new_playlist_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (playlistName.isNotBlank()) {
                                viewModel.saveQueueAsPlaylist(
                                    name = playlistName,
                                    onComplete = {
                                        showSavePlaylistDialog = false
                                        onShowSnackbar(okMessage)
                                    },
                                    onError = { error ->
                                        onShowSnackbar(error)
                                    }
                                )
                            }
                        },
                        enabled = playlistName.isNotBlank() && !isProcessing
                    ) {
                        Text(context.getString(R.string.action_save))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showSavePlaylistDialog = false },
                        enabled = !isProcessing
                    ) {
                        Text(context.getString(R.string.action_cancel))
                    }
                }
            )
        }
    } // End of outer Box
    } // End of ModalBottomSheet
}

@Composable
private fun QueueItem(
    item: MediaItem,
    index: Int,
    isCurrentSong: Boolean,
    isDragged: Boolean = false,
    isHovered: Boolean = false,
    dragOffset: Float = 0f,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    onDragStart: () -> Unit = {},
    onDrag: (PointerInputChange, Offset) -> Unit = { _, _ -> },
    onDragEnd: () -> Unit = {}
) {
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    
    // Animación de escala cuando está siendo arrastrado
    val scale by animateFloatAsState(
        targetValue = if (isDragged) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "queue_item_scale"
    )
    
    // Animación de elevación
    val elevation by animateFloatAsState(
        targetValue = if (isDragged) 16f else if (isHovered) 4f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "queue_item_elevation"
    )
    
    // Animación de alpha
    val alpha by animateFloatAsState(
        targetValue = if (isDragged) 0.9f else 1f,
        label = "queue_item_alpha"
    )
    
    Surface(
        onClick = { if (!isDragged) onPlay() },
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = if (isDragged) dragOffset else 0f
                this.alpha = alpha
            }
            .pointerInput(item.mediaId) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { currentOnDragStart() },
                    onDrag = { change, dragAmount -> 
                        change.consume()
                        currentOnDrag(change, dragAmount) 
                    },
                    onDragEnd = { currentOnDragEnd() },
                    onDragCancel = { currentOnDragEnd() }
                )
            },
        color = when {
            isDragged -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
            isHovered -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
            isCurrentSong -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else -> Color.Transparent
        },
        shape = RoundedCornerShape(12.dp),
        shadowElevation = elevation.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Artwork
            AsyncImage(
                model = item.mediaMetadata.artworkUri,
                contentDescription = null,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.mediaMetadata.title?.toString() ?: stringResource(R.string.unknown_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isCurrentSong) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCurrentSong)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.mediaMetadata.artist?.toString() ?: stringResource(R.string.unknown_artist),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Remove button
            IconButton(
                onClick = { onRemove() },
                modifier = Modifier.size(32.dp),
                enabled = !isDragged
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.queue_remove_title),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

        }
    }
}

@Composable
private fun SleepTimerDialog(
    remainingMs: Long,
    primaryColor: Color,
    onStartTimer: (Long) -> Unit,
    onStartAtEnd: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedDurationMinutes by remember { mutableStateOf(15) }
    var isAtEndOfSongSelected by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Temporizador de apagado",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (remainingMs > 0) {
                    val minutesLeft = (remainingMs / 1000 / 60)
                    val secondsLeft = (remainingMs / 1000 % 60)
                    val formattedTime = String.format("%02d:%02d", minutesLeft, secondsLeft)

                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val strokeWidth = 8.dp.toPx()
                            val radius = (size.minDimension / 2f) - strokeWidth
                            
                            // Draw thin glowing countdown track
                            drawCircle(
                                color = primaryColor.copy(alpha = 0.15f),
                                radius = radius,
                                center = center,
                                style = Stroke(width = strokeWidth)
                            )
                            
                            // Draw active countdown arc representing remaining seconds of the minute
                            val secondsProgress = (secondsLeft / 60f) * 360f
                            drawArc(
                                color = primaryColor,
                                startAngle = -90f,
                                sweepAngle = secondsProgress,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = formattedTime,
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "restantes",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Text(
                        text = "El reproductor se pausará automáticamente",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    Button(
                        onClick = {
                            onCancel()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Timer, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Detener temporizador", fontWeight = FontWeight.Bold)
                    }

                } else if (remainingMs == -1L) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(vertical = 16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier
                                .size(72.dp)
                                .background(primaryColor.copy(alpha = 0.1f), CircleShape)
                                .padding(16.dp)
                        )
                        Text(
                            text = "Se apagará al finalizar la canción actual",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }

                    Button(
                        onClick = {
                            onCancel()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Timer, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Detener temporizador", fontWeight = FontWeight.Bold)
                    }

                } else {
                    // Selector Dial Circular interactivo
                    CircularDurationPicker(
                        modifier = Modifier
                            .size(200.dp)
                            .padding(8.dp),
                        initialMinutes = selectedDurationMinutes,
                        primaryColor = if (isAtEndOfSongSelected) primaryColor.copy(alpha = 0.2f) else primaryColor,
                        onMinutesChange = { minutes ->
                            selectedDurationMinutes = minutes
                            isAtEndOfSongSelected = false
                        }
                    )

                    // Suggestion chips presets
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                    ) {
                        val presets = listOf(15, 30, 45, 60)
                        presets.forEach { preset ->
                            PresetChip(
                                label = "${preset} min",
                                isSelected = !isAtEndOfSongSelected && selectedDurationMinutes == preset,
                                primaryColor = primaryColor,
                                onClick = {
                                    selectedDurationMinutes = preset
                                    isAtEndOfSongSelected = false
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Opción al finalizar la canción
                    val endOfSongBorderColor = if (isAtEndOfSongSelected) {
                        primaryColor.copy(alpha = 0.4f)
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    }
                    val endOfSongContentColor = if (isAtEndOfSongSelected) {
                        primaryColor
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    val endOfSongBgColor = if (isAtEndOfSongSelected) {
                        primaryColor.copy(alpha = 0.15f)
                    } else {
                        Color.Transparent
                    }

                    OutlinedButton(
                        onClick = {
                            isAtEndOfSongSelected = !isAtEndOfSongSelected
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = endOfSongBgColor,
                            contentColor = endOfSongContentColor
                        ),
                        border = BorderStroke(1.dp, endOfSongBorderColor)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = endOfSongContentColor
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Al finalizar la canción actual",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (remainingMs <= 0) {
                Button(
                    onClick = {
                        if (isAtEndOfSongSelected) {
                            onStartAtEnd()
                        } else {
                            onStartTimer(selectedDurationMinutes * 60 * 1000L)
                        }
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Iniciar", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}

@Composable
private fun PresetChip(
    label: String,
    isSelected: Boolean,
    primaryColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) primaryColor.copy(alpha = 0.15f)
                else Color.Transparent
            )
            .border(
                width = 1.dp,
                color = if (isSelected) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) primaryColor else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun CircularDurationPicker(
    modifier: Modifier = Modifier,
    initialMinutes: Int = 15,
    primaryColor: Color,
    onMinutesChange: (Int) -> Unit
) {
    var minutes by remember { mutableStateOf(initialMinutes) }
    val angle = remember { mutableStateOf((initialMinutes / 120f) * 360f) }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(initialMinutes) {
        minutes = initialMinutes
        angle.value = (initialMinutes / 120f) * 360f
    }

    Box(
        modifier = modifier.aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
        val onSurfaceColor = MaterialTheme.colorScheme.onSurface
        
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, _ ->
                            val size = this.size
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val touchPoint = change.position
                            val vector = touchPoint - center
                            
                            val angleRad = atan2(vector.y, vector.x)
                            var angleDeg = Math.toDegrees(angleRad.toDouble()).toFloat()
                            
                            // Adjust angle so 0 is at top (12 o'clock)
                            angleDeg = (angleDeg + 90f) % 360f
                            if (angleDeg < 0f) {
                                angleDeg += 360f
                            }
                            
                            angle.value = angleDeg
                            
                            // Map [0, 360] degrees to [1, 120] minutes
                            val newMinutes = ((angleDeg / 360f) * 120f).roundToInt().coerceIn(1, 120)
                            if (minutes != newMinutes) {
                                minutes = newMinutes
                                onMinutesChange(newMinutes)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            change.consume()
                        }
                    )
                }
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val strokeWidth = 10.dp.toPx()
            val radius = (size.minDimension / 2f) - strokeWidth - 20.dp.toPx()
            
            // Draw background track arc (full circle)
            drawCircle(
                color = primaryColor.copy(alpha = 0.1f),
                radius = radius,
                center = center,
                style = Stroke(width = strokeWidth)
            )
            
            // Draw active progress arc (starting at -90 degrees, i.e., 12 o'clock)
            drawArc(
                color = primaryColor,
                startAngle = -90f,
                sweepAngle = angle.value,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            
            // Draw ticks representing every 10 minutes (12 ticks total)
            val tickCount = 12
            for (i in 0 until tickCount) {
                val tickAngleDeg = (i * (360f / tickCount)) - 90f
                val tickAngleRad = Math.toRadians(tickAngleDeg.toDouble())
                val startRadius = radius + 10.dp.toPx()
                val endRadius = radius + 18.dp.toPx()
                val startX = center.x + startRadius * cos(tickAngleRad).toFloat()
                val startY = center.y + startRadius * sin(tickAngleRad).toFloat()
                val endX = center.x + endRadius * cos(tickAngleRad).toFloat()
                val endY = center.y + endRadius * sin(tickAngleRad).toFloat()
                
                val tickMinutes = i * 10
                val isTickActive = tickMinutes <= minutes && minutes > 0
                val tickColor = if (isTickActive) primaryColor else onSurfaceVariantColor.copy(alpha = 0.3f)
                val tickWidth = if (isTickActive) 3.dp.toPx() else 1.5.dp.toPx()
                
                drawLine(
                    color = tickColor,
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = tickWidth
                )
            }
            
            // Draw drag handle at end of active progress arc
            val handleAngleRad = Math.toRadians((angle.value - 90f).toDouble())
            val handleCenter = Offset(
                x = center.x + radius * cos(handleAngleRad).toFloat(),
                y = center.y + radius * sin(handleAngleRad).toFloat()
            )
            
            // Draw outer handle border / circle
            drawCircle(
                color = primaryColor,
                radius = 12.dp.toPx(),
                center = handleCenter
            )
            // Draw inner handle dot
            drawCircle(
                color = Color.White,
                radius = 5.dp.toPx(),
                center = handleCenter
            )
        }
        
        // Custom text layout in the center of the wheel
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "$minutes",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = onSurfaceColor
            )
            Text(
                text = "minutos",
                style = MaterialTheme.typography.bodyMedium,
                color = onSurfaceVariantColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
