package com.example.neosynth.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Barra de navegación alfabética reutilizable para listas largas.
 * 
 * @param availableLetters Las letras que tienen elementos en la lista
 * @param currentLetter La letra actualmente visible/seleccionada
 * @param onLetterSelected Callback cuando se selecciona una letra
 * @param modifier Modifier para personalizar el componente
 */
@Composable
fun AlphabetScrollbar(
    availableLetters: Set<Char>,
    currentLetter: Char?,
    onLetterSelected: (Char) -> Unit,
    modifier: Modifier = Modifier
) {
    val allLetters = remember { ('A'..'Z').toList() + listOf('#') }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    
    var isDragging by remember { mutableStateOf(false) }
    var draggedLetter by remember { mutableStateOf<Char?>(null) }
    var componentHeight by remember { mutableStateOf(0) }
    
    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.1f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    Box(
        modifier = modifier
            .width(28.dp)
            .onSizeChanged { componentHeight = it.height }
            .scale(scale)
            .pointerInput(availableLetters) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        val letterIndex = (offset.y / componentHeight * allLetters.size)
                            .toInt()
                            .coerceIn(0, allLetters.lastIndex)
                        val letter = allLetters[letterIndex]
                        if (letter in availableLetters && letter != draggedLetter) {
                            draggedLetter = letter
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onLetterSelected(letter)
                        }
                    },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                    onVerticalDrag = { change, _ ->
                        val letterIndex = (change.position.y / componentHeight * allLetters.size)
                            .toInt()
                            .coerceIn(0, allLetters.lastIndex)
                        val letter = allLetters[letterIndex]
                        if (letter in availableLetters && letter != draggedLetter) {
                            draggedLetter = letter
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onLetterSelected(letter)
                        }
                    }
                )
            }
            .pointerInput(availableLetters) {
                detectTapGestures { offset ->
                    val letterIndex = (offset.y / componentHeight * allLetters.size)
                        .toInt()
                        .coerceIn(0, allLetters.lastIndex)
                    val letter = allLetters[letterIndex]
                    if (letter in availableLetters) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onLetterSelected(letter)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            allLetters.forEach { letter ->
                val isAvailable = letter in availableLetters
                val isSelected = letter == currentLetter || letter == draggedLetter
                
                val alpha by animateFloatAsState(
                    targetValue = when {
                        isSelected -> 1f
                        isAvailable -> 0.7f
                        else -> 0.25f
                    },
                    label = "alpha"
                )
                
                val letterScale by animateFloatAsState(
                    targetValue = if (isSelected && isDragging) 1.4f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "letterScale"
                )
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = letter.toString(),
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            isSelected -> MaterialTheme.colorScheme.primary
                            isAvailable -> MaterialTheme.colorScheme.onSurface
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier
                            .alpha(alpha)
                            .scale(letterScale)
                    )
                }
            }
        }
    }
    
    // Popup indicator cuando se arrastra
    if (isDragging && draggedLetter != null) {
        LetterPopup(letter = draggedLetter!!)
    }
}

@Composable
private fun LetterPopup(letter: Char) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = 8.dp
        ) {
            Text(
                text = letter.toString(),
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp)
            )
        }
    }
}
