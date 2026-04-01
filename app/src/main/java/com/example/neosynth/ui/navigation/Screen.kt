package com.example.neosynth.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.neosynth.R

sealed class Screen(val route: String, @StringRes val titleResId: Int, val icon: ImageVector) {
    object Home : Screen("home", R.string.nav_home, Icons.Rounded.Home)
    object Discover : Screen("discover", R.string.nav_discover, Icons.Rounded.Search)
    object Library : Screen("library", R.string.nav_library, Icons.Rounded.LibraryMusic)
    object Downloads : Screen("downloads", R.string.nav_downloads, Icons.Rounded.Download)
    object Settings : Screen("settings", R.string.nav_settings, Icons.Rounded.Settings)

    object AlbumDetail : Screen("album_detail/{albumId}", R.string.nav_detail, Icons.Rounded.Home)
}