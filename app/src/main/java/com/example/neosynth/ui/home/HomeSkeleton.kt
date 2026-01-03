package com.example.neosynth.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

@Composable
fun HomeSkeleton(brush: Brush) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Top Bar con iconos (Library y Settings)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 0.dp)
                .offset(y = (-8).dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icono Library
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(brush)
            )
            // Icono Settings
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(brush)
            )
        }
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .padding(horizontal = 24.dp)
        ) {
            Column(modifier = Modifier.align(Alignment.TopStart)) {
                // "Random"
                Box(modifier = Modifier.size(200.dp, 60.dp).clip(RoundedCornerShape(8.dp)).background(brush))
                // "Mix"
                Box(modifier = Modifier.size(120.dp, 60.dp).clip(RoundedCornerShape(8.dp)).background(brush))
            }

            // Botón shuffle circular
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(brush)
            )

            // Álbumes carousel (3 tarjetas superpuestas)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                repeat(3) { index ->
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .offset(
                                x = if (index == 0) (-60).dp else if (index == 2) 60.dp else 0.dp,
                                y = if (index == 1) 0.dp else 20.dp
                            )
                            .graphicsLayer {
                                rotationZ = if (index == 0) -15f else if (index == 2) 15f else 0f
                            }
                            .clip(RoundedCornerShape(16.dp))
                            .background(brush)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))

        // Título de sección "Álbumes recientes"
        Box(modifier = Modifier.padding(start = 24.dp, bottom = 16.dp).size(180.dp, 24.dp).clip(RoundedCornerShape(4.dp)).background(brush))

        // Lista horizontal de álbumes
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            userScrollEnabled = false
        ) {
            items(5) {
                Box(modifier = Modifier.size(140.dp).clip(RoundedCornerShape(12.dp)).background(brush))
            }
        }
    }
}