package com.example.neosynth.ui.downloads

import com.example.neosynth.ui.components.bounceClick
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.res.stringResource
import com.example.neosynth.R
import com.example.neosynth.data.local.entities.SongEntity
import com.example.neosynth.ui.components.BottomMultiSelectBar
import com.example.neosynth.ui.components.SelectionModeState
import com.example.neosynth.ui.components.MultiSelectAction
import com.example.neosynth.ui.components.RowListItem
import com.example.neosynth.ui.components.AlphabetScrollbar
import com.example.neosynth.ui.stats.rememberBounceScale
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val groupedSongs: Map<Char, List<SongEntity>> by viewModel.groupedSongs.collectAsStateWithLifecycle(initialValue = emptyMap())
    val allPlaylists by viewModel.allPlaylists.collectAsStateWithLifecycle()
    val selectedPlaylistId by viewModel.selectedPlaylistId.collectAsStateWithLifecycle()
    val activeFilterCategory by viewModel.activeFilterCategory.collectAsStateWithLifecycle()
    val activeSortOrder by viewModel.activeSortOrder.collectAsStateWithLifecycle()

    var showSortDropdown by remember { mutableStateOf(false) }

    val allSongs = remember(groupedSongs) { groupedSongs.values.flatten() }

    var selectedSongIds by rememberSaveable { mutableStateOf<Set<String>>(setOf()) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearchVisible by rememberSaveable { mutableStateOf(false) }
    
    // Estado para el diálogo de agregar a playlist
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var pendingAddSongIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    val isSelectionMode = selectedSongIds.isNotEmpty()
    
    val currentSong by viewModel.currentSong
    val isMiniPlayerVisible = currentSong != null
    val listBottomPadding = if (isSelectionMode) {
        if (isMiniPlayerVisible) 305.dp else 225.dp
    } else {
        if (isMiniPlayerVisible) 180.dp else 100.dp
    }

    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Filtrar canciones según búsqueda y filtro
    val filteredSongs = remember(groupedSongs, searchQuery, activeFilterCategory, activeSortOrder) {
        val query = searchQuery.lowercase()
        
        if (query.isEmpty()) {
            groupedSongs
        } else {
            val allFlat = groupedSongs.values.flatten()
            
            val filtered = allFlat.filter { song ->
                song.title.lowercase().contains(query) ||
                song.artist.lowercase().contains(query) ||
                song.album.lowercase().contains(query)
            }
            
            // Agrupar según el filtro seleccionado
            if (activeSortOrder == SortOrder.RECENT) {
                filtered.groupBy { '↓' }
            } else if (activeFilterCategory == FilterCategory.ALBUMS) {
                filtered.groupBy { song ->
                    val firstChar = song.album.firstOrNull()?.uppercaseChar() ?: '#'
                    if (firstChar.isLetter()) firstChar else '#'
                }
            } else if (activeFilterCategory == FilterCategory.ARTISTS) {
                filtered.groupBy { song ->
                    val firstChar = song.artist.firstOrNull()?.uppercaseChar() ?: '#'
                    if (firstChar.isLetter()) firstChar else '#'
                }
            } else {
                filtered.groupBy { song ->
                    val firstChar = song.title.firstOrNull()?.uppercaseChar() ?: '#'
                    if (firstChar.isLetter()) firstChar else '#'
                }
            }
        }
    }
    
    // Detectar si estamos en modo álbum o artista para mostrar headers agrupados
    val showAlbumHeaders = activeFilterCategory == FilterCategory.ALBUMS
    val showArtistHeaders = activeFilterCategory == FilterCategory.ARTISTS

    LaunchedEffect(isSearchVisible) {
        if (isSearchVisible) {
            focusRequester.requestFocus()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSelectionMode) {
                    IconButton(
                        onClick = { selectedSongIds = emptySet() },
                        modifier = Modifier.bounceClick()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.action_clear_selection)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Text(
                        text = stringResource(R.string.recent_selection_count, selectedSongIds.size),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    
                    IconButton(
                        onClick = { selectedSongIds = allSongs.map { it.id }.toSet() },
                        modifier = Modifier.bounceClick()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SelectAll,
                            contentDescription = stringResource(R.string.action_select_all)
                        )
                    }
                } else {
                    // Botón atrás / cerrar búsqueda
                    IconButton(
                        onClick = {
                            when {
                                isSearchVisible -> {
                                    isSearchVisible = false
                                    searchQuery = ""
                                }
                                else -> onBack()
                            }
                        },
                        modifier = Modifier.bounceClick()
                    ) {
                        Icon(
                            imageVector = if (isSearchVisible) Icons.Rounded.Close else Icons.Rounded.ArrowBack,
                            contentDescription = null
                        )
                    }

                    // Barra de búsqueda animada
                    AnimatedContent(
                        targetState = isSearchVisible,
                        transitionSpec = {
                            fadeIn(tween(200)) togetherWith fadeOut(tween(150))
                        },
                        modifier = Modifier.weight(1f),
                        label = "search_bar"
                    ) { showSearch ->
                        if (showSearch) {
                            // Campo de búsqueda
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    BasicTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        modifier = Modifier
                                            .weight(1f)
                                            .focusRequester(focusRequester),
                                        textStyle = TextStyle(
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = MaterialTheme.typography.bodyMedium.fontSize
                                        ),
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                        singleLine = true,
                                        decorationBox = { innerTextField ->
                                            if (searchQuery.isEmpty()) {
                                                Text(
                                                    text = when (activeFilterCategory) {
                                                        FilterCategory.ARTISTS -> stringResource(R.string.search_artists_hint)
                                                        FilterCategory.ALBUMS -> stringResource(R.string.search_albums_hint)
                                                        FilterCategory.SONGS -> stringResource(R.string.search_songs_hint)
                                                        else -> stringResource(R.string.search_hint)
                                                    },
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            }
                                            innerTextField()
                                        }
                                    )
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(
                                            onClick = { searchQuery = "" },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Clear,
                                                contentDescription = stringResource(R.string.discover_clear),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // Título + Botón Desplegable para Orden
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.downloads),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                
                                Box {
                                    val sortLabelRes = when (activeSortOrder) {
                                        SortOrder.ASCENDING -> R.string.sort_ascending
                                        SortOrder.DESCENDING -> R.string.sort_descending
                                        SortOrder.TITLE -> R.string.sort_title
                                        SortOrder.ARTIST -> R.string.sort_artist
                                        SortOrder.ALBUM -> R.string.sort_album
                                        SortOrder.RECENT -> R.string.sort_recent
                                    }
                                    val interactionSource = remember { MutableInteractionSource() }
                                    val scale by rememberBounceScale(interactionSource)

                                    Surface(
                                        onClick = { showSortDropdown = true },
                                        interactionSource = interactionSource,
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                        ),
                                        modifier = Modifier.graphicsLayer {
                                            scaleX = scale
                                            scaleY = scale
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Sort,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = stringResource(sortLabelRes),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Icon(
                                                imageVector = Icons.Rounded.ArrowDropDown,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = showSortDropdown,
                                        onDismissRequest = { showSortDropdown = false },
                                        shape = RoundedCornerShape(20.dp),
                                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                                    ) {
                                        SortOrder.values().forEach { order ->
                                            val labelRes = when (order) {
                                                SortOrder.ASCENDING -> R.string.sort_ascending
                                                SortOrder.DESCENDING -> R.string.sort_descending
                                                SortOrder.TITLE -> R.string.sort_title
                                                SortOrder.ARTIST -> R.string.sort_artist
                                                SortOrder.ALBUM -> R.string.sort_album
                                                SortOrder.RECENT -> R.string.sort_recent
                                            }
                                            val icon = when (order) {
                                                SortOrder.ASCENDING -> Icons.Rounded.ArrowUpward
                                                SortOrder.DESCENDING -> Icons.Rounded.ArrowDownward
                                                SortOrder.TITLE -> Icons.Rounded.Title
                                                SortOrder.ARTIST -> Icons.Rounded.Person
                                                SortOrder.ALBUM -> Icons.Rounded.Album
                                                SortOrder.RECENT -> Icons.Rounded.Schedule
                                            }
                                            DropdownMenuItem(
                                                text = { 
                                                    Text(
                                                        text = stringResource(labelRes),
                                                        fontWeight = FontWeight.Medium
                                                    ) 
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = icon,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                },
                                                onClick = {
                                                    viewModel.setSortOrder(order)
                                                    showSortDropdown = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Botón de búsqueda
                    if (!isSearchVisible) {
                        IconButton(
                            onClick = { isSearchVisible = true },
                            modifier = Modifier.bounceClick()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = stringResource(R.string.action_search)
                            )
                        }
                    }
                }
            }

            // Carrusel horizontal de categorías (canciones, albumes, artistas, listas de reproducción, favoritos)
            if (!isSearchVisible) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(FilterCategory.values().toList()) { category ->
                        val isSelected = category == activeFilterCategory
                        val labelRes = when (category) {
                            FilterCategory.SONGS -> R.string.tab_songs
                            FilterCategory.ALBUMS -> R.string.tab_albums
                            FilterCategory.ARTISTS -> R.string.tab_artists
                            FilterCategory.PLAYLISTS -> R.string.tab_playlists
                            FilterCategory.FAVORITES -> R.string.tab_favorites
                        }
                        val interactionSource = remember { MutableInteractionSource() }
                        val scale by rememberBounceScale(interactionSource)

                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.setFilterCategory(category) },
                            label = { Text(stringResource(labelRes), fontWeight = FontWeight.Bold) },
                            shape = RoundedCornerShape(16.dp),
                            interactionSource = interactionSource,
                            modifier = Modifier.graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }

            // Lista de canciones con Alphabet Scrollbar
            val availableLetters = remember(filteredSongs) {
                filteredSongs.keys.toSet()
            }
            
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween // Asegurar que el scrollbar permanezca a la derecha
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = listBottomPadding) // Espacio dinámico para FAB + MiniPlayer + NavBar / SelectBar
                ) {
                    if (activeFilterCategory == FilterCategory.PLAYLISTS) {
                        if (allPlaylists.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Rounded.QueueMusic,
                                            contentDescription = null,
                                            modifier = Modifier.size(64.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = stringResource(R.string.playlist_no_available),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        } else {
                            items(
                                items = allPlaylists,
                                key = { it.playlist.id }
                            ) { playlistWithSongs ->
                                PlaylistDownloadItem(
                                    playlistWithSongs = playlistWithSongs,
                                    isSelected = selectedPlaylistId == playlistWithSongs.playlist.id,
                                    onClick = {
                                        if (selectedPlaylistId == playlistWithSongs.playlist.id) {
                                            viewModel.clearPlaylistFilter()
                                        } else {
                                            viewModel.selectPlaylist(playlistWithSongs.playlist.id)
                                        }
                                    },
                                    onPlay = { viewModel.playPlaylist(playlistWithSongs) },
                                    onDelete = { viewModel.deletePlaylist(playlistWithSongs.playlist.id) }
                                )
                            }
                        }
                    } else {
                        if (filteredSongs.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = if (searchQuery.isNotEmpty()) Icons.Rounded.SearchOff else Icons.Rounded.MusicOff,
                                            contentDescription = null,
                                            modifier = Modifier.size(64.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = if (searchQuery.isNotEmpty()) stringResource(R.string.discover_no_results) else stringResource(R.string.downloads_empty),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        } else {
                            // Chip de filtro activo
                            if (selectedPlaylistId != null) {
                                item {
                                    val selectedPlaylist = allPlaylists.find { it.playlist.id == selectedPlaylistId }
                                    if (selectedPlaylist != null) {
                                        FilterChip(
                                            selected = true,
                                            onClick = { viewModel.clearPlaylistFilter() },
                                            label = { Text(stringResource(R.string.downloads_filtering, selectedPlaylist.playlist.name)) },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Rounded.QueueMusic,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            },
                                            trailingIcon = {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = stringResource(R.string.action_clear_filter),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            },
                                            modifier = Modifier
                                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }

                            // Sección de Canciones
                            filteredSongs.forEach { (initial, songsInGroup) ->
                                stickyHeader(key = initial) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.background)
                                            .padding(horizontal = 24.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = initial.toString(),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                if (showAlbumHeaders) {
                                    val albumGroups = songsInGroup.groupBy { it.album }.toList().sortedBy { it.first }
                                    
                                    albumGroups.forEach { (albumName, albumSongs) ->
                                        item(key = "album_header_$albumName") {
                                            val albumSongIds = albumSongs.map { it.id }.toSet()
                                            val areAllSelected = albumSongIds.all { it in selectedSongIds }
                                            
                                            Surface(
                                                onClick = {
                                                    selectedSongIds = if (areAllSelected) {
                                                        selectedSongIds - albumSongIds
                                                    } else {
                                                        selectedSongIds + albumSongIds
                                                    }
                                                },
                                                color = if (areAllSelected) 
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                                else 
                                                    MaterialTheme.colorScheme.background,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Album,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(16.dp),
                                                            tint = MaterialTheme.colorScheme.primary
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            text = albumName,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.primary,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                    }
                                                    Text(
                                                        text = "${albumSongs.size}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.padding(end = 4.dp)
                                                    )
                                                }
                                            }
                                        }
                                        
                                        items(
                                            items = albumSongs,
                                            key = { "album_${it.id}" }
                                        ) { song ->
                                            val isSelected = selectedSongIds.contains(song.id)

                                            RowListItem(
                                                song = song.toDomainModel(),
                                                isSelected = isSelected,
                                                onClick = {
                                                    if (isSelectionMode) {
                                                        selectedSongIds = if (isSelected) selectedSongIds - song.id
                                                        else selectedSongIds + song.id
                                                    } else {
                                                        viewModel.playAll(listOf(song), 0)
                                                    }
                                                },
                                                onLongClick = {
                                                    if (!isSelectionMode) selectedSongIds = setOf(song.id)
                                                }
                                            )
                                        }
                                    }
                                } else if (showArtistHeaders) {
                                    val artistGroups = songsInGroup.groupBy { it.artist }.toList().sortedBy { it.first }
                                    
                                    artistGroups.forEach { (artistName, artistSongs) ->
                                        item(key = "artist_header_$artistName") {
                                            val artistSongIds = artistSongs.map { it.id }.toSet()
                                            val areAllSelected = artistSongIds.all { it in selectedSongIds }
                                            
                                            Surface(
                                                onClick = {
                                                    selectedSongIds = if (areAllSelected) {
                                                        selectedSongIds - artistSongIds
                                                    } else {
                                                        selectedSongIds + artistSongIds
                                                    }
                                                },
                                                color = if (areAllSelected) 
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                                else 
                                                    MaterialTheme.colorScheme.background,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Person,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(16.dp),
                                                            tint = MaterialTheme.colorScheme.primary
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            text = artistName,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.primary,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                    }
                                                    Text(
                                                        text = "${artistSongs.size}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.padding(end = 4.dp)
                                                    )
                                                }
                                            }
                                        }
                                        
                                        items(
                                            items = artistSongs,
                                            key = { "artist_${it.id}" }
                                        ) { song ->
                                            val isSelected = selectedSongIds.contains(song.id)

                                            RowListItem(
                                                song = song.toDomainModel(),
                                                isSelected = isSelected,
                                                onClick = {
                                                    if (isSelectionMode) {
                                                        selectedSongIds = if (isSelected) selectedSongIds - song.id
                                                        else selectedSongIds + song.id
                                                    } else {
                                                        viewModel.playAll(listOf(song), 0)
                                                    }
                                                },
                                                onLongClick = {
                                                    if (!isSelectionMode) selectedSongIds = setOf(song.id)
                                                }
                                            )
                                        }
                                    }
                                } else {
                                    items(
                                        items = songsInGroup,
                                        key = { it.id }
                                    ) { song ->
                                        val isSelected = selectedSongIds.contains(song.id)

                                        RowListItem(
                                            song = song.toDomainModel(),
                                            isSelected = isSelected,
                                            onClick = {
                                                if (isSelectionMode) {
                                                    selectedSongIds = if (isSelected) selectedSongIds - song.id
                                                    else selectedSongIds + song.id
                                                } else {
                                                    viewModel.playAll(listOf(song), 0)
                                                }
                                            },
                                            onLongClick = {
                                                if (!isSelectionMode) selectedSongIds = setOf(song.id)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Alphabet Scrollbar
                if (allSongs.size > 15 && !isSearchVisible) {
                    AlphabetScrollbar(
                        availableLetters = availableLetters,
                        currentLetter = null,
                        onLetterSelected = { letter: Char ->
                            // Buscar el índice del primer item con esa letra
                            val keys = filteredSongs.keys.toList()
                            val targetKeyIndex = keys.indexOf(letter)
                            if (targetKeyIndex >= 0) {
                                // Calcular el índice real en la lista (headers + items)
                                var itemIndex = 0
                                
                                // Agregar offset por playlists si existen
                                if (allPlaylists.isNotEmpty() && searchQuery.isEmpty()) {
                                    itemIndex += 1 // Header de playlists
                                    itemIndex += allPlaylists.size // Items de playlists
                                    if (selectedPlaylistId != null) {
                                        itemIndex += 1 // Chip de filtro
                                    }
                                    itemIndex += 1 // Spacer
                                }
                                
                                // Calcular índices para las secciones anteriores
                                for (i in 0 until targetKeyIndex) {
                                    itemIndex += 1 // Header de letra
                                    val songsInGroup = filteredSongs[keys[i]] ?: emptyList()
                                    
                                    // Si estamos en modo álbum o artista, contar headers adicionales
                                    if (showAlbumHeaders) {
                                        val albumGroups = songsInGroup.groupBy { it.album }
                                        albumGroups.forEach { (_, albumSongs) ->
                                            itemIndex += 1 // Header de álbum
                                            itemIndex += albumSongs.size // Canciones del álbum
                                        }
                                    } else if (showArtistHeaders) {
                                        val artistGroups = songsInGroup.groupBy { it.artist }
                                        artistGroups.forEach { (_, artistSongs) ->
                                            itemIndex += 1 // Header de artista
                                            itemIndex += artistSongs.size // Canciones del artista
                                        }
                                    } else {
                                        itemIndex += songsInGroup.size // Solo canciones
                                    }
                                }
                                scope.launch {
                                    listState.animateScrollToItem(itemIndex)
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(end = 4.dp, top = 8.dp, bottom = listBottomPadding)
                    )
                }
            } // Row
        }

        val bottomPaddingOffset = if (isMiniPlayerVisible) 225.dp else 145.dp
        BottomMultiSelectBar(
            visible = isSelectionMode,
            selectedCount = selectedSongIds.size,
            onClearSelection = { selectedSongIds = emptySet() },
            onPlaySelected = {
                viewModel.playSelected(selectedSongIds, allSongs)
                selectedSongIds = emptySet()
            },
            menuActions = listOf(
                MultiSelectAction(
                    icon = Icons.Rounded.PlaylistAdd,
                    label = stringResource(R.string.action_playlist),
                    onClick = {
                        pendingAddSongIds = selectedSongIds
                        showAddToPlaylistDialog = true
                    }
                ),
                MultiSelectAction(
                    icon = Icons.Rounded.PlayArrow,
                    label = stringResource(R.string.action_play_next),
                    onClick = {
                        viewModel.playNextSelected(selectedSongIds, allSongs)
                        selectedSongIds = emptySet()
                    }
                ),
                MultiSelectAction(
                    icon = Icons.Rounded.QueueMusic,
                    label = stringResource(R.string.action_add_to_queue),
                    onClick = {
                        viewModel.addToQueueSelected(selectedSongIds, allSongs)
                        selectedSongIds = emptySet()
                    }
                ),
                MultiSelectAction(
                    icon = Icons.Rounded.Favorite,
                    label = stringResource(R.string.action_add_favorite),
                    onClick = {
                        viewModel.addToFavorites(selectedSongIds)
                        selectedSongIds = emptySet()
                    }
                )
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = bottomPaddingOffset)
        )

        // Diálogo selector de playlist (Agregar canciones a una playlist)
        if (showAddToPlaylistDialog) {
            AddToPlaylistDialog(
                playlists = allPlaylists,
                onDismiss = { showAddToPlaylistDialog = false },
                onPlaylistSelected = { playlistId ->
                    viewModel.addSongsToPlaylist(pendingAddSongIds, playlistId)
                    showAddToPlaylistDialog = false
                    pendingAddSongIds = emptySet()
                }
            )
        }
    }
}

@Composable
private fun AddToPlaylistDialog(
    playlists: List<com.example.neosynth.data.local.entities.PlaylistWithSongs>,
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
                    playlists.forEach { playlistWithSongs ->
                        Surface(
                            onClick = { onPlaylistSelected(playlistWithSongs.playlist.id) },
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
                                Column {
                                    Text(
                                        text = playlistWithSongs.playlist.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = stringResource(R.string.downloads_songs_count, playlistWithSongs.playlist.songCount),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
