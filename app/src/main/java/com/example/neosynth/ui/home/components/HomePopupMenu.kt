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
import androidx.compose.ui.res.stringResource
import com.example.neosynth.R

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration

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
    val context = LocalContext.current
    val config = LocalConfiguration.current

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = offset,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(12.dp))
            .width(220.dp)
    ) {
        CompositionLocalProvider(
            LocalContext provides context,
            LocalConfiguration provides config
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_play_next)) },
                onClick = {
                    onPlayNext()
                    onDismiss()
                },
                leadingIcon = {
                    Icon(Icons.Rounded.QueueMusic, contentDescription = null)
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_add_to_queue)) },
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
                text = { Text(stringResource(R.string.action_go_to_artist)) },
                onClick = {
                    onGoToArtist()
                    onDismiss()
                },
                leadingIcon = {
                    Icon(Icons.Rounded.Person, contentDescription = null)
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_go_to_album)) },
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
}
