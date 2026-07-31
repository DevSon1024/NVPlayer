package com.devson.nvplayer.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun FrameCaptureOverlay(
    currentFrame: Long,
    totalFrames: Long,
    onStepBackward: () -> Unit,
    onStepForward: () -> Unit,
    onSliderScrubbing: (Long) -> Unit,
    onSliderReleased: (Long) -> Unit,
    onCapture: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalFormatted = remember(totalFrames) {
        String.format(Locale.US, "%,d", totalFrames.coerceAtLeast(1L))
    }
    val currentFormatted = remember(currentFrame) {
        String.format(Locale.US, "%,d", currentFrame.coerceAtLeast(0L))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.2f),
                        Color.Black.copy(alpha = 0.6f)
                    )
                )
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Floating Translucent Glass Card
        Surface(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 18.dp)
                .navigationBarsPadding()
                .widthIn(max = 540.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.30f),
                            Color.White.copy(alpha = 0.10f)
                        )
                    ),
                    shape = RoundedCornerShape(28.dp)
                ),
            color = Color(0x730A0A0E), // Translucent dark glass card background
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Top Header Row: Exit Button, Pill Badge & Frame Counter
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Glass Close Button
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f))
                            .border(0.5.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                            .clickable(onClick = onExit),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Exit Frame Capture",
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Center Pill Badge & Frame Info
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "FRAME SURFER",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                letterSpacing = 1.1.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }

                        Text(
                            text = "$currentFormatted / $totalFormatted",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.95f),
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        )
                    }

                    // Symmetric Spacer
                    Spacer(modifier = Modifier.width(36.dp))
                }

                // Middle Scrubber Section: Frame Slider
                val maxRange = totalFrames.coerceAtLeast(1L).toFloat()
                val sliderValue = currentFrame.coerceIn(0L, totalFrames.coerceAtLeast(1L)).toFloat()

                Slider(
                    value = sliderValue,
                    onValueChange = { newVal ->
                        onSliderScrubbing(newVal.toLong())
                    },
                    onValueChangeFinished = {
                        onSliderReleased(sliderValue.toLong())
                    },
                    valueRange = 0f..maxRange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                    )
                )

                // Bottom Control Row: Micro-step Prev, Capture FAB, Micro-step Next
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Step Backward Button with Frame Badge
                    Surface(
                        onClick = onStepBackward,
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ChevronLeft,
                                contentDescription = "Previous Frame",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "-1F",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.85f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Shutter / Capture Camera Button (Prominent Shutter FAB)
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.secondary
                                    )
                                )
                            )
                            .border(
                                width = 2.dp,
                                color = Color.White.copy(alpha = 0.8f),
                                shape = CircleShape
                            )
                            .clickable(onClick = onCapture),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CameraAlt,
                            contentDescription = "Capture Frame",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    // Step Forward Button with Frame Badge
                    Surface(
                        onClick = onStepForward,
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "+1F",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.85f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = "Next Frame",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
