package com.example.neosynth

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.neosynth.data.local.entities.SongEntity
import com.example.neosynth.data.preferences.SettingsPreferences
import com.example.neosynth.data.preferences.ThemeMode
import com.example.neosynth.data.repository.MusicRepository
import com.example.neosynth.data.repository.ServerRepository
import com.example.neosynth.player.MusicController
import com.example.neosynth.ui.components.SkeletonLoader
import com.example.neosynth.ui.navigation.FloatingNavBar
import com.example.neosynth.ui.navigation.NeosynthNavGraph
import com.example.neosynth.ui.navigation.Screen
import com.example.neosynth.ui.theme.NeoSynth_androidTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var serverRepository: ServerRepository
    @Inject lateinit var settingsPreferences: SettingsPreferences
    @Inject lateinit var musicController: MusicController
    @Inject lateinit var musicRepository: MusicRepository

    // Permission launcher para notificaciones
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // No action needed, notifications will just not show if denied
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Solicitar permiso de notificaciones en Android 13+
        requestNotificationPermission()
        
        // Manejar comandos de voz del Asistente
        handleVoiceCommand(intent)

        enableEdgeToEdge()

        setContent {
            val appSettings by settingsPreferences.appSettings.collectAsState(
                initial = runBlocking { settingsPreferences.appSettings.first() }
            )
            
            val systemInDarkTheme = isSystemInDarkTheme()
            val useDarkTheme = when (appSettings.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> systemInDarkTheme
            }
            
            NeoSynth_androidTheme(darkTheme = useDarkTheme) {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                val view = androidx.compose.ui.platform.LocalView.current

                var startDestination by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(Unit) {
                    val activeServer = serverRepository.getActiveServer()
                    startDestination = if (activeServer != null) Screen.Home.route else "login"
                }

                if (!view.isInEditMode) {
                    SideEffect {
                        val window = this.window
                        val insetsController = WindowCompat.getInsetsController(window, view)

                        // En Android 8+ esto asegura que si el fondo es claro, los iconos sean oscuros
                        insetsController.isAppearanceLightStatusBars = !useDarkTheme
                        insetsController.isAppearanceLightNavigationBars = !useDarkTheme
                    }
                }

                if (startDestination == null) {
                    SkeletonLoader()
                } else {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        // Usamos Box en lugar de Scaffold para mejor control de animaciones
                        Box(modifier = Modifier.fillMaxSize()) {
                            NeosynthNavGraph(
                                navController = navController,
                                startDestination = startDestination!!
                            )
                            
                            // NavBar con animación sincronizada
                            androidx.compose.animation.AnimatedVisibility(
                                visible = currentRoute != "login" && currentRoute != "player_full",
                                enter = androidx.compose.animation.fadeIn(
                                    animationSpec = androidx.compose.animation.core.tween(200)
                                ) + androidx.compose.animation.slideInVertically(
                                    initialOffsetY = { it },
                                    animationSpec = androidx.compose.animation.core.tween(250)
                                ),
                                exit = androidx.compose.animation.fadeOut(
                                    animationSpec = androidx.compose.animation.core.tween(150)
                                ) + androidx.compose.animation.slideOutVertically(
                                    targetOffsetY = { it },
                                    animationSpec = androidx.compose.animation.core.tween(200)
                                ),
                                modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
                            ) {
                                FloatingNavBar(navController = navController)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleVoiceCommand(intent)
    }

    private fun handleVoiceCommand(intent: Intent?) {
        if (intent?.action == MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) {
            Log.d("MainActivity", "Comando de voz recibido del Asistente")
            
            val query = intent.getStringExtra(MediaStore.EXTRA_MEDIA_FOCUS)
            val searchQuery = intent.getStringExtra(android.app.SearchManager.QUERY)
            val title = intent.getStringExtra(MediaStore.EXTRA_MEDIA_TITLE)
            val artist = intent.getStringExtra(MediaStore.EXTRA_MEDIA_ARTIST)
            val album = intent.getStringExtra(MediaStore.EXTRA_MEDIA_ALBUM)
            val playlist = intent.getStringExtra(MediaStore.EXTRA_MEDIA_PLAYLIST)
            
            Log.d("MainActivity", "Query: $searchQuery, Title: $title, Artist: $artist, Album: $album, Playlist: $playlist, Focus: $query")
            
            lifecycleScope.launch {
                try {
                    val downloadedSongs = musicRepository.getDownloadedSongs().first()
                    
                    when {
                        // Buscar por título de canción
                        !title.isNullOrEmpty() -> {
                            val song = downloadedSongs.find { 
                                it.title.contains(title, ignoreCase = true) 
                            }
                            if (song != null) {
                                val mediaItems = downloadedSongs.map { it.toMediaItem() }
                                val startIndex = downloadedSongs.indexOf(song)
                                musicController.playQueue(mediaItems, startIndex)
                                Log.d("MainActivity", "Reproduciendo canción: ${song.title}")
                            } else {
                                Log.d("MainActivity", "Canción no encontrada: $title")
                            }
                        }
                        
                        // Buscar por artista
                        !artist.isNullOrEmpty() -> {
                            val artistSongs = downloadedSongs.filter { 
                                it.artist.contains(artist, ignoreCase = true) 
                            }
                            if (artistSongs.isNotEmpty()) {
                                val mediaItems = artistSongs.shuffled().map { it.toMediaItem() }
                                musicController.playQueue(mediaItems, 0)
                                Log.d("MainActivity", "Reproduciendo artista: $artist (${artistSongs.size} canciones)")
                            } else {
                                Log.d("MainActivity", "Artista no encontrado: $artist")
                            }
                        }
                        
                        // Buscar por álbum
                        !album.isNullOrEmpty() -> {
                            val albumSongs = downloadedSongs.filter { 
                                it.album?.contains(album, ignoreCase = true) == true 
                            }
                            if (albumSongs.isNotEmpty()) {
                                val mediaItems = albumSongs.map { it.toMediaItem() }
                                musicController.playQueue(mediaItems, 0)
                                Log.d("MainActivity", "Reproduciendo álbum: $album (${albumSongs.size} canciones)")
                            } else {
                                Log.d("MainActivity", "Álbum no encontrado: $album")
                            }
                        }
                        
                        // Buscar por playlist
                        !playlist.isNullOrEmpty() -> {
                            val playlistData = musicRepository.getPlaylistWithSongs(playlist)
                            if (playlistData != null && playlistData.songs.isNotEmpty()) {
                                val mediaItems = playlistData.songs.map { it.toMediaItem() }
                                musicController.playQueue(mediaItems, 0)
                                Log.d("MainActivity", "Reproduciendo playlist: $playlist (${playlistData.songs.size} canciones)")
                            } else {
                                Log.d("MainActivity", "Playlist no encontrada: $playlist")
                            }
                        }
                        
                        // Búsqueda genérica
                        !searchQuery.isNullOrEmpty() -> {
                            val song = downloadedSongs.find { 
                                it.title.contains(searchQuery, ignoreCase = true) ||
                                it.artist.contains(searchQuery, ignoreCase = true) ||
                                it.album?.contains(searchQuery, ignoreCase = true) == true
                            }
                            if (song != null) {
                                val mediaItems = downloadedSongs.map { it.toMediaItem() }
                                val startIndex = downloadedSongs.indexOf(song)
                                musicController.playQueue(mediaItems, startIndex)
                                Log.d("MainActivity", "Reproduciendo búsqueda: $searchQuery -> ${song.title}")
                            } else {
                                Log.d("MainActivity", "No se encontró: $searchQuery")
                            }
                        }
                        
                        // Sin parámetros específicos: reproducir todo en shuffle
                        else -> {
                            if (downloadedSongs.isNotEmpty()) {
                                val mediaItems = downloadedSongs.shuffled().map { it.toMediaItem() }
                                musicController.playQueue(mediaItems, 0)
                                Log.d("MainActivity", "Reproduciendo música (shuffle)")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error procesando comando de voz", e)
                }
            }
        }
    }

    private fun SongEntity.toMediaItem(): MediaItem {
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(path.toUri())
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setArtworkUri(imageUrl?.toUri())
                    .build()
            )
            .build()
    }
}