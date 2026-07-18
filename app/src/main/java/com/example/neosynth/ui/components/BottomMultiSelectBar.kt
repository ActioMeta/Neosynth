package com.example.neosynth.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.neosynth.R
import androidx.compose.ui.text.style.TextAlign
import com.example.neosynth.ui.stats.rememberBounceScale

object SelectionModeState {
    var isSelectionModeActive = mutableStateOf(false)
}

/**
 * Reusable Bottom Multi-Select Bar with 3 main buttons:
 * 1. Quit selection (Icon: Close)
 * 2. Play selected (Icon: PlayArrow with count)
 * 3. More options Menu (Icon: Menu) -> opens a ModalBottomSheet with a Grid of actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomMultiSelectBar(
    visible: Boolean,
    selectedCount: Int,
    onClearSelection: () -> Unit,
    onPlaySelected: () -> Unit,
    menuActions: List<MultiSelectAction>,
    modifier: Modifier = Modifier
) {
    var showMenuSheet by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        ),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        ),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Button 1: Quit selection
            val interactionSource = remember { MutableInteractionSource() }
            val scale by rememberBounceScale(interactionSource)

            IconButton(
                onClick = onClearSelection,
                interactionSource = interactionSource,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.action_close),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Button 2: Play selected (with count)
            val playInteraction = remember { MutableInteractionSource() }
            val playScale by rememberBounceScale(playInteraction)

            Button(
                onClick = onPlaySelected,
                interactionSource = playInteraction,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .height(56.dp)
                    .graphicsLayer {
                        scaleX = playScale
                        scaleY = playScale
                    }
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(R.string.action_play)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$selectedCount",
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            // Button 3: Menu for other actions (with hamburger icon)
            val menuInteraction = remember { MutableInteractionSource() }
            val menuScale by rememberBounceScale(menuInteraction)

            IconButton(
                onClick = { showMenuSheet = true },
                interactionSource = menuInteraction,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f))
                    .graphicsLayer {
                        scaleX = menuScale
                        scaleY = menuScale
                    }
            ) {
                Icon(
                    imageVector = Icons.Rounded.Menu,
                    contentDescription = stringResource(R.string.action_menu),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }

    // Modal Bottom Sheet displaying extra actions in a Grid
    if (showMenuSheet) {
        val sheetState = rememberModalBottomSheetState()

        ModalBottomSheet(
            onDismissRequest = { showMenuSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.action_menu),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                val rows = remember(menuActions) { menuActions.chunked(2) }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rows.forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowItems.forEach { action ->
                                val itemInteraction = remember { MutableInteractionSource() }
                                val itemScale by rememberBounceScale(itemInteraction)

                                Surface(
                                    onClick = {
                                        action.onClick()
                                        showMenuSheet = false
                                    },
                                    interactionSource = itemInteraction,
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(88.dp)
                                        .graphicsLayer {
                                            scaleX = itemScale
                                            scaleY = itemScale
                                        }
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = action.icon,
                                            contentDescription = action.label,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = action.label,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(horizontal = 8.dp)
                                        )
                                    }
                                }
                            }
                            if (rowItems.size < 2) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}
