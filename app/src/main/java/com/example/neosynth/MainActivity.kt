package com.example.neosynth

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import androidx.core.view.WindowCompat
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.neosynth.data.preferences.SettingsPreferences
import com.example.neosynth.data.preferences.ThemeMode
import com.example.neosynth.data.repository.ServerRepository
import com.example.neosynth.ui.components.SkeletonLoader
import com.example.neosynth.ui.navigation.FloatingNavBar
import com.example.neosynth.ui.navigation.NeosynthNavGraph
import com.example.neosynth.ui.navigation.Screen
import com.example.neosynth.ui.theme.NeoSynth_androidTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var serverRepository: ServerRepository
    @Inject lateinit var settingsPreferences: SettingsPreferences

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
}