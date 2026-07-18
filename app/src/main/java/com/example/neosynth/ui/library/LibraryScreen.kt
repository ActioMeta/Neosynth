package com.example.neosynth.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.neosynth.data.remote.responses.AlbumDto
import com.example.neosynth.data.remote.responses.ArtistDto
import com.example.neosynth.data.remote.responses.PlaylistDto
import com.example.neosynth.ui.components.AlphabetScrollbar
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import com.example.neosynth.ui.stats.rememberBounceScale
import androidx.compose.ui.res.stringResource
import com.example.neosynth.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onNavigateToArtist: (artistId: String, artistName: String) -> Unit = { _, _ -> },
    onNavigateToPlaylist: (playlistId: String) -> Unit = {},
    onNavigateToAlbum: (albumId: String) -> Unit = {}
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isLoadingMoreAlbums by viewModel.isLoadingMoreAlbums.collectAsStateWithLifecycle()
    val pinnedPlaylistIds by viewModel.pinnedPlaylistIds.collectAsStateWithLifecycle()
    val pinnedAlbumIds by viewModel.pinnedAlbumIds.collectAsStateWithLifecycle()
    val pinnedArtistIds by viewModel.pinnedArtistIds.collectAsStateWithLifecycle()
    val favoriteSongsCount by viewModel.favoriteSongsCount.collectAsStateWithLifecycle()
    
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(stringResource(R.string.tab_playlists), stringResource(R.string.tab_albums), stringResource(R.string.tab_artists))
    
    // Dialogs
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showEditPlaylistDialog by remember { mutableStateOf<PlaylistDto?>(null) }
    var showDeletePlaylistDialog by remember { mutableStateOf<PlaylistDto?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadLibrary()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        stringResource(R.string.nav_library),
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    if (selectedTab == 0) {
                        IconButton(onClick = { showCreatePlaylistDialog = true }) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = stringResource(R.string.playlist_new)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tabs with Segmented Buttons
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
            ) {
                tabs.forEachIndexed { index, title ->
                    val tabInteraction = remember { MutableInteractionSource() }
                    val tabScale by rememberBounceScale(tabInteraction)
                    SegmentedButton(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size),
                        interactionSource = tabInteraction,
                        modifier = Modifier.graphicsLayer {
                            scaleX = tabScale
                            scaleY = tabScale
                        }
                    ) {
                        Text(
                            text = title,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            if (isLoading) {
                LibrarySkeleton(brush = com.example.neosynth.ui.components.rememberShimmerBrush())
            } else {
                when (selectedTab) {
                    0 -> PlaylistsTab(
                        playlists = playlists,
                        pinnedPlaylistIds = pinnedPlaylistIds,
                        favoriteSongsCount = favoriteSongsCount,
                        onTogglePinPlaylist = { viewModel.togglePinPlaylist(it) },
                        getCoverUrl = { viewModel.getCoverUrl(it) },
                        onPlaylistClick = onNavigateToPlaylist,
                        onEditPlaylist = { showEditPlaylistDialog = it },
                        onDeletePlaylist = { showDeletePlaylistDialog = it }
                    )
                    1 -> AlbumsTab(
                        albums = albums,
                        pinnedAlbumIds = pinnedAlbumIds,
                        onTogglePinAlbum = { viewModel.togglePinAlbum(it) },
                        isLoadingMoreAlbums = isLoadingMoreAlbums,
                        getCoverUrl = { viewModel.getCoverUrl(it) },
                        onAlbumClick = onNavigateToAlbum,
                        onLoadMore = { viewModel.loadMoreAlbums() }
                    )
                    2 -> ArtistsTab(
                        artists = artists,
                        pinnedArtistIds = pinnedArtistIds,
                        onTogglePinArtist = { viewModel.togglePinArtist(it) },
                        onArtistClick = onNavigateToArtist
                    )
                }
            }
        }
    }

    // Create Playlist Dialog
    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylistDialog = false },
            onCreate = { name ->
                viewModel.createPlaylist(name)
                showCreatePlaylistDialog = false
            }
        )
    }

    // Edit Playlist Dialog
    showEditPlaylistDialog?.let { playlist ->
        EditPlaylistDialog(
            playlist = playlist,
            onDismiss = { showEditPlaylistDialog = null },
            onSave = { newName ->
                viewModel.updatePlaylist(playlist.id, newName)
                showEditPlaylistDialog = null
            }
        )
    }

    // Delete Playlist Dialog
    showDeletePlaylistDialog?.let { playlist ->
        DeletePlaylistDialog(
            playlistName = playlist.name,
            onDismiss = { showDeletePlaylistDialog = null },
            onConfirm = {
                viewModel.deletePlaylist(playlist.id)
                showDeletePlaylistDialog = null
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistsTab(
    playlists: List<PlaylistDto>,
    pinnedPlaylistIds: Set<String>,
    favoriteSongsCount: Int,
    onTogglePinPlaylist: (String) -> Unit,
    getCoverUrl: (String?) -> String?,
    onPlaylistClick: (String) -> Unit,
    onEditPlaylist: (PlaylistDto) -> Unit,
    onDeletePlaylist: (PlaylistDto) -> Unit
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    
    // Pinned vs Unpinned playlists
    val pinnedPlaylists = remember(playlists, pinnedPlaylistIds) {
        playlists.filter { it.id in pinnedPlaylistIds }.sortedBy { it.name.lowercase() }
    }
    val unpinnedPlaylists = remember(playlists, pinnedPlaylistIds) {
        playlists.filter { it.id !in pinnedPlaylistIds }
    }

    // Agrupar unpinned playlists por primera letra
    val groupedPlaylists = remember(unpinnedPlaylists) {
        unpinnedPlaylists.sortedBy { it.name.lowercase() }
            .groupBy { playlist ->
                val firstChar = playlist.name.firstOrNull()?.uppercaseChar() ?: '#'
                if (firstChar.isLetter()) firstChar else '#'
            }
    }
    
    val availableLetters = remember(groupedPlaylists) {
        groupedPlaylists.keys.toSet()
    }

    Row(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 180.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // --- 1. Favorites Playlist ---
            item(key = "virtual_favorites") {
                PlaylistRow(
                    playlist = PlaylistDto(
                        id = "favorites",
                        name = stringResource(R.string.favorites),
                        songCount = favoriteSongsCount,
                        duration = 0,
                        coverArt = null
                    ),
                    coverUrl = null,
                    isVirtualFavorites = true,
                    isPinned = false,
                    onTogglePin = {},
                    onClick = { onPlaylistClick("favorites") },
                    onEdit = {},
                    onDelete = {}
                )
            }

            // --- 2. Pinned Playlists ---
            if (pinnedPlaylists.isNotEmpty()) {
                stickyHeader(key = "header_pinned") {
                    Surface(
                        color = MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(elevation = 2.dp),
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = stringResource(R.string.pinned_items),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }

                items(
                    items = pinnedPlaylists,
                    key = { "pinned_${it.id}" }
                ) { playlist ->
                    PlaylistRow(
                        playlist = playlist,
                        coverUrl = getCoverUrl(playlist.coverArt),
                        isPinned = true,
                        onTogglePin = { onTogglePinPlaylist(playlist.id) },
                        onClick = { onPlaylistClick(playlist.id) },
                        onEdit = { onEditPlaylist(playlist) },
                        onDelete = { onDeletePlaylist(playlist) }
                    )
                }
                
                item(key = "pinned_divider") {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 8.dp))
                }
            }

            // --- 3. Unpinned Grouped Playlists ---
            groupedPlaylists.forEach { (initial, playlistsInGroup) ->
                stickyHeader(key = "header_$initial") {
                    Surface(
                        color = MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(elevation = 2.dp),
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = initial.toString(),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
                
                items(
                    items = playlistsInGroup,
                    key = { it.id }
                ) { playlist ->
                    PlaylistRow(
                        playlist = playlist,
                        coverUrl = getCoverUrl(playlist.coverArt),
                        isPinned = false,
                        onTogglePin = { onTogglePinPlaylist(playlist.id) },
                        onClick = { onPlaylistClick(playlist.id) },
                        onEdit = { onEditPlaylist(playlist) },
                        onDelete = { onDeletePlaylist(playlist) }
                    )
                }
            }
        }
        
        // Alphabet Scrollbar
        if (unpinnedPlaylists.size > 10) {
            AlphabetScrollbar(
                availableLetters = availableLetters,
                currentLetter = null,
                onLetterSelected = { letter ->
                    val keys = groupedPlaylists.keys.toList()
                    val targetKeyIndex = keys.indexOf(letter)
                    if (targetKeyIndex >= 0) {
                        var itemIndex = 1 // Start at 1 for the favorites row
                        if (pinnedPlaylists.isNotEmpty()) {
                            itemIndex += 1 // Pinned header
                            itemIndex += pinnedPlaylists.size // Pinned items
                            itemIndex += 1 // Divider
                        }
                        for (i in 0 until targetKeyIndex) {
                            itemIndex += 1 + (groupedPlaylists[keys[i]]?.size ?: 0)
                        }
                        scope.launch {
                            listState.animateScrollToItem(itemIndex)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(end = 4.dp, top = 8.dp, bottom = 180.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumsTab(
    albums: List<AlbumDto>,
    pinnedAlbumIds: Set<String>,
    onTogglePinAlbum: (String) -> Unit,
    isLoadingMoreAlbums: Boolean,
    getCoverUrl: (String?) -> String?,
    onAlbumClick: (String) -> Unit,
    onLoadMore: () -> Unit
) {
    if (albums.isEmpty()) {
        EmptyState(
            icon = Icons.Rounded.Album,
            title = stringResource(R.string.library_no_albums_title),
            subtitle = stringResource(R.string.library_no_albums_subtitle)
        )
    } else {
        val listState = rememberLazyListState()
        val scope = rememberCoroutineScope()
        
        // Pinned vs Unpinned albums
        val pinnedAlbums = remember(albums, pinnedAlbumIds) {
            albums.filter { it.id in pinnedAlbumIds }.sortedBy { it.title.lowercase() }
        }
        val unpinnedAlbums = remember(albums, pinnedAlbumIds) {
            albums.filter { it.id !in pinnedAlbumIds }
        }

        // Agrupar unpinned álbumes por primera letra
        val groupedAlbums = remember(unpinnedAlbums) {
            unpinnedAlbums.sortedBy { it.title.lowercase() }
                .groupBy { album ->
                    val firstChar = album.title.firstOrNull()?.uppercaseChar() ?: '#'
                    if (firstChar.isLetter()) firstChar else '#'
                }
        }
        
        val availableLetters = remember(groupedAlbums) {
            groupedAlbums.keys.toSet()
        }

        Row(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 180.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // --- Pinned Albums ---
                if (pinnedAlbums.isNotEmpty()) {
                    stickyHeader(key = "header_pinned_albums") {
                        Surface(
                            color = MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(elevation = 2.dp),
                            contentColor = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = stringResource(R.string.pinned_items),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }

                    items(
                        items = pinnedAlbums,
                        key = { "pinned_${it.id}" }
                    ) { album ->
                        AlbumRow(
                            album = album,
                            coverUrl = getCoverUrl(album.coverArt),
                            isPinned = true,
                            onTogglePin = { onTogglePinAlbum(album.id) },
                            onClick = { onAlbumClick(album.id) }
                        )
                    }
                    
                    item(key = "pinned_divider_albums") {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 8.dp))
                    }
                }

                // --- Unpinned Grouped Albums ---
                groupedAlbums.forEach { (initial, albumsInGroup) ->
                    stickyHeader(key = "header_$initial") {
                        Surface(
                            color = MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(elevation = 2.dp),
                            contentColor = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = initial.toString(),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                    
                    items(
                        items = albumsInGroup,
                        key = { it.id }
                    ) { album ->
                        AlbumRow(
                            album = album,
                            coverUrl = getCoverUrl(album.coverArt),
                            isPinned = false,
                            onTogglePin = { onTogglePinAlbum(album.id) },
                            onClick = { onAlbumClick(album.id) }
                        )
                    }
                }

                if (isLoadingMoreAlbums) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }
            
            // Endless scrolling detection
            val isScrollToEnd by remember {
                derivedStateOf {
                    val layoutInfo = listState.layoutInfo
                    val totalItems = layoutInfo.totalItemsCount
                    val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    totalItems > 0 && lastVisibleItemIndex >= totalItems - 5
                }
            }

            LaunchedEffect(isScrollToEnd) {
                if (isScrollToEnd && !isLoadingMoreAlbums) {
                    onLoadMore()
                }
            }
            
            // Alphabet Scrollbar
            if (unpinnedAlbums.size > 10) {
                AlphabetScrollbar(
                    availableLetters = availableLetters,
                    currentLetter = null,
                    onLetterSelected = { letter ->
                        val keys = groupedAlbums.keys.toList()
                        val targetKeyIndex = keys.indexOf(letter)
                        if (targetKeyIndex >= 0) {
                            var itemIndex = 0
                            if (pinnedAlbums.isNotEmpty()) {
                                itemIndex += 1 // Pinned header
                                itemIndex += pinnedAlbums.size // Pinned items
                                itemIndex += 1 // Divider
                            }
                            for (i in 0 until targetKeyIndex) {
                                itemIndex += 1 + (groupedAlbums[keys[i]]?.size ?: 0)
                            }
                            scope.launch {
                                listState.animateScrollToItem(itemIndex)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(end = 4.dp, top = 8.dp, bottom = 180.dp)
                )
            }
        }
    }
}

@Composable
private fun AlbumRow(
    album: AlbumDto,
    coverUrl: String?,
    isPinned: Boolean = false,
    onTogglePin: () -> Unit = {},
    onClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val scale by rememberBounceScale(interactionSource)

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(12.dp),
        color = androidx.compose.ui.graphics.Color.Transparent,
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cover
            if (coverUrl != null) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = album.title,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Album,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = album.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isPinned) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Rounded.PushPin,
                            contentDescription = stringResource(R.string.pinned_items),
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    text = album.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.action_options)
                    )
                }
                
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (isPinned)
                                    stringResource(R.string.action_unpin)
                                else
                                    stringResource(R.string.action_pin)
                            )
                        },
                        onClick = {
                            showMenu = false
                            onTogglePin()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.PushPin,
                                contentDescription = null,
                                tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArtistsTab(
    artists: List<ArtistDto>,
    pinnedArtistIds: Set<String>,
    onTogglePinArtist: (String) -> Unit,
    onArtistClick: (String, String) -> Unit
) {
    if (artists.isEmpty()) {
        EmptyState(
            icon = Icons.Rounded.Person,
            title = stringResource(R.string.library_no_artists_title),
            subtitle = stringResource(R.string.library_no_artists_subtitle)
        )
    } else {
        val listState = rememberLazyListState()
        val scope = rememberCoroutineScope()
        
        // Pinned vs Unpinned artists
        val pinnedArtists = remember(artists, pinnedArtistIds) {
            artists.filter { it.id in pinnedArtistIds }.sortedBy { it.name.lowercase() }
        }
        val unpinnedArtists = remember(artists, pinnedArtistIds) {
            artists.filter { it.id !in pinnedArtistIds }
        }

        // Agrupar unpinned artistas por primera letra
        val groupedArtists = remember(unpinnedArtists) {
            unpinnedArtists.sortedBy { it.name.lowercase() }
                .groupBy { artist ->
                    val firstChar = artist.name.firstOrNull()?.uppercaseChar() ?: '#'
                    if (firstChar.isLetter()) firstChar else '#'
                }
        }
        
        val availableLetters = remember(groupedArtists) {
            groupedArtists.keys.toSet()
        }

        Row(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 180.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // --- Pinned Artists ---
                if (pinnedArtists.isNotEmpty()) {
                    stickyHeader(key = "header_pinned_artists") {
                        Surface(
                            color = MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(elevation = 2.dp),
                            contentColor = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = stringResource(R.string.pinned_items),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }

                    items(
                        items = pinnedArtists,
                        key = { "pinned_${it.id}" }
                    ) { artist ->
                        ArtistRowItem(
                            artist = artist,
                            isPinned = true,
                            onTogglePin = { onTogglePinArtist(artist.id) },
                            onClick = { onArtistClick(artist.id, artist.name) }
                        )
                    }
                    
                    item(key = "pinned_divider_artists") {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 8.dp))
                    }
                }

                // --- Unpinned Grouped Artists ---
                groupedArtists.forEach { (initial, artistsInGroup) ->
                    stickyHeader(key = "header_$initial") {
                        Surface(
                            color = MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(elevation = 2.dp),
                            contentColor = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = initial.toString(),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                    
                    items(
                        items = artistsInGroup,
                        key = { it.id }
                    ) { artist ->
                        ArtistRowItem(
                            artist = artist,
                            isPinned = false,
                            onTogglePin = { onTogglePinArtist(artist.id) },
                            onClick = { onArtistClick(artist.id, artist.name) }
                        )
                    }
                }
            }
            
            // Alphabet Scrollbar
            if (unpinnedArtists.size > 15) {
                AlphabetScrollbar(
                    availableLetters = availableLetters,
                    currentLetter = null,
                    onLetterSelected = { letter ->
                        val keys = groupedArtists.keys.toList()
                        val targetKeyIndex = keys.indexOf(letter)
                        if (targetKeyIndex >= 0) {
                            var itemIndex = 0
                            if (pinnedArtists.isNotEmpty()) {
                                itemIndex += 1 // Pinned header
                                itemIndex += pinnedArtists.size // Pinned items
                                itemIndex += 1 // Divider
                            }
                            for (i in 0 until targetKeyIndex) {
                                itemIndex += 1 + (groupedArtists[keys[i]]?.size ?: 0)
                            }
                            scope.launch {
                                listState.animateScrollToItem(itemIndex)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(end = 4.dp, top = 8.dp, bottom = 180.dp)
                )
            }
        }
    }
}

@Composable
private fun PlaylistRow(
    playlist: PlaylistDto,
    coverUrl: String?,
    isVirtualFavorites: Boolean = false,
    isPinned: Boolean = false,
    onTogglePin: () -> Unit = {},
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val scale by rememberBounceScale(interactionSource)

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(12.dp),
        color = androidx.compose.ui.graphics.Color.Transparent,
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cover
            if (isVirtualFavorites) {
                FavoritesCover(modifier = Modifier.size(56.dp))
            } else if (coverUrl != null) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = playlist.name,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                PlaylistCollageCover(modifier = Modifier.size(56.dp))
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isPinned) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Rounded.PushPin,
                            contentDescription = stringResource(R.string.pinned_items),
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.library_songs_count, playlist.songCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!isVirtualFavorites) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = stringResource(R.string.action_options)
                        )
                    }
                    
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (isPinned)
                                        stringResource(R.string.action_unpin)
                                    else
                                        stringResource(R.string.action_pin)
                                )
                            },
                            onClick = {
                                showMenu = false
                                onTogglePin()
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.PushPin,
                                    contentDescription = null,
                                    tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_edit)) },
                            onClick = {
                                showMenu = false
                                onEdit()
                            },
                            leadingIcon = {
                                Icon(Icons.Rounded.Edit, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_delete)) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                            leadingIcon = {
                                Icon(Icons.Rounded.Delete, contentDescription = null)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistRowItem(
    artist: ArtistDto,
    isPinned: Boolean = false,
    onTogglePin: () -> Unit = {},
    onClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val scale by rememberBounceScale(interactionSource)

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(12.dp),
        color = androidx.compose.ui.graphics.Color.Transparent,
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val imageUrl = artist.artistImageUrl ?: artist.coverArt
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = artist.name,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = artist.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isPinned) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Rounded.PushPin,
                            contentDescription = stringResource(R.string.pinned_items),
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                artist.albumCount?.let { count ->
                    Text(
                        text = stringResource(R.string.library_albums_count, count),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.action_options)
                    )
                }
                
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (isPinned)
                                    stringResource(R.string.action_unpin)
                                else
                                    stringResource(R.string.action_pin)
                            )
                        },
                        onClick = {
                            showMenu = false
                            onTogglePin()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.PushPin,
                                contentDescription = null,
                                tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.playlist_new)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.playlist_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name) },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.action_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun EditPlaylistDialog(
    playlist: PlaylistDto,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember { mutableStateOf(playlist.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.playlist_edit)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.playlist_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name) },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun DeletePlaylistDialog(
    playlistName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.playlist_delete_title)) },
        text = { Text(stringResource(R.string.playlist_delete_desc, playlistName)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun PlaylistCollageCover(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.secondaryContainer
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f))
                )
            }
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f))
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                )
            }
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
            modifier = Modifier
                .size(28.dp)
                .shadow(4.dp, CircleShape),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.QueueMusic,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun FavoritesCover(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = listOf(
                        androidx.compose.ui.graphics.Color(0xFFE91E63),
                        androidx.compose.ui.graphics.Color(0xFF9C27B0)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Favorite,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = androidx.compose.ui.graphics.Color.White
        )
    }
}
