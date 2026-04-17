package com.example.neosynth.ui.lyrics

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.neosynth.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.neosynth.player.MusicController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsEditorScreen(
    musicController: MusicController,
    onBack: () -> Unit,
    viewModel: LyricsEditorViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val currentMediaItem by musicController.currentMediaItem
    val trackTitle = currentMediaItem?.mediaMetadata?.title?.toString() ?: stringResource(R.string.unknown_title)
    val artistName = currentMediaItem?.mediaMetadata?.artist?.toString() ?: stringResource(R.string.unknown_artist)
    val albumName = currentMediaItem?.mediaMetadata?.albumTitle?.toString() ?: stringResource(R.string.unknown_album)
    
    // Estado del reproductor local aislado
    val exoPlayer = remember { androidx.media3.exoplayer.ExoPlayer.Builder(context).build() }
    var isLocalPlaying by remember { mutableStateOf(false) }
    var currentLocalPosition by remember { mutableStateOf(0L) }
    var localDuration by remember { mutableStateOf(1L) } // Evitar división por cero
    val durationSeconds = (localDuration / 1000).toInt()

    DisposableEffect(Unit) {
        // Pausar reproductor global al entrar para no mezclar audios
        if (musicController.isPlaying.value) {
            musicController.togglePlayPause()
        }
        
        // Cargar canción actual en reproductor local
        currentMediaItem?.let {
            exoPlayer.setMediaItem(it)
            exoPlayer.prepare()
        }

        val listener = object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isLocalPlaying = playing
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == androidx.media3.common.Player.STATE_READY) {
                    localDuration = exoPlayer.duration.coerceAtLeast(1L)
                }
            }
        }
        exoPlayer.addListener(listener)
        
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(isLocalPlaying) {
        while(isLocalPlaying) {
            currentLocalPosition = exoPlayer.currentPosition
            kotlinx.coroutines.delay(50) // Alta frecuencia para precisión de sincronización
        }
    }
    
    val rawLyrics by viewModel.rawLyrics.collectAsStateWithLifecycle()
    val parsedLines by viewModel.parsedLines.collectAsStateWithLifecycle()
    val isPublishing by viewModel.isPublishing.collectAsStateWithLifecycle()
    val publishStatus by viewModel.publishStatus.collectAsStateWithLifecycle()
    val appSettings by viewModel.geminiApiKey.collectAsStateWithLifecycle(initialValue = com.example.neosynth.data.preferences.AppSettings())

    // UI States
    var showTextInputDialog by remember { mutableStateOf(false) }
    var editingLineIndex by remember { mutableStateOf<Int?>(null) }

    // File Picker
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { inputStream ->
                    val content = inputStream.bufferedReader().readText()
                    viewModel.importFromFile(content)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    
    LaunchedEffect(publishStatus) {
        publishStatus?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatus()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            trackTitle, 
                            style = MaterialTheme.typography.titleMedium, 
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "$artistName • $albumName", 
                            style = MaterialTheme.typography.labelSmall, 
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (isPublishing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(
                            onClick = { viewModel.publishLyrics(trackTitle, artistName, albumName, durationSeconds) },
                            enabled = rawLyrics.isNotBlank()
                        ) {
                            Icon(
                                Icons.Rounded.CloudUpload, 
                                contentDescription = stringResource(R.string.lyrics_publish_lrclib),
                                tint = if (rawLyrics.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            LyricsEditorBottomBar(
                exoPlayer = exoPlayer,
                isPlaying = isLocalPlaying,
                currentPosition = currentLocalPosition,
                duration = localDuration
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding) 
                .padding(horizontal = 16.dp)
        ) {
            // Import Tools Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                    label = { Text(stringResource(R.string.lyrics_file), style = MaterialTheme.typography.labelMedium) },
                    leadingIcon = { Icon(Icons.Rounded.FileOpen, null, Modifier.size(18.dp)) },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                )
                
                AssistChip(
                    onClick = { viewModel.generateWithGemini(trackTitle, artistName) },
                    label = { Text("Gemini AI", style = MaterialTheme.typography.labelMedium) },
                    leadingIcon = { Icon(Icons.Rounded.AutoFixHigh, null, Modifier.size(18.dp)) },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                )
                
                AssistChip(
                    onClick = { showTextInputDialog = true },
                    label = { Text(stringResource(R.string.lyrics_text), style = MaterialTheme.typography.labelMedium) },
                    leadingIcon = { Icon(Icons.Rounded.EditNote, null, Modifier.size(18.dp)) },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                )
            }
            
            HorizontalDivider(modifier = Modifier.alpha(0.2f))
            Spacer(modifier = Modifier.height(8.dp))
            
            // Lyrics List lines
            if (parsedLines.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.lyrics_import_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                // Current line index based on local player position
                val currentEditorLineIndex = remember(currentLocalPosition, parsedLines) {
                    parsedLines.indexOfLast { it.timeMs != null && it.timeMs <= currentLocalPosition }
                }
                val editorListState = androidx.compose.foundation.lazy.rememberLazyListState()
                
                LaunchedEffect(currentEditorLineIndex) {
                    if (currentEditorLineIndex >= 0) {
                        editorListState.animateScrollToItem(
                            index = currentEditorLineIndex.coerceAtMost(parsedLines.size - 1),
                            scrollOffset = -200
                        )
                    }
                }
                
                LazyColumn(
                    state = editorListState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(bottom = 100.dp, top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(parsedLines) { index, line ->
                        val isCurrentLine = index == currentEditorLineIndex
                        
                        // Predictive progress for current line
                        var progress by remember { mutableStateOf(0f) }
                        if (isCurrentLine) {
                            val nextLineTime = parsedLines.getOrNull(index + 1)?.timeMs
                            if (line.timeMs != null && nextLineTime != null) {
                                val total = (nextLineTime - line.timeMs).toFloat()
                                val current = (currentLocalPosition - line.timeMs).toFloat()
                                progress = (current / total).coerceIn(0f, 1f)
                            }
                        }

                        Column(modifier = Modifier.fillMaxWidth()) {
                            LyricLineItem(
                                line = line,
                                isCurrentLine = isCurrentLine,
                                onPlayClick = {
                                    line.timeMs?.let { exoPlayer.seekTo(it) }
                                },
                                onTextClick = {
                                    editingLineIndex = index
                                },
                                onTimerClick = {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    viewModel.updateLineTime(index, currentLocalPosition)
                                },
                                onAdjustTime = { delta ->
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    val currentTime = line.timeMs ?: 0L
                                    viewModel.updateLineTime(index, (currentTime + delta).coerceAtLeast(0L))
                                }
                            )
                            
                            if (isCurrentLine && progress > 0f && progress < 1f) {
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp)
                                        .height(2.dp)
                                        .clip(RoundedCornerShape(1.dp)),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = Color.Transparent
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTextInputDialog) {
        TextInputDialog(
            initialText = rawLyrics,
            onDismiss = { showTextInputDialog = false },
            onSave = { 
                viewModel.updateRawLyrics(it)
                showTextInputDialog = false 
            }
        )
    }
    
    if (editingLineIndex != null) {
        val index = editingLineIndex!!
        val line = parsedLines.getOrNull(index)
        if (line != null) {
            EditLineDialog(
                line = line,
                onDismiss = { editingLineIndex = null },
                onSave = { newText, newTimeMs ->
                    viewModel.updateLine(index, newText, newTimeMs)
                    editingLineIndex = null
                },
                onDelete = {
                    viewModel.removeLine(index)
                    editingLineIndex = null
                }
            )
        } else {
            editingLineIndex = null
        }
    }
}

@Composable
fun LyricLineItem(
    line: LyricLine,
    isCurrentLine: Boolean = false,
    onPlayClick: () -> Unit,
    onTextClick: () -> Unit,
    onTimerClick: () -> Unit,
    onAdjustTime: (Long) -> Unit = {}
) {
    val isSynced = line.timeMs != null
    val contentAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isCurrentLine) 1f else 0.6f, label = "alpha"
    )
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isCurrentLine) 1.02f else 1f, label = "scale"
    )
    
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentLine)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            else
                MaterialTheme.colorScheme.surface,
        ),
        border = when {
            isCurrentLine -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            isSynced -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = contentAlpha
            }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = if (isCurrentLine && isSynced) 4.dp else 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Play Icon
                IconButton(
                    onClick = onPlayClick,
                    enabled = isSynced,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Rounded.PlayArrow, 
                        contentDescription = stringResource(R.string.lyrics_jump_time),
                        tint = if (isSynced) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                }
                
                // 2. Text Content (Clickable)
                Text(
                    text = line.text.ifEmpty { "(Línea en blanco)" },
                    style = if (isCurrentLine) MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onTextClick)
                        .padding(horizontal = 8.dp)
                )
                
                // 3. Timer and Time Display Column
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = onTimerClick,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Timer, 
                            contentDescription = stringResource(R.string.lyrics_assign_time), 
                            tint = if (isCurrentLine) MaterialTheme.colorScheme.primary else if (isSynced) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    if (isSynced) {
                        val min = line.timeMs!! / 60000
                        val sec = (line.timeMs % 60000) / 1000
                        val mil = (line.timeMs % 1000) / 10
                        Text(
                            text = String.format("%02d:%02d.%02d", min, sec, mil),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }
            }

            // 4. Fine-tuning Controls with Icons
            if (isSynced && isCurrentLine) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp, start = 48.dp, end = 48.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onAdjustTime(-100L) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Rounded.FastRewind, 
                            contentDescription = "-100ms", 
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                    }
                    
                    Text(
                        "100ms", 
                        style = MaterialTheme.typography.labelSmall, 
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    
                    IconButton(
                        onClick = { onAdjustTime(100L) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Rounded.FastForward, 
                            contentDescription = "+100ms", 
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TextInputDialog(
    initialText: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialText) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lyrics_editor_pasted)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().height(300.dp),
                placeholder = { Text(stringResource(R.string.lyrics_editor_paste_hint)) }
            )
        },
        confirmButton = {
            Button(onClick = { onSave(text) }) {
                Text(stringResource(R.string.lyrics_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
fun EditLineDialog(
    line: LyricLine,
    onDismiss: () -> Unit,
    onSave: (String, Long?) -> Unit,
    onDelete: () -> Unit
) {
    var text by remember { mutableStateOf(line.text) }
    var timeStr by remember { 
        mutableStateOf(
            if (line.timeMs != null) {
                val min = line.timeMs / 60000
                val sec = (line.timeMs % 60000) / 1000
                val mil = (line.timeMs % 1000) / 10
                String.format("%02d:%02d.%02d", min, sec, mil)
            } else ""
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lyrics_edit_line)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.lyrics_lyric)) }
                )
                OutlinedTextField(
                    value = timeStr,
                    onValueChange = { timeStr = it },
                    label = { Text(stringResource(R.string.lyrics_time_format)) },
                    placeholder = { Text("00:00.00") }
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val parsedTime = try {
                    if (timeStr.isNotBlank()) {
                        val match = Regex("""^(\d{1,2}):(\d{2})(?:\.(\d{2,3}))?$""").find(timeStr.trim())
                        if (match != null) {
                            val min = match.groupValues[1].toLong()
                            val sec = match.groupValues[2].toLong()
                            val millisStr = match.groupValues.getOrNull(3)?.takeIf { it.isNotEmpty() } ?: "00"
                            val mil = if (millisStr.length == 2) millisStr.toLong() * 10 else millisStr.toLong()
                            min * 60000 + sec * 1000 + mil
                        } else line.timeMs
                    } else null
                } catch(e: Exception) { line.timeMs }
                
                onSave(text, parsedTime)
            }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text(stringResource(R.string.action_delete))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    )
}

