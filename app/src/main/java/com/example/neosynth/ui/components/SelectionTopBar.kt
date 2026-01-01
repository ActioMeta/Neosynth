package com.example.neosynth.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(
    selectedCount: Int,
    onClearSelection: () -> Unit,
    onPlaySelected: () -> Unit,
    onDownloadSelected: () -> Unit,
    onAddToFavorites: () -> Unit,
    onAddToPlaylist: () -> Unit,
    showDeleteInsteadOfDownload: Boolean = false,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = selectedCount > 0,
        enter = fadeIn() + slideInVertically(
            initialOffsetY = { -it },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ),
        exit = fadeOut() + slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ),
        modifier = modifier
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "$selectedCount",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = onClearSelection) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Cancelar selección"
                    )
                }
            },
            actions = {
                // Play
                IconButton(onClick = onPlaySelected) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Reproducir"
                    )
                }
                
                // Download o Delete
                IconButton(onClick = onDownloadSelected) {
                    Icon(
                        imageVector = if (showDeleteInsteadOfDownload) Icons.Rounded.Delete else Icons.Rounded.Download,
                        contentDescription = if (showDeleteInsteadOfDownload) "Eliminar" else "Descargar"
                    )
                }
                
                // Add to favorites
                IconButton(onClick = onAddToFavorites) {
                    Icon(
                        imageVector = Icons.Rounded.Favorite,
                        contentDescription = "Agregar a favoritos"
                    )
                }
                
                // Add to playlist
                IconButton(onClick = onAddToPlaylist) {
                    Icon(
                        imageVector = Icons.Rounded.PlaylistAdd,
                        contentDescription = "Agregar a playlist"
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )
    }
}
