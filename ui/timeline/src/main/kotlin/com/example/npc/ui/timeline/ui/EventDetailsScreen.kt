package com.example.npc.ui.timeline.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.npc.ui.timeline.mapper.EventUiMapper
import com.example.npc.ui.timeline.model.EventDetailsUiState
import com.example.npc.ui.timeline.model.EventUiModel
import com.example.npc.ui.timeline.ui.components.RawPayloadJsonViewer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailsBottomSheet(
    event: EventUiModel,
    onDismiss: () -> Unit,
    onCopyJson: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        EventDetailsContent(
            event = event,
            onDismiss = onDismiss,
            onCopyJson = onCopyJson
        )
    }
}

@Composable
fun EventDetailsContent(
    event: EventUiModel,
    onDismiss: () -> Unit,
    onCopyJson: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val formattedJson = event.payloadJson?.let { EventUiMapper.formatJsonPretty(it) } ?: "{}"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Событие #${event.id}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Закрыть"
                )
            }
        }

        // Section: Metadata
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Метаданные",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                MetadataRow(label = "ID события", value = event.id.toString())
                MetadataRow(label = "Raw ID", value = event.rawId.toString())
                MetadataRow(label = "Время", value = event.timeLabel)
                MetadataRow(label = "Источник", value = event.sourceName)
                MetadataRow(label = "Язык", value = event.langLabel)
                MetadataRow(label = "Ключ треда", value = event.threadKey ?: "—")
                MetadataRow(
                    label = "Связь обновления",
                    value = event.isUpdateOf?.let { "#$it" } ?: "Нет (первичное)"
                )
                if (!event.packageName.isNullOrBlank()) {
                    MetadataRow(label = "Пакет", value = event.packageName)
                }
            }
        }

        // Section: Content
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Текстовое содержимое",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = event.displayTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                SelectionContainer {
                    Text(
                        text = event.displayText,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (event.normalizedText != event.displayText && event.normalizedText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Нормализованный:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = event.normalizedText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Section: Raw JSON
        RawPayloadJsonViewer(
            jsonText = formattedJson,
            onCopyClick = { onCopyJson(formattedJson) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailsScreen(
    uiState: EventDetailsUiState,
    onDismiss: () -> Unit,
    onCopyJson: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    when (uiState) {
        EventDetailsUiState.Hidden -> Unit
        EventDetailsUiState.Loading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        is EventDetailsUiState.Success -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Событие #${uiState.event.id}") },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Назад")
                            }
                        }
                    )
                },
                modifier = modifier
            ) { paddingValues ->
                EventDetailsContent(
                    event = uiState.event.copy(payloadJson = uiState.formattedPayloadJson),
                    onDismiss = onDismiss,
                    onCopyJson = onCopyJson,
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
        is EventDetailsUiState.Error -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = uiState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = onDismiss) {
                        Text("Закрыть")
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}
