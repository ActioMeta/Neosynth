package com.example.neosynth.ui.album

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.*
import com.example.neosynth.ui.stats.rememberBounceScale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import kotlin.math.min

import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.luminance

data class AlbumPaletteColors(
    val accent: Color,
    val onAccent: Color,
    val container: Color
)

@Composable
fun rememberAlbumPalette(coverUrl: String?): AlbumPaletteColors {
    val context = androidx.compose.ui.platform.LocalContext.current
    var accentColor by remember { mutableStateOf<Color?>(null) }
    var onAccentColor by remember { mutableStateOf<Color?>(null) }
    var containerColor by remember { mutableStateOf<Color?>(null) }

    LaunchedEffect(coverUrl) {
        if (coverUrl.isNullOrBlank()) return@LaunchedEffect
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val loader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(coverUrl)
                    .allowHardware(false)
                    .build()
                val result = loader.execute(request)
                if (result is SuccessResult) {
                    val bitmap = result.drawable.toBitmap()
                    val palette = Palette.from(bitmap).generate()
                    val vibrant = palette.getVibrantColor(palette.getLightVibrantColor(palette.getDominantColor(0)))
                    if (vibrant != 0) {
                        val baseColor = Color(vibrant)
                        val lum = baseColor.luminance()
                        val color = if (lum < 0.42f) {
                            val boost = (0.42f - lum) + 0.25f
                            Color(
                                red = (baseColor.red + boost).coerceIn(0f, 1f),
                                green = (baseColor.green + boost).coerceIn(0f, 1f),
                                blue = (baseColor.blue + boost).coerceIn(0f, 1f),
                                alpha = 1f
                            )
                        } else baseColor

                        accentColor = color
                        onAccentColor = if (color.luminance() > 0.6f) Color.Black else Color.White
                        containerColor = color.copy(alpha = 0.28f)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val defaultPrimary = MaterialTheme.colorScheme.primary
    val defaultOnPrimary = MaterialTheme.colorScheme.onPrimary
    val defaultContainer = MaterialTheme.colorScheme.surfaceContainerHigh

    val animatedAccent by animateColorAsState(
        targetValue = accentColor ?: defaultPrimary,
        animationSpec = tween(durationMillis = 350),
        label = "accent_color_anim"
    )
    val animatedOnAccent by animateColorAsState(
        targetValue = onAccentColor ?: defaultOnPrimary,
        animationSpec = tween(durationMillis = 350),
        label = "on_accent_color_anim"
    )
    val animatedContainer by animateColorAsState(
        targetValue = containerColor ?: defaultContainer,
        animationSpec = tween(durationMillis = 350),
        label = "container_color_anim"
    )

    return AlbumPaletteColors(
        accent = animatedAccent,
        onAccent = animatedOnAccent,
        container = animatedContainer
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun AlbumDetailScreen(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    albumId: String,
    viewModel: AlbumDetailViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onArtistClick: (String, String) -> Unit = { _, _ -> }
) {
    val album by viewModel.album.collectAsStateWithLifecycle()
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val downloadedIds by viewModel.downloadedSongIds.collectAsStateWithLifecycle()
    
    // Multi-selection state
    var selectedSongIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val isSelectionMode = selectedSongIds.isNotEmpty()
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var showMultiSelectGridBottomSheet by remember { mutableStateOf(false) }

    val currentSong by viewModel.musicController.currentMediaItem
    val isMiniPlayerVisible = currentSong != null
    val listBottomPadding = if (isSelectionMode) {
        if (isMiniPlayerVisible) 260.dp else 180.dp
    } else {
        if (isMiniPlayerVisible) 180.dp else 100.dp
    }

    val backgroundColor = MaterialTheme.colorScheme.background
    
    // State para parallax
    val listState = rememberLazyListState()
    val scrollOffset = remember { derivedStateOf { listState.firstVisibleItemScrollOffset.toFloat() } }
    val firstVisibleItemIndex = remember { derivedStateOf { listState.firstVisibleItemIndex } }
    
    val parallaxOffset = if (firstVisibleItemIndex.value == 0) {
        scrollOffset.value * 0.5f
    } else {
        0f
    }
    
    val headerAlpha = if (firstVisibleItemIndex.value == 0) {
        (1f - (scrollOffset.value / 800f)).coerceIn(0f, 1f)
    } else {
        0f
    }

    LaunchedEffect(albumId) {
        viewModel.loadAlbum(albumId)
    }

    var songForOptions by remember { mutableStateOf<SongDto?>(null) }
    val coverUrl = viewModel.getCoverUrl(album?.coverArt ?: albumId)
    val palette = rememberAlbumPalette(coverUrl)

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = listBottomPadding)
        ) {
            // Header Hero con cover del álbum y difuminado hacia abajo (Visible desde el frame 0)
            item {
                val coverShape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(370.dp)
                        .graphicsLayer {
                            translationY = -parallaxOffset
                            alpha = headerAlpha
                        }
                ) {
                    // Imagen de portada Hero
                    with(sharedTransitionScope) {
                        AsyncImage(
                            model = coverUrl,
                            contentDescription = album?.name,
                            modifier = Modifier
                                .sharedElement(
                                    sharedContentState = rememberSharedContentState(key = "cover_$albumId"),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    boundsTransform = { _, _ ->
                                        spring(
                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    },
                                    clipInOverlayDuringTransition = OverlayClip(coverShape)
                                )
                                .clip(coverShape)
                                .fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    // Gradiente difuminado hacia abajo usando el color del Palette
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

                    // Título y Artista sobre el cover en la esquina inferior izquierda
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 20.dp, bottom = 16.dp, end = 150.dp)
                    ) {
                        Text(
                            text = album?.name ?: "",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = album?.artist ?: "",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable {
                                album?.let { a ->
                                    val artistId = songs.firstOrNull()?.artistId ?: ""
                                    if (artistId.isNotEmpty()) {
                                        onArtistClick(artistId, a.artist ?: "")
                                    }
                                }
                            }
                        )
                    }

                    // Botones juntos (Play 68dp + Random 52dp) con Palette en esquina inferior derecha
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 20.dp, bottom = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Botón de aleatorio (más grande 52.dp, icono gris claro)
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
                                    tint = Color(0xFFE8E8E8),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        // Botón de reproducir (más grande 68.dp, forma irregular M3 Expressive)
                        val playInteraction = remember { MutableInteractionSource() }
                        val playScale by rememberBounceScale(playInteraction)
                        val playExpressiveShape = RoundedCornerShape(
                            topStart = 28.dp,
                            topEnd = 12.dp,
                            bottomEnd = 28.dp,
                            bottomStart = 12.dp
                        )

                        Surface(
                            onClick = { viewModel.playAlbum() },
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
                            AlbumInfoRow(
                                year = album?.year,
                                songCount = songs.size,
                                totalDuration = songs.sumOf { it.duration },
                                genre = album?.genre,
                                paletteColors = palette
                            )
                        }
                    }
                )
            }

            // Lista de canciones
            itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                val isSelected = song.id in selectedSongIds

                AlbumSongRow(
                    index = index + 1,
                    song = song,
                    isDownloaded = song.id in downloadedIds,
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
                    viewModel.loadPlaylists()
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

        // Normal Top App Bar
        TopAppBar(
            title = { },
            navigationIcon = {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(backgroundColor.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.action_back)
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            ),
            modifier = Modifier.statusBarsPadding()
        )
        
        // Playlist Picker Dialog
        if (showPlaylistPicker) {
            val playlists by viewModel.playlists.collectAsStateWithLifecycle()
            
            LaunchedEffect(showPlaylistPicker) {
                if (showPlaylistPicker) {
                    viewModel.loadPlaylists()
                }
            }
            
            com.example.neosynth.ui.components.PlaylistPickerDialog(
                playlists = playlists,
                onDismiss = { 
                    showPlaylistPicker = false 
                },
                onPlaylistSelected = { playlistId ->
                    viewModel.addToPlaylist(selectedSongIds, playlistId)
                    selectedSongIds = emptySet()
                }
            )
        }

        // Song Options BottomSheet
        songForOptions?.let { song ->
            com.example.neosynth.ui.components.SongOptionsBottomSheet(
                song = song,
                coverUrl = viewModel.getCoverUrl(song.coverArt),
                isDownloaded = song.id in downloadedIds,
                onDismiss = { songForOptions = null },
                onPlay = { viewModel.playSong(song) },
                onPlayNext = { viewModel.playSongsNext(setOf(song.id)) },
                onAddToQueue = { viewModel.addSongsToQueue(setOf(song.id)) },
                onDownload = { viewModel.downloadSong(song) }
            )
        }
    }
}

@Composable
private fun AlbumInfoRow(
    year: Int?,
    songCount: Int,
    totalDuration: Int,
    genre: String?,
    paletteColors: AlbumPaletteColors
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        year?.let {
            InfoChip(text = it.toString(), paletteColors = paletteColors)
            Spacer(modifier = Modifier.width(6.dp))
        }
        
        InfoChip(text = stringResource(R.string.library_songs_count, songCount), paletteColors = paletteColors)
        Spacer(modifier = Modifier.width(6.dp))
        
        InfoChip(text = formatTotalDuration(totalDuration), paletteColors = paletteColors)
        
        genre?.let {
            Spacer(modifier = Modifier.width(6.dp))
            InfoChip(text = it, paletteColors = paletteColors)
        }
    }
}

@Composable
private fun InfoChip(text: String, paletteColors: AlbumPaletteColors) {
    Surface(
        shape = CircleShape,
        color = paletteColors.accent.copy(alpha = 0.22f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFFE8E8E8),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun AlbumSongRow(
    index: Int,
    song: SongDto,
    isDownloaded: Boolean,
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
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
                            tint = accentColor
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
        targetValue = if (isPressed) 0.85f else 1f, // Más visible
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow // Más lento y visible
        ),
        label = "icon_button_scale"
    )
    
    val elevation by animateFloatAsState(
        targetValue = if (isPressed) 1f else 12f, // Mayor contraste
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
        shadowElevation = elevation.dp
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
private fun formatTotalDuration(seconds: Int): String {
    val hours = seconds / 3600
    val mins = (seconds % 3600) / 60
    return if (hours > 0) {
        stringResource(R.string.duration_hours_mins, hours, mins)
    } else {
        stringResource(R.string.duration_mins, mins)
    }
}
