package com.example.neosynth.ui.playlist

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.*
import com.example.neosynth.ui.stats.rememberBounceScale
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
import com.example.neosynth.ui.components.BottomMultiSelectBar
import com.example.neosynth.ui.components.MultiSelectAction
import androidx.compose.ui.res.stringResource
import com.example.neosynth.R

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlaylistDetailScreen(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    playlistId: String,
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onArtistClick: (String, String) -> Unit = { _, _ -> }
) {
    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val downloadedIds by viewModel.downloadedSongIds.collectAsStateWithLifecycle()

    var showAddSongsSheet by remember { mutableStateOf(false) }
    var showDeleteSongDialog by remember { mutableStateOf<Pair<Int, SongDto>?>(null) }
    
    // Multi-selection state
    var selectedSongIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val isSelectionMode = selectedSongIds.isNotEmpty()
    var showMultiSelectGridBottomSheet by remember { mutableStateOf(false) }
    var showPlaylistPicker by remember { mutableStateOf(false) }

    val currentSong by viewModel.musicController.currentMediaItem
    val isMiniPlayerVisible = currentSong != null
    val listBottomPadding = if (isSelectionMode) {
        if (isMiniPlayerVisible) 260.dp else 180.dp
    } else {
        if (isMiniPlayerVisible) 180.dp else 100.dp
    }

    val backgroundColor = MaterialTheme.colorScheme.background
    val primaryColor = MaterialTheme.colorScheme.primary

    LaunchedEffect(playlistId) {
        viewModel.loadPlaylist(playlistId)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            PlaylistSkeleton(brush = com.example.neosynth.ui.components.rememberShimmerBrush())
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = listBottomPadding)
            ) {
                // Header Hero con cover de playlist y difuminado hacia abajo (Visible desde el frame 0)
                item {
                    val coverUrl = viewModel.getCoverUrl(playlist?.coverArt)
                    val coverShape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
                    val palette = com.example.neosynth.ui.album.rememberAlbumPalette(coverUrl)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(370.dp)
                    ) {
                        // Imagen de portada Hero
                        if (coverUrl != null) {
                            AsyncImage(
                                model = coverUrl,
                                contentDescription = playlist?.name,
                                modifier = Modifier
                                    .clip(coverShape)
                                    .fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(coverShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.QueueMusic,
                                    contentDescription = null,
                                    modifier = Modifier.size(90.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Gradiente difuminado hacia abajo usando el color del Palette
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colorStops = arrayOf(
                                            0.0f to androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.25f),
                                            0.35f to androidx.compose.ui.graphics.Color.Transparent,
                                            0.75f to palette.accent.copy(alpha = 0.5f),
                                            1.0f to backgroundColor
                                        )
                                    )
                                )
                        )

                        // Título de la Playlist sobre el cover en la esquina inferior izquierda
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 20.dp, bottom = 16.dp, end = 170.dp)
                        ) {
                            Text(
                                text = playlist?.name ?: "",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = androidx.compose.ui.graphics.Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Botones juntos (Play 68dp + Random 52dp + Sync 46dp) con Palette en la esquina inferior derecha
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 20.dp, bottom = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Botón de sincronizar (más pequeño 46dp)
                            val syncInteraction = remember { MutableInteractionSource() }
                            val syncScale by rememberBounceScale(syncInteraction)
                            val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
                            Surface(
                                onClick = { viewModel.syncPlaylist() },
                                interactionSource = syncInteraction,
                                enabled = !isSyncing,
                                shape = CircleShape,
                                color = palette.container,
                                tonalElevation = 6.dp,
                                shadowElevation = 6.dp,
                                modifier = Modifier
                                    .size(46.dp)
                                    .graphicsLayer {
                                        scaleX = syncScale
                                        scaleY = syncScale
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (isSyncing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = palette.accent
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.Sync,
                                            contentDescription = stringResource(R.string.action_sync_playlist),
                                            tint = androidx.compose.ui.graphics.Color(0xFFE8E8E8),
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }

                            // Botón de aleatorio (52dp, icono gris claro)
                            val shuffleInteraction = remember { MutableInteractionSource() }
                            val shuffleScale by rememberBounceScale(shuffleInteraction)
                            Surface(
                                onClick = { viewModel.shufflePlay() },
                                interactionSource = shuffleInteraction,
                                shape = CircleShape,
                                color = palette.container,
                                tonalElevation = 6.dp,
                                shadowElevation = 6.dp,
                                modifier = Modifier
                                    .size(52.dp)
                                    .graphicsLayer {
                                        scaleX = shuffleScale
                                        scaleY = shuffleScale
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Shuffle,
                                        contentDescription = stringResource(R.string.action_shuffle),
                                        tint = androidx.compose.ui.graphics.Color(0xFFE8E8E8),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            // Botón de reproducir (más grande 68dp, M3 Expressive shape)
                            val playInteraction = remember { MutableInteractionSource() }
                            val playScale by rememberBounceScale(playInteraction)
                            val playExpressiveShape = RoundedCornerShape(
                                topStart = 28.dp,
                                topEnd = 12.dp,
                                bottomEnd = 28.dp,
                                bottomStart = 12.dp
                            )

                            Surface(
                                onClick = { viewModel.playPlaylist() },
                                interactionSource = playInteraction,
                                shape = playExpressiveShape,
                                color = palette.accent,
                                tonalElevation = 8.dp,
                                shadowElevation = 8.dp,
                                modifier = Modifier
                                    .size(68.dp)
                                    .graphicsLayer {
                                        scaleX = playScale
                                        scaleY = playScale
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.PlayArrow,
                                        contentDescription = stringResource(R.string.action_play),
                                        tint = palette.onAccent,
                                        modifier = Modifier.size(38.dp)
                                    )
                                }
                            }
                        }
                    }
                }

            // Fila debajo del cover: Chips de información o Control de Selección animado (AnimatedSelectionRow)
            item {
                val coverUrl = viewModel.getCoverUrl(playlist?.coverArt)
                val palette = com.example.neosynth.ui.album.rememberAlbumPalette(coverUrl)

                com.example.neosynth.ui.components.AnimatedSelectionRow(
                    isSelectionMode = isSelectionMode,
                    selectedCount = selectedSongIds.size,
                    totalCount = songs.size,
                    onSelectAll = { selectedSongIds = songs.map { it.id }.toSet() },
                    onClearSelection = { selectedSongIds = emptySet() },
                    onOpenOptionsGrid = { showMultiSelectGridBottomSheet = true },
                    accentColor = palette.accent,
                    infoContent = {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = palette.accent.copy(alpha = 0.22f)
                            ) {
                                Text(
                                    text = stringResource(R.string.library_songs_count, songs.size),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = androidx.compose.ui.graphics.Color(0xFFE8E8E8),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                )
            }

            // Lista de canciones
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
                                text = stringResource(R.string.playlist_empty),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                    PlaylistSongRow(
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
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

        // MultiSelectGridBottomSheet
        if (showMultiSelectGridBottomSheet) {
            com.example.neosynth.ui.components.MultiSelectGridBottomSheet(
                selectedCount = selectedSongIds.size,
                onDismiss = { showMultiSelectGridBottomSheet = false },
                onPlay = {
                    viewModel.playSongs(selectedSongIds)
                    selectedSongIds = emptySet()
                    showMultiSelectGridBottomSheet = false
                },
                onDownload = {
                    viewModel.downloadSongs(selectedSongIds)
                    selectedSongIds = emptySet()
                    showMultiSelectGridBottomSheet = false
                },
                onAddToPlaylist = {
                    viewModel.loadAllPlaylists()
                    showPlaylistPicker = true
                    showMultiSelectGridBottomSheet = false
                },
                onPlayNext = {
                    viewModel.playSongsNext(selectedSongIds)
                    selectedSongIds = emptySet()
                    showMultiSelectGridBottomSheet = false
                },
                onAddToQueue = {
                    viewModel.addSongsToQueue(selectedSongIds)
                    selectedSongIds = emptySet()
                    showMultiSelectGridBottomSheet = false
                },
                onFavorite = {
                    viewModel.addToFavorites(selectedSongIds)
                    selectedSongIds = emptySet()
                    showMultiSelectGridBottomSheet = false
                }
            )
        }

        if (showPlaylistPicker) {
            val allPlaylists by viewModel.allPlaylists.collectAsStateWithLifecycle()
            PlaylistPickerDialog(
                playlists = allPlaylists,
                onDismiss = { showPlaylistPicker = false },
                onPlaylistSelected = { targetPlaylistId ->
                    viewModel.addToPlaylist(selectedSongIds, targetPlaylistId)
                    showPlaylistPicker = false
                    selectedSongIds = emptySet()
                }
            )
        }

        // Top bar (solo visible cuando no hay selección)
        if (!isSelectionMode) {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.action_back)
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = androidx.compose.ui.graphics.Color.Transparent
            ),
            modifier = Modifier.statusBarsPadding()
        )
        }
    }

    // Delete song dialog
    showDeleteSongDialog?.let { (index, song) ->
        AlertDialog(
            onDismissRequest = { showDeleteSongDialog = null },
            title = { Text(stringResource(R.string.playlist_remove_song_title)) },
            text = { Text(stringResource(R.string.playlist_remove_song_desc, song.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeSongFromPlaylist(index)
                        showDeleteSongDialog = null
                    }
                ) {
                    Text(stringResource(R.string.action_remove), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSongDialog = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistSongRow(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
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
    val interactionSource = remember { MutableInteractionSource() }
    val scale by rememberBounceScale(interactionSource)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f)
        }
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cover
            Box {
                with(sharedTransitionScope) {
                     AsyncImage(
                        model = coverUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .sharedElement(
                                sharedContentState = rememberSharedContentState(key = "artwork-${song.id}"),
                                animatedVisibilityScope = animatedVisibilityScope
                            ),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    
                    if (isDownloaded) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Rounded.DownloadDone,
                            contentDescription = stringResource(R.string.content_desc_downloaded),
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

            if (!isSelectionMode) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Remove,
                        contentDescription = stringResource(R.string.playlist_remove_song_title),
                        tint = MaterialTheme.colorScheme.error,
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
            MaterialTheme.colorScheme.surfaceContainerLow,
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

@Composable
private fun PlaylistPickerDialog(
    playlists: List<com.example.neosynth.data.remote.responses.PlaylistDto>,
    onDismiss: () -> Unit,
    onPlaylistSelected: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.playlist_add_to)) },
        text = {
            if (playlists.isEmpty()) {
                Text(stringResource(R.string.playlist_no_available))
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    playlists.forEach { playlist ->
                        Surface(
                            onClick = { onPlaylistSelected(playlist.id) },
                            shape = RoundedCornerShape(8.dp),
                            color = androidx.compose.ui.graphics.Color.Transparent,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.QueueMusic,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = playlist.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
