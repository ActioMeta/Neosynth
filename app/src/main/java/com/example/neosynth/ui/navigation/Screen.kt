package com.example.neosynth.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "Inicio", Icons.Rounded.Home)
    object Discover : Screen("discover", "Descubrir", Icons.Rounded.Search)
    object Library : Screen("library", "Biblioteca", Icons.Rounded.LibraryMusic)
    object Downloads : Screen("downloads", "Descargas", Icons.Rounded.Download)
    object Settings : Screen("settings", "Ajustes", Icons.Rounded.Settings)

    object AlbumDetail : Screen("album_detail/{albumId}", "Detalle", Icons.Rounded.Home)
}