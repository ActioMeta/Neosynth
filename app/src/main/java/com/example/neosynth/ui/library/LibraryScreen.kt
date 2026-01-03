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
import com.example.neosynth.data.remote.responses.ArtistDto
import com.example.neosynth.data.remote.responses.PlaylistDto
import com.example.neosynth.ui.components.AlphabetScrollbar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onNavigateToArtist: (artistId: String, artistName: String) -> Unit = { _, _ -> },
    onNavigateToPlaylist: (playlistId: String) -> Unit = {}
) {
    val playlists by viewModel.playlists.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Playlists", "Artistas")
    
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
                        "Biblioteca",
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                },
                actions = {
                    if (selectedTab == 0) {
                        IconButton(onClick = { showCreatePlaylistDialog = true }) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Crear playlist"
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
            // Tabs with spring animation indicator
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp),
                indicator = { tabPositions ->
                    if (selectedTab < tabPositions.size) {
                        val currentTabPosition = tabPositions[selectedTab]
                        
                        val indicatorOffset by androidx.compose.animation.core.animateDpAsState(
                            targetValue = currentTabPosition.left,
                            animationSpec = androidx.compose.animation.core.spring(
                                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                                stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                            ),
                            label = "tab_indicator_offset"
                        )
                        
                        val indicatorWidth by androidx.compose.animation.core.animateDpAsState(
                            targetValue = currentTabPosition.width,
                            animationSpec = androidx.compose.animation.core.spring(
                                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                                stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                            ),
                            label = "tab_indicator_width"
                        )
                        
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .wrapContentSize(Alignment.BottomStart)
                                .offset(x = indicatorOffset)
                                .width(indicatorWidth)
                                .height(3.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
                                )
                        )
                    }
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { 
                            Text(
                                text = title,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            if (isLoading) {
                LibrarySkeleton(brush = com.example.neosynth.ui.components.rememberShimmerBrush())
            } else {
                when (selectedTab) {
                    0 -> PlaylistsTab(
                        playlists = playlists,
                        getCoverUrl = { viewModel.getCoverUrl(it) },
                        onPlaylistClick = onNavigateToPlaylist,
                        onEditPlaylist = { showEditPlaylistDialog = it },
                        onDeletePlaylist = { showDeletePlaylistDialog = it }
                    )
                    1 -> ArtistsTab(
                        artists = artists,
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
    getCoverUrl: (String?) -> String?,
    onPlaylistClick: (String) -> Unit,
    onEditPlaylist: (PlaylistDto) -> Unit,
    onDeletePlaylist: (PlaylistDto) -> Unit
) {
    if (playlists.isEmpty()) {
        EmptyState(
            icon = Icons.Rounded.QueueMusic,
            title = "No tienes playlists",
            subtitle = "Crea tu primera playlist con el botón +"
        )
    } else {
        val listState = rememberLazyListState()
        val scope = rememberCoroutineScope()
        
        // Agrupar playlists por primera letra
        val groupedPlaylists = remember(playlists) {
            playlists.sortedBy { it.name.lowercase() }
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
                groupedPlaylists.forEach { (initial, playlistsInGroup) ->
                    stickyHeader(key = "header_$initial") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f))
                                .padding(vertical = 6.dp)
                        ) {
                            Text(
                                text = initial.toString(),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
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
                            onClick = { onPlaylistClick(playlist.id) },
                            onEdit = { onEditPlaylist(playlist) },
                            onDelete = { onDeletePlaylist(playlist) }
                        )
                    }
                }
            }
            
            // Alphabet Scrollbar
            if (playlists.size > 10) {
                AlphabetScrollbar(
                    availableLetters = availableLetters,
                    currentLetter = null,
                    onLetterSelected = { letter ->
                        val keys = groupedPlaylists.keys.toList()
                        val targetKeyIndex = keys.indexOf(letter)
                        if (targetKeyIndex >= 0) {
                            var itemIndex = 0
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArtistsTab(
    artists: List<ArtistDto>,
    onArtistClick: (String, String) -> Unit
) {
    if (artists.isEmpty()) {
        EmptyState(
            icon = Icons.Rounded.Person,
            title = "No hay artistas",
            subtitle = "Los artistas aparecerán aquí"
        )
    } else {
        val listState = rememberLazyListState()
        val scope = rememberCoroutineScope()
        
        // Agrupar artistas por primera letra
        val groupedArtists = remember(artists) {
            artists.sortedBy { it.name.lowercase() }
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
                contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 180.dp)
            ) {
                groupedArtists.forEach { (initial, artistsInGroup) ->
                    stickyHeader(key = "header_$initial") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f))
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = initial.toString(),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    // Mostrar artistas en fila de 3
                    val chunkedArtists = artistsInGroup.chunked(3)
                    items(
                        items = chunkedArtists,
                        key = { row -> row.map { it.id }.joinToString() }
                    ) { row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            row.forEach { artist ->
                                Box(modifier = Modifier.weight(1f)) {
                                    ArtistGridItem(
                                        artist = artist,
                                        onClick = { onArtistClick(artist.id, artist.name) }
                                    )
                                }
                            }
                            // Espacios vacíos para mantener el grid
                            repeat(3 - row.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            
            // Alphabet Scrollbar
            if (artists.size > 15) {
                AlphabetScrollbar(
                    availableLetters = availableLetters,
                    currentLetter = null,
                    onLetterSelected = { letter ->
                        val keys = groupedArtists.keys.toList()
                        val targetKeyIndex = keys.indexOf(letter)
                        if (targetKeyIndex >= 0) {
                            var itemIndex = 0
                            for (i in 0 until targetKeyIndex) {
                                val artistsCount = groupedArtists[keys[i]]?.size ?: 0
                                val rowsCount = (artistsCount + 2) / 3 // Ceiling division
                                itemIndex += 1 + rowsCount
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
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
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
                    contentDescription = playlist.name,
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
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.QueueMusic,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${playlist.songCount} canciones",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Opciones"
                    )
                }
                
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Editar") },
                        onClick = {
                            showMenu = false
                            onEdit()
                        },
                        leadingIcon = {
                            Icon(Icons.Rounded.Edit, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Eliminar") },
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

@Composable
private fun ArtistGridItem(
    artist: ArtistDto,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (artist.artistImageUrl != null) {
                AsyncImage(
                    model = artist.artistImageUrl,
                    contentDescription = artist.name,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = artist.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            artist.albumCount?.let { count ->
                Text(
                    text = "$count álbumes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
        title = { Text("Nueva playlist") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name) },
                enabled = name.isNotBlank()
            ) {
                Text("Crear")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
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
        title = { Text("Editar playlist") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name) },
                enabled = name.isNotBlank()
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
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
        title = { Text("Eliminar playlist") },
        text = { Text("¿Estás seguro de que deseas eliminar \"$playlistName\"?") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Eliminar", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
