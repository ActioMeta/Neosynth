package com.example.neosynth.ui.lyrics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.TextSnippet
import androidx.compose.material3.*
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.neosynth.data.model.LyricsResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsSelectionSheet(
    options: List<LyricsResult>,
    selectedOptionId: String? = null,
    applyingOptionId: String? = null,
    onSelect: (LyricsResult) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var selectedIndex by remember(selectedOptionId, options) {
        mutableIntStateOf(
            when (options.firstOrNull { it.id == selectedOptionId }?.isSynced) {
                false -> 1
                else -> 0
            }
        )
    }

    LaunchedEffect(selectedOptionId, options) {
        selectedIndex = when (options.firstOrNull { it.id == selectedOptionId }?.isSynced) {
            false -> 1
            else -> 0
        }
    }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Selecciona una letra",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )
            
            // Segmented Button Row
            val optionsTitles = listOf("Sincronizadas", "Texto plano")
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 16.dp)
            ) {
                optionsTitles.forEachIndexed { index, label ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = optionsTitles.size),
                        onClick = { selectedIndex = index },
                        selected = index == selectedIndex
                    ) {
                        Text(label)
                    }
                }
            }
            
            val displayedOptions = remember(options, selectedIndex) {
                if (selectedIndex == 0) {
                    options.filter { it.isSynced }
                } else {
                    options.filter { !it.isSynced }
                }
            }
            
            if (displayedOptions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No hay opciones disponibles en esta categoría",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    items(displayedOptions) { option ->
                        LyricsOptionItem(
                            option = option,
                            icon = if (option.isSynced) Icons.Rounded.Lyrics else Icons.Rounded.TextSnippet,
                            isSelected = option.id == selectedOptionId,
                            isApplying = option.id == applyingOptionId,
                            onClick = { onSelect(option) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LyricsOptionItem(
    option: LyricsResult,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    isApplying: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                // Remove redundant info (Netease, parens)
                val cleanTitle = option.source
                    .replace("Netease", "", ignoreCase = true)
                    .replace(Regex("\\(.*\\)"), "") // Remove anything in parentheses
                    .trim()
                    
                Text(
                    text = cleanTitle.ifEmpty { option.source }, // Fallback if empty
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )

                val previewText = option.lyric
                    .lineSequence()
                    .map { it.trim() }
                    .firstOrNull { it.isNotEmpty() && !it.startsWith("[") }
                    ?: if (option.isSynced) "Letra sincronizada" else "Letra en texto plano"

                Text(
                    text = previewText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            when {
                isApplying -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                }
                isSelected -> {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
