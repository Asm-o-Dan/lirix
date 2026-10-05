package com.lirix.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.lirix.app.feature.share.ShareCardFormat
import com.lirix.app.ui.theme.AppColors
import kotlinx.coroutines.launch

/**
 * AMOLED modal dialog allowing the user to select the export format
 * (Stories 9:16 or Square 1:1) and triggers generation with a loading indicator.
 *
 * Spec: TASK-SHR-01 / .sdd/tasks/TASK-SHR-01.md
 */
@Composable
fun ShareFormatDialog(
    onDismissRequest: () -> Unit,
    onFormatSelected: suspend (ShareCardFormat) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var isGenerating by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = { if (!isGenerating) onDismissRequest() }) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = AppColors.SurfaceLevel2,
            border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Поделиться карточкой",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = AppColors.TextPrimary
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Выберите формат для экспорта инфографики",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextSecondary
                )

                Spacer(modifier = Modifier.height(18.dp))

                if (isGenerating) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = AppColors.CyberCyan,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Генерация карточки...",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.TextSecondary
                            )
                        }
                    }
                } else {
                    FormatOptionCard(
                        title = "📱 Истории (9:16)",
                        subtitle = "Идеально для Telegram Stories, Instagram, VK",
                        accentColor = AppColors.CyberCyan,
                        onClick = {
                            isGenerating = true
                            coroutineScope.launch {
                                try {
                                    onFormatSelected(ShareCardFormat.STORIES_9_16)
                                    onDismissRequest()
                                } finally {
                                    isGenerating = false
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    FormatOptionCard(
                        title = "🖼 Квадрат (1:1)",
                        subtitle = "Для постов, чатов и Telegram-каналов",
                        accentColor = AppColors.HyperViolet,
                        onClick = {
                            isGenerating = true
                            coroutineScope.launch {
                                try {
                                    onFormatSelected(ShareCardFormat.SQUARE_1_1)
                                    onDismissRequest()
                                } finally {
                                    isGenerating = false
                                }
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismissRequest,
                        enabled = !isGenerating
                    ) {
                        Text(
                            text = "Отмена",
                            color = AppColors.TextSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FormatOptionCard(
    title: String,
    subtitle: String,
    accentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.SurfaceLevel1)
            .border(1.dp, AppColors.BorderSubtle, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = accentColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = AppColors.TextSecondary
                )
            }
        }
    }
}
