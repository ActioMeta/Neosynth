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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.neosynth.ui.components.CardItem
import com.example.neosynth.ui.components.ServerErrorScreen
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToLibrary: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToArtist: (artistId: String, artistName: String) -> Unit = { _, _ -> }
) {
    val recentlyAdded = viewModel.recentlyAdded
    val isLoading = viewModel.isLoading
    val isRefreshing = viewModel.isRefreshing
    val context = LocalContext.current
    val errorMsg = viewModel.error
    val snackbarHostState = remember { SnackbarHostState() }

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
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
        start = Offset.Zero,
        end = Offset(x = translateAnim, y = translateAnim)
    )

    LaunchedEffect(Unit) {
        viewModel.initPlayer(context)
        viewModel.loadHomeData()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }
    ) { padding ->

        Crossfade(targetState = isLoading, label = "home_state") { loading ->
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
                    ServerErrorScreen(onRetry = { viewModel.loadHomeData() })
                }
            } else {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = padding.calculateTopPadding(),
                            bottom = padding.calculateBottomPadding() + 80.dp // Espacio para MiniPlayer + NavBar
                        )
                    ) {
                    // Top Bar with icons
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 0.dp)
                                .offset(y = (-8).dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onNavigateToLibrary) {
                                Icon(
                                    imageVector = Icons.Rounded.LibraryMusic,
                                    contentDescription = "Biblioteca",
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            IconButton(onClick = onNavigateToSettings) {
                                Icon(
                                    imageVector = Icons.Rounded.Settings,
                                    contentDescription = "Configuración",
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    }
                    
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp)
                                .padding(horizontal = 24.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                            ) {
                                Text(
                                    text = "Random",
                                    style = MaterialTheme.typography.displayLarge,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "Mix",
                                    style = MaterialTheme.typography.displayLarge,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            IconButton(
                                onClick = { viewModel.playShuffle() },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Shuffle,
                                    contentDescription = "Shuffle",
                                    tint = Color.Black,
                                    modifier = Modifier.size(30.dp)
                                )
                            }

                            val randomCovers = viewModel.randomCoverArts
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                randomCovers.forEachIndexed { index, cover ->
                                    Card(
                                        modifier = Modifier
                                            .size(140.dp)
                                            .offset(
                                                x = if (index == 0) (-60).dp else if (index == 2) 60.dp else 0.dp,
                                                y = if (index == 1) 0.dp else 20.dp
                                            )
                                            .graphicsLayer {
                                                rotationZ = if (index == 0) -15f else if (index == 2) 15f else 0f
                                                scaleX = if (index == 1) 1.1f else 0.9f
                                                scaleY = if (index == 1) 1.1f else 0.9f
                                            },
                                        shape = RoundedCornerShape(16.dp),
                                        elevation = CardDefaults.cardElevation(if (index == 1) 12.dp else 6.dp),
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

                    item { Spacer(modifier = Modifier.height(40.dp)) }

                    item {
                        Text(
                            text = "Recién agregados",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(start = 24.dp, bottom = 16.dp),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(recentlyAdded) { album ->
                                CardItem(
                                    album = album,
                                    onClick = { viewModel.playAlbum(album.id) },
                                    onPlay = { viewModel.playAlbum(album.id) },
                                    onShuffle = { viewModel.playAlbum(album.id, shuffle = true) },
                                    onDownload = { viewModel.downloadAlbum(album.id) },
                                    onGoToArtist = { onNavigateToArtist(album.artistId, album.artistName) }
                                )
                            }
                        }
                    }
                } // LazyColumn
                } // PullToRefreshBox
            }
        }
    }
}