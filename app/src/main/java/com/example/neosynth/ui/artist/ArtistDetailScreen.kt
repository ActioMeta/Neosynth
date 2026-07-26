package com.example.neosynth.ui.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.graphicsLayer
import com.example.neosynth.ui.stats.rememberBounceScale
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.neosynth.data.remote.responses.AlbumDto
import androidx.compose.ui.res.stringResource
import com.example.neosynth.R

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ArtistDetailScreen(
    artistId: String,
    artistName: String,
    viewModel: ArtistDetailViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onAlbumClick: (String) -> Unit = {}
) {
    val artist by viewModel.artist.collectAsStateWithLifecycle()
    val artistInfo by viewModel.artistInfo.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val topSongs by viewModel.topSongs.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val backgroundColor = MaterialTheme.colorScheme.background

    LaunchedEffect(artistId) {
        viewModel.loadArtist(artistId, artistName)
    }

    var selectedSongIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val isSelectionMode = selectedSongIds.isNotEmpty()
    var showMultiSelectGridBottomSheet by remember { mutableStateOf(false) }
    var songForOptions by remember { mutableStateOf<com.example.neosynth.data.remote.responses.SongDto?>(null) }
    var showPlaylistPicker by remember { mutableStateOf(false) }

    val rawImageUrl = artistInfo?.largeImageUrl 
        ?: artistInfo?.mediumImageUrl
        ?: artistInfo?.smallImageUrl
        ?: albums.firstOrNull { !it.coverArt.isNullOrBlank() }?.coverArt
        ?: topSongs.firstOrNull { !it.coverArt.isNullOrBlank() }?.coverArt

    val imageUrl = remember(rawImageUrl, albums, topSongs) {
        viewModel.getCoverUrl(rawImageUrl)
    }
    val palette = com.example.neosynth.ui.album.rememberAlbumPalette(imageUrl)

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 180.dp)
        ) {
            // Header Hero de Artista (Visible desde el frame 0)
            item {
                val coverShape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(370.dp)
                ) {
                    // Imagen de artista Hero
                    if (imageUrl != null) {
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = artistName,
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
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                modifier = Modifier.size(90.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Gradiente difuminado hacia abajo con Palette
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to Color.Black.copy(alpha = 0.25f),
                                        0.35f to Color.Transparent,
                                        0.75f to palette.accent.copy(alpha = 0.5f),
                                        1.0f to backgroundColor
                                    )
                                )
                            )
                    )

                    // Nombre del Artista sobre la imagen en la esquina inferior izquierda
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 20.dp, bottom = 16.dp, end = 150.dp)
                    ) {
                        Text(
                            text = artist?.name ?: artistName,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Botones juntos (Play/Aleatorio más grande + Favorito) con Palette
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 20.dp, bottom = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Botón de aleatorio (más grande 68dp, M3 Expressive shape)
                        val shuffleInteraction = remember { MutableInteractionSource() }
                        val shuffleScale by rememberBounceScale(shuffleInteraction)
                        val playExpressiveShape = RoundedCornerShape(
                            topStart = 28.dp,
                            topEnd = 12.dp,
                            bottomEnd = 28.dp,
                            bottomStart = 12.dp
                        )

                        Surface(
                            onClick = { viewModel.shufflePlay() },
                            interactionSource = shuffleInteraction,
                            shape = playExpressiveShape,
                            color = palette.accent,
                            tonalElevation = 8.dp,
                            shadowElevation = 8.dp,
                            modifier = Modifier
                                .size(68.dp)
                                .graphicsLayer {
                                    scaleX = shuffleScale
                                    scaleY = shuffleScale
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Shuffle,
                                    contentDescription = stringResource(R.string.action_shuffle),
                                    tint = palette.onAccent,
                                    modifier = Modifier.size(38.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Fila debajo del cover: Chips de información o Control de Selección animado
            item {
                com.example.neosynth.ui.components.AnimatedSelectionRow(
                    isSelectionMode = isSelectionMode,
                    selectedCount = selectedSongIds.size,
                    totalCount = topSongs.size,
                    onSelectAll = { selectedSongIds = topSongs.map { it.id }.toSet() },
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
                                    text = "${artist?.albumCount ?: albums.size} álbumes",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFE8E8E8),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = CircleShape,
                                color = palette.accent.copy(alpha = 0.22f)
                            ) {
                                Text(
                                    text = "${topSongs.size} canciones populares",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFE8E8E8),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                )
            }

            // Discografía
            if (albums.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.artist_discography),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }

                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(albums) { album ->
                            AlbumCard(
                                album = album,
                                coverUrl = viewModel.getCoverUrl(album.coverArt),
                                onClick = { onAlbumClick(album.id) }
                            )
                        }
                    }
                }
            }

            // Canciones populares
            if (topSongs.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.artist_popular_songs),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }

                items(topSongs.take(10)) { song ->
                    val isSelected = song.id in selectedSongIds

                    TopSongRow(
                        title = song.title,
                        album = song.album,
                        coverUrl = viewModel.getCoverUrl(song.coverArt),
                        isSelected = isSelected,
                        isSelectionMode = isSelectionMode,
                        accentColor = palette.accent,
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
                        onOpenMenu = { songForOptions = song }
                    )
                }
            }

            // Biografía
            item {
                ArtistInfoSection(
                    albumCount = artist?.albumCount ?: albums.size,
                    songCount = topSongs.size,
                    biography = artistInfo?.biography,
                    lastFmUrl = artistInfo?.lastFmUrl
                )
            }
        }

        // MultiSelectGridBottomSheet
        if (showMultiSelectGridBottomSheet) {
            com.example.neosynth.ui.components.MultiSelectGridBottomSheet(
                selectedCount = selectedSongIds.size,
                onDismiss = { showMultiSelectGridBottomSheet = false },
                onPlay = {
                    val songsToPlay = topSongs.filter { it.id in selectedSongIds }
                    if (songsToPlay.isNotEmpty()) viewModel.playSong(songsToPlay.first())
                    selectedSongIds = emptySet()
                    showMultiSelectGridBottomSheet = false
                },
                onDownload = {
                    selectedSongIds = emptySet()
                    showMultiSelectGridBottomSheet = false
                },
                onAddToPlaylist = {
                    showPlaylistPicker = true
                    showMultiSelectGridBottomSheet = false
                },
                onPlayNext = {
                    selectedSongIds = emptySet()
                    showMultiSelectGridBottomSheet = false
                },
                onAddToQueue = {
                    selectedSongIds = emptySet()
                    showMultiSelectGridBottomSheet = false
                },
                onFavorite = {
                    selectedSongIds = emptySet()
                    showMultiSelectGridBottomSheet = false
                }
            )
        }

        // Song Options BottomSheet
        songForOptions?.let { song ->
            com.example.neosynth.ui.components.SongOptionsBottomSheet(
                song = song,
                coverUrl = viewModel.getCoverUrl(song.coverArt),
                isDownloaded = false,
                onDismiss = { songForOptions = null },
                onPlay = { viewModel.playSong(song) },
                onPlayNext = { },
                onAddToQueue = { },
                onDownload = { }
            )
        }

        // Back button flotante
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(8.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.action_back)
            )
        }
    }
}

