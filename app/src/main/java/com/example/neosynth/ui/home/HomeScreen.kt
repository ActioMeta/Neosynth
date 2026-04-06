package com.example.neosynth.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
                            Column {
                                Text(
                                    text = "Random",
                                    style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = "Mix",
                                    style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = onNavigateToLibrary) {
                                Icon(
                                    imageVector = Icons.Rounded.LibraryMusic,
                                    contentDescription = stringResource(R.string.nav_library)
                                )
                            }
                            IconButton(onClick = onNavigateToSettings) {
                                Icon(
                                    imageVector = Icons.Rounded.Settings,
                                    contentDescription = stringResource(R.string.nav_settings)
                                )
                            }
                        },
                        scrollBehavior = scrollBehavior,
                        colors = TopAppBarDefaults.largeTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            scrolledContainerColor = MaterialTheme.colorScheme.background
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
                    val isOfflineEmpty = errorMsg == "Sin canciones descargadas" || errorMsg == "No downloaded songs"
                    ServerErrorScreen(
                        onRetry = { viewModel.loadHomeData(forceRetry = true) },
                        onSettings = onNavigateToSettings,
                        title = if (isOfflineEmpty) stringResource(R.string.error_no_songs_title) else stringResource(R.string.error_connection_title),
                        message = if (errorMsg == "Sin canciones descargadas") stringResource(R.string.error_no_downloaded_songs) else (errorMsg ?: stringResource(R.string.error_connection_message))
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
                            bottom = padding.calculateBottomPadding() + 80.dp // Espacio para MiniPlayer + NavBar
                        )
                    ) {
                    
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .padding(horizontal = 24.dp)
                        ) {
                            IconButton(
                                onClick = { viewModel.playShuffle() },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .zIndex(3f)
                                    .size(64.dp)
                                    .clip(
                                        RoundedCornerShape(percent = 38)
                                    )
                                    .background(MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Shuffle,
                                    contentDescription = "Shuffle",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            val randomCovers = viewModel.randomCoverArts
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 0.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                randomCovers.forEachIndexed { index, cover ->
                                    Surface(
                                        modifier = Modifier
                                            .size(150.dp)
                                            .offset(
                                                x = if (index == 0) (-55).dp else if (index == 2) 55.dp else 0.dp,
                                                y = if (index == 1) 10.dp else 18.dp
                                            )
                                            .graphicsLayer {
                                                rotationZ = if (index == 0) -15f else if (index == 2) 15f else 0f
                                                scaleX = if (index == 1) 1.1f else 0.9f
                                                scaleY = if (index == 1) 1.1f else 0.9f
                                                clip = true
                                                shape = RoundedCornerShape(20.dp)
                                            },
                                        shape = RoundedCornerShape(20.dp),
                                        tonalElevation = if (index == 1) 12.dp else 6.dp,
                                        shadowElevation = if (index == 1) 12.dp else 6.dp,
                                        border = if (index == 1) null else BorderStroke(
                                            1.dp,
                                            Color.White.copy(alpha = 0.1f)
                                        )
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
                            onClick = { viewModel.playAlbum(it) },
                            onPlay = { viewModel.playAlbum(it) },
                            onShuffle = { viewModel.playAlbum(it, shuffle = true) },
                            onDownload = { viewModel.downloadAlbum(it.id) },
                            onGoToArtist = { onNavigateToArtist(it.artistId, it.artistName) },
                            onPlayNext = { viewModel.onContextPlayNext(it) },
                            onAddToQueue = { viewModel.onContextAddToQueue(it) },
                            onGoToAlbum = { onNavigateToAlbum(it.id) },
                            itemHeight = 200,
                            itemWidth = 180,
                            contentPadding = 24,
                            itemSpacing = 8
                        )
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