package com.example.neosynth.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.neosynth.data.preferences.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val serverInfo by viewModel.serverInfo.collectAsState()
    val allServers by viewModel.allServers.collectAsState()
    val cacheSize by viewModel.cacheSize.collectAsState()
    val downloadedCount by viewModel.downloadedCount.collectAsState()
    val audioSettings by viewModel.audioSettings.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState()
    
    var showQualityDialog by remember { mutableStateOf<QualityDialogType?>(null) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showServerDialog by remember { mutableStateOf(false) }
    var showServersListDialog by remember { mutableStateOf(false) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadSettings()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Configuración",
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 180.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Server Section
            item {
                SettingsSection(title = "Servidor") {
                    SettingsCard {
                        SettingsClickableItem(
                            icon = Icons.Rounded.Dns,
                            title = "Gestionar servidores",
                            subtitle = "${allServers.size} servidor(es) configurado(s)",
                            onClick = { showServersListDialog = true }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsItem(
                            icon = Icons.Rounded.CheckCircle,
                            title = "Servidor activo",
                            subtitle = serverInfo?.url ?: "No conectado"
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsItem(
                            icon = Icons.Rounded.Person,
                            title = "Usuario",
                            subtitle = serverInfo?.username ?: "-"
                        )
                    }
                }
            }

            // Storage Section
            item {
                SettingsSection(title = "Almacenamiento") {
                    SettingsCard {
                        SettingsItem(
                            icon = Icons.Rounded.Download,
                            title = "Canciones descargadas",
                            subtitle = "$downloadedCount canciones"
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsItem(
                            icon = Icons.Rounded.Storage,
                            title = "Caché de imágenes",
                            subtitle = cacheSize
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsClickableItem(
                            icon = Icons.Rounded.DeleteSweep,
                            title = "Limpiar caché",
                            subtitle = "Eliminar archivos temporales",
                            onClick = { viewModel.clearCache() }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsClickableItem(
                            icon = Icons.Rounded.DeleteForever,
                            title = "Eliminar todas las descargas",
                            subtitle = "Borrar canciones y cover arts descargados",
                            onClick = { showDeleteAllDialog = true },
                            iconTint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Playback Section
            item {
                SettingsSection(title = "Reproducción") {
                    SettingsCard {
                        SettingsSwitchItem(
                            icon = Icons.Rounded.GraphicEq,
                            title = "Crossfade",
                            subtitle = "Transición suave entre canciones",
                            checked = audioSettings.crossfadeEnabled,
                            onCheckedChange = { viewModel.updateCrossfadeEnabled(it) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsSwitchItem(
                            icon = Icons.Rounded.VolumeUp,
                            title = "Normalizar volumen",
                            subtitle = "Igualar volumen entre canciones",
                            checked = audioSettings.normalizeVolume,
                            onCheckedChange = { viewModel.updateNormalizeVolume(it) }
                        )
                    }
                }
            }

            // Quality Section
            item {
                SettingsSection(title = "Calidad de audio - Streaming") {
                    SettingsCard {
                        SettingsClickableItem(
                            icon = Icons.Rounded.Wifi,
                            title = "Streaming por WiFi",
                            subtitle = getStreamQualityLabel(audioSettings.streamWifiQuality),
                            onClick = { showQualityDialog = QualityDialogType.STREAM_WIFI }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsClickableItem(
                            icon = Icons.Rounded.SignalCellularAlt,
                            title = "Streaming por datos móviles",
                            subtitle = getStreamQualityLabel(audioSettings.streamMobileQuality),
                            onClick = { showQualityDialog = QualityDialogType.STREAM_MOBILE }
                        )
                    }
                }
            }

            // Download Quality Section
            item {
                SettingsSection(title = "Calidad de audio - Descarga") {
                    SettingsCard {
                        SettingsClickableItem(
                            icon = Icons.Rounded.Wifi,
                            title = "Descarga por WiFi",
                            subtitle = getDownloadQualityLabel(audioSettings.downloadWifiQuality),
                            onClick = { showQualityDialog = QualityDialogType.DOWNLOAD_WIFI }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsClickableItem(
                            icon = Icons.Rounded.SignalCellularAlt,
                            title = "Descarga por datos móviles",
                            subtitle = getDownloadQualityLabel(audioSettings.downloadMobileQuality),
                            onClick = { showQualityDialog = QualityDialogType.DOWNLOAD_MOBILE }
                        )
                    }
                }
            }

            // Appearance Section
            item {
                SettingsSection(title = "Apariencia") {
                    SettingsCard {
                        SettingsClickableItem(
                            icon = Icons.Rounded.Palette,
                            title = "Tema",
                            subtitle = getThemeLabel(appSettings.themeMode),
                            onClick = { showThemeDialog = true }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsSwitchItem(
                            icon = Icons.Rounded.ColorLens,
                            title = "Colores dinámicos",
                            subtitle = "Adaptar colores a la carátula",
                            checked = appSettings.dynamicColors,
                            onCheckedChange = { viewModel.updateDynamicColors(it) }
                        )
                    }
                }
            }

            // About Section
            item {
                SettingsSection(title = "Acerca de") {
                    SettingsCard {
                        SettingsItem(
                            icon = Icons.Rounded.Info,
                            title = "NeoSynth",
                            subtitle = "Versión 1.0.0"
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsClickableItem(
                            icon = Icons.Rounded.Code,
                            title = "Código fuente",
                            subtitle = "github.com/ActioMeta/NeoSynth",
                            onClick = { /* TODO: Open browser */ }
                        )
                    }
                }
            }
        }
    }
    
    // Dialogs
    showQualityDialog?.let { type ->
        when (type) {
            QualityDialogType.STREAM_WIFI -> {
                StreamQualityPickerDialog(
                    title = "Streaming por WiFi",
                    currentQuality = audioSettings.streamWifiQuality,
                    onQualitySelected = { quality ->
                        viewModel.updateStreamWifiQuality(quality)
                        showQualityDialog = null
                    },
                    onDismiss = { showQualityDialog = null }
                )
            }
            QualityDialogType.STREAM_MOBILE -> {
                StreamQualityPickerDialog(
                    title = "Streaming por datos móviles",
                    currentQuality = audioSettings.streamMobileQuality,
                    onQualitySelected = { quality ->
                        viewModel.updateStreamMobileQuality(quality)
                        showQualityDialog = null
                    },
                    onDismiss = { showQualityDialog = null }
                )
            }
            QualityDialogType.DOWNLOAD_WIFI -> {
                DownloadQualityPickerDialog(
                    title = "Descarga por WiFi",
                    currentQuality = audioSettings.downloadWifiQuality,
                    onQualitySelected = { quality ->
                        viewModel.updateDownloadWifiQuality(quality)
                        showQualityDialog = null
                    },
                    onDismiss = { showQualityDialog = null }
                )
            }
            QualityDialogType.DOWNLOAD_MOBILE -> {
                DownloadQualityPickerDialog(
                    title = "Descarga por datos móviles",
                    currentQuality = audioSettings.downloadMobileQuality,
                    onQualitySelected = { quality ->
                        viewModel.updateDownloadMobileQuality(quality)
                        showQualityDialog = null
                    },
                    onDismiss = { showQualityDialog = null }
                )
            }
        }
    }
    
    if (showThemeDialog) {
        ThemePickerDialog(
            currentTheme = appSettings.themeMode,
            onThemeSelected = { 
                viewModel.updateThemeMode(it)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }
    
    if (showServersListDialog) {
        ServersListDialog(
            servers = allServers,
            activeServerId = serverInfo?.id,
            onServerSelected = { viewModel.setActiveServer(it) },
            onAddServer = { 
                showServersListDialog = false
                showServerDialog = true 
            },
            onEditServer = { /* TODO */ },
            onDeleteServer = { viewModel.deleteServer(it) },
            onDismiss = { showServersListDialog = false }
        )
    }
    
    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text("Eliminar todas las descargas")
            },
            text = {
                Text("Se eliminarán todas las canciones y cover arts descargados. Esta acción no se puede deshacer.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAllDownloads()
                        showDeleteAllDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Eliminar todo")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
    
    if (showServerDialog) {
        AddServerDialog(
            onDismiss = { showServerDialog = false },
            onServerAdded = { server ->
                viewModel.addServer(server)
                showServerDialog = false
            }
        )
    }
}

private enum class QualityDialogType {
    STREAM_WIFI, STREAM_MOBILE, DOWNLOAD_WIFI, DOWNLOAD_MOBILE
}

private fun getStreamQualityLabel(quality: StreamQuality): String {
    return when (quality) {
        StreamQuality.LOW -> "Baja (128 kbps MP3)"
        StreamQuality.MEDIUM -> "Media (192 kbps MP3)"
        StreamQuality.HIGH -> "Alta (256 kbps MP3)"
        StreamQuality.VERY_HIGH -> "Muy alta (320 kbps MP3)"
        StreamQuality.LOSSLESS -> "Sin pérdida (Original)"
    }
}

private fun getDownloadQualityLabel(quality: DownloadQuality): String {
    return when (quality) {
        DownloadQuality.LOW -> "Baja (128 kbps MP3)"
        DownloadQuality.MEDIUM -> "Media (192 kbps MP3)"
        DownloadQuality.HIGH -> "Alta (256 kbps MP3)"
        DownloadQuality.VERY_HIGH -> "Muy alta (320 kbps MP3)"
        DownloadQuality.LOSSLESS -> "Sin pérdida (Original)"
    }
}

private fun getQualityLabel(quality: StreamQuality): String {
    return when (quality) {
        StreamQuality.LOW -> "Baja (128 kbps MP3)"
        StreamQuality.MEDIUM -> "Media (192 kbps MP3)"
        StreamQuality.HIGH -> "Alta (256 kbps MP3)"
        StreamQuality.VERY_HIGH -> "Muy alta (320 kbps MP3)"
        StreamQuality.LOSSLESS -> "Sin pérdida (Original)"
    }
}

private fun getThemeLabel(theme: ThemeMode): String {
    return when (theme) {
        ThemeMode.LIGHT -> "Claro"
        ThemeMode.DARK -> "Oscuro"
        ThemeMode.SYSTEM -> "Sistema"
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
        )
        content()
    }
}

@Composable
private fun SettingsCard(
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsClickableItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        ),
        label = "settings_item_scale"
    )
    
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0f),
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val thumbScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (checked) 1f else 0.85f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        ),
        label = "switch_thumb_scale"
    )
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.graphicsLayer {
                scaleX = thumbScale
                scaleY = thumbScale
            }
        )
    }
}

// Stream Quality Picker Dialog
@Composable
private fun StreamQualityPickerDialog(
    title: String,
    currentQuality: StreamQuality,
    onQualitySelected: (StreamQuality) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = title, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                StreamQuality.values().forEach { quality ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onQualitySelected(quality) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = quality == currentQuality,
                            onClick = { onQualitySelected(quality) }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = when (quality) {
                                    StreamQuality.LOW -> "Baja"
                                    StreamQuality.MEDIUM -> "Media"
                                    StreamQuality.HIGH -> "Alta"
                                    StreamQuality.VERY_HIGH -> "Muy alta"
                                    StreamQuality.LOSSLESS -> "Sin pérdida"
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (quality == currentQuality) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                text = when (quality) {
                                    StreamQuality.LOW -> "128 kbps MP3 - Ahorro de datos"
                                    StreamQuality.MEDIUM -> "192 kbps MP3 - Equilibrado"
                                    StreamQuality.HIGH -> "256 kbps MP3 - Calidad alta"
                                    StreamQuality.VERY_HIGH -> "320 kbps MP3 - Máxima calidad MP3"
                                    StreamQuality.LOSSLESS -> "Original - Sin transcodificar"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

// Download Quality Picker Dialog
@Composable
private fun DownloadQualityPickerDialog(
    title: String,
    currentQuality: DownloadQuality,
    onQualitySelected: (DownloadQuality) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = title, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                DownloadQuality.values().forEach { quality ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onQualitySelected(quality) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = quality == currentQuality,
                            onClick = { onQualitySelected(quality) }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = when (quality) {
                                    DownloadQuality.LOW -> "Baja"
                                    DownloadQuality.MEDIUM -> "Media"
                                    DownloadQuality.HIGH -> "Alta"
                                    DownloadQuality.VERY_HIGH -> "Muy alta"
                                    DownloadQuality.LOSSLESS -> "Sin pérdida"
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (quality == currentQuality) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                text = when (quality) {
                                    DownloadQuality.LOW -> "128 kbps MP3 - Ahorro de espacio"
                                    DownloadQuality.MEDIUM -> "192 kbps MP3 - Equilibrado"
                                    DownloadQuality.HIGH -> "256 kbps MP3 - Calidad alta"
                                    DownloadQuality.VERY_HIGH -> "320 kbps MP3 - Máxima calidad MP3"
                                    DownloadQuality.LOSSLESS -> "Original - Sin transcodificar"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

// OLD Quality Picker Dialog (deprecated - can be removed)
@Composable
private fun QualityPickerDialog(
    type: QualityDialogType,
    currentQuality: StreamQuality,
    onQualitySelected: (StreamQuality) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = when (type) {
                    QualityDialogType.STREAM_WIFI -> "Calidad de streaming en WiFi"
                    QualityDialogType.STREAM_MOBILE -> "Calidad de streaming en datos móviles"
                    QualityDialogType.DOWNLOAD_WIFI -> "Calidad de descarga en WiFi"
                    QualityDialogType.DOWNLOAD_MOBILE -> "Calidad de descarga en datos móviles"
                },
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                StreamQuality.values().forEach { quality ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onQualitySelected(quality) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = quality == currentQuality,
                            onClick = { onQualitySelected(quality) }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = when (quality) {
                                    StreamQuality.LOW -> "Baja"
                                    StreamQuality.MEDIUM -> "Media"
                                    StreamQuality.HIGH -> "Alta"
                                    StreamQuality.VERY_HIGH -> "Muy alta"
                                    StreamQuality.LOSSLESS -> "Sin pérdida"
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (quality == currentQuality) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                text = when (quality) {
                                    StreamQuality.LOW -> "128 kbps MP3 - Ahorro de datos"
                                    StreamQuality.MEDIUM -> "192 kbps MP3 - Equilibrado"
                                    StreamQuality.HIGH -> "256 kbps MP3 - Calidad alta"
                                    StreamQuality.VERY_HIGH -> "320 kbps MP3 - Máxima calidad MP3"
                                    StreamQuality.LOSSLESS -> "Original - Sin transcodificar"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

// Theme Picker Dialog
@Composable
private fun ThemePickerDialog(
    currentTheme: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Tema", fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                ThemeMode.values().forEach { theme ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onThemeSelected(theme) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = theme == currentTheme,
                            onClick = { onThemeSelected(theme) }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = when (theme) {
                                    ThemeMode.LIGHT -> "Claro"
                                    ThemeMode.DARK -> "Oscuro"
                                    ThemeMode.SYSTEM -> "Sistema"
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (theme == currentTheme) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                text = when (theme) {
                                    ThemeMode.LIGHT -> "Siempre tema claro"
                                    ThemeMode.DARK -> "Siempre tema oscuro"
                                    ThemeMode.SYSTEM -> "Seguir configuración del sistema"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}

// Servers List Dialog
@Composable
private fun ServersListDialog(
    servers: List<com.example.neosynth.data.local.entities.ServerEntity>,
    activeServerId: Long?,
    onServerSelected: (Long) -> Unit,
    onAddServer: () -> Unit,
    onEditServer: (com.example.neosynth.data.local.entities.ServerEntity) -> Unit,
    onDeleteServer: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Servidores", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (servers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Rounded.CloudOff,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No hay servidores",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    servers.forEach { server ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = if (server.id == activeServerId) 
                                MaterialTheme.colorScheme.primaryContainer 
                            else 
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { if (server.id != activeServerId) onServerSelected(server.id) }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (server.id == activeServerId) 
                                        Icons.Rounded.CheckCircle 
                                    else 
                                        Icons.Rounded.Circle,
                                    contentDescription = null,
                                    tint = if (server.id == activeServerId)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = server.url,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (server.id == activeServerId) FontWeight.Bold else FontWeight.Normal
                                    )
                                    Text(
                                        text = server.username,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (servers.size > 1) {
                                    IconButton(
                                        onClick = { onDeleteServer(server.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Delete,
                                            contentDescription = "Eliminar",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = onAddServer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Rounded.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Agregar servidor")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}
