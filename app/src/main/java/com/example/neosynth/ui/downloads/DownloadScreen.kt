package com.example.neosynth.ui.downloads

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.neosynth.R
import com.example.neosynth.data.local.entities.SongEntity
import com.example.neosynth.data.remote.responses.SongDto
import com.example.neosynth.ui.components.AlphabetScrollbar
import com.example.neosynth.ui.components.MultiSelectGridBottomSheet
import com.example.neosynth.ui.components.SongOptionsBottomSheet
import com.example.neosynth.ui.stats.rememberBounceScale
import kotlinx.coroutines.launch

data class DownloadedAlbumItem(
    val albumId: String,
    val name: String,
    val artist: String,
    val coverArt: String?,
    val songs: List<SongEntity>
)

data class DownloadedArtistItem(
    val artistId: String,
    val name: String,
    val coverArt: String? = null,
    val songs: List<SongEntity>
)

fun SongEntity.toSongDto(): SongDto {
    return SongDto(
        id = this.id,
        title = this.title,
        artist = this.artist,
        artistId = this.artistID,
        album = this.album,
        albumId = this.albumID,
        duration = (this.duration / 1000).toInt(),
        coverArt = this.imageUrl,
        path = this.path,
        year = this.year,
        contentType = "audio/mpeg",
        suffix = "mp3"
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onAlbumClick: (String) -> Unit = {},
    onArtistClick: (String, String) -> Unit = { _, _ -> },
    onPlaylistClick: (String) -> Unit = {}
) {
    val groupedSongs: Map<Char, List<SongEntity>> by viewModel.groupedSongs.collectAsStateWithLifecycle(initialValue = emptyMap())
    val allPlaylists by viewModel.allPlaylists.collectAsStateWithLifecycle()
    val activeFilterCategory by viewModel.activeFilterCategory.collectAsStateWithLifecycle()
    val activeSortOrder by viewModel.activeSortOrder.collectAsStateWithLifecycle()

    var showCategoryDropdown by remember { mutableStateOf(false) }
    var showSortDropdown by remember { mutableStateOf(false) }
    var showMultiSelectGridBottomSheet by remember { mutableStateOf(false) }
    var songForOptions by remember { mutableStateOf<SongEntity?>(null) }

    val allSongs = remember(groupedSongs) { groupedSongs.values.flatten() }

    var selectedSongIds by rememberSaveable { mutableStateOf<Set<String>>(setOf()) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearchVisible by rememberSaveable { mutableStateOf(false) }

    val isSelectionMode = selectedSongIds.isNotEmpty()

    val currentSong by viewModel.currentSong
    val isMiniPlayerVisible = currentSong != null
    val listBottomPadding = if (isMiniPlayerVisible) 180.dp else 100.dp

    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Agrupaciones de Álbumes descargados
    val downloadedAlbums = remember(allSongs, activeSortOrder, searchQuery) {
        val query = searchQuery.lowercase()
        val filtered = if (query.isEmpty()) allSongs else allSongs.filter {
            it.album.lowercase().contains(query) || it.artist.lowercase().contains(query)
        }
        val groupedMap = filtered.groupBy { if (it.albumID.isNotEmpty()) it.albumID else it.album }
        val items = groupedMap.map { (id, songs) ->
            DownloadedAlbumItem(
                albumId = id,
                name = songs.first().album,
                artist = songs.first().artist,
                coverArt = songs.first().imageUrl,
                songs = songs
            )
        }
        when (activeSortOrder) {
            SortOrder.ASCENDING -> items.sortedBy { it.name.lowercase() }
            SortOrder.DESCENDING -> items.sortedByDescending { it.name.lowercase() }
            SortOrder.RECENT -> items.sortedByDescending { album -> album.songs.maxOfOrNull { s -> s.downloadedAt ?: 0L } ?: 0L }
            else -> items.sortedBy { it.name.lowercase() }
        }
    }

    // Agrupaciones de Artistas descargados
    val downloadedArtists = remember(allSongs, activeSortOrder, searchQuery) {
        val query = searchQuery.lowercase()
        val filtered = if (query.isEmpty()) allSongs else allSongs.filter {
            it.artist.lowercase().contains(query)
        }
        val groupedMap = filtered.groupBy { if (it.artistID.isNotEmpty()) it.artistID else it.artist }
        val items = groupedMap.map { (id, songs) ->
            DownloadedArtistItem(
                artistId = id,
                name = songs.first().artist,
                coverArt = songs.firstOrNull { !it.imageUrl.isNullOrBlank() }?.imageUrl,
                songs = songs
            )
        }
        when (activeSortOrder) {
            SortOrder.ASCENDING -> items.sortedBy { it.name.lowercase() }
            SortOrder.DESCENDING -> items.sortedByDescending { it.name.lowercase() }
            SortOrder.RECENT -> items.sortedByDescending { artist -> artist.songs.maxOfOrNull { s -> s.downloadedAt ?: 0L } ?: 0L }
            else -> items.sortedBy { it.name.lowercase() }
        }
    }

    // Listado de canciones filtrado
    val filteredSongsList = remember(allSongs, activeSortOrder, searchQuery) {
        val query = searchQuery.lowercase()
        val filtered = if (query.isEmpty()) allSongs else allSongs.filter {
            it.title.lowercase().contains(query) ||
            it.artist.lowercase().contains(query) ||
            it.album.lowercase().contains(query)
        }
        when (activeSortOrder) {
            SortOrder.ASCENDING -> filtered.sortedBy { it.title.lowercase() }
            SortOrder.DESCENDING -> filtered.sortedByDescending { it.title.lowercase() }
            SortOrder.RECENT -> filtered.sortedByDescending { it.downloadedAt ?: 0L }
            else -> filtered.sortedBy { it.title.lowercase() }
        }
    }

    // Configuración dinámica del AlphabetScrollbar
    val showScrollbar = activeSortOrder != SortOrder.RECENT
    val isDescendingSort = activeSortOrder == SortOrder.DESCENDING

    val availableLetters = remember(activeFilterCategory, activeSortOrder, downloadedAlbums, downloadedArtists, allPlaylists, filteredSongsList) {
        if (!showScrollbar) return@remember emptySet()
        when (activeFilterCategory) {
            FilterCategory.ALBUMS -> downloadedAlbums.mapNotNull { it.name.firstOrNull()?.uppercaseChar() }.toSet()
            FilterCategory.ARTISTS -> downloadedArtists.mapNotNull { it.name.firstOrNull()?.uppercaseChar() }.toSet()
            FilterCategory.PLAYLISTS -> allPlaylists.mapNotNull { it.playlist.name.firstOrNull()?.uppercaseChar() }.toSet()
            else -> filteredSongsList.mapNotNull { it.title.firstOrNull()?.uppercaseChar() }.toSet()
        }
    }

    val totalCount = when (activeFilterCategory) {
        FilterCategory.ALBUMS -> downloadedAlbums.size
        FilterCategory.ARTISTS -> downloadedArtists.size
        FilterCategory.PLAYLISTS -> allPlaylists.size
        else -> filteredSongsList.size
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Top Bar (Integrado fluidamente sin bloque sólido)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (isSearchVisible) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .clip(RoundedCornerShape(21.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .padding(horizontal = 14.dp),
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
                                decorationBox = { inner ->
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.search_hint),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    inner()
                                }
                            )
                            IconButton(
                                onClick = {
                                    isSearchVisible = false
                                    searchQuery = ""
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.action_cancel),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    } else {
                        // Selector Desplegable de Categorías (Canciones, Álbumes, Artistas, Playlists)
                        Box {
                            val categoryName = when (activeFilterCategory) {
                                FilterCategory.SONGS -> stringResource(R.string.tab_songs)
                                FilterCategory.ALBUMS -> stringResource(R.string.tab_albums)
                                FilterCategory.ARTISTS -> stringResource(R.string.tab_artists)
                                FilterCategory.PLAYLISTS -> stringResource(R.string.tab_playlists)
                                FilterCategory.FAVORITES -> stringResource(R.string.tab_favorites)
                            }
                            val categoryIcon = when (activeFilterCategory) {
                                FilterCategory.SONGS -> Icons.Rounded.MusicNote
                                FilterCategory.ALBUMS -> Icons.Rounded.Album
                                FilterCategory.ARTISTS -> Icons.Rounded.Person
                                FilterCategory.PLAYLISTS -> Icons.Rounded.QueueMusic
                                FilterCategory.FAVORITES -> Icons.Rounded.Favorite
                            }

                            Surface(
                                onClick = { showCategoryDropdown = true },
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = categoryIcon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = categoryName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Icon(
                                        imageVector = Icons.Rounded.ArrowDropDown,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showCategoryDropdown,
                                onDismissRequest = { showCategoryDropdown = false },
                                shape = RoundedCornerShape(20.dp),
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                listOf(
                                    FilterCategory.SONGS to (stringResource(R.string.tab_songs) to Icons.Rounded.MusicNote),
                                    FilterCategory.ALBUMS to (stringResource(R.string.tab_albums) to Icons.Rounded.Album),
                                    FilterCategory.ARTISTS to (stringResource(R.string.tab_artists) to Icons.Rounded.Person),
                                    FilterCategory.PLAYLISTS to (stringResource(R.string.tab_playlists) to Icons.Rounded.QueueMusic)
                                ).forEach { (cat, info) ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = info.first,
                                                fontWeight = FontWeight.Bold
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = info.second,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        },
                                        onClick = {
                                            viewModel.setFilterCategory(cat)
                                            showCategoryDropdown = false
                                        }
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = { isSearchVisible = true },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = stringResource(R.string.action_search),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Fila de Controles de Acción Animada (Desplazamiento in-place para Selección Múltiple)
            AnimatedContent(
                targetState = isSelectionMode,
                transitionSpec = {
                    (slideInHorizontally { width -> if (targetState) width else -width } + fadeIn()).togetherWith(
                        slideOutHorizontally { width -> if (targetState) -width else width } + fadeOut()
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                label = "DownloadActionRowTransition"
            ) { inSelection ->
                if (inSelection) {
                    // Modo Selección: 2 botones independientes + 3 Puntos a la derecha
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Botón 1: Seleccionar todo
                                Surface(
                                    onClick = {
                                        selectedSongIds = when (activeFilterCategory) {
                                            FilterCategory.ALBUMS -> downloadedAlbums.map { it.albumId }.toSet()
                                            FilterCategory.ARTISTS -> downloadedArtists.map { it.artistId }.toSet()
                                            FilterCategory.PLAYLISTS -> allPlaylists.map { it.playlist.id }.toSet()
                                            else -> filteredSongsList.map { it.id }.toSet()
                                        }
                                    },
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.SelectAll,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Seleccionar todo",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }

                                // Botón 2: Deseleccionar todo
                                Surface(
                                    onClick = { selectedSongIds = emptySet() },
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Deselect,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Deseleccionar",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                Text(
                                    text = "${selectedSongIds.size} / $totalCount",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Botón 3 Puntos -> Abre MultiSelectGridBottomSheet
                            Surface(
                                onClick = { showMultiSelectGridBottomSheet = true },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Box(
                                    modifier = Modifier.size(36.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.MoreVert,
                                        contentDescription = stringResource(R.string.action_options),
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Modo Normal: Botón Aleatorio compacto (Izquierda) + Ordenamiento (Derecha)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Botón Aleatorio M3 Expressive
                        val shuffleInteraction = remember { MutableInteractionSource() }
                        val shuffleScale by rememberBounceScale(shuffleInteraction)
                        Surface(
                            onClick = {
                                val songsToPlay = when (activeFilterCategory) {
                                    FilterCategory.ALBUMS -> downloadedAlbums.flatMap { it.songs }
                                    FilterCategory.ARTISTS -> downloadedArtists.flatMap { it.songs }
                                    FilterCategory.PLAYLISTS -> allPlaylists.flatMap { it.songs }
                                    else -> filteredSongsList
                                }
                                viewModel.shufflePlayAll(songsToPlay)
                            },
                            interactionSource = shuffleInteraction,
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            tonalElevation = 4.dp,
                            shadowElevation = 4.dp,
                            modifier = Modifier
                                .height(46.dp)
                                .width(56.dp)
                                .graphicsLayer {
                                    scaleX = shuffleScale
                                    scaleY = shuffleScale
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Shuffle,
                                    contentDescription = stringResource(R.string.action_play_shuffle),
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        // Botón de Ordenamiento (Ascendente, Descendente, Recientes)
                        Box {
                            val sortLabelRes = when (activeSortOrder) {
                                SortOrder.ASCENDING -> R.string.sort_ascending
                                SortOrder.DESCENDING -> R.string.sort_descending
                                SortOrder.RECENT -> R.string.sort_recent
                                else -> R.string.sort_ascending
                            }

                            Surface(
                                onClick = { showSortDropdown = true },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Sort,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = stringResource(sortLabelRes),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Icon(
                                        imageVector = Icons.Rounded.ArrowDropDown,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showSortDropdown,
                                onDismissRequest = { showSortDropdown = false },
                                shape = RoundedCornerShape(20.dp),
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                listOf(
                                    SortOrder.ASCENDING to (stringResource(R.string.sort_ascending) to Icons.Rounded.ArrowUpward),
                                    SortOrder.DESCENDING to (stringResource(R.string.sort_descending) to Icons.Rounded.ArrowDownward),
                                    SortOrder.RECENT to (stringResource(R.string.sort_recent) to Icons.Rounded.Schedule)
                                ).forEach { (order, info) ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = info.first,
                                                fontWeight = FontWeight.Bold
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = info.second,
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

            // Lista Principal con AlphabetScrollbar adaptable
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = listBottomPadding)
                ) {
                    when (activeFilterCategory) {
                        FilterCategory.ALBUMS -> {
                            items(downloadedAlbums, key = { it.albumId }) { album ->
                                val isSelected = album.albumId in selectedSongIds

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp)
                                        .combinedClickable(
                                            onClick = {
                                                if (isSelectionMode) {
                                                    selectedSongIds = if (isSelected) selectedSongIds - album.albumId else selectedSongIds + album.albumId
                                                } else {
                                                    onAlbumClick(album.albumId)
                                                }
                                            },
                                            onLongClick = {
                                                if (!isSelectionMode) selectedSongIds = setOf(album.albumId)
                                            }
                                        ),
                                    shape = RoundedCornerShape(18.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AsyncImage(
                                            model = album.coverArt,
                                            contentDescription = album.name,
                                            modifier = Modifier
                                                .size(52.dp)
                                                .clip(RoundedCornerShape(14.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = album.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${album.artist} • ${album.songs.size} canciones",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        FilterCategory.ARTISTS -> {
                            items(downloadedArtists, key = { it.artistId }) { artist ->
                                val isSelected = artist.artistId in selectedSongIds

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp)
                                        .combinedClickable(
                                            onClick = {
                                                if (isSelectionMode) {
                                                    selectedSongIds = if (isSelected) selectedSongIds - artist.artistId else selectedSongIds + artist.artistId
                                                } else {
                                                    onArtistClick(artist.artistId, artist.name)
                                                }
                                            },
                                            onLongClick = {
                                                if (!isSelectionMode) selectedSongIds = setOf(artist.artistId)
                                            }
                                        ),
                                    shape = RoundedCornerShape(18.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.size(52.dp)
                                        ) {
                                            if (!artist.coverArt.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = artist.coverArt,
                                                    contentDescription = artist.name,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .clip(CircleShape)
                                                )
                                            } else {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Person,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.size(28.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = artist.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${artist.songs.size} canciones descargadas",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        FilterCategory.PLAYLISTS -> {
                            items(allPlaylists, key = { it.playlist.id }) { playlistWithSongs ->
                                val isSelected = playlistWithSongs.playlist.id in selectedSongIds

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp)
                                        .combinedClickable(
                                            onClick = {
                                                if (isSelectionMode) {
                                                    selectedSongIds = if (isSelected) selectedSongIds - playlistWithSongs.playlist.id else selectedSongIds + playlistWithSongs.playlist.id
                                                } else {
                                                    onPlaylistClick(playlistWithSongs.playlist.id)
                                                }
                                            },
                                            onLongClick = {
                                                if (!isSelectionMode) selectedSongIds = setOf(playlistWithSongs.playlist.id)
                                            }
                                        ),
                                    shape = RoundedCornerShape(18.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AsyncImage(
                                            model = playlistWithSongs.playlist.coverArt,
                                            contentDescription = playlistWithSongs.playlist.name,
                                            modifier = Modifier
                                                .size(52.dp)
                                                .clip(RoundedCornerShape(14.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = playlistWithSongs.playlist.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${playlistWithSongs.songs.size} canciones",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        else -> {
                            // CANCIONES
                            items(filteredSongsList, key = { it.id }) { song ->
                                val isSelected = song.id in selectedSongIds

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 3.dp)
                                        .combinedClickable(
                                            onClick = {
                                                if (isSelectionMode) {
                                                    selectedSongIds = if (isSelected) selectedSongIds - song.id else selectedSongIds + song.id
                                                } else {
                                                    val index = filteredSongsList.indexOf(song)
                                                    viewModel.playAll(filteredSongsList, if (index >= 0) index else 0)
                                                }
                                            },
                                            onLongClick = {
                                                if (!isSelectionMode) selectedSongIds = setOf(song.id)
                                            }
                                        ),
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AsyncImage(
                                            model = song.imageUrl,
                                            contentDescription = song.title,
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(RoundedCornerShape(12.dp)),
                                            contentScale = ContentScale.Crop
                                        )

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = song.title,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Bold,
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

                                        if (!isSelectionMode) {
                                            IconButton(
                                                onClick = { songForOptions = song },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.MoreVert,
                                                    contentDescription = stringResource(R.string.action_options),
                                                    modifier = Modifier.size(20.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // AlphabetScrollbar adaptable (desaparece si el orden es Recientes)
                if (showScrollbar && availableLetters.isNotEmpty()) {
                    val scrollbarBottomPadding = if (isMiniPlayerVisible) 190.dp else 105.dp

                    AlphabetScrollbar(
                        availableLetters = availableLetters,
                        currentLetter = null,
                        isDescending = isDescendingSort,
                        onLetterSelected = { letter ->
                            scope.launch {
                                when (activeFilterCategory) {
                                    FilterCategory.ALBUMS -> {
                                        val idx = downloadedAlbums.indexOfFirst { it.name.firstOrNull()?.uppercaseChar() == letter }
                                        if (idx >= 0) listState.animateScrollToItem(idx)
                                    }
                                    FilterCategory.ARTISTS -> {
                                        val idx = downloadedArtists.indexOfFirst { it.name.firstOrNull()?.uppercaseChar() == letter }
                                        if (idx >= 0) listState.animateScrollToItem(idx)
                                    }
                                    FilterCategory.PLAYLISTS -> {
                                        val idx = allPlaylists.indexOfFirst { it.playlist.name.firstOrNull()?.uppercaseChar() == letter }
                                        if (idx >= 0) listState.animateScrollToItem(idx)
                                    }
                                    else -> {
                                        val idx = filteredSongsList.indexOfFirst { it.title.firstOrNull()?.uppercaseChar() == letter }
                                        if (idx >= 0) listState.animateScrollToItem(idx)
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(top = 4.dp, bottom = scrollbarBottomPadding, end = 6.dp)
                    )
                }
            }
        }

        // MultiSelectGridBottomSheet
        if (showMultiSelectGridBottomSheet) {
            val selectedSongs = remember(selectedSongIds, activeFilterCategory, downloadedAlbums, downloadedArtists, allPlaylists, allSongs) {
                when (activeFilterCategory) {
                    FilterCategory.ALBUMS -> {
                        downloadedAlbums.filter { it.albumId in selectedSongIds }.flatMap { it.songs }
                    }
                    FilterCategory.ARTISTS -> {
                        downloadedArtists.filter { it.artistId in selectedSongIds }.flatMap { it.songs }
                    }
                    FilterCategory.PLAYLISTS -> {
                        allPlaylists.filter { it.playlist.id in selectedSongIds }.flatMap { it.songs }
                    }
                    else -> {
                        allSongs.filter { it.id in selectedSongIds }
                    }
                }
            }

            MultiSelectGridBottomSheet(
                selectedCount = selectedSongIds.size,
                onDismiss = { showMultiSelectGridBottomSheet = false },
                onPlay = {
                    if (selectedSongs.isNotEmpty()) {
                        viewModel.playAll(selectedSongs, 0)
                    }
                    selectedSongIds = emptySet()
                    showMultiSelectGridBottomSheet = false
                },
                onDownload = {
                    selectedSongIds = emptySet()
                    showMultiSelectGridBottomSheet = false
                },
                onAddToPlaylist = {
                    selectedSongIds = emptySet()
                    showMultiSelectGridBottomSheet = false
                },
                onPlayNext = {
                    if (selectedSongs.isNotEmpty()) {
                        viewModel.addToQueue(selectedSongs)
                    }
                    selectedSongIds = emptySet()
                    showMultiSelectGridBottomSheet = false
                },
                onAddToQueue = {
                    if (selectedSongs.isNotEmpty()) {
                        viewModel.addToQueue(selectedSongs)
                    }
                    selectedSongIds = emptySet()
                    showMultiSelectGridBottomSheet = false
                },
                onFavorite = {
                    selectedSongIds = emptySet()
                    showMultiSelectGridBottomSheet = false
                }
            )
        }

        // Song Options BottomSheet para canciones individuales
        songForOptions?.let { song ->
            SongOptionsBottomSheet(
                song = song.toSongDto(),
                coverUrl = song.imageUrl,
                isDownloaded = true,
                onDismiss = { songForOptions = null },
                onPlay = {
                    val idx = filteredSongsList.indexOf(song)
                    viewModel.playAll(filteredSongsList, if (idx >= 0) idx else 0)
                },
                onPlayNext = { viewModel.playNextSelected(setOf(song.id), allSongs) },
                onAddToQueue = { viewModel.addToQueueSelected(setOf(song.id), allSongs) },
                onDownload = { }
            )
        }
    }
}
