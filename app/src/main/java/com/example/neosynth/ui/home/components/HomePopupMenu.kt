package com.example.neosynth.ui.home.components

import androidx.compose.foundation.background
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

@Composable
fun HomePopupMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onGoToArtist: () -> Unit,
    onGoToAlbum: () -> Unit,
    offset: DpOffset = DpOffset(0.dp, 0.dp)
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = offset,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(12.dp))
            .width(220.dp)
    ) {
        DropdownMenuItem(
            text = { Text("Reproducir siguiente") },
            onClick = {
                onPlayNext()
                onDismiss()
            },
            leadingIcon = {
                Icon(Icons.Rounded.QueueMusic, contentDescription = null)
            }
        )
        DropdownMenuItem(
            text = { Text("Agregar a la cola") },
            onClick = {
                onAddToQueue()
                onDismiss()
            },
            leadingIcon = {
                Icon(Icons.Rounded.PlaylistAdd, contentDescription = null)
            }
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        DropdownMenuItem(
            text = { Text("Ir al artista") },
            onClick = {
                onGoToArtist()
                onDismiss()
            },
            leadingIcon = {
                Icon(Icons.Rounded.Person, contentDescription = null)
            }
        )
        DropdownMenuItem(
            text = { Text("Ir al álbum") },
            onClick = {
                onGoToAlbum()
                onDismiss()
            },
            leadingIcon = {
                Icon(Icons.Rounded.Album, contentDescription = null)
            }
        )
    }
}