@Composable
private fun ArtistInfoSection(
    albumCount: Int,
    songCount: Int,
    biography: String?,
    lastFmUrl: String?
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = stringResource(R.string.artist_info),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Stats row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(
                        icon = Icons.Rounded.Album,
                        value = albumCount.toString(),
                        label = stringResource(R.string.tab_albums)
                    )
                    StatItem(
                        icon = Icons.Rounded.MusicNote,
                        value = songCount.toString(),
                        label = stringResource(R.string.discover_songs)
                    )
                    if (lastFmUrl != null) {
                        StatItem(
                            icon = Icons.Rounded.Language,
                            value = "Last.fm",
                            label = stringResource(R.string.artist_profile)
                        )
                    }
                }

                // Biography
                if (!biography.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = stringResource(R.string.artist_biography),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = biography.replace(Regex("<[^>]*>"), ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) Int.MAX_VALUE else 4,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (biography.length > 200) {
                        TextButton(
                            onClick = { expanded = !expanded },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(if (expanded) stringResource(R.string.artist_see_less) else stringResource(R.string.artist_see_more))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TopSongRow(
    title: String,
    album: String,
    coverUrl: String?,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onOpenMenu: () -> Unit
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
        color = if (isSelected) 
            accentColor.copy(alpha = 0.35f) 
        else 
            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f)
    ) {
        Row(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = coverUrl,
                contentDescription = title,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = album,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!isSelectionMode) {
                IconButton(
                    onClick = onOpenMenu,
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

@Composable
private fun AlbumCard(
    album: AlbumDto,
    coverUrl: String?,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale by rememberBounceScale(interactionSource)

    Surface(
        modifier = Modifier
            .width(150.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f),
        shadowElevation = 4.dp
    ) {
        Column {
            AsyncImage(
                model = coverUrl,
                contentDescription = album.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(24.dp)),
                contentScale = ContentScale.Crop
            )

            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                album.year?.let { year ->
                    Text(
                        text = year.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
