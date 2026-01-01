package com.example.neosynth.ui.playlist

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.neosynth.data.remote.responses.SongDto
import com.example.neosynth.ui.components.SelectionTopBar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onArtistClick: (String, String) -> Unit = { _, _ -> }
) {
    val playlist by viewModel.playlist.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val downloadedIds by viewModel.downloadedSongIds.collectAsState()

    var showAddSongsSheet by remember { mutableStateOf(false) }
    var showDeleteSongDialog by remember { mutableStateOf<Pair<Int, SongDto>?>(null) }
    
    // Multi-selection state
    var selectedSongIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val isSelectionMode = selectedSongIds.isNotEmpty()
    var showPlaylistPicker by remember { mutableStateOf(false) }

    val backgroundColor = MaterialTheme.colorScheme.background
    val primaryColor = MaterialTheme.colorScheme.primary

    LaunchedEffect(playlistId) {
        viewModel.loadPlaylist(playlistId)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 180.dp)
            ) {
                // Header
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(530.dp)
                    ) {
                        // Background gradient
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
                            // Playlist Cover
                            val coverUrl = viewModel.getCoverUrl(playlist?.coverArt)
                            if (coverUrl != null) {
                                Card(
                                    modifier = Modifier.size(160.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
                                ) {
                                    AsyncImage(
                                        model = coverUrl,
                                        contentDescription = playlist?.name,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            } else {
                                Card(
                                    modifier = Modifier.size(160.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.QueueMusic,
                                            contentDescription = null,
                                            modifier = Modifier.size(64.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Playlist name
                            Text(
                                text = playlist?.name ?: "",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // Song count
                            Text(
                                text = "${songs.size} canciones",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            // Botones compactos (solo iconos) como en AlbumDetail
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            ) {
                                // Botón Play (grande y prominente)
                                AnimatedIconButton(
                                    onClick = { viewModel.playPlaylist() },
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
                                    onClick = { viewModel.downloadPlaylist() },
                                    icon = Icons.Rounded.Download,
                                    contentDescription = "Descargar playlist",
                                    size = 48.dp,
                                    iconSize = 24.dp
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Botón agregar canciones
                            TextButton(
                                onClick = { showAddSongsSheet = true },
                                modifier = Modifier.padding(horizontal = 24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Agregar canciones")
                            }
                        }
                    }
                }

                // Songs header
                item {
                    Text(
                        text = "Canciones",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                    )
                }

                // Songs list
                if (songs.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Esta playlist está vacía",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    itemsIndexed(songs) { index, song ->
                        PlaylistSongRow(
                            index = index + 1,
                            song = song,
                            isDownloaded = song.id in downloadedIds,
                            isSelected = song.id in selectedSongIds,
                            isSelectionMode = isSelectionMode,
                            coverUrl = viewModel.getCoverUrl(song.coverArt),
                            onClick = {
                                if (isSelectionMode) {
                                    selectedSongIds = if (song.id in selectedSongIds) {
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
                            onRemove = { showDeleteSongDialog = index to song }
                        )
                    }
                }
            }
        }

        // Selection Top Bar
        SelectionTopBar(
            selectedCount = selectedSongIds.size,
            onClearSelection = { selectedSongIds = emptySet() },
            onPlaySelected = {
                viewModel.playSongs(selectedSongIds)
                selectedSongIds = emptySet()
            },
            onDownloadSelected = {
                viewModel.downloadSongs(selectedSongIds)
                selectedSongIds = emptySet()
            },
            onAddToFavorites = {
                viewModel.addToFavorites(selectedSongIds)
                selectedSongIds = emptySet()
            },
            onAddToPlaylist = {
                showPlaylistPicker = true
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
        )

        // Top bar (solo visible cuando no hay selección)
        if (!isSelectionMode) {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Volver"
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = androidx.compose.ui.graphics.Color.Transparent
            ),
            modifier = Modifier.statusBarsPadding()
        )
        }
        
        // Playlist Picker Dialog
        if (showPlaylistPicker) {
            com.example.neosynth.ui.components.PlaylistPickerDialog(
                playlists = emptyList<com.example.neosynth.data.remote.responses.PlaylistDto>(),
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

    // Delete song dialog
    showDeleteSongDialog?.let { (index, song) ->
        AlertDialog(
            onDismissRequest = { showDeleteSongDialog = null },
            title = { Text("Quitar canción") },
            text = { Text("¿Quitar \"${song.title}\" de esta playlist?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeSongFromPlaylist(index)
                        showDeleteSongDialog = null
                    }
                ) {
                    Text("Quitar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSongDialog = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistSongRow(
    index: Int,
    song: SongDto,
    isDownloaded: Boolean,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    coverUrl: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRemove: () -> Unit
) {
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isSelected) 0.97f else 1f,
        label = "scale"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        } else {
            androidx.compose.ui.graphics.Color.Transparent
        }
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox o índice
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = null,
                    modifier = Modifier.width(28.dp)
                )
            } else {
                Text(
                    text = index.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(28.dp),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Cover
            Box {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop
                )
                
                if (isDownloaded) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 4.dp, y = 4.dp)
                            .size(18.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DownloadDone,
                            contentDescription = "Descargada",
                            modifier = Modifier.padding(2.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Duration
            Text(
                text = formatDuration(song.duration),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Remove button (solo visible cuando no hay selección)
            if (!isSelectionMode) {
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Rounded.Remove,
                        contentDescription = "Quitar de playlist",
                        tint = MaterialTheme.colorScheme.error
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
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "icon_button_scale"
    )
    
    val elevation by animateFloatAsState(
        targetValue = if (isPressed) 1f else 12f,
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
        shadowElevation = androidx.compose.ui.unit.Dp(elevation)
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
