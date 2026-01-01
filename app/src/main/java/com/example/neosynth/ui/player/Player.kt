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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
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
    onPlayFromQueue: (Int) -> Unit,
    onMoveItem: (Int, Int) -> Unit,
    onRemoveItem: (Int) -> Unit
) {
    val listState = rememberLazyListState()
    
    // Sistema de reorden simplificado: seleccionar item con long press, luego tap en otro para intercambiar
    var selectedForMove by remember { mutableStateOf<Int?>(null) }

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
            Text(
                text = "Cola de reproducción",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = if (selectedForMove != null) 
                    "Toca otra canción para intercambiar posiciones"
                else
                    "${queue.size} canciones • Mantén presionado para reordenar",
                style = MaterialTheme.typography.bodyMedium,
                color = if (selectedForMove != null)
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
                    val isSelectedForMove = selectedForMove == index
                    
                    QueueItem(
                        item = item,
                        index = index,
                        isCurrentSong = isCurrentSong,
                        isSelectedForMove = isSelectedForMove,
                        onPlay = {
                            if (selectedForMove == null) {
                                onPlayFromQueue(index)
                            } else if (selectedForMove != index) {
                                // Intercambiar posiciones
                                onMoveItem(selectedForMove!!, index)
                                selectedForMove = null
                            } else {
                                // Deseleccionar
                                selectedForMove = null
                            }
                        },
                        onRemove = { onRemoveItem(index) },
                        onLongPress = {
                            selectedForMove = if (selectedForMove == index) null else index
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
    isSelectedForMove: Boolean = false,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    onLongPress: () -> Unit = {}
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    // Animación de escala cuando está seleccionado para mover
    val scale by animateFloatAsState(
        targetValue = if (isSelectedForMove) 1.08f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "queue_item_scale"
    )
    
    // Animación de elevación
    val elevation by animateFloatAsState(
        targetValue = if (isSelectedForMove) 12f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "queue_item_elevation"
    )
    
    Surface(
        onClick = {
            if (isSelectedForMove) {
                onPlay() // Cuando está seleccionado, al hacer click en otro item se intercambian
            } else {
                onPlay()
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .combinedClickable(
                onClick = {
                    onPlay() // Siempre ejecuta onPlay (que en QueueBottomSheet maneja el swap)
                },
                onLongClick = onLongPress
            ),
        color = when {
            isSelectedForMove -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
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
            // Indicador de selección (en lugar de drag handle)
            if (isSelectedForMove) {
                Icon(
                    imageVector = Icons.Rounded.SwapVert,
                    contentDescription = "Toca otro item para intercambiar",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

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
                modifier = Modifier.size(32.dp)
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
