package com.example.neosynth.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                    viewModel = homeViewModel,
                    onNavigateToLibrary = { navController.navigate("library") },
                    onNavigateToSettings = { navController.navigate("settings") },
                    onNavigateToArtist = { artistId, artistName ->
                        val encodedName = java.net.URLEncoder.encode(artistName, "UTF-8")
                        navController.navigate("artist/$artistId/$encodedName")
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
                    }
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
                route = "player_full",
                enterTransition = {
                    fadeIn(animationSpec = tween(300)) + scaleIn(
                        initialScale = 0.92f,
                        animationSpec = tween(300)
                    )
                },
                exitTransition = {
                    fadeOut(animationSpec = tween(250)) + scaleOut(
                        targetScale = 0.92f,
                        animationSpec = tween(250)
                    )
                },
                popEnterTransition = {
                    fadeIn(animationSpec = tween(300)) + scaleIn(
                        initialScale = 0.92f,
                        animationSpec = tween(300)
                    )
                },
                popExitTransition = {
                    fadeOut(animationSpec = tween(250)) + scaleOut(
                        targetScale = 0.92f,
                        animationSpec = tween(250)
                    )
                }
            ) {
                val currentSongId = currentSong?.mediaId
                val isFavorite by homeViewModel.isCurrentSongFavorite.collectAsState()
                
                // Actualizar estado de favorito cuando cambia la canción
                LaunchedEffect(currentSongId) {
                    homeViewModel.updateCurrentSongFavoriteStatus()
                }
                
                PlayerScreen(
                    musicController = musicController,
                    onBack = { navController.popBackStack() },
                    onDownload = { homeViewModel.downloadCurrentSong() },
                    onLyricsClick = { navController.navigate("lyrics") },
                    isCurrentSongDownloaded = currentSongId != null && currentSongId in downloadedIds,
                    isFavorite = isFavorite,
                    onToggleFavorite = { homeViewModel.toggleFavorite() }
                )
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
                val lyricsError by homeViewModel.lyricsError.collectAsState()
                
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
                    lyricsError = lyricsError,
                    onClose = { navController.popBackStack() }
                )
            }
        }

        val song = currentSong
        val showMiniPlayer = currentRoute != "login" && currentRoute != "player_full" && currentRoute != "lyrics" && song != null
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
            MiniPlayer(
                title = song?.mediaMetadata?.title?.toString() ?: "",
                artist = song?.mediaMetadata?.artist?.toString() ?: "Desconocido",
                artworkUri = song?.mediaMetadata?.artworkUri?.toString(),
                isPlaying = isPlaying,
                onPlayPause = { musicController.togglePlayPause() },
                onClick = { navController.navigate("player_full") }
            )
        }
    }
}