@Composable
fun LyricsEditorBottomBar(
    exoPlayer: androidx.media3.exoplayer.ExoPlayer,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(bottom = 8.dp, top = 4.dp, start = 16.dp, end = 16.dp)) {
            // Slider
            Slider(
                value = currentPosition.toFloat(),
                onValueChange = { exoPlayer.seekTo(it.toLong()) },
                valueRange = 0f..duration.toFloat(),
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left side: Current Time Monitor (Styled like total duration)
                val min = currentPosition / 60000
                val sec = (currentPosition % 60000) / 1000
                val mil = (currentPosition % 1000) / 10
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = String.format("%02d:%02d.%02d", min, sec, mil),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // Center: Playback Controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(onClick = { exoPlayer.seekTo((currentPosition - 5000).coerceAtLeast(0)) }) {
                        Icon(Icons.Rounded.FastRewind, contentDescription = stringResource(R.string.lyrics_minus_5s), tint = MaterialTheme.colorScheme.primary)
                    }
                    
                    FloatingActionButton(
                        onClick = { 
                            if (isPlaying) exoPlayer.pause() else exoPlayer.play() 
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = CircleShape,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(R.string.play_pause),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    
                    IconButton(onClick = { exoPlayer.seekTo((currentPosition + 5000).coerceAtMost(duration)) }) {
                        Icon(Icons.Rounded.FastForward, contentDescription = stringResource(R.string.lyrics_plus_5s), tint = MaterialTheme.colorScheme.primary)
                    }
                }

                // Right side: Total Duration / Spacer
                val dMin = duration / 60000
                val dSec = (duration % 60000) / 1000
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = String.format("%02d:%02d", dMin, dSec),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}

