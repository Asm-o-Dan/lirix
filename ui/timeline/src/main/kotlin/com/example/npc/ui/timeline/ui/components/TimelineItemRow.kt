package com.example.npc.ui.timeline.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.npc.ui.timeline.model.EventUiModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimelineItemRow(
    event: EventUiModel,
    onClick: () -> Unit,
    onCategoryClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val containerColor = if (event.isUpdate) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.surface
    }
    val updateIndicatorColor = MaterialTheme.colorScheme.primary

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    if (event.isUpdate) {
                        drawRect(
                            color = updateIndicatorColor,
                            topLeft = Offset.Zero,
                            size = Size(width = 4.dp.toPx(), height = size.height)
                        )
                    }
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = if (event.isUpdate) 16.dp else 12.dp,
                        end = 12.dp,
                        top = 10.dp,
                        bottom = 10.dp
                    )
            ) {
                // Строка 1: Слева [Иконка источника + Название источника], Справа [Время]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (event.sourceIconRes != 0) {
                        Icon(
                            painter = painterResource(id = event.sourceIconRes),
                            contentDescription = event.sourceName,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = event.sourceName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Text(
                        text = event.formattedTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                // Строка 2: Бейджи метаданных (CategoryBadge, ИЗМЕНЕНО, rev #, langLabel)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    // Бейдж категории
                    CategoryBadge(
                        category = event.category,
                        engine = event.engineUsed,
                        confidence = event.confidence.toDouble(),
                        isUserCorrected = event.isUserCorrected,
                        onClick = onCategoryClick
                    )

                    if (event.isUpdate) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "ИЗМЕНЕНО",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    if (event.pipelineRevisionId != null) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "rev #${event.pipelineRevisionId}",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    if (event.langLabel.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Text(
                                text = event.langLabel,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                // Заголовок и текст события с фолбэком для пустых событий (Ghost Cards)
                val rawTitle = event.displayTitle.trim()
                val rawText = event.displayText.trim()
                val isTitleBlank = rawTitle.isEmpty() || rawTitle == "Без названия"
                val isTextBlank = rawText.isEmpty()

                if (isTitleBlank && isTextBlank) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "(Событие без текста)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    if (!isTitleBlank) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = rawTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (!isTextBlank) {
                        Spacer(modifier = Modifier.height(if (!isTitleBlank) 4.dp else 6.dp))
                        Text(
                            text = rawText,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        // Заголовок есть, но текст пустой
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "(Событие без текста)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Финансовая карточка
                if (event.financialData != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TransactionCard(transaction = event.financialData)
                }

                if (!event.threadKey.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Тред: ${event.threadKey}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
