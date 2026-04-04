package com.example.neosynth.ui.player

import androidx.compose.animation.Crossfade
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import coil.compose.AsyncImage
import com.example.neosynth.player.MusicController
import com.example.neosynth.ui.components.AlphabetScrollbar
import com.example.neosynth.ui.components.AnimatedPlayerSlider
import com.example.neosynth.ui.components.bounceClick
import kotlinx.coroutines.launch
import androidx.media3.common.Player
import androidx.compose.ui.res.stringResource
import com.example.neosynth.R

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.togetherWith

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState

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

    val bitrateText by viewModel.bitrateText.collectAsState()
    val hasAudioPermission by viewModel.hasAudioPermission.collectAsState()

    var showQueueSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val song = currentSong
    
    // Pager State synchronized with queue
    // Pager State synchronized with queue
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = currentIndex,
        pageCount = { queue.size.coerceAtLeast(1) }
    )
    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()

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
        // Espacio superior aumentado para bajar todo el contenido
        Spacer(modifier = Modifier.weight(0.15f))

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

                    Card(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(24.dp))
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
                                        showControls = !showControls
                                    }
                                )
                            },
                        shape = RoundedCornerShape(24.dp),
                        elevation = CardDefaults.cardElevation(15.dp)
                    ) {
                        Crossfade(
                            targetState = pageSong?.mediaMetadata?.artworkUri,
                            animationSpec = tween(durationMillis = 300),
                            label = "cover_crossfade"
                        ) { artworkUri ->
                            with(sharedTransitionScope) {
                                AsyncImage(
                                    model = androidx.compose.ui.platform.LocalContext.current.let { context ->
                                        coil.request.ImageRequest.Builder(context)
                                            .data(artworkUri)
                                            .allowHardware(false)
                                            .crossfade(true)
                                            .build()
                                    },
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(24.dp))
                                        .then(
                                            if (pageSong != null) {
                                                Modifier.sharedElement(
                                                    sharedContentState = rememberSharedContentState(key = "artwork-${pageSong.mediaId}"),
                                                    animatedVisibilityScope = animatedVisibilityScope
                                                )
                                            } else Modifier
                                        )
                                )
                            }
                        }
                    }
                    
                    // Overlay de Controles (sólo visible si showControls y es la página actual)
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showControls && page == currentIndex,
                        enter = androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.fadeOut()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color.Black.copy(alpha = 0.6f))
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onPress = {
                                            isPressed = true
                                            tryAwaitRelease()
                                            isPressed = false
                                            showControls = false
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    CoverArtButton(icon = Icons.Rounded.Shuffle, isActive = musicController.shuffleModeEnabled.value, onClick = { musicController.toggleShuffle() })
                                    CoverArtButton(icon = if (musicController.repeatMode.value == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, isActive = musicController.repeatMode.value != Player.REPEAT_MODE_OFF, onClick = { musicController.toggleRepeat() })
                                    CoverArtButton(icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, isActive = isFavorite, onClick = onToggleFavorite)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    CoverArtButton(icon = if (isCurrentSongDownloaded) Icons.Rounded.DownloadDone else Icons.Rounded.Download, isActive = isCurrentSongDownloaded, onClick = onDownload)
                                    CoverArtButton(icon = Icons.Rounded.QueueMusic, isActive = false, onClick = { showQueueSheet = true })
                                    CoverArtButton(icon = Icons.Rounded.Lyrics, isActive = false, onClick = onLyricsClick)
                                }
                            }
                        }
                    }
                } // Fin Box del CoverArt
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Audio Quality Badge (Bitrate Real y Dinámico)
                // Se muestra el bitrate sólo en la página actual o un texto por defecto
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
                                contentDescription = "Audio Quality",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
        
                Spacer(modifier = Modifier.weight(0.1f))
                
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = pageSong?.mediaMetadata?.title?.toString() ?: "Sin título",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .basicMarquee(
                                iterations = Int.MAX_VALUE,
                                spacing = MarqueeSpacing(24.dp),
                                initialDelayMillis = 2000,
                                repeatDelayMillis = 2000
                            )
                    )
        
                    Text(
                        text = pageSong?.mediaMetadata?.artist?.toString() ?: "Artista desconocido",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .basicMarquee(
                                iterations = Int.MAX_VALUE,
                                spacing = MarqueeSpacing(24.dp),
                                initialDelayMillis = 2000,
                                repeatDelayMillis = 2000
                            )
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
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
                color = MaterialTheme.colorScheme.primary
            )
        } else {
             AnimatedPlayerSlider(
                musicController = musicController
            )
        }
        
        Spacer(modifier = Modifier.weight(0.1f))

        // Botones de control con animaciones más visibles
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                    null, 
                    modifier = Modifier.size(50.dp),
                    tint = MaterialTheme.colorScheme.onSurface
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
                    .size(80.dp)
                    .graphicsLayer {
                        scaleX = scalePlay
                        scaleY = scalePlay
                    },
                shape = RoundedCornerShape(percent = cornerRadiusPercent.coerceIn(0, 100)), // Using percent for smooth square->circle
                color = MaterialTheme.colorScheme.primary,
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
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(44.dp)
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
                    null, 
                    modifier = Modifier.size(50.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.weight(0.15f))
        Spacer(modifier = Modifier.height(10.dp))
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
        val currentCenter = currentInfo.offset + dragOffset + (currentInfo.size / 2f)

        if (dragOffset > 0f && currentDisplayIndex < displayQueue.lastIndex) {
            val nextInfo = visibleItems.find { it.index == currentDisplayIndex + 1 } ?: return
            val nextCenter = nextInfo.offset + (nextInfo.size / 2f)
            if (currentCenter > nextCenter) {
                moveInDisplayQueue(currentDisplayIndex, currentDisplayIndex + 1)
                dragOffset -= (nextInfo.size + itemSpacingPx)
                hoveredIndex = currentDisplayIndex + 1
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            return
        }

        if (dragOffset < 0f && currentDisplayIndex > 0) {
            val prevInfo = visibleItems.find { it.index == currentDisplayIndex - 1 } ?: return
            val prevCenter = prevInfo.offset + (prevInfo.size / 2f)
            if (currentCenter < prevCenter) {
                moveInDisplayQueue(currentDisplayIndex, currentDisplayIndex - 1)
                dragOffset += (prevInfo.size + itemSpacingPx)
                hoveredIndex = currentDisplayIndex - 1
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
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
                // Header con título
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                  Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.queue_title),
                style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                }
                
                Row(
                    modifier = Modifier.padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = if (draggedIndex >= 0)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (draggedIndex >= 0) 
                        stringResource(R.string.drag_to_reorder)
                    else
                        stringResource(R.string.hold_to_reorder, queue.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (draggedIndex >= 0)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true), // Take all space above FAB
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(bottom = 88.dp) // Space for FAB
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
            // ---------------------------
        } // End of Column inner content
        
        // FAB Group overlay
        var showSavePlaylistDialog by remember { mutableStateOf(false) }
        
        if (queue.isNotEmpty()) {
            com.example.neosynth.ui.components.FabGroup(
                actions = listOf(
                    com.example.neosynth.ui.components.FabAction(
                        icon = Icons.Rounded.PlaylistAdd,
                        label = stringResource(R.string.action_save),
                        onClick = { showSavePlaylistDialog = true }
                    ),
                    com.example.neosynth.ui.components.FabAction(
                        icon = Icons.Rounded.Download,
                        label = stringResource(R.string.action_download),
                        onClick = {
                            val downloadingMsg = context.getString(R.string.queue_downloading)
                            viewModel.downloadQueue()
                            onShowSnackbar(downloadingMsg)
                        }
                    ),
                    com.example.neosynth.ui.components.FabAction(
                        icon = Icons.Rounded.ClearAll,
                        label = stringResource(R.string.action_clear),
                        onClick = {
                            musicController.clearQueue()
                            onDismiss()
                        }
                    )
                ),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 32.dp, end = 24.dp)
            )
        }
        
        if (showSavePlaylistDialog) {
            var playlistName by remember { mutableStateOf("") }
            val isProcessing by viewModel.isProcessingQueueAction.collectAsState()
            val okMessage = stringResource(R.string.playlist_new) // Reusing playlist_new or similar for success? Actually just let it be or extract
            
            AlertDialog(
                onDismissRequest = { if (!isProcessing) showSavePlaylistDialog = false },
                title = { Text(stringResource(R.string.save_as_playlist)) },
                text = {
                    OutlinedTextField(
                        value = playlistName,
                        onValueChange = { playlistName = it },
                        label = { Text(stringResource(R.string.new_playlist_name)) },
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
                                        onShowSnackbar("Playlist creada correctamente")
                                    },
                                    onError = { error ->
                                        onShowSnackbar(error)
                                    }
                                )
                            }
                        },
                        enabled = playlistName.isNotBlank() && !isProcessing
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showSavePlaylistDialog = false },
                        enabled = !isProcessing
                    ) {
                        Text(stringResource(R.string.action_cancel))
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
    var showDeleteDialog by remember { mutableStateOf(false) }
    
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
                    onDragStart = { onDragStart() },
                    onDrag = { change, dragAmount -> 
                        change.consume()
                        onDrag(change, dragAmount) 
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() }
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
                    text = item.mediaMetadata.title?.toString() ?: "Sin título",
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
                    text = item.mediaMetadata.artist?.toString() ?: "Desconocido",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Remove button
            IconButton(
                onClick = { showDeleteDialog = true },
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
    
    // Confirmación de eliminar
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.queue_remove_title)) },
            text = { 
                Text(item.mediaMetadata.title?.toString() ?: "Canción") 
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemove()
                        showDeleteDialog = false
                    }
                ) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}
