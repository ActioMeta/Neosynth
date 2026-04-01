package com.example.neosynth.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.example.neosynth.ui.home.HomeScreen
import com.example.neosynth.ui.home.HomeViewModel
import com.example.neosynth.ui.login.LoginScreen
import com.example.neosynth.ui.components.MiniPlayer
import com.example.neosynth.ui.discover.DiscoverScreen
import com.example.neosynth.ui.downloads.DownloadsScreen
import com.example.neosynth.ui.player.PlayerScreen
import com.example.neosynth.ui.lyrics.LyricsScreen
import com.example.neosynth.ui.artist.ArtistDetailScreen
import com.example.neosynth.ui.album.AlbumDetailScreen
import com.example.neosynth.ui.library.LibraryScreen
import com.example.neosynth.ui.settings.SettingsScreen
import com.example.neosynth.ui.playlist.PlaylistDetailScreen
import com.example.neosynth.ui.discover.recent.RecentSongsScreen
import java.net.URLDecoder
import java.net.URLEncoder

@Composable
fun NeosynthNavGraph(
    navController: NavHostController,
    startDestination: String = "login"
) {
    val homeViewModel: HomeViewModel = hiltViewModel()
    val musicController = homeViewModel.musicController

    val currentSong by musicController.currentMediaItem
    val isPlaying by musicController.isPlaying
    val downloadedIds by homeViewModel.downloadedSongIds.collectAsState()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Box(modifier = Modifier.fillMaxSize()) {
        SharedTransitionLayout {
            NavHost(
                navController = navController,
                startDestination = startDestination
            ) {
            composable("login") {
                LoginScreen(onLoginSuccess = {
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                    }
                })
            }

            composable("home") {
                HomeScreen(
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this,
                    viewModel = homeViewModel,
                    onNavigateToLibrary = { navController.navigate("library") },
                    onNavigateToSettings = { navController.navigate("settings") },
                    onNavigateToStats = { navController.navigate("stats") },
                    onNavigateToArtist = { artistId, artistName ->
                        val encodedName = java.net.URLEncoder.encode(artistName, "UTF-8")
                        navController.navigate("artist/$artistId/$encodedName")
                    },
                    onNavigateToAlbum = { albumId ->
                        navController.navigate("album/$albumId")
                    }
                )
            }

            composable("downloads") {
                DownloadsScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            composable("discover") {
                DiscoverScreen(
                    onNavigateToArtist = { artistId, artistName ->
                        val encodedName = URLEncoder.encode(artistName, "UTF-8")
                        navController.navigate("artist/$artistId/$encodedName")
                    },
                    onNavigateToRecentSongs = {
                        navController.navigate("recent_songs")
                    }
                )
            }

            composable("recent_songs") {
                RecentSongsScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "artist/{artistId}/{artistName}",
                arguments = listOf(
                    navArgument("artistId") { type = NavType.StringType },
                    navArgument("artistName") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val artistId = backStackEntry.arguments?.getString("artistId") ?: ""
                val artistName = URLDecoder.decode(
                    backStackEntry.arguments?.getString("artistName") ?: "",
                    "UTF-8"
                )
                ArtistDetailScreen(
                    artistId = artistId,
                    artistName = artistName,
                    onBack = { navController.popBackStack() },
                    onAlbumClick = { albumId ->
                        navController.navigate("album/$albumId")
                    }
                )
            }

            composable(
                route = "album/{albumId}",
                arguments = listOf(
                    navArgument("albumId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val albumId = backStackEntry.arguments?.getString("albumId") ?: ""
                AlbumDetailScreen(
                    albumId = albumId,
                    onBack = { navController.popBackStack() },
                    onArtistClick = { artistId, artistName ->
                        val encodedName = URLEncoder.encode(artistName, "UTF-8")
                        navController.navigate("artist/$artistId/$encodedName")
                    }
                )
            }

            composable("library") {
                LibraryScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToArtist = { artistId, artistName ->
                        val encodedName = URLEncoder.encode(artistName, "UTF-8")
                        navController.navigate("artist/$artistId/$encodedName")
                    },
                    onNavigateToPlaylist = { playlistId ->
                        navController.navigate("playlist/$playlistId")
                    },
                    onNavigateToAlbum = { albumId ->
                        navController.navigate("album/$albumId")
                    }
                )
            }

            composable(
                route = "playlist/{playlistId}",
                arguments = listOf(
                    navArgument("playlistId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val playlistId = backStackEntry.arguments?.getString("playlistId") ?: ""
                PlaylistDetailScreen(
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this,
                    playlistId = playlistId,
                    onBack = { navController.popBackStack() },
                    onArtistClick = { artistId, artistName ->
                        val encodedName = URLEncoder.encode(artistName, "UTF-8")
                        navController.navigate("artist/$artistId/$encodedName")
                    }
                )
            }

            composable("settings") {
                SettingsScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "player_full"
            ) {
                val currentSongId = currentSong?.mediaId
                val isFavorite by homeViewModel.isCurrentSongFavorite.collectAsState()
                val visualizerEnabled by homeViewModel.visualizerEnabled.collectAsState()
                
                // Estados para letras
                val showLyricsSelection by homeViewModel.showLyricsSelection.collectAsState()
                val lyricsOptions by homeViewModel.lyricsOptions.collectAsState()
                val isLoadingLyrics by homeViewModel.isLoadingLyrics.collectAsState()
                val lyricsError by homeViewModel.lyricsError.collectAsState()
                
                // Si no hay canción activa al abrir el player completo, volver atrás
                LaunchedEffect(currentSong) {
                    if (currentSong == null) {
                        navController.popBackStack()
                    }
                }

                // Actualizar estado de favorito cuando cambia la canción
                LaunchedEffect(currentSongId) {
                    homeViewModel.updateCurrentSongFavoriteStatus()
                }
                
                // Mostrar error de letras si ocurre
                LaunchedEffect(lyricsError) {
                    if (lyricsError != null) {
                        // Podríamos mostrar un snackbar o toast aquí si tuviéramos acceso a un host
                        // Por ahora solo log
                        android.util.Log.e("NavGraph", "Lyrics error: $lyricsError")
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .sharedBounds(
                            sharedContentState = rememberSharedContentState(key = "player_bounds"),
                            animatedVisibilityScope = this@composable
                        )
                ) {
                    // Fix: AnimatedVisibilityScope is required by PlayerScreen
                    // Since we are already inside a SharedTransitionLayout but not an AnimatedVisibility,
                    // we need to provide one or remove the requirement from PlayerScreen if not needed.
                    // However, looking at the previous code, PlayerScreen was called directly.
                    // The error says "actual type is BoxScope, but AnimatedVisibilityScope was expected".
                    // This means PlayerScreen's second argument `animatedVisibilityScope` is receiving `this` (BoxScope) instead of a valid scope.
                    // The `composable` block gives us `AnimatedContentScope` which implements `AnimatedVisibilityScope`.
                    // So `this` should work IF we are directly in the content lambda of `composable`.
                     
                    PlayerScreen(
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this@composable,
                        onBack = { navController.popBackStack() },
                        onDownload = { homeViewModel.downloadCurrentSong() },
                        onLyricsClick = { navController.navigate("lyrics") },
                        isCurrentSongDownloaded = currentSongId != null && currentSongId in downloadedIds,
                        isFavorite = isFavorite,
                        onToggleFavorite = { homeViewModel.toggleFavorite() },
                        visualizerEnabled = visualizerEnabled
                    )
                    
                    // Loading Overlay
                    if (isLoadingLyrics) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.material3.CircularProgressIndicator()
                        }
                    }
                }
            }

            composable(
                route = "lyrics",
                enterTransition = {
                    fadeIn(animationSpec = tween(300)) + scaleIn(
                        initialScale = 0.95f,
                        animationSpec = tween(300)
                    )
                },
                exitTransition = {
                    fadeOut(animationSpec = tween(250)) + scaleOut(
                        targetScale = 0.95f,
                        animationSpec = tween(250)
                    )
                }
            ) {
                val currentSongId = currentSong?.mediaId
                val currentLyrics by homeViewModel.currentLyrics.collectAsState()
                val isLoadingLyrics by homeViewModel.isLoadingLyrics.collectAsState()
                val isLoadingLyricsOptions by homeViewModel.isLoadingLyricsOptions.collectAsState()
                val lyricsError by homeViewModel.lyricsError.collectAsState()
                val lyricsOptions by homeViewModel.lyricsOptions.collectAsState()
                val selectedLyricsOption by homeViewModel.selectedLyricsOption.collectAsState()
                
                // Cargar letras cuando cambia la canción
                LaunchedEffect(currentSongId) {
                    if (currentSongId != null) {
                        homeViewModel.loadLyrics()
                    }
                }
                
                LyricsScreen(
                    musicController = musicController,
                    lyrics = currentLyrics,
                    isLoadingLyrics = isLoadingLyrics,
                    isLoadingOptions = isLoadingLyricsOptions,
                    lyricsError = lyricsError,
                    lyricsOptions = lyricsOptions,
                    selectedLyricsOption = selectedLyricsOption,
                    onSelectOption = { homeViewModel.selectLyric(it) },
                    onOpenOptions = { homeViewModel.loadLyricsOptions() },
                    onEditLyrics = { navController.navigate("lyrics_editor") },
                    onClose = { navController.popBackStack() }
                )
            }

            composable(
                route = "lyrics_editor",
                enterTransition = {
                    androidx.compose.animation.slideInVertically(initialOffsetY = { it }, animationSpec = tween(300))
                },
                exitTransition = {
                    androidx.compose.animation.slideOutVertically(targetOffsetY = { it }, animationSpec = tween(300))
                }
            ) {
                com.example.neosynth.ui.lyrics.LyricsEditorScreen(
                    musicController = musicController,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "stats",
                enterTransition = {
                    androidx.compose.animation.slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300))
                },
                exitTransition = {
                    androidx.compose.animation.slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300))
                }
            ) {
                com.example.neosynth.ui.stats.StatsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }

        val song = currentSong
        // Show mini player if there's an active or restored song (not on login/player/lyrics screens)
        val showMiniPlayer = currentRoute != "login" && currentRoute != "player_full" && currentRoute != "lyrics" && currentRoute != "lyrics_editor" && song != null
        AnimatedVisibility(
            visible = showMiniPlayer,
            enter = fadeIn(animationSpec = tween(200)) + scaleIn(
                initialScale = 0.95f,
                animationSpec = tween(250)
            ),
            exit = fadeOut(animationSpec = tween(150)) + scaleOut(
                targetScale = 0.95f,
                animationSpec = tween(200)
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 100.dp) // Espacio para la NavBar flotante
        ) {
            val miniPlayerSongId = song?.mediaId
            val hasPrevious by musicController.hasPrevious
            val hasNext by musicController.hasNext
            
            Box(
                modifier = Modifier
                    .sharedBounds(
                        sharedContentState = rememberSharedContentState(key = "player_bounds"),
                        animatedVisibilityScope = this@AnimatedVisibility
                    )
            ) {
                MiniPlayer(
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this@AnimatedVisibility,
                    mediaId = miniPlayerSongId,
                    title = song?.mediaMetadata?.title?.toString() ?: "",
                    artist = song?.mediaMetadata?.artist?.toString() ?: "Desconocido",
                    artworkUri = song?.mediaMetadata?.artworkUri?.toString(),
                    isPlaying = isPlaying,
                    hasPrevious = hasPrevious,
                    hasNext = hasNext,
                    onPlayPause = { musicController.togglePlayPause() },
                    onSkipPrevious = { musicController.skipPreviousOrRestart() },
                    onSkipNext = { musicController.skipNext() },
                    onClick = { navController.navigate("player_full") }
                )
            }
        }
    }
}
}