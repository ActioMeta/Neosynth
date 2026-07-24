package com.example.neosynth.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import com.example.neosynth.R

data class FabAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit
)

@Composable
fun FabGroup(
    actions: List<FabAction>,
    mainIcon: ImageVector = Icons.Rounded.Add,
    expandedIcon: ImageVector = Icons.Rounded.Close,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    // Animación de rotación del FAB principal (opcional, si el icono lo permite)
    val rotation by animateFloatAsState(
        targetValue = if (expanded && (mainIcon == Icons.Rounded.Add)) 45f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "rotation"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Mini FABs con animación escalonada
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                actions.forEachIndexed { index, action ->
                    FabMenuItem(
                        icon = action.icon,
                        label = action.label,
                        onClick = {
                            action.onClick()
                            expanded = false
                        },
                        delay = index * 50
                    )
                }
            }
        }

        // FAB Principal
        FloatingActionButton(
            onClick = { expanded = !expanded },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = if (expanded) expandedIcon else mainIcon,
                contentDescription = if (expanded) stringResource(R.string.action_close) else stringResource(R.string.content_desc_more_options),
                modifier = if (mainIcon == Icons.Rounded.Add) Modifier.rotate(rotation) else Modifier
            )
        }
    }
}

@Composable
fun FabMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    delay: Int,
    isSelected: Boolean = false
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(delay.toLong())
        visible = true
    }

    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.5f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )
    
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(150),
        label = "alpha"
    )
    
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    
    // Instead of collectIsPressedAsState which can sometimes be tricky to import without the right module extensions
    var isPressed by remember { mutableStateOf(false) }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is androidx.compose.foundation.interaction.PressInteraction.Press -> isPressed = true
                is androidx.compose.foundation.interaction.PressInteraction.Release -> isPressed = false
                is androidx.compose.foundation.interaction.PressInteraction.Cancel -> isPressed = false
            }
        }
    }
    
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "press_scale"
    )

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(24.dp),
        color = if (isSelected) 
            MaterialTheme.colorScheme.primary 
        else 
            MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (isSelected)
            MaterialTheme.colorScheme.onPrimary
        else
            MaterialTheme.colorScheme.onSecondaryContainer,
        shadowElevation = 4.dp,
        modifier = Modifier
            .scale(scale)
            .graphicsLayer(
                alpha = alpha,
                scaleX = pressScale,
                scaleY = pressScale
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
            )
        }
    }
}