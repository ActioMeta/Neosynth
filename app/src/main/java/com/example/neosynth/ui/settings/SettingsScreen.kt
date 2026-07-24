package com.example.neosynth.ui.settings

import android.content.Intent
import android.net.Uri
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.neosynth.R
import com.example.neosynth.data.preferences.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val serverInfo by viewModel.serverInfo.collectAsStateWithLifecycle()
    val allServers by viewModel.allServers.collectAsStateWithLifecycle()
    val cacheSize by viewModel.cacheSize.collectAsStateWithLifecycle()
    val downloadedCount by viewModel.downloadedCount.collectAsStateWithLifecycle()
    val audioSettings by viewModel.audioSettings.collectAsStateWithLifecycle()
    val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()
    
    val currentConfiguration = androidx.compose.ui.platform.LocalConfiguration.current
    val currentLocaleTag = remember(currentConfiguration) {
        val appLocales = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(android.app.LocaleManager::class.java)?.applicationLocales?.toLanguageTags()
        } else {
            androidx.appcompat.app.AppCompatDelegate.getApplicationLocales().toLanguageTags()
        }
        
        if (!appLocales.isNullOrEmpty()) {
            appLocales
        } else {
            currentConfiguration.locales.get(0).language
        }
    }
    val currentLanguageStr = remember(currentLocaleTag) {
        when {
            currentLocaleTag.startsWith("es") -> context.getString(R.string.lang_spanish)
            currentLocaleTag.startsWith("en") -> context.getString(R.string.lang_english)
            else -> {
                val sysLang = java.util.Locale.getDefault().displayLanguage.replaceFirstChar { it.uppercase() }
                context.getString(R.string.lang_system_with_name, sysLang)
            }
        }
    }
    
    var showQualityDialog by remember { mutableStateOf<QualityDialogType?>(null) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showPaletteDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showServerDialog by remember { mutableStateOf(false) }
    var showServersListDialog by remember { mutableStateOf(false) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var showEditServerDialog by remember { mutableStateOf(false) }
    var showGeminiKeyDialog by remember { mutableStateOf(false) }
    var serverToEdit by remember { mutableStateOf<com.example.neosynth.data.local.entities.ServerEntity?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadSettings()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        stringResource(R.string.settings_title),
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
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
            // 1. Server Section
            item {
                SettingsSection(title = stringResource(R.string.server_title)) {
                    SettingsCard {
                        SettingsClickableItem(
                            icon = Icons.Rounded.Dns,
                            title = stringResource(R.string.server_manage),
                            subtitle = stringResource(R.string.settings_servers_count, allServers.size),
                            onClick = { showServersListDialog = true }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsItem(
                            icon = Icons.Rounded.CheckCircle,
                            title = stringResource(R.string.server_active),
                            subtitle = serverInfo?.url ?: stringResource(R.string.server_not_connected)
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsItem(
                            icon = Icons.Rounded.Person,
                            title = stringResource(R.string.server_username),
                            subtitle = serverInfo?.username ?: "-"
                        )
                    }
                }
            }

            // 2. Appearance Section
            item {
                SettingsSection(title = stringResource(R.string.settings_appearance)) {
                    SettingsCard {
                        SettingsClickableItem(
                            icon = Icons.Rounded.Palette,
                            title = stringResource(R.string.settings_theme),
                            subtitle = getThemeLabel(appSettings.themeMode),
                            onClick = { showThemeDialog = true }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsClickableItem(
                            icon = Icons.Rounded.ColorLens,
                            title = "Paleta de colores",
                            subtitle = when (appSettings.colorPalette) {
                                AppColorPalette.MATERIAL_YOU -> "Material You (Dinámico)"
                                AppColorPalette.NEOSYNTH -> "NeoSynth (Púrpura suave / Lila)"
                                AppColorPalette.TOKYO_NIGHT -> "Tokyo Night (Azul Slate / Pastel)"
                            },
                            onClick = { showPaletteDialog = true }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsClickableItem(
                            icon = Icons.Rounded.Language,
                            title = stringResource(R.string.settings_language),
                            subtitle = currentLanguageStr,
                            onClick = { showLanguageDialog = true }
                        )
                    }
                }
            }

            // 3. Playback & Audio Section
            item {
                SettingsSection(title = stringResource(R.string.settings_playback)) {
                    SettingsCard {
                        SettingsSwitchItem(
                            icon = Icons.Rounded.GraphicEq,
                            title = stringResource(R.string.settings_playback_fade_in),
                            subtitle = stringResource(R.string.settings_playback_fade_in_desc),
                            checked = audioSettings.crossfadeEnabled,
                            onCheckedChange = { viewModel.updateCrossfadeEnabled(it) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsSwitchItem(
                            icon = Icons.Rounded.Headphones,
                            title = stringResource(R.string.settings_playback_crossfeed),
                            subtitle = stringResource(R.string.settings_playback_crossfeed_desc),
                            checked = audioSettings.crossfeedEnabled,
                            onCheckedChange = { viewModel.updateCrossfeedEnabled(it) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsSwitchItem(
                            icon = Icons.Rounded.VolumeUp,
                            title = stringResource(R.string.settings_playback_normalize),
                            subtitle = stringResource(R.string.settings_playback_normalize_desc),
                            checked = audioSettings.normalizeVolume,
                            onCheckedChange = { viewModel.updateNormalizeVolume(it) }
                        )
                    }
                }
            }

            // 4. Streaming Quality Section
            item {
                SettingsSection(title = stringResource(R.string.settings_audio_streaming)) {
                    SettingsCard {
                        SettingsClickableItem(
                            icon = Icons.Rounded.Wifi,
                            title = stringResource(R.string.settings_stream_wifi),
                            subtitle = getStreamQualityLabel(audioSettings.streamWifiQuality),
                            onClick = { showQualityDialog = QualityDialogType.STREAM_WIFI }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsClickableItem(
                            icon = Icons.Rounded.SignalCellularAlt,
                            title = stringResource(R.string.settings_stream_mobile),
                            subtitle = getStreamQualityLabel(audioSettings.streamMobileQuality),
                            onClick = { showQualityDialog = QualityDialogType.STREAM_MOBILE }
                        )
                    }
                }
            }

            // 5. Download Quality Section
            item {
                SettingsSection(title = stringResource(R.string.settings_audio_download)) {
                    SettingsCard {
                        SettingsClickableItem(
                            icon = Icons.Rounded.Wifi,
                            title = stringResource(R.string.settings_download_wifi),
                            subtitle = getDownloadQualityLabel(audioSettings.downloadWifiQuality),
                            onClick = { showQualityDialog = QualityDialogType.DOWNLOAD_WIFI }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsClickableItem(
                            icon = Icons.Rounded.SignalCellularAlt,
                            title = stringResource(R.string.settings_download_mobile),
                            subtitle = getDownloadQualityLabel(audioSettings.downloadMobileQuality),
                            onClick = { showQualityDialog = QualityDialogType.DOWNLOAD_MOBILE }
                        )
                    }
                }
            }

            // 6. Storage Section
            item {
                SettingsSection(title = stringResource(R.string.settings_storage)) {
                    SettingsCard {
                        SettingsItem(
                            icon = Icons.Rounded.Download,
                            title = stringResource(R.string.settings_downloaded_songs),
                            subtitle = stringResource(R.string.library_songs_count, downloadedCount)
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsItem(
                            icon = Icons.Rounded.Storage,
                            title = stringResource(R.string.settings_image_cache),
                            subtitle = cacheSize
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsClickableItem(
                            icon = Icons.Rounded.DeleteSweep,
                            title = stringResource(R.string.settings_clear_cache),
                            subtitle = stringResource(R.string.settings_clear_cache_desc),
                            onClick = { viewModel.clearCache() }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsClickableItem(
                            icon = Icons.Rounded.DeleteForever,
                            title = stringResource(R.string.settings_delete_all_downloads),
                            subtitle = stringResource(R.string.settings_delete_all_downloads_desc),
                            onClick = { showDeleteAllDialog = true },
                            iconTint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // 7. External Services & IA Section
            item {
                SettingsSection(title = stringResource(R.string.settings_services)) {
                    SettingsCard {
                        SettingsClickableItem(
                            icon = Icons.Rounded.VpnKey,
                            title = stringResource(R.string.settings_gemini_title),
                            subtitle = if (appSettings.geminiApiKey.isNotBlank()) stringResource(R.string.settings_gemini_configured) else stringResource(R.string.settings_gemini_not_configured),
                            onClick = { showGeminiKeyDialog = true }
                        )
                    }
                }
            }

            // 8. About Section
            item {
                SettingsSection(title = stringResource(R.string.settings_about)) {
                    SettingsCard {
                        SettingsItem(
                            icon = Icons.Rounded.Info,
                            title = stringResource(R.string.app_name),
                            subtitle = stringResource(R.string.settings_version_label, "2.2.0")
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsClickableItem(
                            icon = Icons.Rounded.Code,
                            title = stringResource(R.string.settings_source_code),
                            subtitle = "github.com/ActioMeta/NeoSynth",
                            onClick = {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://github.com/ActioMeta/NeoSynth")
                                )
                                context.startActivity(intent)
                            }
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
                    title = stringResource(R.string.settings_stream_wifi),
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
                    title = stringResource(R.string.quality_stream_mobile),
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
                    title = stringResource(R.string.settings_download_wifi),
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
                    title = stringResource(R.string.quality_download_mobile),
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

    if (showPaletteDialog) {
        PalettePickerDialog(
            currentPalette = appSettings.colorPalette,
            onPaletteSelected = {
                viewModel.updateColorPalette(it)
                showPaletteDialog = false
            },
            onDismiss = { showPaletteDialog = false }
        )
    }
    
    if (showLanguageDialog) {
        LanguagePickerDialog(
            currentLanguageTag = currentLocaleTag,
            onLanguageSelected = { tag ->
                viewModel.updateLanguage(tag)
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false }
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
            onEditServer = { server -> 
                showServersListDialog = false
                serverToEdit = server
                showEditServerDialog = true
            },
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
                Text(stringResource(R.string.settings_delete_all_downloads))
            },
            text = {
                Text(stringResource(R.string.settings_delete_all_downloads_desc))
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
                    Text(stringResource(R.string.settings_delete_all_downloads))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
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
    
    if (showEditServerDialog && serverToEdit != null) {
        EditServerDialog(
            server = serverToEdit!!,
            onDismiss = { 
                showEditServerDialog = false
                serverToEdit = null
            },
            onServerUpdated = { updatedServer ->
                viewModel.updateServer(updatedServer)
                showEditServerDialog = false
                serverToEdit = null
            }
        )
    }
    
    if (showGeminiKeyDialog) {
        GeminiApiKeyDialog(
            currentKey = appSettings.geminiApiKey,
            onKeySaved = { 
                viewModel.updateGeminiApiKey(it)
                showGeminiKeyDialog = false 
            },
            onDismiss = { showGeminiKeyDialog = false }
        )
    }
}

private enum class QualityDialogType {
    STREAM_WIFI, STREAM_MOBILE, DOWNLOAD_WIFI, DOWNLOAD_MOBILE
}

@Composable
private fun getStreamQualityLabel(quality: StreamQuality): String {
    return when (quality) {
        StreamQuality.LOW -> stringResource(R.string.quality_low) + " (128 kbps Opus)"
        StreamQuality.MEDIUM -> stringResource(R.string.quality_medium) + " (192 kbps MP3)"
        StreamQuality.HIGH -> stringResource(R.string.quality_high) + " (256 kbps AAC)"
        StreamQuality.VERY_HIGH -> stringResource(R.string.quality_max_mp3)
        StreamQuality.LOSSLESS -> stringResource(R.string.quality_lossless_desc)
    }
}

@Composable
private fun getDownloadQualityLabel(quality: DownloadQuality): String {
    return when (quality) {
        DownloadQuality.LOW -> stringResource(R.string.quality_low) + " (128 kbps Opus)"
        DownloadQuality.MEDIUM -> stringResource(R.string.quality_medium) + " (192 kbps MP3)"
        DownloadQuality.HIGH -> stringResource(R.string.quality_high) + " (256 kbps AAC)"
        DownloadQuality.VERY_HIGH -> stringResource(R.string.quality_max_mp3)
        DownloadQuality.LOSSLESS -> stringResource(R.string.quality_lossless_desc)
    }
}

@Composable
private fun getThemeLabel(theme: ThemeMode): String {
    return when (theme) {
        ThemeMode.LIGHT -> stringResource(R.string.theme_light)
        ThemeMode.DARK -> stringResource(R.string.theme_dark)
        ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
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
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f)
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
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0f),
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

@Composable
private fun GeminiApiKeyDialog(
    currentKey: String,
    onKeySaved: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(currentKey) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_gemini_title), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.gemini_dialog_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.gemini_api_key_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onKeySaved(text.trim()) }) {
                Text(stringResource(R.string.action_save))
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
private fun LanguagePickerDialog(
    currentLanguageTag: String,
    onLanguageSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_language), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onLanguageSelected("es") }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = currentLanguageTag.startsWith("es"),
                        onClick = { onLanguageSelected("es") }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(stringResource(R.string.lang_spanish), style = MaterialTheme.typography.bodyLarge)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onLanguageSelected("en") }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = currentLanguageTag.startsWith("en"),
                        onClick = { onLanguageSelected("en") }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(stringResource(R.string.lang_english), style = MaterialTheme.typography.bodyLarge)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
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
                                    StreamQuality.LOW -> stringResource(R.string.quality_low)
                                    StreamQuality.MEDIUM -> stringResource(R.string.quality_medium)
                                    StreamQuality.HIGH -> stringResource(R.string.quality_high)
                                    StreamQuality.VERY_HIGH -> stringResource(R.string.quality_very_high)
                                    StreamQuality.LOSSLESS -> stringResource(R.string.quality_lossless)
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (quality == currentQuality) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                text = when (quality) {
                                    StreamQuality.LOW -> stringResource(R.string.quality_low_desc_stream)
                                    StreamQuality.MEDIUM -> stringResource(R.string.quality_medium_desc_stream)
                                    StreamQuality.HIGH -> stringResource(R.string.quality_high_desc_stream)
                                    StreamQuality.VERY_HIGH -> stringResource(R.string.quality_very_high_desc_stream)
                                    StreamQuality.LOSSLESS -> stringResource(R.string.quality_lossless_desc_stream)
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
                Text(stringResource(R.string.action_cancel))
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
                                    DownloadQuality.LOW -> stringResource(R.string.quality_low)
                                    DownloadQuality.MEDIUM -> stringResource(R.string.quality_medium)
                                    DownloadQuality.HIGH -> stringResource(R.string.quality_high)
                                    DownloadQuality.VERY_HIGH -> stringResource(R.string.quality_very_high)
                                    DownloadQuality.LOSSLESS -> stringResource(R.string.quality_lossless)
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (quality == currentQuality) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                text = when (quality) {
                                    DownloadQuality.LOW -> stringResource(R.string.quality_low_desc_download)
                                    DownloadQuality.MEDIUM -> stringResource(R.string.quality_medium_desc_download)
                                    DownloadQuality.HIGH -> stringResource(R.string.quality_high_desc_download)
                                    DownloadQuality.VERY_HIGH -> stringResource(R.string.quality_very_high_desc_download)
                                    DownloadQuality.LOSSLESS -> stringResource(R.string.quality_lossless_desc_download)
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
                Text(stringResource(R.string.action_cancel))
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
                Text(stringResource(R.string.action_cancel))
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
            Text(stringResource(R.string.settings_theme), fontWeight = FontWeight.Bold)
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
                                    ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                                    ThemeMode.DARK -> stringResource(R.string.theme_dark)
                                    ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (theme == currentTheme) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                text = when (theme) {
                                    ThemeMode.LIGHT -> stringResource(R.string.theme_light_desc)
                                    ThemeMode.DARK -> stringResource(R.string.theme_dark_desc)
                                    ThemeMode.SYSTEM -> stringResource(R.string.theme_system_desc)
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
                Text(stringResource(R.string.action_close))
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
            Text(stringResource(R.string.settings_servers), fontWeight = FontWeight.Bold)
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
                                text = stringResource(R.string.no_servers),
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
                                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f)
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
                                        text = server.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (server.id == activeServerId) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${server.url} • ${server.username}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { onEditServer(server) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Edit,
                                            contentDescription = stringResource(R.string.action_edit),
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    if (servers.size > 1) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        IconButton(
                                            onClick = { onDeleteServer(server.id) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Delete,
                                                contentDescription = stringResource(R.string.action_delete),
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
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
                    Text(stringResource(R.string.server_add_title))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        }
    )
}

@Composable
private fun PalettePickerDialog(
    currentPalette: AppColorPalette,
    onPaletteSelected: (AppColorPalette) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ColorLens,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("Paleta de Colores", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    AppColorPalette.MATERIAL_YOU to ("Material You" to "Colores dinámicos del sistema (Android 12+)"),
                    AppColorPalette.NEOSYNTH to ("NeoSynth" to "Púrpura real elegante con fondo negro profundo"),
                    AppColorPalette.TOKYO_NIGHT to ("Tokyo Night" to "Fondo slate oscuro con azul pastel y cian neón")
                ).forEach { (palette, info) ->
                    val isSelected = palette == currentPalette
                    Surface(
                        onClick = { onPaletteSelected(palette) },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { onPaletteSelected(palette) }
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = info.first,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = info.second,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
