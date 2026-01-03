package com.example.neosynth.ui.album

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.neosynth.data.remote.responses.SongDto
import com.example.neosynth.ui.components.SideMultiSelectBar
import com.example.neosynth.ui.components.MultiSelectAction
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AlbumDetailScreen(
    albumId: String,
    viewModel: AlbumDetailViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onArtistClick: (String, String) -> Unit = { _, _ -> }
) {
    val album by viewModel.album.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val downloadedIds by viewModel.downloadedSongIds.collectAsState()
    
    // Multi-selection state
    var selectedSongIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val isSelectionMode = selectedSongIds.isNotEmpty()
    var showPlaylistPicker by remember { mutableStateOf(false) }

    val backgroundColor = MaterialTheme.colorScheme.background
    val primaryColor = MaterialTheme.colorScheme.primary
    
    // State para parallax
    val listState = rememberLazyListState()
    val scrollOffset = remember { derivedStateOf { listState.firstVisibleItemScrollOffset.toFloat() } }
    val firstVisibleItemIndex = remember { derivedStateOf { listState.firstVisibleItemIndex } }
    
    // Parallax offset (el header se mueve más lento que el scroll)
    val parallaxOffset = if (firstVisibleItemIndex.value == 0) {
        scrollOffset.value * 0.5f // Factor parallax
    } else {
        0f
    }
    
    // Fade del header según scroll
    val headerAlpha = if (firstVisibleItemIndex.value == 0) {
        (1f - (scrollOffset.value / 800f)).coerceIn(0f, 1f)
    } else {
        0f
    }

    LaunchedEffect(albumId) {
        viewModel.loadAlbum(albumId)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            AlbumSkeleton(brush = com.example.neosynth.ui.components.rememberShimmerBrush())
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 180.dp)
            ) {
                // Header con cover del álbum (con parallax)
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(520.dp)
                            .graphicsLayer {
                                translationY = -parallaxOffset
                                alpha = headerAlpha
                            }
                    ) {
                        // Fondo con gradiente suave
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    brush = Brush.verticalGradient(
                                        colorStops = arrayOf(
                                            0.0f to primaryColor.copy(alpha = 0.35f),
                                            0.35f to primaryColor.copy(alpha = 0.2f),
                                            0.55f to primaryColor.copy(alpha = 0.1f),
                                            0.75f to backgroundColor.copy(alpha = 0.9f),
                                            1.0f to backgroundColor
                                        )
                                    )
                                )
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
                                .padding(top = 56.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Album Cover
                            val coverUrl = viewModel.getCoverUrl(album?.coverArt)
                            Card(
                                modifier = Modifier.size(180.dp),
                                shape = RoundedCornerShape(12.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
                            ) {
                                AsyncImage(
                                    model = coverUrl,
                                    contentDescription = album?.name,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Título del álbum
                            Text(
                                text = album?.name ?: "",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // Artista (clickeable)
                            TextButton(
                                onClick = {
                                    album?.let { a ->
                                        val artistId = songs.firstOrNull()?.artistId ?: ""
                                        if (artistId.isNotEmpty()) {
                                            onArtistClick(artistId, a.artist ?: "")
                                        }
                                    }
                                }
                            ) {
                                Text(
                                    text = album?.artist ?: "",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Botones compactos (solo iconos) con animaciones
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            ) {
                                // Botón Play (grande y prominente)
                                AnimatedIconButton(
                                    onClick = { viewModel.playAlbum() },
                                    icon = Icons.Rounded.PlayArrow,
                                    contentDescription = "Reproducir",
                                    isPrimary = true,
                                    size = 64.dp,
                                    iconSize = 36.dp
                                )
                                
                                Spacer(modifier = Modifier.width(8.dp))

                                // Botones secundarios más pequeños
                                AnimatedIconButton(
                                    onClick = { viewModel.shufflePlay() },
                                    icon = Icons.Rounded.Shuffle,
                                    contentDescription = "Aleatorio",
                                    size = 48.dp,
                                    iconSize = 24.dp
                                )

                                AnimatedIconButton(
                                    onClick = { viewModel.downloadAlbum() },
                                    icon = Icons.Rounded.Download,
                                    contentDescription = "Descargar álbum",
                                    size = 48.dp,
                                    iconSize = 24.dp
                                )
                            }
                        }
                    }
                }

                // Info del álbum
                item {
                    AlbumInfoRow(
                        year = album?.year,
                        songCount = songs.size,
                        totalDuration = songs.sumOf { it.duration },
                        genre = album?.genre
                    )
                }

                // Lista de canciones
                item {
                    Text(
                        text = "Canciones",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)
                    )
                }

                itemsIndexed(songs) { index, song ->
                    val isSelected = song.id in selectedSongIds
                    AlbumSongRow(
                        index = index + 1,
                        song = song,
                        isDownloaded = song.id in downloadedIds,
                        isSelected = isSelected,
                        isSelectionMode = isSelectionMode,
                        onClick = {
                            if (isSelectionMode) {
                                selectedSongIds = if (isSelected) {
                                    selectedSongIds - song.id
                                } else {
                                    selectedSongIds + song.id
                                }
                            } else {
                                viewModel.playSong(song)
                            }
                        },
                        onLongClick = {
                            if (!isSelectionMode) {
                                selectedSongIds = setOf(song.id)
                            }
                        },
                        onDownload = { viewModel.downloadSong(song) }
                    )
                }
            }
        }
        
        // Barra lateral de selección
        SideMultiSelectBar(
            visible = selectedSongIds.isNotEmpty(),
            selectedCount = selectedSongIds.size,
            actions = listOf(
                MultiSelectAction(
                    icon = Icons.Rounded.PlayArrow,
                    label = "Play",
                    onClick = {
                        viewModel.playSongs(selectedSongIds)
                        selectedSongIds = emptySet()
                    }
                ),
                MultiSelectAction(
                    icon = Icons.Rounded.Favorite,
                    label = "Favoritos",
                    onClick = {
                        viewModel.addToFavorites(selectedSongIds)
                        selectedSongIds = emptySet()
                    }
                ),
                MultiSelectAction(
                    icon = Icons.Rounded.Download,
                    label = "Descargar",
                    onClick = {
                        viewModel.downloadSongs(selectedSongIds)
                        selectedSongIds = emptySet()
                    }
                ),
                MultiSelectAction(
                    icon = Icons.Rounded.PlaylistAdd,
                    label = "Playlist",
                    onClick = {
                        showPlaylistPicker = true
                    }
                )
            ),
            onClose = { selectedSongIds = emptySet() },
            modifier = Modifier.align(Alignment.CenterEnd)
        )

        // Normal Top App Bar (solo visible cuando no hay selección)
        if (!isSelectionMode) {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(backgroundColor.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),
                modifier = Modifier.statusBarsPadding()
            )
        }
        
        // Playlist Picker Dialog
        if (showPlaylistPicker) {
            val playlists by viewModel.playlists.collectAsState()
            
            LaunchedEffect(showPlaylistPicker) {
                if (showPlaylistPicker) {
                    viewModel.loadPlaylists()
                }
            }
            
            com.example.neosynth.ui.components.PlaylistPickerDialog(
                playlists = playlists,
                onDismiss = { 
                    showPlaylistPicker = false 
                },
                onPlaylistSelected = { playlistId ->
                    viewModel.addToPlaylist(selectedSongIds, playlistId)
                    selectedSongIds = emptySet()
                }
            )
        }
    }
}

