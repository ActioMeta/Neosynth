package com.example.neosynth.ui.lyrics

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.neosynth.player.MusicController
import com.example.neosynth.ui.components.AnimatedPlayerSlider
import com.example.neosynth.utils.LrcParser
import kotlinx.coroutines.launch

@Composable
fun LyricsScreen(
    musicController: MusicController,
    lyrics: String?,
    isLoadingLyrics: Boolean,
    lyricsError: String?,
    onClose: () -> Unit
) {
    val currentSong by musicController.currentMediaItem
    val isPlaying by musicController.isPlaying
    val currentPosition by musicController.currentPosition
    
    // Parsear letras
    val parsedLyrics = remember(lyrics) {
        LrcParser.parse(lyrics)
    }
    val hasLyrics = parsedLyrics.isNotEmpty()
    val isLrcFormat = remember(lyrics) {
        LrcParser.isLrcFormat(lyrics)
    }
    
    // Encontrar línea actual (con adelanto de 300ms para mejor sincronización)
    val currentLyricIndex = remember(currentPosition, parsedLyrics, isLrcFormat) {
        if (isLrcFormat) {
            LrcParser.getCurrentLineIndex(parsedLyrics, currentPosition + 300)
        } else {
            -1
        }
    }
    
    // Auto-scroll
    val lyricsListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(currentLyricIndex) {
        if (hasLyrics && isLrcFormat && currentLyricIndex >= 0) {
            scope.launch {
                lyricsListState.animateScrollToItem(
                    index = currentLyricIndex.coerceAtMost((parsedLyrics.size - 1).coerceAtLeast(0)),
                    scrollOffset = -300
                )
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        // Header con botón cerrar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Cerrar",
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Información de la canción
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = currentSong?.mediaMetadata?.title?.toString() ?: "Sin título",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Text(
                text = currentSong?.mediaMetadata?.artist?.toString() ?: "Artista desconocido",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Letras (scrollable)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when {
                isLoadingLyrics -> {
                    // Loading
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(64.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Cargando letras...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                lyricsError != null -> {
                    // Error
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = lyricsError,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                
                hasLyrics -> {
                    // Letras sincronizadas
                    LazyColumn(
                        state = lyricsListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 100.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        itemsIndexed(parsedLyrics) { index, lyricLine ->
                            val isCurrent = isLrcFormat && index == currentLyricIndex
                            val isPast = isLrcFormat && index < currentLyricIndex
                            
                            val scale by animateFloatAsState(
                                targetValue = if (isCurrent) 1.15f else 1f,
                                label = "scale"
                            )
                            
                            Text(
                                text = lyricLine.text,
                                fontSize = when {
                                    isCurrent -> 28.sp
                                    else -> 22.sp
                                },
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                color = when {
                                    isCurrent -> MaterialTheme.colorScheme.primary
                                    isPast -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                },
                                textAlign = TextAlign.Center,
                                lineHeight = 36.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp, horizontal = 16.dp)
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                    }
                            )
                        }
                    }
                }
                
                else -> {
                    // Sin letras
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "🎵",
                            style = MaterialTheme.typography.displayLarge
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No hay letras disponibles",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Controles básicos
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Slider de progreso
            AnimatedPlayerSlider(musicController = musicController)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Botón Play/Pause
            FloatingActionButton(
                onClick = { musicController.togglePlayPause() },
                modifier = Modifier.size(64.dp),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}
