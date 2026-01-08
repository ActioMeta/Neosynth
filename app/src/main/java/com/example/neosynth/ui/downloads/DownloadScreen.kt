package com.example.neosynth.ui.downloads

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
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
import com.example.neosynth.data.local.entities.SongEntity
import com.example.neosynth.ui.components.AlphabetScrollbar
import com.example.neosynth.ui.components.RowListItem
import com.example.neosynth.ui.components.SideMultiSelectBar
import com.example.neosynth.ui.components.MultiSelectAction
import kotlinx.coroutines.launch

enum class FilterType {
    ALL, ARTIST, ALBUM, SONG
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val groupedSongs: Map<Char, List<SongEntity>> by viewModel.groupedSongs.collectAsState(initial = emptyMap())
    val downloadedPlaylists by viewModel.downloadedPlaylists.collectAsState()
    val selectedPlaylistId by viewModel.selectedPlaylistId.collectAsState()
    val allSongs = remember(groupedSongs) { groupedSongs.values.flatten() }

    var selectedSongIds by rememberSaveable { mutableStateOf<Set<String>>(setOf()) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearchVisible by rememberSaveable { mutableStateOf(false) }
    var currentFilter by rememberSaveable { mutableStateOf(FilterType.ALL) }
    var fabExpanded by remember { mutableStateOf(false) }

    val isSelectionMode = selectedSongIds.isNotEmpty()
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Filtrar canciones según búsqueda y filtro
    val filteredSongs = remember(groupedSongs, searchQuery, currentFilter) {
        val query = searchQuery.lowercase()
        
        // Si no hay query y el filtro es ALL, mostrar todo agrupado alfabéticamente
        if (query.isEmpty() && currentFilter == FilterType.ALL) {
            groupedSongs
        } else {
            val allFlat = groupedSongs.values.flatten()
            
            val filtered = allFlat.filter { song ->
                val matchesQuery = when {
                    query.isEmpty() -> true
                    else -> song.title.lowercase().contains(query) ||
                            song.artist.lowercase().contains(query) ||
                            song.album.lowercase().contains(query)
                }
                matchesQuery
            }
            
            // Agrupar según el filtro seleccionado
            when (currentFilter) {
                FilterType.ARTIST -> {
                    // Agrupar por primera letra del artista
                    filtered.groupBy { song ->
                        val firstChar = song.artist.firstOrNull()?.uppercaseChar() ?: '#'
                        if (firstChar.isLetter()) firstChar else '#'
                    }.toSortedMap()
                }
                FilterType.ALBUM -> {
                    // Agrupar por nombre completo del álbum para mostrar álbumes completos
                    filtered.groupBy { song ->
                        song.album.firstOrNull()?.uppercaseChar() ?: '#'
                    }.toSortedMap()
                }
                else -> {
                    // Agrupar por primera letra del título
                    filtered.groupBy { song ->
                        val firstChar = song.title.firstOrNull()?.uppercaseChar() ?: '#'
                        if (firstChar.isLetter()) firstChar else '#'
                    }.toSortedMap()
                }
            }
        }
    }
    
    // Detectar si estamos en modo álbum o artista para mostrar headers agrupados
    val showAlbumHeaders = currentFilter == FilterType.ALBUM
    val showArtistHeaders = currentFilter == FilterType.ARTIST

    LaunchedEffect(isSearchVisible) {
        if (isSearchVisible) {
            focusRequester.requestFocus()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Barra superior compacta con padding para status bar (solo visible cuando NO hay selección)
            if (!isSelectionMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                        }
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
                                .background(MaterialTheme.colorScheme.surfaceVariant),
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
                                                text = when (currentFilter) {
                                                    FilterType.ARTIST -> "Buscar artistas..."
                                                    FilterType.ALBUM -> "Buscar álbumes..."
                                                    FilterType.SONG -> "Buscar canciones..."
                                                    else -> "Buscar..."
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
                                            contentDescription = "Limpiar",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Mostrar cantidad de canciones
                        Text(
                            text = "${allSongs.size} canciones",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 8.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Botón de búsqueda
                if (!isSearchVisible) {
                    IconButton(onClick = { isSearchVisible = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Buscar"
                        )
                    }
                }
            }
            }

            // Lista de canciones con Alphabet Scrollbar
            val availableLetters = remember(filteredSongs) {
                filteredSongs.keys.toSet()
            }
            
            Row(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 180.dp) // Espacio para FAB + MiniPlayer + NavBar
                ) {
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
                                    text = if (searchQuery.isNotEmpty()) "Sin resultados" else "No hay descargas",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    // Sección de Playlists descargadas
                    if (downloadedPlaylists.isNotEmpty() && searchQuery.isEmpty()) {
                        stickyHeader(key = "playlists_header") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.background)
                                    .padding(horizontal = 24.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Playlists",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        items(
                            items = downloadedPlaylists,
                            key = { it.playlist.id }
                        ) { playlistWithSongs ->
                            PlaylistDownloadItem(
                                playlistWithSongs = playlistWithSongs,
                                isSelected = selectedPlaylistId == playlistWithSongs.playlist.id,
                                onClick = {
                                    // Toggle: Si ya está seleccionada, limpiar filtro; si no, seleccionarla
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
                        
                        // Chip de filtro activo
                        if (selectedPlaylistId != null) {
                            item {
                                val selectedPlaylist = downloadedPlaylists.find { it.playlist.id == selectedPlaylistId }
                                if (selectedPlaylist != null) {
                                    FilterChip(
                                        selected = true,
                                        onClick = { viewModel.clearPlaylistFilter() },
                                        label = { Text("Filtrando: ${selectedPlaylist.playlist.name}") },
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
                                                contentDescription = "Limpiar filtro",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        },
                                        modifier = Modifier
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                        
                        // Separador entre playlists y canciones
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
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

                        // Si estamos filtrando por álbum, agrupar por nombre de álbum
                        if (showAlbumHeaders) {
                            val albumGroups = songsInGroup.groupBy { it.album }.toList().sortedBy { it.first }
                            
                            albumGroups.forEach { (albumName, albumSongs) ->
                                item(key = "album_header_$albumName") {
                                    val albumSongIds = albumSongs.map { it.id }.toSet()
                                    val areAllSelected = albumSongIds.all { it in selectedSongIds }
                                    
                                    Surface(
                                        onClick = {
                                            // Multi-select: Toggle selección de todas las canciones del álbum
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
                                                val index = allSongs.indexOfFirst { it.id == song.id }
                                                if (index >= 0) {
                                                    viewModel.playAll(allSongs, index)
                                                }
                                            }
                                        },
                                        onLongClick = {
                                            if (!isSelectionMode) selectedSongIds = setOf(song.id)
                                        }
                                    )
                                }
                            }
                        } else if (showArtistHeaders) {
                            // Filtrado por artista: mostrar headers de artista clickeables
                            val artistGroups = songsInGroup.groupBy { it.artist }.toList().sortedBy { it.first }
                            
                            artistGroups.forEach { (artistName, artistSongs) ->
                                item(key = "artist_header_$artistName") {
                                    val artistSongIds = artistSongs.map { it.id }.toSet()
                                    val areAllSelected = artistSongIds.all { it in selectedSongIds }
                                    
                                    Surface(
                                        onClick = {
                                            // Multi-select: Toggle selección de todas las canciones del artista
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
                                                val index = allSongs.indexOfFirst { it.id == song.id }
                                                if (index >= 0) {
                                                    viewModel.playAll(allSongs, index)
                                                }
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
                                            // Reproducir canción
                                            val index = allSongs.indexOfFirst { it.id == song.id }
                                            if (index >= 0) {
                                                viewModel.playAll(allSongs, index)
                                            }
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
                } // LazyColumn
                
                // Alphabet Scrollbar
                if (allSongs.size > 15 && !isSearchVisible) {
                    AlphabetScrollbar(
                        availableLetters = availableLetters,
                        currentLetter = null,
                        onLetterSelected = { letter ->
                            // Buscar el índice del primer item con esa letra
                            val keys = filteredSongs.keys.toList()
                            val targetKeyIndex = keys.indexOf(letter)
                            if (targetKeyIndex >= 0) {
                                // Calcular el índice real en la lista (headers + items)
                                var itemIndex = 0
                                for (i in 0 until targetKeyIndex) {
                                    itemIndex += 1 + (filteredSongs[keys[i]]?.size ?: 0)
                                }
                                scope.launch {
                                    listState.animateScrollToItem(itemIndex)
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(end = 4.dp, top = 8.dp, bottom = 260.dp) // Aumentado para dar espacio al FAB y MiniPlayer
                    )
                }
            } // Row
        }

        // Barra lateral de selección
        SideMultiSelectBar(
            visible = isSelectionMode,
            selectedCount = selectedSongIds.size,
            actions = listOf(
                MultiSelectAction(
                    icon = Icons.Rounded.PlayArrow,
                    label = "Play",
                    onClick = {
                        val selectedSongs = allSongs.filter { it.id in selectedSongIds }
                        if (selectedSongs.isNotEmpty()) {
                            viewModel.playAll(selectedSongs, 0)
                        }
                        selectedSongIds = emptySet()
                    }
                ),
                MultiSelectAction(
                    icon = Icons.Rounded.Favorite,
                    label = "Fav",
                    onClick = {
                        val selectedSongs = allSongs.filter { it.id in selectedSongIds }
                        viewModel.addToFavorites(selectedSongs.map { it.id }.toSet())
                        selectedSongIds = emptySet()
                    }
                ),
                MultiSelectAction(
                    icon = Icons.Rounded.Delete,
                    label = "Del",
                    onClick = {
                        viewModel.deleteSelectedSongs(selectedSongIds)
                        selectedSongIds = emptySet()
                    }
                ),
                MultiSelectAction(
                    icon = Icons.Rounded.QueueMusic,
                    label = "Queue",
                    onClick = {
                        val selectedSongs = allSongs.filter { it.id in selectedSongIds }
                        viewModel.addToQueue(selectedSongs)
                        selectedSongIds = emptySet()
                    }
                )
            ),
            onClose = { selectedSongIds = emptySet() },
            modifier = Modifier.align(Alignment.CenterEnd)
        )

        // FAB Group (solo visible cuando NO hay selección)
        if (!isSelectionMode) {
            DownloadsFabGroup(
                expanded = fabExpanded,
                onExpandedChange = { fabExpanded = it },
                onShuffleAll = {
                    if (allSongs.isNotEmpty()) {
                        viewModel.playAll(allSongs.shuffled(), 0)
                    }
                    fabExpanded = false
                },
                onPlayQueue = {
                    // Reproducir la cola actual en lugar de todas las descargas
                    viewModel.playCurrentQueue()
                    fabExpanded = false
                },
                onFilterAll = {
                    currentFilter = FilterType.ALL
                    fabExpanded = false
                },
                onFilterArtist = {
                    currentFilter = FilterType.ARTIST
                    fabExpanded = false
                },
                onFilterAlbum = {
                    currentFilter = FilterType.ALBUM
                    fabExpanded = false
                },
                currentFilter = currentFilter,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 16.dp, bottom = 200.dp)
            )
        }
    }
}

@Composable
private fun DownloadsFabGroup(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onShuffleAll: () -> Unit,
    onPlayQueue: () -> Unit,
    onFilterAll: () -> Unit,
    onFilterArtist: () -> Unit,
    onFilterAlbum: () -> Unit,
    currentFilter: FilterType,
    modifier: Modifier = Modifier
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "rotation"
    )

    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(150)) + expandVertically(expandFrom = Alignment.Bottom),
            exit = fadeOut(tween(100)) + shrinkVertically(shrinkTowards = Alignment.Bottom)
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FabMenuPill(
                    icon = Icons.Rounded.Shuffle,
                    label = "Aleatorio",
                    onClick = onShuffleAll,
                    delay = 0
                )
                FabMenuPill(
                    icon = Icons.Rounded.QueueMusic,
                    label = "Reproducir cola",
                    onClick = onPlayQueue,
                    delay = 40
                )
                FabMenuPill(
                    icon = Icons.Rounded.Album,
                    label = "Álbumes",
                    onClick = onFilterAlbum,
                    isSelected = currentFilter == FilterType.ALBUM,
                    delay = 80
                )
                FabMenuPill(
                    icon = Icons.Rounded.Person,
                    label = "Artistas",
                    onClick = onFilterArtist,
                    isSelected = currentFilter == FilterType.ARTIST,
                    delay = 120
                )
                FabMenuPill(
                    icon = Icons.Rounded.LibraryMusic,
                    label = "Todo",
                    onClick = onFilterAll,
                    isSelected = currentFilter == FilterType.ALL,
                    delay = 160
                )
            }
        }

        // FAB principal
        FloatingActionButton(
            onClick = { onExpandedChange(!expanded) },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = if (expanded) Icons.Rounded.Close else Icons.Rounded.MoreVert,
                contentDescription = if (expanded) "Cerrar" else "Opciones",
                modifier = Modifier.rotate(rotation)
            )
        }
    }
}

@Composable
private fun FabMenuPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    delay: Int,
    isSelected: Boolean = false
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(delay.toLong())
        visible = true
    }

    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.5f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )
    
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(150),
        label = "alpha"
    )
    
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "press_scale"
    )

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(24.dp),
        color = if (isSelected) 
            MaterialTheme.colorScheme.primary 
        else 
            MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (isSelected)
            MaterialTheme.colorScheme.onPrimary
        else
            MaterialTheme.colorScheme.onSecondaryContainer,
        shadowElevation = 4.dp,
        modifier = Modifier
            .scale(scale)
            .graphicsLayer { 
                this.alpha = alpha
                scaleX = pressScale
                scaleY = pressScale
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
        }
    }
}