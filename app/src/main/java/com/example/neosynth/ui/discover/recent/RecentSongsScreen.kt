package com.example.neosynth.ui.discover.recent

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.neosynth.R
import com.example.neosynth.data.remote.responses.SongDto

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RecentSongsScreen(
    viewModel: RecentSongsViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val listState = rememberLazyListState()
    val downloadedIds by viewModel.downloadedSongIds.collectAsState()
    val currentSong by viewModel.musicController.currentMediaItem
    val isMiniPlayerVisible = currentSong != null

    // Detectar cuando el usuario llega al final para cargar más
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            lastVisible >= total - 3 && total > 0
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMore()
    }

    // Sort dropdown state
    var showSortMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            RecentSongsTopBar(
                selectionCount = viewModel.selectedSongIds.size,
                isSelectionMode = viewModel.isSelectionMode,
                currentSort = viewModel.sortOrder,
                showSortMenu = showSortMenu,
                onShowSortMenu = { showSortMenu = true },
                onDismissSortMenu = { showSortMenu = false },
                onSortSelected = { viewModel.changeSortOrder(it); showSortMenu = false },
                onSelectAll = { viewModel.selectAll() },
                onClearSelection = { viewModel.clearSelection() },
                onBack = {
                    if (viewModel.isSelectionMode) viewModel.clearSelection() else onBack()
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                viewModel.isLoading && viewModel.songs.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Text(stringResource(R.string.recent_loading_songs), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                viewModel.error != null && viewModel.songs.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                            Text(viewModel.error ?: stringResource(R.string.error_unknown), color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { viewModel.loadSongs() }) { Text(stringResource(R.string.action_retry)) }
                        }
                    }
                }
                else -> {
                    val bottomPadding = if (viewModel.isSelectionMode) {
                        if (isMiniPlayerVisible) 280.dp else 200.dp
                    } else {
                        if (isMiniPlayerVisible) 100.dp else 16.dp
                    }
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(bottom = bottomPadding),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(viewModel.songs, key = { it.id }) { song ->
                            RecentSongRow(
                                song = song,
                                isSelected = song.id in viewModel.selectedSongIds,
                                isSelectionMode = viewModel.isSelectionMode,
                                isDownloaded = song.id in downloadedIds,
                                getCoverUrl = viewModel::getCoverUrl,
                                onClick = {
                                    if (viewModel.isSelectionMode) {
                                        viewModel.toggleSelection(song.id)
                                    } else {
                                        viewModel.playSong(song)
                                    }
                                },
                                onLongClick = { viewModel.toggleSelection(song.id) },
                                modifier = Modifier.animateItem()
                            )
                        }

                        // Indicador de carga de más elementos
                        if (viewModel.isLoadingMore) {
                            item {
                                Box(
                                    Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        if (!viewModel.hasMore && viewModel.songs.isNotEmpty()) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        stringResource(R.string.recent_songs_count, viewModel.songs.size),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // FAB de descarga masiva
            AnimatedVisibility(
                visible = viewModel.isSelectionMode,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = if (isMiniPlayerVisible) 200.dp else 120.dp) // sobre la navbar flotante / miniplayer
            ) {
                val count = viewModel.selectedSongIds.size
                val alreadyDownloaded = viewModel.selectedSongIds.count { it in downloadedIds }
                val toDownload = count - alreadyDownloaded
                ExtendedFloatingActionButton(
                    onClick = { viewModel.downloadSelected() },
                    icon = { Icon(Icons.Rounded.Download, contentDescription = null) },
                    text = {
                        Text(
                            if (toDownload > 0) stringResource(R.string.recent_download_count, toDownload)
                            else stringResource(R.string.recent_already_downloaded)
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecentSongsTopBar(
    selectionCount: Int,
    isSelectionMode: Boolean,
    currentSort: SongSortOrder,
    showSortMenu: Boolean,
    onShowSortMenu: () -> Unit,
    onDismissSortMenu: () -> Unit,
    onSortSelected: (SongSortOrder) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onBack: () -> Unit,
) {
    TopAppBar(
        title = {
            if (isSelectionMode) {
                Text(stringResource(R.string.recent_selection_count, selectionCount), fontWeight = FontWeight.SemiBold)
            } else {
                Text(stringResource(R.string.discover_recent_songs), fontWeight = FontWeight.SemiBold)
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
        },
        actions = {
            if (isSelectionMode) {
                IconButton(onClick = onSelectAll) {
                    Icon(Icons.Rounded.SelectAll, contentDescription = stringResource(R.string.action_select_all))
                }
                IconButton(onClick = onClearSelection) {
                    Icon(Icons.Rounded.Deselect, contentDescription = stringResource(R.string.action_clear_selection))
                }
            } else {
                Box {
                    IconButton(onClick = onShowSortMenu) {
                        Icon(Icons.Rounded.Sort, contentDescription = stringResource(R.string.action_sort))
                    }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = onDismissSortMenu) {
                        SongSortOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        order.label,
                                        fontWeight = if (order == currentSort) FontWeight.Bold else FontWeight.Normal,
                                        color = if (order == currentSort) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                leadingIcon = {
                                    if (order == currentSort) {
                                        Icon(Icons.Rounded.Check, contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp))
                                    } else {
                                        Spacer(Modifier.size(18.dp))
                                    }
                                },
                                onClick = { onSortSelected(order) }
                            )
                        }
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = if (isSelectionMode)
                MaterialTheme.colorScheme.secondaryContainer
            else
                MaterialTheme.colorScheme.surface
        )
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentSongRow(
    song: SongDto,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    isDownloaded: Boolean,
    getCoverUrl: (String?) -> String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isSelected)
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
    else
        Color.Transparent

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(bgColor)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Cover art + overlay de selección
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            // Siempre mostrar la portada
            val coverUrl = getCoverUrl(song.coverArt)
            if (coverUrl != null) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Icon(
                        Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
            // Overlay de selección encima de la cover
            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (isSelected)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            else
                                Color.Black.copy(alpha = 0.35f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // Metadata
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${song.artist} • ${song.album}",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Badges + duración
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (isDownloaded) {
                Icon(
                    Icons.Rounded.DownloadDone,
                    contentDescription = stringResource(R.string.content_desc_downloaded),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = formatSeconds(song.duration),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    HorizontalDivider(
        modifier = Modifier.padding(start = 76.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    )
}

private fun formatSeconds(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}
