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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    val currentMediaItem by musicController.currentMediaItem
    val trackTitle = currentMediaItem?.mediaMetadata?.title?.toString() ?: "Desconocido"
    val artistName = currentMediaItem?.mediaMetadata?.artist?.toString() ?: "Desconocido"
    val albumName = currentMediaItem?.mediaMetadata?.albumTitle?.toString() ?: "Desconocido"
    
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
    
    val rawLyrics by viewModel.rawLyrics.collectAsState()
    val parsedLines by viewModel.parsedLines.collectAsState()
    val isPublishing by viewModel.isPublishing.collectAsState()
    val publishStatus by viewModel.publishStatus.collectAsState()
    val appSettings by viewModel.geminiApiKey.collectAsState(initial = com.example.neosynth.data.preferences.AppSettings())

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
                title = { Text("Editor de Letras", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.publishLyrics(trackTitle, artistName, albumName, durationSeconds) },
                        enabled = !isPublishing && rawLyrics.isNotBlank()
                    ) {
                        Icon(Icons.Rounded.CloudUpload, contentDescription = "Publicar a LRCLib")
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
                .padding(padding) // Includes SystemBars and NavigationBars padding automatically 
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            
            // Header Info
            Text(trackTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$artistName • $albumName", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 3 Import Options
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Icon(Icons.Rounded.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Archivo", maxLines = 1)
                }
                
                Button(
                    onClick = { viewModel.generateWithGemini(trackTitle, artistName) },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Gemini", maxLines = 1)
                }
                
                Button(
                    onClick = { showTextInputDialog = true },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Icon(Icons.Rounded.EditNote, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Texto", maxLines = 1)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Lyrics List lines
            if (parsedLines.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("Importa letras para empezar a sincronizar.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        LyricLineItem(
                            line = line,
                            isCurrentLine = index == currentEditorLineIndex,
                            onPlayClick = {
                                line.timeMs?.let { exoPlayer.seekTo(it) }
                            },
                            onTextClick = {
                                editingLineIndex = index
                            },
                            onTimerClick = {
                                viewModel.updateLineTime(index, currentLocalPosition)
                            }
                        )
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
    onTimerClick: () -> Unit
) {
    val isSynced = line.timeMs != null
    
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentLine)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface,
        ),
        border = when {
            isCurrentLine -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
            isSynced -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
            else -> BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
        },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
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
                    contentDescription = "Saltar a este tiempo",
                    tint = if (isSynced) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
            }
            
            // 2. Text Content (Clickable)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onTextClick)
                    .padding(horizontal = 8.dp)
            ) {
                if (isSynced) {
                    val min = line.timeMs!! / 60000
                    val sec = (line.timeMs % 60000) / 1000
                    val mil = (line.timeMs % 1000) / 10
                    Text(
                        text = String.format("[%02d:%02d.%02d]", min, sec, mil),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
                Text(
                    text = line.text.ifEmpty { "(Línea en blanco)" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            // 3. Timer Icon
            IconButton(
                onClick = onTimerClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Rounded.Timer, 
                    contentDescription = "Asignar tiempo actual", 
                    tint = if (isSynced) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                )
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
        title = { Text("Pegar Letras") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().height(300.dp),
                placeholder = { Text("Pega las letras aquí...") }
            )
        },
        confirmButton = {
            Button(onClick = { onSave(text) }) {
                Text("Aplicar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
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
        title = { Text("Editar Línea") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Letra") }
                )
                OutlinedTextField(
                    value = timeStr,
                    onValueChange = { timeStr = it },
                    label = { Text("Tiempo (mm:ss.xx)") },
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
                Text("Guardar")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text("Eliminar")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancelar")
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
        Column(modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp)) {
            // Slider
            Slider(
                value = currentPosition.toFloat(),
                onValueChange = { exoPlayer.seekTo(it.toLong()) },
                valueRange = 0f..duration.toFloat(),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Playback Controls
                IconButton(onClick = { exoPlayer.seekTo((currentPosition - 5000).coerceAtLeast(0)) }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.FastRewind, contentDescription = "-5s")
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                FloatingActionButton(
                    onClick = { 
                        if (isPlaying) exoPlayer.pause() else exoPlayer.play() 
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape,
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = "Play/Pause",
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                IconButton(onClick = { exoPlayer.seekTo((currentPosition + 5000).coerceAtMost(duration)) }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.FastForward, contentDescription = "+5s")
                }
            }
            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}
