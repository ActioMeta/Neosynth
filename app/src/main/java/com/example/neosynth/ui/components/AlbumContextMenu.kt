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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.example.neosynth.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration


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
    val context = LocalContext.current
    val config = LocalConfiguration.current

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = offset,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            LocalContext provides context,
            LocalConfiguration provides config
        ) {
            ContextMenuItem(
                icon = Icons.Rounded.PlayArrow,
                label = stringResource(R.string.action_play),
                onClick = {
                    onPlay()
                    onDismiss()
                }
            )
            ContextMenuItem(
                icon = Icons.Rounded.Shuffle,
                label = stringResource(R.string.action_shuffle),
                onClick = {
                    onShuffle()
                    onDismiss()
                }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            
            var downloadStarted by remember { mutableStateOf(false) }
            
            ContextMenuItem(
                icon = Icons.Rounded.Download,
                label = if (downloadStarted) stringResource(R.string.download_album_downloading) else stringResource(R.string.action_download_album),
                onClick = {
                    downloadStarted = true
                    onDownload()
                    onDismiss()
                },
                isLoading = downloadStarted
            )
            ContextMenuItem(
                icon = Icons.Rounded.Person,
                label = stringResource(R.string.action_go_to_artist),
                onClick = {
                    onGoToArtist()
                    onDismiss()
                }
            )
        }
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
