package com.example.npc.ui.timeline.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.npc.ui.timeline.mapper.EventUiMapper
import com.example.npc.ui.timeline.model.EventUiModel

import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString

@Composable
fun EventDetailsDialog(
    event: EventUiModel,
    onCategoryCorrectionClick: () -> Unit,
    onDismissRequest: () -> Unit,
    onCreateTemplateClick: (() -> Unit)? = null,
    onCopyJson: ((String) -> Unit)? = null
) {
    val clipboardManager = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = event.sourceIconRes),
                        contentDescription = event.sourceName,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Событие #${event.id}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = event.formattedTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Заголовок и текст сообщения
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = event.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Секция семантической классификации
                Text(
                    text = "Классификация",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CategoryBadge(
                        category = event.category,
                        engine = event.engineUsed,
                        confidence = event.confidence.toDouble(),
                        isUserCorrected = event.isUserCorrected,
                        onClick = onCategoryCorrectionClick
                    )

                    OutlinedButton(onClick = onCategoryCorrectionClick) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Изменить категорию",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Изменить",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Движок: ${event.engineUsed.name} | Уверенность: ${(event.confidence * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )

                if (event.pipelineRevisionId != null) {
                    Text(
                        text = "Ревизия конвейера: #${event.pipelineRevisionId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                if (!event.contentFingerprint.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Отпечаток: ${event.contentFingerprint}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                // Финансовая карточка (если есть транзакция)
                if (event.financialData != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Финансовая транзакция",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    TransactionCard(transaction = event.financialData)
                }

                // Сырой JSON payload
                if (!event.payloadJson.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    val rawJson = event.payloadJson
                    val prettyJson = EventUiMapper.formatJsonPretty(rawJson)
                    RawPayloadJsonViewer(
                        jsonText = prettyJson,
                        onCopyClick = {
                            clipboardManager.setText(AnnotatedString(rawJson))
                            onCopyJson?.invoke(rawJson)
                        }
                    )
                }
            }
        },
        confirmButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onCreateTemplateClick != null) {
                    Button(
                        onClick = onCreateTemplateClick,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Создать шаблон")
                    }
                }
                TextButton(onClick = onDismissRequest) {
                    Text("Закрыть")
                }
            }
        }
    )
}

@Composable
fun EventDetailsDialog(
    event: EventUiModel,
    onDismiss: () -> Unit,
    onOpenCategoryCorrection: () -> Unit,
    onCopyJson: (String) -> Unit = {},
    onCreateTemplateClick: (() -> Unit)? = null
) = EventDetailsDialog(
    event = event,
    onCategoryCorrectionClick = onOpenCategoryCorrection,
    onDismissRequest = onDismiss,
    onCreateTemplateClick = onCreateTemplateClick,
    onCopyJson = onCopyJson
)
