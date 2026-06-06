package com.example.neosynth.ui.lyrics

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ManageSearch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import com.example.neosynth.player.MusicController
import com.example.neosynth.ui.components.AnimatedPlayerSlider
import com.example.neosynth.utils.LrcParser
import androidx.compose.ui.res.stringResource
import com.example.neosynth.R
import kotlinx.coroutines.launch
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
fun LyricsScreen(
    musicController: MusicController,
    lyrics: String?,
    isLoadingLyrics: Boolean,
    isLoadingOptions: Boolean = false,
    lyricsError: String?,
    lyricsOptions: List<com.example.neosynth.data.model.LyricsResult> = emptyList(),
    selectedLyricsOption: com.example.neosynth.data.model.LyricsResult? = null,
    onSelectOption: (com.example.neosynth.data.model.LyricsResult) -> Unit = {},
    onOpenOptions: () -> Unit = {},
    onEditLyrics: () -> Unit = {},
    onClose: () -> Unit
) {
    val currentSong by musicController.currentMediaItem
    val isPlaying by musicController.isPlaying
    val currentPosition by musicController.currentPosition
    
    // Extract dominant color for Fade effect
    val dominantColorInt by musicController.dominantColorInt
    val dominantColor = Color(dominantColorInt)
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingSelectionId by remember { mutableStateOf<String?>(null) }
    var lastAnnouncedSelectionId by remember { mutableStateOf<String?>(null) }

    // Parsear letras
    val parsedLyrics = remember(lyrics, selectedLyricsOption?.id) {
        LrcParser.parse(lyrics)
    }
    val isLrcFormat = remember(lyrics, selectedLyricsOption?.id) {
        LrcParser.isLrcFormat(lyrics)
    }
    val hasLyrics = parsedLyrics.isNotEmpty()
    // Para letras de texto plano (sin timestamps) — se muestran directamente línea a línea
    val plainTextLines = remember(lyrics, isLrcFormat) {
        if (!isLrcFormat && !lyrics.isNullOrBlank()) {
            lyrics.lines().map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            emptyList()
        }
    }
    val hasPlainLyrics = plainTextLines.isNotEmpty()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(selectedLyricsOption?.id) {
        val selectedId = selectedLyricsOption?.id ?: return@LaunchedEffect
        if (pendingSelectionId == selectedId) {
            pendingSelectionId = null
        }
        if (lastAnnouncedSelectionId != null && lastAnnouncedSelectionId != selectedId) {
            snackbarHostState.showSnackbar(
                message = context.getString(R.string.lyrics_showing_source, selectedLyricsOption.source)
            )
        }
        lastAnnouncedSelectionId = selectedId
    }
    
    // Encontrar línea actual (con adelanto de 300ms para mejor sincronización)
    val currentLyricIndex = remember(currentPosition, parsedLyrics, isLrcFormat) {
        if (isLrcFormat) {
            LrcParser.getCurrentLineIndex(parsedLyrics, currentPosition + 300)
        } else {
            -1
        }
    }
    
    // Índice manual temporal para reflejar taps inmediatamente,
    // incluso si el callback de posición del player tarda en confirmarse.
    var manualSelectedIndex by remember { mutableStateOf<Int?>(null) }
    val effectiveLyricIndex = manualSelectedIndex ?: currentLyricIndex

    LaunchedEffect(currentLyricIndex, manualSelectedIndex) {
        val selected = manualSelectedIndex
        if (selected != null && selected == currentLyricIndex) {
            manualSelectedIndex = null
        }
    }

    LaunchedEffect(manualSelectedIndex, currentPosition, parsedLyrics) {
        val selected = manualSelectedIndex ?: return@LaunchedEffect
        val selectedTime = parsedLyrics.getOrNull(selected)?.timeMs ?: return@LaunchedEffect
        if (abs(currentPosition - selectedTime) > 2500) {
            // Si el seek real terminó lejos del destino, no mantener highlight manual.
            manualSelectedIndex = null
        }
    }
    
    // Auto-scroll — suspended for 3s after a manual seek to avoid race condition
    val lyricsListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var userSeeked by remember { mutableStateOf(false) }
    
    LaunchedEffect(userSeeked) {
        if (userSeeked) {
            delay(3000)
            userSeeked = false
        }
    }
    
    LaunchedEffect(currentLyricIndex) {
        if (!userSeeked && hasLyrics && isLrcFormat && currentLyricIndex >= 0) {
            scope.launch {
                lyricsListState.animateScrollToItem(
                    index = currentLyricIndex.coerceAtMost((parsedLyrics.size - 1).coerceAtLeast(0)),
                    scrollOffset = -300
                )
            }
        }
    }

    // Animación suave del color de fondo
    val animatedColor by animateColorAsState(
        targetValue = dominantColor,
        animationSpec = tween(durationMillis = 1000),
        label = "bg_color_anim"
    )

    // Immersive Mode
    var showUi by remember { mutableStateOf(true) }
    
    // Auto-hide UI timer
    LaunchedEffect(showUi, isPlaying) {
        if (showUi && isPlaying) {
            delay(5000)
            showUi = false
        }
    }

    var showOptionsSheet by remember { mutableStateOf(false) }
    
    // Animation for layout smoothing
    val headerSpacerHeight by animateDpAsState(
        targetValue = if (showUi) 8.dp else 48.dp,
        animationSpec = tween(durationMillis = 600),
        label = "header_spacer"
    )

    val fadeBrush = remember {
        Brush.verticalGradient(
            0f to Color.Transparent,
            0.15f to Color.Black,
            0.85f to Color.Black,
            1f to Color.Transparent
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Dynamic Background (Color sólido animado)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            animatedColor.copy(alpha = 0.6f),
                            MaterialTheme.colorScheme.background, // Negro/Oscuro abajo
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .animateContentSize(animationSpec = tween(durationMillis = 600))
                // Child clickable handlers win over this in Compose's gesture arena
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { showUi = !showUi }
        ) {
            // Header con botón cerrar
            AnimatedVisibility(
                visible = showUi,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (lyricsOptions.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                onOpenOptions()
                                showOptionsSheet = true
                            }
                        ) {
                            if (isLoadingOptions) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.ManageSearch,
                                    contentDescription = stringResource(R.string.lyrics_web_options),
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                    IconButton(onClick = onEditLyrics) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = stringResource(R.string.lyrics_edit),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.action_close),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(headerSpacerHeight))
            
        // Información de la canción
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start // Alineación Start dinámica
            ) {
                Text(
                    text = currentSong?.mediaMetadata?.title?.toString() ?: stringResource(R.string.unknown_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = currentSong?.mediaMetadata?.artist?.toString() ?: stringResource(R.string.unknown_artist),
                    style = MaterialTheme.typography.bodyMedium,
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
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    .drawWithContent {
                        drawContent()
                        drawRect(brush = fadeBrush, blendMode = BlendMode.DstIn)
                    }
            ) {
                    when {
                        isLoadingLyrics -> {
                            val shimmerBrush = com.example.neosynth.ui.components.rememberShimmerBrush()
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 100.dp, horizontal = 16.dp),
                                horizontalAlignment = Alignment.Start,
                                verticalArrangement = Arrangement.spacedBy(28.dp)
                            ) {
                                val lineHeights = listOf(32.dp, 24.dp, 24.dp, 24.dp, 24.dp, 24.dp, 24.dp)
                                val lineFractions = listOf(0.7f, 0.9f, 0.8f, 0.6f, 0.85f, 0.4f, 0.75f)

                                for (i in lineHeights.indices) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(lineFractions[i])
                                            .height(lineHeights[i])
                                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                            .background(shimmerBrush)
                                    )
                                }
                            }
                        }

                        lyricsError != null -> {
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
                            LazyColumn(
                                state = lyricsListState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(vertical = 100.dp, horizontal = 16.dp),
                                horizontalAlignment = Alignment.Start
                            ) {
                                itemsIndexed(parsedLyrics, key = { index, _ -> index }) { index, lyricLine ->
                                    val isCurrent = isLrcFormat && index == effectiveLyricIndex
                                    val isPast = isLrcFormat && index < effectiveLyricIndex

                                    val targetAlpha = when {
                                        isCurrent -> 1f
                                        isPast -> 0.4f
                                        else -> 0.3f
                                    }
                                    val targetScale = if (isCurrent) 1.05f else 1f

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = 56.dp)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                if (isLrcFormat && index != currentLyricIndex) {
                                                    manualSelectedIndex = index
                                                    userSeeked = true
                                                    musicController.seekTo(lyricLine.timeMs)
                                                }
                                                showUi = true
                                            },
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(
                                            text = lyricLine.text,
                                            fontSize = if (isCurrent) 28.sp else 22.sp,
                                            fontFamily = FontFamily.SansSerif,
                                            fontWeight = if (isCurrent) FontWeight.Black else FontWeight.Bold,
                                            color = if (isCurrent) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurface,
                                            textAlign = TextAlign.Start,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 10.dp)
                                                .graphicsLayer {
                                                    scaleX = targetScale
                                                    scaleY = targetScale
                                                    alpha = targetAlpha
                                                }
                                        )
                                    }
                                }
                            }
                        }

                        else -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "🎵",
                                    style = MaterialTheme.typography.displayLarge,
                                    modifier = Modifier.clearAndSetSemantics { 
                                        contentDescription = context.getString(R.string.content_desc_music_note) 
                                    }
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = stringResource(R.string.lyrics_no_lyrics),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
        // Controles básicos
        AnimatedVisibility(
            visible = showUi,
            enter = fadeIn() + androidx.compose.animation.slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + androidx.compose.animation.slideOutVertically(targetOffsetY = { it })
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
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
                    androidx.compose.animation.AnimatedContent(
                        targetState = isPlaying,
                        label = "play_pause_lyrics"
                    ) { playing ->
                        Icon(
                            imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(R.string.play_pause),
                            tint = Color.Black,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
    }
    
    // Bottom Sheet for options
    if (showOptionsSheet) {
        LyricsSelectionSheet(
            options = lyricsOptions,
            selectedOptionId = selectedLyricsOption?.id,
            applyingOptionId = pendingSelectionId,
            onSelect = { 
                pendingSelectionId = it.id
                onSelectOption(it)
                showOptionsSheet = false
            },
            onDismiss = { showOptionsSheet = false }
        )
    }

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp)
    )
}