@Composable
private fun AlbumInfoRow(
    year: Int?,
    songCount: Int,
    totalDuration: Int,
    genre: String?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        year?.let {
            InfoChip(text = it.toString())
            Spacer(modifier = Modifier.width(8.dp))
        }
        
        InfoChip(text = "$songCount canciones")
        Spacer(modifier = Modifier.width(8.dp))
        
        InfoChip(text = formatTotalDuration(totalDuration))
        
        genre?.let {
            Spacer(modifier = Modifier.width(8.dp))
            InfoChip(text = it)
        }
    }
}

@Composable
private fun InfoChip(text: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun AlbumSongRow(
    index: Int,
    song: SongDto,
    isDownloaded: Boolean,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDownload: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "song_scale"
    )
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox o número
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.size(28.dp)
                )
            } else {
                Text(
                    text = index.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(28.dp),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    
                    if (isDownloaded) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Rounded.DownloadDone,
                            contentDescription = "Descargada",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Duración
            Text(
                text = formatDuration(song.duration),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            // Botón de descarga (solo visible si no está en modo selección)
            if (!isSelectionMode && !isDownloaded) {
                IconButton(
                    onClick = onDownload,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = "Descargar",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimatedIconButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    isPrimary: Boolean = false,
    size: androidx.compose.ui.unit.Dp = 48.dp,
    iconSize: androidx.compose.ui.unit.Dp = 24.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f, // Más visible
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow // Más lento y visible
        ),
        label = "icon_button_scale"
    )
    
    val elevation by animateFloatAsState(
        targetValue = if (isPressed) 1f else 12f, // Mayor contraste
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "icon_button_elevation"
    )
    
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = CircleShape,
        color = if (isPrimary) 
            MaterialTheme.colorScheme.primary 
        else 
            MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = elevation.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (isPrimary)
                    MaterialTheme.colorScheme.onPrimary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return "$mins:${secs.toString().padStart(2, '0')}"
}

private fun formatTotalDuration(seconds: Int): String {
    val hours = seconds / 3600
    val mins = (seconds % 3600) / 60
    return if (hours > 0) {
        "$hours h $mins min"
    } else {
        "$mins min"
    }
}
