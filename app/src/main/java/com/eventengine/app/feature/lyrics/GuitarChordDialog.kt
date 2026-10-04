package com.eventengine.app.feature.lyrics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.eventengine.app.ui.theme.AppColors

@Composable
fun GuitarChordDialog(
    initialChordName: String,
    availableChords: List<String> = emptyList(),
    onDismissRequest: () -> Unit
) {
    var currentChordName by remember(initialChordName) { mutableStateOf(initialChordName) }

    val chord = remember(currentChordName) {
        GuitarChordDictionary.getChord(currentChordName)
    }

    val currentIndex = availableChords.indexOf(currentChordName)
    val hasPrev = currentIndex > 0
    val hasNext = currentIndex in 0 until (availableChords.size - 1)

    Dialog(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(AppColors.SurfaceLevel1)
                .border(1.dp, AppColors.BorderSubtle, RoundedCornerShape(24.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Row: Navigation & Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Аппликатура аккорда",
                    style = MaterialTheme.typography.titleMedium,
                    color = AppColors.TextPrimary
                )

                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(AppColors.SurfaceLevel2)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Закрыть",
                        tint = AppColors.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (chord != null) {
                GuitarFretboardDiagram(
                    chord = chord,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(AppColors.SurfaceLevel2),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Аппликатура для '$currentChordName'\nпока не добавлена",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            // Progression arrows if multiple chords are present in the song
            if (availableChords.size > 1) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (hasPrev) AppColors.SurfaceLevel3 else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable(enabled = hasPrev) {
                                if (hasPrev) {
                                    currentChordName = availableChords[currentIndex - 1]
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "Пред.",
                            tint = if (hasPrev) AppColors.TextPrimary else AppColors.TextTertiary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = if (hasPrev) availableChords[currentIndex - 1] else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (hasPrev) AppColors.TextPrimary else AppColors.TextTertiary
                        )
                    }

                    Text(
                        text = "${currentIndex + 1} из ${availableChords.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.TextSecondary
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (hasNext) AppColors.SurfaceLevel3 else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable(enabled = hasNext) {
                                if (hasNext) {
                                    currentChordName = availableChords[currentIndex + 1]
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (hasNext) availableChords[currentIndex + 1] else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (hasNext) AppColors.TextPrimary else AppColors.TextTertiary
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "След.",
                            tint = if (hasNext) AppColors.TextPrimary else AppColors.TextTertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
