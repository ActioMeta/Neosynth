package com.example.neosynth.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.neosynth.domain.model.Album

/**
 * Componente Carrusel para mostrar una colección de álbumes de forma desplazable.
 * 
 * Utiliza un LazyRow optimizado para lograr un desplazamiento fluido, suave y con inercia,
 * evitando los saltos bruscos y toscos de los carruseles por defecto.
 */
@Composable
fun Carousel(
    albums: List<Album>,
    title: String? = null,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
    onClick: (Album) -> Unit = {},
    onPlay: (Album) -> Unit = {},
    onShuffle: (Album) -> Unit = {},
    onDownload: (Album) -> Unit = {},
    onGoToArtist: (Album) -> Unit = {},
    onPlayNext: ((Album) -> Unit)? = null,
    onAddToQueue: ((Album) -> Unit)? = null,
    onGoToAlbum: ((Album) -> Unit)? = null,
    itemHeight: Int = 200,
    itemWidth: Int = 180,
    contentPadding: Int = 24,
    itemSpacing: Int = 8
) {
    if (albums.isEmpty()) {
        return
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Mostrar título si se proporciona
        title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(start = contentPadding.dp, bottom = 12.dp),
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(itemSpacing.dp),
            contentPadding = PaddingValues(horizontal = contentPadding.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight.dp)
        ) {
            items(albums, key = { it.id }) { album ->
                Box(modifier = Modifier.width(itemWidth.dp)) {
                    CardItem(
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        album = album,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(itemHeight.dp)
                            .clip(RoundedCornerShape(28.dp)),
                        onClick = { onClick(album) },
                        onPlay = { onPlay(album) },
                        onShuffle = { onShuffle(album) },
                        onDownload = { onDownload(album) },
                        onGoToArtist = { onGoToArtist(album) },
                        onPlayNext = onPlayNext?.let { { it(album) } },
                        onAddToQueue = onAddToQueue?.let { { it(album) } },
                        onGoToAlbum = onGoToAlbum?.let { { it(album) } }
                    )
                }
            }
        }
    }
}

/**
 * Componente Carrusel sin contenedor para mostrar una colección de álbumes
 * en un flujo continuo y altamente fluido.
 */
@Composable
fun UncontainedCarousel(
    albums: List<Album>,
    title: String? = null,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
    onClick: (Album) -> Unit = {},
    onPlay: (Album) -> Unit = {},
    onShuffle: (Album) -> Unit = {},
    onDownload: (Album) -> Unit = {},
    onGoToArtist: (Album) -> Unit = {},
    onPlayNext: ((Album) -> Unit)? = null,
    onAddToQueue: ((Album) -> Unit)? = null,
    onGoToAlbum: ((Album) -> Unit)? = null,
    itemHeight: Int = 160,
    itemWidth: Int = 150,
    contentPadding: Int = 24,
    itemSpacing: Int = 16
) {
    if (albums.isEmpty()) {
        return
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Mostrar título si se proporciona
        title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(start = contentPadding.dp, bottom = 12.dp),
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(itemSpacing.dp),
            contentPadding = PaddingValues(horizontal = contentPadding.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight.dp)
        ) {
            items(albums, key = { it.id }) { album ->
                Box(modifier = Modifier.width(itemWidth.dp)) {
                    CardItem(
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        album = album,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(itemHeight.dp)
                            .clip(RoundedCornerShape(28.dp)),
                        onClick = { onClick(album) },
                        onPlay = { onPlay(album) },
                        onShuffle = { onShuffle(album) },
                        onDownload = { onDownload(album) },
                        onGoToArtist = { onGoToArtist(album) },
                        onPlayNext = onPlayNext?.let { { it(album) } },
                        onAddToQueue = onAddToQueue?.let { { it(album) } },
                        onGoToAlbum = onGoToAlbum?.let { { it(album) } }
                    )
                }
            }
        }
    }
}
