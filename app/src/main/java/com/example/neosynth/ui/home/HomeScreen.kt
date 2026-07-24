package com.example.neosynth.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.example.neosynth.ui.stats.rememberBounceScale
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.neosynth.ui.components.CardItem
import com.example.neosynth.ui.components.ServerErrorScreen
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.res.stringResource
import com.example.neosynth.R
import com.example.neosynth.domain.model.Album
import com.example.neosynth.ui.components.Carousel
import com.example.neosynth.ui.components.NeoPullToRefreshOverlayIndicator

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToLibrary: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToStats: () -> Unit = {},
    onNavigateToArtist: (artistId: String, artistName: String) -> Unit = { _, _ -> },
    onNavigateToAlbum: (albumId: String) -> Unit = {}
) {
    val recentlyAdded = viewModel.recentlyAdded
    val isLoading = viewModel.isLoading
    val isRefreshing = viewModel.isRefreshing
    val context = LocalContext.current
    val errorMsg = viewModel.error
    val snackbarHostState = remember { SnackbarHostState() }
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(topAppBarState)
    val homeListState = remember { LazyListState() }
    val pullToRefreshState = rememberPullToRefreshState()
    var wasRefreshing by remember { mutableStateOf(false) }
    val canTriggerRefresh by remember(homeListState, topAppBarState) {
        derivedStateOf {
            homeListState.firstVisibleItemIndex == 0 &&
                homeListState.firstVisibleItemScrollOffset == 0 &&
                topAppBarState.collapsedFraction <= 0.01f
        }
    }
    


    // Escuchar eventos de UI (Snackbar)
    LaunchedEffect(Unit) {
        viewModel.uiEvent.collectLatest { event ->
            when (event) {
                is HomeViewModel.UiEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(
                        message = event.message,
                        duration = SnackbarDuration.Short
                    )
                }
            }
        }
    }

    // --- Lógica del Shimmer Brush ---
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2000f,
        animationSpec = infiniteRepeatable(animation = tween(1500, easing = LinearEasing)),
        label = ""
    )
    val brush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.4f),
            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.8f),
            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.4f),
        ),
        start = Offset.Zero,
        end = Offset(x = translateAnim, y = translateAnim)
    )

    LaunchedEffect(Unit) {
        viewModel.initPlayer(context)
        viewModel.loadHomeData()
        homeListState.scrollToItem(0)
    }

    LaunchedEffect(isRefreshing) {
        if (wasRefreshing && !isRefreshing) {
            homeListState.scrollToItem(0)
        }
        wasRefreshing = isRefreshing
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                if (!isLoading && errorMsg == null) {
                    LargeTopAppBar(
                        title = {
                            val titleAlpha = (1f - (scrollBehavior.state.collapsedFraction * 2.5f)).coerceIn(0f, 1f)
                            Column(
                                modifier = Modifier.graphicsLayer {
                                    alpha = titleAlpha
                                }
                            ) {
                                Text(
                                    text = stringResource(R.string.home_title_random),
                                    style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = stringResource(R.string.home_title_mix),
                                    style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        actions = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(end = 12.dp)
                            ) {
                                val libInteraction = remember { MutableInteractionSource() }
                                val libScale by rememberBounceScale(libInteraction)
                                Surface(
                                    onClick = onNavigateToLibrary,
                                    interactionSource = libInteraction,
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shadowElevation = 2.dp,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .graphicsLayer {
                                            scaleX = libScale
                                            scaleY = libScale
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.LibraryMusic,
                                            contentDescription = stringResource(R.string.nav_library),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }

                                val settingsInteraction = remember { MutableInteractionSource() }
                                val settingsScale by rememberBounceScale(settingsInteraction)
                                Surface(
                                    onClick = onNavigateToSettings,
                                    interactionSource = settingsInteraction,
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shadowElevation = 2.dp,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .graphicsLayer {
                                            scaleX = settingsScale
                                            scaleY = settingsScale
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.Settings,
                                            contentDescription = stringResource(R.string.nav_settings),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }
                        },
                        scrollBehavior = scrollBehavior,
                        colors = TopAppBarDefaults.largeTopAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent
                        )
                    )
                }
            },
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.padding(bottom = 165.dp) // Avoid overlap with NavBar/MiniPlayer
                ) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        ) { padding ->

        Crossfade(
            targetState = isLoading,
            modifier = Modifier.fillMaxSize(),
            label = "home_state"
        ) { loading ->
            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = padding.calculateTopPadding())
                ) {
                    HomeSkeleton(brush = brush)
                }
            } else if (errorMsg != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val isOfflineEmpty = errorMsg == stringResource(R.string.error_no_downloaded_songs) || 
                                         errorMsg == stringResource(R.string.error_offline_no_downloads) ||
                                         errorMsg == "Sin canciones descargadas" || 
                                         errorMsg == "No downloaded songs"
                    ServerErrorScreen(
                        onRetry = { viewModel.loadHomeData(forceRetry = true) },
                        onSettings = onNavigateToSettings,
                        title = if (isOfflineEmpty) stringResource(R.string.error_no_songs_title) else stringResource(R.string.error_connection_title),
                        message = if (isOfflineEmpty) stringResource(R.string.error_no_downloaded_songs) else (errorMsg ?: stringResource(R.string.error_connection_message))
                    )
                }
            } else {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        if (canTriggerRefresh) {
                            viewModel.refresh()
                        }
                    },
                    state = pullToRefreshState,
                    enabled = canTriggerRefresh,
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    LazyColumn(
                        state = homeListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = padding.calculateTopPadding(),
                            bottom = padding.calculateBottomPadding() + 180.dp // Espacio para MiniPlayer + NavBar
                        )
                    ) {
                    
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(170.dp)
                                .padding(horizontal = 24.dp)
                        ) {
                            val shuffleInteraction = remember { MutableInteractionSource() }
                            val shuffleScale by rememberBounceScale(shuffleInteraction)
                            
                            // Botón Random con estilo Pill / Cápsula Expresiva
                            Surface(
                                onClick = { viewModel.playShuffle() },
                                interactionSource = shuffleInteraction,
                                shape = RoundedCornerShape(32.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shadowElevation = 8.dp,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .zIndex(3f)
                                    .size(width = 68.dp, height = 52.dp)
                                    .graphicsLayer {
                                        scaleX = shuffleScale
                                        scaleY = shuffleScale
                                    }
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Shuffle,
                                        contentDescription = stringResource(R.string.action_shuffle),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }

                            val randomCovers = viewModel.randomCoverArts
                            val expressiveCardShape = RoundedCornerShape(
                                topStart = 28.dp,
                                topEnd = 12.dp,
                                bottomEnd = 28.dp,
                                bottomStart = 12.dp
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter),
                                contentAlignment = Alignment.Center
                            ) {
                                randomCovers.forEachIndexed { index, cover ->
                                    val cardInteraction = remember { MutableInteractionSource() }
                                    val cardScale by rememberBounceScale(cardInteraction)

                                    Surface(
                                        onClick = { viewModel.playRandomMixSongAt(index) },
                                        interactionSource = cardInteraction,
                                        shape = expressiveCardShape,
                                        tonalElevation = if (index == 1) 12.dp else 6.dp,
                                        shadowElevation = if (index == 1) 12.dp else 6.dp,
                                        border = if (index == 1) 
                                            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) 
                                        else 
                                            BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                                        modifier = Modifier
                                            .size(150.dp)
                                            .offset(
                                                x = if (index == 0) (-55).dp else if (index == 2) 55.dp else 0.dp,
                                                y = if (index == 1) 8.dp else 18.dp
                                            )
                                            .graphicsLayer {
                                                rotationZ = if (index == 0) -14f else if (index == 2) 14f else 0f
                                                val baseScale = if (index == 1) 1.08f else 0.92f
                                                scaleX = baseScale * cardScale
                                                scaleY = baseScale * cardScale
                                                clip = true
                                                shape = expressiveCardShape
                                            }
                                    ) {
                                        AsyncImage(
                                            model = cover,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(48.dp)) }

                    item {
                        Carousel(
                            albums = recentlyAdded,
                            title = stringResource(R.string.home_recently_added),
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onNavigateToAlbum(it.id) },
                            onPlay = { viewModel.playAlbum(it) },
                            onShuffle = { viewModel.playAlbum(it, shuffle = true) },
                            onDownload = { viewModel.downloadAlbum(it.id) },
                            onGoToArtist = { onNavigateToArtist(it.artistId, it.artistName) },
                            onPlayNext = { viewModel.onContextPlayNext(it) },
                            onAddToQueue = { viewModel.onContextAddToQueue(it) },
                            onGoToAlbum = { onNavigateToAlbum(it.id) },
                            itemHeight = 188,
                            itemWidth = 180,
                            contentPadding = 24,
                            itemSpacing = 8
                        )
                    }

                    val topSongs = viewModel.topSongsThisWeek
                    if (topSongs.isNotEmpty()) {
                        item { Spacer(modifier = Modifier.height(24.dp)) }
                        item {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                ),
                                shape = RoundedCornerShape(28.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Equalizer,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = stringResource(R.string.stats_top_songs),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        val arrowInteraction = remember { MutableInteractionSource() }
                                        val arrowScale by rememberBounceScale(arrowInteraction)
                                        IconButton(
                                            onClick = onNavigateToStats,
                                            interactionSource = arrowInteraction,
                                            modifier = Modifier.graphicsLayer {
                                                scaleX = arrowScale
                                                scaleY = arrowScale
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.ChevronRight,
                                                contentDescription = stringResource(R.string.stats_title),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        topSongs.forEachIndexed { index, song ->
                                            val rowInteraction = remember { MutableInteractionSource() }
                                            val rowScale by rememberBounceScale(rowInteraction)

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .graphicsLayer {
                                                        scaleX = rowScale
                                                        scaleY = rowScale
                                                    }
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .background(
                                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                                    )
                                                    .clickable(
                                                        interactionSource = rowInteraction,
                                                        indication = null
                                                    ) {
                                                        viewModel.playTopSong(song.songId)
                                                    }
                                                    .padding(vertical = 10.dp, horizontal = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                val badgeColor = when (index + 1) {
                                                    1 -> Color(0xFFFFD700)
                                                    2 -> Color(0xFFC0C0C0)
                                                    3 -> Color(0xFFCD7F32)
                                                    else -> MaterialTheme.colorScheme.secondaryContainer
                                                }
                                                val textColor = when (index + 1) {
                                                    1, 2, 3 -> Color.Black
                                                    else -> MaterialTheme.colorScheme.onSecondaryContainer
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .size(26.dp)
                                                        .background(badgeColor, CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "${index + 1}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = textColor
                                                    )
                                                }
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = song.title,
                                                        style = MaterialTheme.typography.bodyMedium,
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
                                                Text(
                                                    text = stringResource(R.string.stats_minutes, song.totalTimeMs / 60000),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(64.dp))
                    }
                } // LazyColumn
                    } // PullToRefreshBox


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