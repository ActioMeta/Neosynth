package com.example.neosynth.ui.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import coil.compose.AsyncImage
import com.example.neosynth.player.MusicController
import com.example.neosynth.ui.components.AlphabetScrollbar
import com.example.neosynth.ui.components.AnimatedPlayerSlider
import com.example.neosynth.ui.components.PlayerOptionsBar
import com.example.neosynth.ui.components.bounceClick
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    musicController: MusicController,
    onBack: () -> Unit,
    onDownload: () -> Unit = {},
    isCurrentSongDownloaded: Boolean = false,
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {}
) {
    val currentSong by musicController.currentMediaItem
    val isPlaying by musicController.isPlaying
    val currentPosition by musicController.currentPosition
    val duration by musicController.duration
    val queue by musicController.currentQueue
    val currentIndex by musicController.currentIndex

    var showQueueSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val song = currentSong

    // Queue Bottom Sheet
    if (showQueueSheet) {
        QueueBottomSheet(
            queue = queue,
            currentIndex = currentIndex,
            onDismiss = { showQueueSheet = false },
            sheetState = sheetState,
            musicController = musicController,
            onPlayFromQueue = { index ->
                musicController.playFromQueue(index)
                showQueueSheet = false
            },
            onMoveItem = { from, to ->
                musicController.moveQueueItem(from, to)
            },
            onRemoveItem = { index ->
                musicController.removeFromQueue(index)
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // BOTÓN BACK: Más grande y alineado totalmente a la izquierda
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.Start)
                .padding(top = 8.dp)
                .size(48.dp) // Tamaño del área de clic aumentado
        ) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = "Cerrar",
                modifier = Modifier.size(40.dp) // Icono más grande
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Artwork con crossfade
        Card(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .aspectRatio(1f),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(15.dp)
        ) {
            Crossfade(
                targetState = song?.mediaMetadata?.artworkUri,
                animationSpec = tween(durationMillis = 300),
                label = "cover_crossfade"
            ) { artworkUri ->
                AsyncImage(
                    model = artworkUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(modifier = Modifier.weight(0.1f))

        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            Text(
                text = song?.mediaMetadata?.title?.toString() ?: "Sin título",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = song?.mediaMetadata?.artist?.toString() ?: "Artista desconocido",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .basicMarquee(
                        iterations = Int.MAX_VALUE, // Infinito
                        spacing = MarqueeSpacing(24.dp),
                        initialDelayMillis = 2000,
                        repeatDelayMillis = 2000
                    )
            )
        }
        Spacer(modifier = Modifier.height(30.dp))
        AnimatedPlayerSlider(
            musicController = musicController
        )
        Spacer(modifier = Modifier.weight(0.1f))

        // Botones de control con animaciones más visibles
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Botón anterior con animación
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
                onClick = { musicController.skipPrevious() },
                interactionSource = interactionSourcePrev,
                modifier = Modifier.graphicsLayer {
                    scaleX = scalePrev
                    scaleY = scalePrev
                }
            ) {
                Icon(
                    Icons.Rounded.SkipPrevious, 
                    null, 
                    modifier = Modifier.size(50.dp)
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
            
            Surface(
                onClick = { musicController.togglePlayPause() },
                interactionSource = interactionSourcePlay,
                modifier = Modifier
                    .size(80.dp)
                    .graphicsLayer {
                        scaleX = scalePlay
                        scaleY = scalePlay
                    },
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = if (isPressedPlay) 4.dp else 12.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }

            // Botón siguiente con animación
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
                onClick = { musicController.skipNext() },
                interactionSource = interactionSourceNext,
                modifier = Modifier.graphicsLayer {
                    scaleX = scaleNext
                    scaleY = scaleNext
                }
            ) {
                Icon(
                    Icons.Rounded.SkipNext, 
                    null, 
                    modifier = Modifier.size(50.dp)
                )
            }
        }

        Spacer(modifier = Modifier.weight(0.15f))

        PlayerOptionsBar(
            musicController = musicController,
            onDownloadClick = onDownload,
            onQueueClick = { showQueueSheet = true },
            isDownloaded = isCurrentSongDownloaded,
            isFavorite = isFavorite,
            onToggleFavorite = onToggleFavorite
        )

        Spacer(modifier = Modifier.height(10.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun QueueBottomSheet(
    queue: List<MediaItem>,
    currentIndex: Int,
    onDismiss: () -> Unit,
    sheetState: SheetState,
    musicController: MusicController,
    onPlayFromQueue: (Int) -> Unit,
    onMoveItem: (Int, Int) -> Unit,
    onRemoveItem: (Int) -> Unit
) {
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    
    // Estado para drag & drop
    var draggedIndex by remember { mutableIntStateOf(-1) }
    var hoveredIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            // Header con título y botón limpiar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cola de reproducción",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                if (queue.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            musicController.clearQueue()
                            onDismiss()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ClearAll,
                            contentDescription = "Limpiar cola",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Limpiar")
                    }
                }
            }
            
            Text(
                text = if (draggedIndex >= 0) 
                    "Arrastra para reordenar"
                else
                    "${queue.size} canciones • Mantén presionado para reordenar",
                style = MaterialTheme.typography.bodyMedium,
                color = if (draggedIndex >= 0)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(max = 450.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(
                    items = queue,
                    key = { _: Int, item: MediaItem -> item.mediaId }
                ) { index, item ->
                    val isCurrentSong = index == currentIndex
                    val isDragged = draggedIndex == index
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
                                onPlayFromQueue(index)
                            }
                        },
                        onRemove = { onRemoveItem(index) },
                        onDragStart = {
                            draggedIndex = index
                            dragOffset = 0f
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDrag = { change, dragAmount ->
                            if (draggedIndex == index) {
                                dragOffset += dragAmount.y
                                
                                // Calcular índice objetivo basado en el offset
                                val itemHeight = 72f // Altura estimada del item
                                val targetIndex = (index + (dragOffset / itemHeight).toInt())
                                    .coerceIn(0, queue.size - 1)
                                
                                if (targetIndex != hoveredIndex && targetIndex != index) {
                                    hoveredIndex = targetIndex
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        },
                        onDragEnd = {
                            if (draggedIndex >= 0 && hoveredIndex >= 0 && draggedIndex != hoveredIndex) {
                                onMoveItem(draggedIndex, hoveredIndex)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            draggedIndex = -1
                            hoveredIndex = -1
                            dragOffset = 0f
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
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
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
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
            .pointerInput(index) {
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
            // Drag handle (siempre visible)
            Icon(
                imageVector = Icons.Rounded.DragHandle,
                contentDescription = "Arrastra para reordenar",
                tint = if (isDragged) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp)
            )

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
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.size(32.dp),
                enabled = !isDragged
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Eliminar de la cola",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Playing indicator
            if (isCurrentSong) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
    
    // Confirmación de eliminar
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Eliminar de la cola") },
            text = { 
                Text("¿Eliminar \"${item.mediaMetadata.title}\" de la lista de reproducción?") 
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemove()
                        showDeleteConfirm = false
                    }
                ) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
