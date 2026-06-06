package com.example.neosynth.ui.discover

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.neosynth.data.remote.responses.AlbumDto
import com.example.neosynth.data.remote.responses.ArtistDto
import com.example.neosynth.data.remote.responses.GenreDto
import com.example.neosynth.data.remote.responses.PlaylistDto
import com.example.neosynth.data.remote.responses.SongDto
import com.example.neosynth.ui.components.SideMultiSelectBar
import com.example.neosynth.ui.components.MultiSelectAction
import com.example.neosynth.ui.components.NeoPullToRefreshOverlayIndicator
import com.example.neosynth.ui.components.ServerErrorScreen
import androidx.compose.ui.res.stringResource
import com.example.neosynth.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    viewModel: DiscoverViewModel = hiltViewModel(),
    onNavigateToArtist: (artistId: String, artistName: String) -> Unit = { _, _ -> },
    onNavigateToRecentSongs: () -> Unit = {}
) {
    val searchQuery = viewModel.searchQuery
    val isSearching = viewModel.isSearching
    val searchResults = viewModel.searchResults
    val genres = viewModel.genres
    val isLoadingGenres = viewModel.isLoadingGenres
    val selectedGenre = viewModel.selectedGenre
    val genreSongs = viewModel.genreSongs
    val isLoadingGenreSongs = viewModel.isLoadingGenreSongs
    val decades = viewModel.decades
    val showAllGenres = viewModel.showAllGenres
    val selectedDecade = viewModel.selectedDecade
    val decadeSongs = viewModel.decadeSongs
    val isLoadingDecadeSongs = viewModel.isLoadingDecadeSongs
    val downloadedIds by viewModel.downloadedSongIds.collectAsStateWithLifecycle()
    val errorMsg = viewModel.error
    val isRefreshing = viewModel.isRefreshing
    val pullToRefreshState = rememberPullToRefreshState()

    val focusRequester = remember { FocusRequester() }

    // Genre songs bottom sheet
    val genreSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val allGenresSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val decadeSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (selectedGenre != null) {
        GenreSongsSheet(
            genre = selectedGenre,
            songs = genreSongs,
            isLoading = isLoadingGenreSongs,
            sheetState = genreSheetState,
            onDismiss = { viewModel.clearGenreSelection() },
            onPlaySong = { song -> viewModel.playSong(song, genreSongs) },
            onShufflePlay = { 
                if (genreSongs.isNotEmpty()) {
                    viewModel.playSong(genreSongs.random(), genreSongs.shuffled())
                }
            },
            getCoverUrl = { viewModel.getCoverUrl(it) },
            downloadedIds = downloadedIds,
            onDownload = { song -> viewModel.downloadSong(song) },
            onPlaySongs = { songs -> viewModel.playSelectedSongs(songs) },
            onAddToPlaylist = { songs -> viewModel.loadPlaylistsForPicker(songs) },
            onAddToFavorites = { songs -> viewModel.addSongsToFavorites(songs) }
        )
    }

    if (showAllGenres) {
        AllGenresSheet(
            genres = genres,
            sheetState = allGenresSheetState,
            onDismiss = { viewModel.showAllGenres = false },
            onGenreClick = { genre ->
                viewModel.showAllGenres = false
                viewModel.loadSongsByGenre(genre)
            }
        )
    }

    if (selectedDecade != null) {
        DecadeSongsSheet(
            decade = selectedDecade!!.first,
            songs = decadeSongs,
            isLoading = isLoadingDecadeSongs,
            sheetState = decadeSheetState,
            onDismiss = { viewModel.clearDecadeSelection() },
            onPlaySong = { song -> viewModel.playSong(song, decadeSongs) },
            onShufflePlay = {
                if (decadeSongs.isNotEmpty()) {
                    viewModel.playSong(decadeSongs.random(), decadeSongs.shuffled())
                }
            },
            getCoverUrl = { viewModel.getCoverUrl(it) },
            downloadedIds = downloadedIds,
            onDownload = { song -> viewModel.downloadSong(song) },
            onPlaySongs = { songs -> viewModel.playSelectedSongs(songs) },
            onAddToPlaylist = { songs -> viewModel.loadPlaylistsForPicker(songs) },
            onAddToFavorites = { songs -> viewModel.addSongsToFavorites(songs) }
        )
    }
    
    // Diálogo para seleccionar playlist
    if (viewModel.showPlaylistPicker) {
        PlaylistPickerDialog(
            playlists = viewModel.availablePlaylists,
            onPlaylistSelected = { playlistId -> viewModel.addSongsToPlaylist(playlistId) },
            onDismiss = { 
                viewModel.showPlaylistPicker = false
                viewModel.songsToAddToPlaylist = emptyList()
            }
        )
    }

    // Mostrar error si existe
    if (errorMsg != null) {
        ServerErrorScreen(
            onRetry = { viewModel.loadGenres() }
        )
        return
    }

    // Mostrar skeleton mientras carga géneros y décadas iniciales
    if (isLoadingGenres && genres.isEmpty()) {
        DiscoverSkeleton(brush = com.example.neosynth.ui.components.rememberShimmerBrush())
        return
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(
            brush = Brush.radialGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.15f),
                    MaterialTheme.colorScheme.background
                ),
                center = androidx.compose.ui.geometry.Offset(x = 0f, y = 0f),
                radius = 2000f
            )
        )
    ) {
        PullToRefreshBox(
            state = pullToRefreshState,
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            indicator = {},
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                // Search Bar
                SearchBar(
                    query = searchQuery,
                    onQueryChange = { viewModel.onSearchQueryChange(it) },
                    isSearching = isSearching,
                    focusRequester = focusRequester,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )

                // Content
                Crossfade(
                    targetState = searchQuery.isNotBlank(),
                    label = "content_switch"
                ) { isSearchMode ->
                    if (isSearchMode) {
                        // Search Results
                        SearchResultsContent(
                            results = searchResults,
                            isLoading = isSearching,
                            onPlaySong = { song ->
                                viewModel.playSong(song, searchResults.songs)
                            },
                            onArtistClick = { artist ->
                                onNavigateToArtist(artist.id, artist.name)
                            },
                            getCoverUrl = { viewModel.getCoverUrl(it) },
                            downloadedIds = downloadedIds,
                            onDownload = { song -> viewModel.downloadSong(song) },
                            onPlaySongs = { songs -> viewModel.playSelectedSongs(songs) },
                            onAddToPlaylist = { songs -> viewModel.loadPlaylistsForPicker(songs) },
                            onAddToFavorites = { songs -> viewModel.addSongsToFavorites(songs) }
                        )
                    } else {
                        // Browse Content (Genres + Decades)
                        BrowseContent(
                            genres = genres,
                            isLoadingGenres = isLoadingGenres,
                            decades = decades,
                            onGenreClick = { viewModel.loadSongsByGenre(it) },
                            onShowAllGenres = { viewModel.showAllGenres = true },
                            onDecadeClick = { label, range -> viewModel.loadSongsByDecade(label to range) },
                            getCoverUrl = { viewModel.getCoverUrl(it) },
                            recentSongsPreview = viewModel.recentSongsPreview,
                            isLoadingRecentSongs = viewModel.isLoadingRecentSongs,
                            downloadedIds = downloadedIds,
                            onNavigateToRecentSongs = onNavigateToRecentSongs
                        )
                    }
                }
            }
        }

        NeoPullToRefreshOverlayIndicator(
            state = pullToRefreshState,
            isRefreshing = isRefreshing,
            modifier = Modifier
        )
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    isSearching: Boolean,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                singleLine = true,
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.discover_search_hint),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    innerTextField()
                }
            )
            
            if (isSearching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Clear,
                        contentDescription = stringResource(R.string.discover_clear),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultsContent(
    results: SearchResults,
    isLoading: Boolean,
    onPlaySong: (SongDto) -> Unit,
    onArtistClick: (ArtistDto) -> Unit,
    getCoverUrl: (String?) -> String?,
    downloadedIds: Set<String> = emptySet(),
    onDownload: (SongDto) -> Unit = {},
    onPlaySongs: (List<SongDto>) -> Unit = {},
    onAddToPlaylist: (List<SongDto>) -> Unit = {},
    onAddToFavorites: (List<SongDto>) -> Unit = {}
) {
    var selectedSongIds by remember { mutableStateOf(setOf<String>()) }
    val selectedSongs = results.songs.filter { it.id in selectedSongIds }
    
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = if (selectedSongIds.isNotEmpty()) 200.dp else 180.dp)
        ) {
        // Artists
        if (results.artists.isNotEmpty()) {
            item {
                androidx.compose.animation.AnimatedVisibility(
                    visible = true,
                    enter = androidx.compose.animation.fadeIn(
                        animationSpec = androidx.compose.animation.core.tween(durationMillis = 300)
                    ) + androidx.compose.animation.slideInVertically(
                        initialOffsetY = { it / 4 },
                        animationSpec = androidx.compose.animation.core.tween(durationMillis = 400)
                    )
                ) {
                    Text(
                        text = stringResource(R.string.discover_artists),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }
            item {
                androidx.compose.animation.AnimatedVisibility(
                    visible = true,
                    enter = androidx.compose.animation.fadeIn(
                        animationSpec = androidx.compose.animation.core.tween(durationMillis = 400, delayMillis = 50)
                    ) + androidx.compose.animation.slideInVertically(
                        initialOffsetY = { it / 4 },
                        animationSpec = androidx.compose.animation.core.tween(durationMillis = 450, delayMillis = 50)
                    )
                ) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(results.artists, key = { it.id }) { artist ->
                            ArtistCard(
                                artist = artist,
                                onClick = { onArtistClick(artist) }
                            )
                        }
                    }
                }
            }
        }

        // Albums
        if (results.albums.isNotEmpty()) {
            item {
                androidx.compose.animation.AnimatedVisibility(
                    visible = true,
                    enter = androidx.compose.animation.fadeIn(
                        animationSpec = androidx.compose.animation.core.tween(durationMillis = 300, delayMillis = 100)
                    ) + androidx.compose.animation.slideInVertically(
                        initialOffsetY = { it / 4 },
                        animationSpec = androidx.compose.animation.core.tween(durationMillis = 400, delayMillis = 100)
                    )
                ) {
                    Text(
                        text = stringResource(R.string.discover_albums),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }
            item {
                androidx.compose.animation.AnimatedVisibility(
                    visible = true,
                    enter = androidx.compose.animation.fadeIn(
                        animationSpec = androidx.compose.animation.core.tween(durationMillis = 400, delayMillis = 150)
                    ) + androidx.compose.animation.slideInVertically(
                        initialOffsetY = { it / 4 },
                        animationSpec = androidx.compose.animation.core.tween(durationMillis = 450, delayMillis = 150)
                    )
                ) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(results.albums, key = { it.id }) { album ->
                            AlbumCard(album = album, getCoverUrl = getCoverUrl)
                        }
                    }
                }
            }
        }

        // Songs
        if (results.songs.isNotEmpty()) {
            item {
                androidx.compose.animation.AnimatedVisibility(
                    visible = true,
                    enter = androidx.compose.animation.fadeIn(
                        animationSpec = androidx.compose.animation.core.tween(durationMillis = 300, delayMillis = 200)
                    ) + androidx.compose.animation.slideInVertically(
                        initialOffsetY = { it / 4 },
                        animationSpec = androidx.compose.animation.core.tween(durationMillis = 400, delayMillis = 200)
                    )
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.discover_songs),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                        // Hint de selección
                        if (selectedSongIds.isEmpty()) {
                            Text(
                                text = stringResource(R.string.discover_select_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                            )
                        }
                    }
                }
            }
            itemsIndexed(results.songs, key = { _, song -> song.id }) { index, song ->
                androidx.compose.animation.AnimatedVisibility(
                    visible = true,
                    enter = androidx.compose.animation.fadeIn(
                        animationSpec = androidx.compose.animation.core.tween(durationMillis = 300, delayMillis = 250 + (index * 30))
                    ) + androidx.compose.animation.slideInVertically(
                        initialOffsetY = { it / 4 },
                        animationSpec = androidx.compose.animation.core.tween(durationMillis = 400, delayMillis = 250 + (index * 30))
                    )
                ) {
                    SongRow(
                        song = song,
                        onClick = { 
                            if (selectedSongIds.isNotEmpty()) {
                                // En modo selección, toggle la canción
                                selectedSongIds = if (song.id in selectedSongIds) {
                                    selectedSongIds - song.id
                                } else {
                                    selectedSongIds + song.id
                                }
                            } else {
                                onPlaySong(song)
                            }
                        },
                        getCoverUrl = getCoverUrl,
                        isDownloaded = song.id in downloadedIds,
                        onDownload = { onDownload(song) },
                        isSelected = song.id in selectedSongIds,
                        onLongClick = {
                            selectedSongIds = selectedSongIds + song.id
                        }
                    )
                }
            }
        }

        // Empty state
        if (results.songs.isEmpty() && results.artists.isEmpty() && results.albums.isEmpty() && !isLoading) {
            item {
                androidx.compose.animation.AnimatedVisibility(
                    visible = true,
                    enter = androidx.compose.animation.fadeIn(
                        animationSpec = androidx.compose.animation.core.tween(durationMillis = 500)
                    ) + androidx.compose.animation.scaleIn(
                        initialScale = 0.8f,
                        animationSpec = androidx.compose.animation.core.tween(durationMillis = 500)
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Rounded.SearchOff,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.discover_no_results),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
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
                    label = stringResource(R.string.action_play),
                    onClick = {
                        onPlaySongs(selectedSongs)
                        selectedSongIds = emptySet()
                    }
                ),
                MultiSelectAction(
                    icon = Icons.Rounded.Favorite,
                    label = stringResource(R.string.action_fav),
                    onClick = {
                        onAddToFavorites(selectedSongs)
                        selectedSongIds = emptySet()
                    }
                ),
                MultiSelectAction(
                    icon = Icons.Rounded.Download,
                    label = stringResource(R.string.action_down),
                    onClick = {
                        selectedSongs.forEach { onDownload(it) }
                        selectedSongIds = emptySet()
                    }
                ),
                MultiSelectAction(
                    icon = Icons.Rounded.PlaylistAdd,
                    label = stringResource(R.string.action_playlist),
                    onClick = {
                        onAddToPlaylist(selectedSongs)
                        selectedSongIds = emptySet()
                    }
                )
            ),
            onClose = { selectedSongIds = emptySet() },
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

@Composable
private fun BrowseContent(
    genres: List<GenreDto>,
    isLoadingGenres: Boolean,
    decades: List<Pair<String, IntRange>>,
    onGenreClick: (String) -> Unit,
    onShowAllGenres: () -> Unit,
    onDecadeClick: (String, IntRange) -> Unit,
    getCoverUrl: (String?) -> String?,
    recentSongsPreview: List<SongDto> = emptyList(),
    isLoadingRecentSongs: Boolean = false,
    downloadedIds: Set<String> = emptySet(),
    onNavigateToRecentSongs: () -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 240.dp)
    ) {        // Recent Songs Section
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.discover_recent_songs),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onNavigateToRecentSongs) {
                    Text(stringResource(R.string.discover_view_all))
                    Icon(
                        Icons.Rounded.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        if (isLoadingRecentSongs && recentSongsPreview.isEmpty()) {
            item {
                Box(
                    Modifier.fillMaxWidth().height(72.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp) }
            }
        } else if (recentSongsPreview.isNotEmpty()) {
            items(recentSongsPreview, key = { "preview_${it.id}" }) { song ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNavigateToRecentSongs)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(6.dp))
                    ) {
                        val url = getCoverUrl(song.coverArt)
                        if (url != null) {
                            AsyncImage(
                                model = url,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Surface(
                                Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                Icon(
                                    Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${song.artist} • ${song.album}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (song.id in downloadedIds) {
                        Icon(
                            Icons.Rounded.DownloadDone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(start = 72.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
            }
        }
        // Genres Section
        item {
            Text(
                text = stringResource(R.string.discover_genres),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }

        if (isLoadingGenres) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        } else {
            item {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(((minOf(genres.size, 10) + 1) / 2 * 68).dp)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(genres.take(10)) { genre ->
                        GenreChip(
                            genre = genre,
                            onClick = { onGenreClick(genre.value) }
                        )
                    }
                }
            }
        }

        // View all genres button
        if (genres.size > 10) {
            item {
                TextButton(
                    onClick = onShowAllGenres,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Text(stringResource(R.string.discover_view_all_genres, genres.size))
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Rounded.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Decades Section
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.discover_by_decade),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(decades) { (label, range) ->
                    DecadeCard(
                        decade = label,
                        onClick = { onDecadeClick(label, range) }
                    )
                }
            }
        }
    }
}

@Composable
private fun GenreChip(
    genre: GenreDto,
    onClick: () -> Unit
) {
    val genreColor = getGenreColor(genre.value)
    
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        label = "genre_chip_scale"
    )
    
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = CircleShape,
        color = genreColor.copy(alpha = 0.35f),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = genre.value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            
            // Badge con número de canciones
            Surface(
                shape = CircleShape,
                color = genreColor.copy(alpha = 0.2f)
            ) {
                Text(
                    text = "${genre.songCount ?: 0}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

// Colores para géneros (rest of getGenreColor function follows...)
private fun getGenreColor(genreName: String): Color {
    val lowerName = genreName.lowercase()
    return when {
        lowerName.contains("rock") -> Color(0xFFE53935)
        lowerName.contains("pop") -> Color(0xFFE91E63)
        lowerName.contains("jazz") -> Color(0xFF9C27B0)
        lowerName.contains("blues") -> Color(0xFF3F51B5)
        lowerName.contains("classical") || lowerName.contains("clásic") -> Color(0xFF795548)
        lowerName.contains("electronic") || lowerName.contains("electr") -> Color(0xFF00BCD4)
        lowerName.contains("hip") || lowerName.contains("rap") -> Color(0xFFFF9800)
        lowerName.contains("r&b") || lowerName.contains("soul") -> Color(0xFF8E24AA)
        lowerName.contains("country") -> Color(0xFF8D6E63)
        lowerName.contains("reggae") -> Color(0xFF4CAF50)
        lowerName.contains("metal") -> Color(0xFF424242)
        lowerName.contains("punk") -> Color(0xFFFF5722)
        lowerName.contains("indie") -> Color(0xFF7986CB)
        lowerName.contains("folk") -> Color(0xFF689F38)
        lowerName.contains("latin") || lowerName.contains("salsa") || lowerName.contains("reggaeton") -> Color(0xFFFFCA28)
        lowerName.contains("ambient") || lowerName.contains("chill") -> Color(0xFF80DEEA)
        lowerName.contains("soundtrack") -> Color(0xFF5C6BC0)
        lowerName.contains("dance") -> Color(0xFFAB47BC)
        lowerName.contains("world") -> Color(0xFF26A69A)
        lowerName.contains("alternative") -> Color(0xFF66BB6A)
        else -> Color(0xFF607D8B)
    }
}

@Composable
private fun DecadeCard(
    decade: String,
    onClick: () -> Unit
) {
    val decadeColor = getDecadeColor(decade)
    
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        label = "decade_card_scale"
    )
    
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(24.dp),
        color = decadeColor.copy(alpha = 0.35f),
        modifier = Modifier
            .size(width = 120.dp, height = 90.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.BottomStart
        ) {
            Text(
                text = decade,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
                color = decadeColor
            )
        }
    }
}
// Colores para décadas (siguiendo el tema de la app)
private fun getDecadeColor(decade: String): Color {
    return when {
        decade.contains("50") -> Color(0xFF8D6E63)
        decade.contains("60") -> Color(0xFFE57373)
        decade.contains("70") -> Color(0xFFFFB74D)
        decade.contains("80") -> Color(0xFF9575CD)
        decade.contains("90") -> Color(0xFF4DB6AC)
        decade.contains("00") || decade.contains("2000") -> Color(0xFF7986CB)
        decade.contains("10") || decade.contains("2010") -> Color(0xFF4FC3F7)
        decade.contains("20") || decade.contains("2020") -> Color(0xFF81C784)
        else -> Color(0xFF90A4AE)
    }
}

@Composable
private fun ArtistCard(
    artist: ArtistDto,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(80.dp)
            .clickable(onClick = onClick)
    ) {
        Surface(
            modifier = Modifier.size(80.dp),
            shape = RoundedCornerShape(40.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AlbumCard(
    album: AlbumDto,
    getCoverUrl: (String?) -> String?
) {
    Column(
        modifier = Modifier.width(130.dp)
    ) {
        Surface(
            modifier = Modifier
                .size(130.dp)
                .graphicsLayer {
                    shadowElevation = 8.dp.toPx()
                    shape = RoundedCornerShape(16.dp)
                    clip = true
                },
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 4.dp,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            AsyncImage(
                model = getCoverUrl(album.coverArt),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = album.title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = album.artist,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongRow(
    song: SongDto,
    onClick: () -> Unit,
    getCoverUrl: (String?) -> String?,
    isDownloaded: Boolean = false,
    isSelected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    onToggleFavorite: (() -> Unit)? = null,
    isFavorite: Boolean = false
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AsyncImage(
                model = getCoverUrl(song.coverArt),
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    
                    if (isDownloaded) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Rounded.DownloadDone,
                            contentDescription = stringResource(R.string.action_download_done),
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
            
            Text(
                text = formatDuration(song.duration),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenreSongsSheet(
    genre: String,
    songs: List<SongDto>,
    isLoading: Boolean,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onPlaySong: (SongDto) -> Unit,
    onShufflePlay: () -> Unit = {},
    getCoverUrl: (String?) -> String?,
    downloadedIds: Set<String> = emptySet(),
    onDownload: (SongDto) -> Unit = {},
    onPlaySongs: (List<SongDto>) -> Unit = {},
    onAddToPlaylist: (List<SongDto>) -> Unit = {},
    onAddToFavorites: (List<SongDto>) -> Unit = {}
) {
    var selectedSongIds by remember { mutableStateOf(setOf<String>()) }
    val selectedSongs = songs.filter { it.id in selectedSongIds }
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val config = androidx.compose.ui.platform.LocalConfiguration.current

    ModalBottomSheet(
        onDismissRequest = {
            selectedSongIds = emptySet()
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalContext provides context,
            androidx.compose.ui.platform.LocalConfiguration provides config
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = genre,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.discover_songs_count, songs.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    // Botón Shuffle
                    if (songs.isNotEmpty() && !isLoading) {
                        FilledTonalButton(
                            onClick = onShufflePlay,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shuffle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.discover_random))
                        }
                    }
                }
                
                // Hint de selección
                if (songs.isNotEmpty() && !isLoading && selectedSongIds.isEmpty()) {
                    Text(
                        text = stringResource(R.string.discover_select_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(bottom = if (selectedSongIds.isNotEmpty()) 80.dp else 0.dp)
                    ) {
                        items(songs) { song ->
                            SongRow(
                                song = song,
                                onClick = { 
                                    if (selectedSongIds.isNotEmpty()) {
                                        // En modo selección, toggle la canción
                                        selectedSongIds = if (song.id in selectedSongIds) {
                                            selectedSongIds - song.id
                                        } else {
                                            selectedSongIds + song.id
                                        }
                                    } else {
                                        onPlaySong(song)
                                    }
                                },
                                getCoverUrl = getCoverUrl,
                                isDownloaded = song.id in downloadedIds,
                                onDownload = { onDownload(song) },
                                isSelected = song.id in selectedSongIds,
                                onLongClick = {
                                    selectedSongIds = selectedSongIds + song.id
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
            
            // Barra lateral de selección (Genres)
            SideMultiSelectBar(
                visible = selectedSongIds.isNotEmpty(),
                selectedCount = selectedSongIds.size,
                actions = listOf(
                    MultiSelectAction(
                        icon = Icons.Rounded.PlayArrow,
                        label = stringResource(R.string.action_play_short),
                        onClick = {
                            onPlaySongs(selectedSongs)
                            selectedSongIds = emptySet()
                        }
                    ),
                    MultiSelectAction(
                        icon = Icons.Rounded.Favorite,
                        label = stringResource(R.string.action_fav_short),
                        onClick = {
                            onAddToFavorites(selectedSongs)
                            selectedSongIds = emptySet()
                        }
                    ),
                    MultiSelectAction(
                        icon = Icons.Rounded.Download,
                        label = stringResource(R.string.action_down_short),
                        onClick = {
                            selectedSongs.forEach { onDownload(it) }
                            selectedSongIds = emptySet()
                        }
                    ),
                    MultiSelectAction(
                        icon = Icons.Rounded.PlaylistAdd,
                        label = stringResource(R.string.action_list_short),
                        onClick = {
                            onAddToPlaylist(selectedSongs)
                            selectedSongIds = emptySet()
                        }
                    )
                ),
                onClose = { selectedSongIds = emptySet() },
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DecadeSongsSheet(
    decade: String,
    songs: List<SongDto>,
    isLoading: Boolean,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onPlaySong: (SongDto) -> Unit,
    onShufflePlay: () -> Unit = {},
    getCoverUrl: (String?) -> String?,
    downloadedIds: Set<String> = emptySet(),
    onDownload: (SongDto) -> Unit = {},
    onPlaySongs: (List<SongDto>) -> Unit = {},
    onAddToPlaylist: (List<SongDto>) -> Unit = {},
    onAddToFavorites: (List<SongDto>) -> Unit = {}
) {
    var selectedSongIds by remember { mutableStateOf(setOf<String>()) }
    val selectedSongs = songs.filter { it.id in selectedSongIds }
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val config = androidx.compose.ui.platform.LocalConfiguration.current

    ModalBottomSheet(
        onDismissRequest = {
            selectedSongIds = emptySet()
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalContext provides context,
            androidx.compose.ui.platform.LocalConfiguration provides config
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.discover_decade_of, decade),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.discover_songs_count, songs.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    // Botón Shuffle
                    if (songs.isNotEmpty() && !isLoading) {
                        FilledTonalButton(
                            onClick = onShufflePlay,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shuffle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.discover_random))
                        }
                    }
                }
                
                // Hint de selección
                if (songs.isNotEmpty() && !isLoading && selectedSongIds.isEmpty()) {
                    Text(
                        text = stringResource(R.string.discover_select_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(bottom = if (selectedSongIds.isNotEmpty()) 80.dp else 0.dp)
                    ) {
                        items(songs) { song ->
                            SongRow(
                                song = song,
                                onClick = { 
                                    if (selectedSongIds.isNotEmpty()) {
                                        selectedSongIds = if (song.id in selectedSongIds) {
                                            selectedSongIds - song.id
                                        } else {
                                            selectedSongIds + song.id
                                        }
                                    } else {
                                        onPlaySong(song)
                                    }
                                },
                                getCoverUrl = getCoverUrl,
                                isDownloaded = song.id in downloadedIds,
                                onDownload = { onDownload(song) },
                                isSelected = song.id in selectedSongIds,
                                onLongClick = {
                                    selectedSongIds = selectedSongIds + song.id
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
            
            // Barra lateral de selección (Decades)
            SideMultiSelectBar(
                visible = selectedSongIds.isNotEmpty(),
                selectedCount = selectedSongIds.size,
                actions = listOf(
                    MultiSelectAction(
                        icon = Icons.Rounded.PlayArrow,
                        label = stringResource(R.string.action_play_short),
                        onClick = {
                            onPlaySongs(selectedSongs)
                            selectedSongIds = emptySet()
                        }
                    ),
                    MultiSelectAction(
                        icon = Icons.Rounded.Favorite,
                        label = stringResource(R.string.action_fav_short),
                        onClick = {
                            onAddToFavorites(selectedSongs)
                            selectedSongIds = emptySet()
                        }
                    ),
                    MultiSelectAction(
                        icon = Icons.Rounded.Download,
                        label = stringResource(R.string.action_down_short),
                        onClick = {
                            selectedSongs.forEach { onDownload(it) }
                            selectedSongIds = emptySet()
                        }
                    ),
                    MultiSelectAction(
                        icon = Icons.Rounded.PlaylistAdd,
                        label = stringResource(R.string.action_list_short),
                        onClick = {
                            onAddToPlaylist(selectedSongs)
                            selectedSongIds = emptySet()
                        }
                    )
                ),
                onClose = { selectedSongIds = emptySet() },
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AllGenresSheet(
    genres: List<GenreDto>,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onGenreClick: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val config = androidx.compose.ui.platform.LocalConfiguration.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalContext provides context,
            androidx.compose.ui.platform.LocalConfiguration provides config
        ) {
            Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.discover_all_genres),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = stringResource(R.string.discover_genres_count, genres.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 450.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(genres) { genre ->
                    GenreChip(
                        genre = genre,
                        onClick = { onGenreClick(genre.value) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
}

private fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return "%d:%02d".format(minutes, secs)
}

@Composable
private fun PlaylistPickerDialog(
    playlists: List<PlaylistDto>,
    onPlaylistSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedPlaylistIds by remember { mutableStateOf(setOf<String>()) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                text = stringResource(R.string.discover_add_to_playlist),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                if (selectedPlaylistIds.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.discover_playlists_selected, selectedPlaylistIds.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                
                if (playlists.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.discover_no_playlists),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(playlists) { playlist ->
                            val isSelected = playlist.id in selectedPlaylistIds
                            
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { 
                                        selectedPlaylistIds = if (isSelected) {
                                            selectedPlaylistIds - playlist.id
                                        } else {
                                            selectedPlaylistIds + playlist.id
                                        }
                                    },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f)
                                }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Checkbox
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            selectedPlaylistIds = if (checked) {
                                                selectedPlaylistIds + playlist.id
                                            } else {
                                                selectedPlaylistIds - playlist.id
                                            }
                                        }
                                    )
                                    
                                    Icon(
                                        imageVector = Icons.Rounded.PlaylistPlay,
                                        contentDescription = null,
                                        tint = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = playlist.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                        Text(
                                            text = stringResource(R.string.playlist_songs_count, playlist.songCount),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // Agregar a todas las playlists seleccionadas
                    selectedPlaylistIds.forEach { playlistId ->
                        onPlaylistSelected(playlistId)
                    }
                    onDismiss()
                },
                enabled = selectedPlaylistIds.isNotEmpty()
            ) {
                Text(
                    if (selectedPlaylistIds.isEmpty()) {
                        stringResource(R.string.discover_select_playlist)
                    } else if (selectedPlaylistIds.size == 1) {
                        stringResource(R.string.action_add)
                    } else {
                        stringResource(R.string.discover_add_to_n_playlists, selectedPlaylistIds.size)
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
