package com.example.neosynth.ui.Settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.example.neosynth.ui.components.SwitchItem
import com.example.neosynth.R

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Header
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, stringResource(R.string.action_back)) }
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        // Grupo de Ajustes: Audio
        SettingSectionTitle("Audio")
        SwitchItem(
            title = stringResource(R.string.settings_high_quality),
            subtitle = stringResource(R.string.settings_high_quality_desc),
            icon = Icons.Rounded.GraphicEq,
            checked = true
        ) { /* Update state */ }

        Divider(modifier = Modifier.padding(horizontal = 16.dp))

        // Grupo de Ajustes: Apariencia
        SettingSectionTitle("Apariencia")
        SwitchItem(
            title = stringResource(R.string.settings_dynamic_theme),
            subtitle = stringResource(R.string.settings_dynamic_theme_desc),
            icon = Icons.Rounded.Palette,
            checked = true
        ) { /* Update state */ }
    }
}

@Composable
fun SettingSectionTitle(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
}