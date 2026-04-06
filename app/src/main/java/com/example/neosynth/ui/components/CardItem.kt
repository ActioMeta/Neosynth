package com.example.neosynth.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.neosynth.domain.model.Album

@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun CardItem(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    album: Album,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onPlay: () -> Unit = {},
    onShuffle: () -> Unit = {},
    onDownload: () -> Unit = {},
    onGoToArtist: () -> Unit = {},
    // Home Context Menu Actions (Optional)
    onPlayNext: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    onGoToAlbum: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }
    val cardShape = RoundedCornerShape(28.dp)

    Surface(
        modifier = modifier,
        shape = cardShape,
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        if (onLongClick != null) {
                            onLongClick()
                        } else {
                            showMenu = true
                        }
                    }
                )
        ) {
            with(sharedTransitionScope) {
                AsyncImage(
                    model = album.coverArtUrl,
                    contentDescription = album.name,
                    modifier = Modifier
                        .sharedElement(
                            sharedContentState = rememberSharedContentState(key = "cover_${album.id}"),
                            animatedVisibilityScope = animatedVisibilityScope
                        )
                        .fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f)
                            ),
                            startY = 140f
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            ) {
                Text(
                    text = album.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = album.artistName,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Menú contextual
            if (onPlayNext != null && onAddToQueue != null && onGoToAlbum != null) {
                com.example.neosynth.ui.home.components.HomePopupMenu(
                    expanded = showMenu,
                    onDismiss = { showMenu = false },
                    onPlayNext = onPlayNext,
                    onAddToQueue = onAddToQueue,
                    onGoToArtist = onGoToArtist,
                    onGoToAlbum = onGoToAlbum,
                    offset = DpOffset(0.dp, 0.dp)
                )
            } else {
                AlbumContextMenu(
                    expanded = showMenu,
                    onDismiss = { showMenu = false },
                    onPlay = onPlay,
                    onShuffle = onShuffle,
                    onDownload = onDownload,
                    onGoToArtist = onGoToArtist,
                    offset = DpOffset(0.dp, 0.dp)
                )
            }
        }
    }
}
