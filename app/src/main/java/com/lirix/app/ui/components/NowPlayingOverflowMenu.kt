package com.lirix.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lirix.app.ui.theme.AppColors

@Composable
fun NowPlayingOverflowMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    isOverlayActive: Boolean,
    onToggleOverlay: () -> Unit,
    onShareClick: () -> Unit,
    onRejectSourceClick: () -> Unit,
    onCalibrateClick: () -> Unit,
    onTeachModeClick: () -> Unit,
    isVinylCollapsed: Boolean,
    onToggleVinyl: () -> Unit,
    modifier: Modifier = Modifier
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier
            .background(AppColors.SurfaceLevel1, RoundedCornerShape(16.dp))
            .border(1.dp, AppColors.BorderSubtle, RoundedCornerShape(16.dp))
    ) {
        DropdownMenuItem(
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Плавающий оверлей",
                        color = if (isOverlayActive) AppColors.CyberCyan else AppColors.TextPrimary,
                        fontSize = 14.sp
                    )
                    if (isOverlayActive) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Активен",
                            tint = AppColors.CyberCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.PictureInPictureAlt,
                    contentDescription = null,
                    tint = if (isOverlayActive) AppColors.CyberCyan else AppColors.TextSecondary
                )
            },
            onClick = {
                onDismissRequest()
                onToggleOverlay()
            }
        )

        DropdownMenuItem(
            text = {
                Text(
                    text = "Поделиться постером",
                    color = AppColors.TextPrimary,
                    fontSize = 14.sp
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    tint = AppColors.HyperViolet
                )
            },
            onClick = {
                onDismissRequest()
                onShareClick()
            }
        )

        DropdownMenuItem(
            text = {
                Text(
                    text = "Сменить источник текста",
                    color = AppColors.ExpenseRed,
                    fontSize = 14.sp
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = AppColors.ExpenseRed
                )
            },
            onClick = {
                onDismissRequest()
                onRejectSourceClick()
            }
        )

        DropdownMenuItem(
            text = {
                Text(
                    text = "Калибровка задержки",
                    color = AppColors.TextPrimary,
                    fontSize = 14.sp
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = AppColors.AmberGold
                )
            },
            onClick = {
                onDismissRequest()
                onCalibrateClick()
            }
        )

        DropdownMenuItem(
            text = {
                Text(
                    text = "Режим обучения (Teach)",
                    color = AppColors.TextPrimary,
                    fontSize = 14.sp
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.School,
                    contentDescription = null,
                    tint = AppColors.HyperViolet
                )
            },
            onClick = {
                onDismissRequest()
                onTeachModeClick()
            }
        )

        DropdownMenuItem(
            text = {
                Text(
                    text = if (isVinylCollapsed) "Показать винил" else "Скрыть винил",
                    color = AppColors.TextPrimary,
                    fontSize = 14.sp
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = if (isVinylCollapsed) Icons.Default.Album else Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = AppColors.CyberCyan
                )
            },
            onClick = {
                onDismissRequest()
                onToggleVinyl()
            }
        )
    }
}

@Composable
fun TimingCalibrationDialog(
    currentOffsetMs: Long,
    onOffsetChange: (Long) -> Unit,
    onResetOffset: () -> Unit,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = "Калибровка тайминга караоке",
                style = MaterialTheme.typography.titleMedium,
                color = AppColors.TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${if (currentOffsetMs >= 0) "+$currentOffsetMs" else currentOffsetMs} мс",
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (currentOffsetMs == 0L) AppColors.CyberCyan else AppColors.AmberGold,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Сдвиг отображения подстрочника относительно звука",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextSecondary
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { onOffsetChange(currentOffsetMs - 50) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.TextPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("-50мс")
                    }
                    OutlinedButton(
                        onClick = { onOffsetChange(currentOffsetMs - 10) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.TextPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("-10мс")
                    }
                    OutlinedButton(
                        onClick = { onOffsetChange(currentOffsetMs + 10) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.TextPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("+10мс")
                    }
                    OutlinedButton(
                        onClick = { onOffsetChange(currentOffsetMs + 50) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.TextPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("+50мс")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(
                    onClick = onResetOffset,
                    colors = ButtonDefaults.textButtonColors(contentColor = AppColors.ExpenseRed)
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Сбросить в 0 мс")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismissRequest,
                colors = ButtonDefaults.textButtonColors(contentColor = AppColors.CyberCyan)
            ) {
                Text("Готово", fontWeight = FontWeight.Bold)
            }
        },
        containerColor = AppColors.SurfaceLevel1,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.border(1.dp, AppColors.BorderSubtle, RoundedCornerShape(20.dp))
    )
}
