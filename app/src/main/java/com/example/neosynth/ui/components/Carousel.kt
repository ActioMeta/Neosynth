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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.neosynth.domain.model.Album

/**
 * Componente Carrusel para mostrar una colección de álbumes de forma desplazable.
 * 
 * Utiliza Material Design 3 HorizontalMultiBrowseCarousel para mostrar múltiples elementos
 * en un carrusel adaptativo que se ajusta al tamaño de la pantalla.
 *
 * @param albums Lista de álbumes a mostrar en el carrusel
 * @param title Título opcional para mostrar encima del carrusel
 * @param sharedTransitionScope Scope para transiciones compartidas entre pantallas
 * @param animatedVisibilityScope Scope para visibilidad animada
 * @param modifier Modificador para personalizar la apariencia del carrusel
 * @param onClick Callback cuando se hace click en un álbum
 * @param onPlay Callback cuando se presiona el botón de reproducción
 * @param onShuffle Callback cuando se presiona el botón de reproducción aleatoria
 * @param onDownload Callback cuando se presiona el botón de descarga
 * @param onGoToArtist Callback cuando se navega al artista
 * @param onPlayNext Callback cuando se añade a reproducción siguiente
 * @param onAddToQueue Callback cuando se añade a la cola
 * @param onGoToAlbum Callback cuando se navega al álbum
 * @param itemHeight Altura de cada elemento del carrusel (por defecto 200.dp)
 * @param itemWidth Ancho preferido de cada elemento del carrusel (por defecto 180.dp)
 * @param contentPadding Relleno horizontal alrededor del carrusel (por defecto 24.dp)
 * @param itemSpacing Espaciado entre elementos (por defecto 8.dp)
 */
@OptIn(ExperimentalMaterial3Api::class)
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

        // Carrusel Multi-Browse para mostrar múltiples elementos
        val carouselState = rememberCarouselState { albums.size }

        HorizontalMultiBrowseCarousel(
            state = carouselState,
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight.dp)
                .padding(0.dp),
            preferredItemWidth = itemWidth.dp,
            itemSpacing = itemSpacing.dp,
            contentPadding = PaddingValues(horizontal = contentPadding.dp)
        ) { albumIndex ->
            val album = albums[albumIndex]

            CardItem(
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                album = album,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(itemHeight.dp)
                    .maskClip(RoundedCornerShape(28.dp)),
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

/**
 * Componente Carrusel sin contenedor para mostrar una colección de álbumes
 * en un carrusel más libre con elementos de un solo tamaño.
 *
 * Utiliza Material Design 3 HorizontalUncontainedCarousel para mostrar elementos
 * que fluyen más allá del borde de la pantalla.
 *
 * @param albums Lista de álbumes a mostrar en el carrusel
 * @param title Título opcional para mostrar encima del carrusel
 * @param sharedTransitionScope Scope para transiciones compartidas entre pantallas
 * @param animatedVisibilityScope Scope para visibilidad animada
 * @param modifier Modificador para personalizar la apariencia del carrusel
 * @param onClick Callback cuando se hace click en un álbum
 * @param onPlay Callback cuando se presiona el botón de reproducción
 * @param onShuffle Callback cuando se presiona el botón de reproducción aleatoria
 * @param onDownload Callback cuando se presiona el botón de descarga
 * @param onGoToArtist Callback cuando se navega al artista
 * @param onPlayNext Callback cuando se añade a reproducción siguiente
 * @param onAddToQueue Callback cuando se añade a la cola
 * @param onGoToAlbum Callback cuando se navega al álbum
 * @param itemHeight Altura de cada elemento del carrusel (por defecto 160.dp)
 * @param itemWidth Ancho exacto de cada elemento del carrusel (por defecto 150.dp)
 * @param contentPadding Relleno horizontal alrededor del carrusel (por defecto 24.dp)
 * @param itemSpacing Espaciado entre elementos (por defecto 16.dp)
 */
@OptIn(ExperimentalMaterial3Api::class)
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

        // Carrusel sin contenedor para elementos de un solo tamaño
        val carouselState = rememberCarouselState { albums.size }

        androidx.compose.material3.carousel.HorizontalUncontainedCarousel(
            state = carouselState,
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight.dp)
                .padding(0.dp),
            itemWidth = itemWidth.dp,
            itemSpacing = itemSpacing.dp,
            contentPadding = PaddingValues(horizontal = contentPadding.dp)
        ) { albumIndex ->
            val album = albums[albumIndex]
            
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
