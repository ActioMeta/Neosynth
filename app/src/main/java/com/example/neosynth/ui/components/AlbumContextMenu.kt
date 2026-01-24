package com.example.neosynth.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

data class ContextMenuAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit
)

@Composable
fun AlbumContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onDownload: () -> Unit,
    onGoToArtist: () -> Unit,
    offset: DpOffset = DpOffset(0.dp, 0.dp)
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = offset,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        ContextMenuItem(
            icon = Icons.Rounded.PlayArrow,
            label = "Reproducir",
            onClick = {
                onPlay()
                onDismiss()
            }
        )
        ContextMenuItem(
            icon = Icons.Rounded.Shuffle,
            label = "Reproducir aleatorio",
            onClick = {
                onShuffle()
                onDismiss()
            }
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        
        var downloadStarted by remember { mutableStateOf(false) }
        
        ContextMenuItem(
            icon = Icons.Rounded.Download,
            label = if (downloadStarted) "Descargando álbum..." else "Descargar álbum",
            onClick = {
                downloadStarted = true
                onDownload()
                onDismiss()
            },
            isLoading = downloadStarted
        )
        ContextMenuItem(
            icon = Icons.Rounded.Person,
            label = "Ir al artista",
            onClick = {
                onGoToArtist()
                onDismiss()
            }
        )
    }
}

@Composable
private fun ContextMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    isLoading: Boolean = false
) {
    DropdownMenuItem(
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        onClick = onClick
    )
}
