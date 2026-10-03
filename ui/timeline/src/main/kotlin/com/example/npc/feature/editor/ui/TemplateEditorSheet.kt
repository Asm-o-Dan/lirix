package com.example.npc.feature.editor.ui

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.npc.feature.editor.EditorViewModel
import com.example.npc.feature.editor.model.EditorUiEffect
import com.example.npc.feature.editor.model.EditorUiIntent
import kotlinx.coroutines.flow.collectLatest

/**
 * Всплывающий модальный диалог Compose (ModalBottomSheet),
 * реализующий One-Tap Human-in-the-Loop редактор шаблонов.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateEditorSheet(
    eventId: Long,
    onDismiss: () -> Unit,
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    var selectedTokenIndexForMenu by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is EditorUiEffect.CloseSheet -> {
                    onDismiss()
                }
                is EditorUiEffect.ShowToast -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
                is EditorUiEffect.SavedSuccessfully -> {
                    Toast.makeText(
                        context,
                        "Шаблон активирован! Затронуто событий: ${effect.affectedEventsCount}",
                        Toast.LENGTH_SHORT
                    ).show()
                    onDismiss()
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Верхняя панель заголовка
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "One-Tap редактор шаблона",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = state.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.canUndo) {
                        IconButton(
                            onClick = { viewModel.dispatch(EditorUiIntent.Undo) }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "Отменить последнее действие (Undo)"
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрыть редактор"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Подсказка для пользователя
            Text(
                text = "Нажмите на токен, чтобы разметить его как сумму, валюту, карту или мерчанта:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Карточка с интерактивной сеткой токенов
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(modifier = Modifier.padding(12.dp)) {
                    TokenGridView(
                        tokens = state.tokens,
                        onChipClick = { clickedIdx ->
                            selectedTokenIndexForMenu = clickedIdx
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Контекстное меню выбора ролей
                    selectedTokenIndexForMenu?.let { tokenIdx ->
                        val token = state.tokens.getOrNull(tokenIdx)
                        RoleSelectionMenu(
                            expanded = true,
                            currentSlot = token?.assignedSlot,
                            onDismissRequest = { selectedTokenIndexForMenu = null },
                            onRoleSelected = { role, slot ->
                                viewModel.dispatch(EditorUiIntent.AssignTokenRole(tokenIdx, role, slot))
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Направление операции",
                style = MaterialTheme.typography.labelLarge
            )
            listOf(
                listOf("AUTO" to "Авто", "DEBIT" to "Списание"),
                listOf("CREDIT" to "Пополнение", "TRANSFER" to "Перевод")
            ).forEach { choices ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    choices.forEach { (type, label) ->
                        FilterChip(
                            selected = state.detectedOpType == type,
                            onClick = { viewModel.dispatch(EditorUiIntent.ChangeOpType(type)) },
                            label = { Text(label) },
                            enabled = !state.isSaving
                        )
                    }
                }
            }
            Text(
                text = "Авто определяет направление по каждому сообщению. Неясные операции требуют уточнения.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Статус валидации и компиляции
            if (state.isValidationInProgress) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Синтез и Replay-проверка шаблона...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else if (state.validationError != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Предупреждение",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = state.validationError ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            } else if (state.candidatePattern != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Шаблон валиден",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Шаблон готов к активации",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Совпадает с ${state.positiveMatchesCount} событиями в истории (0 конфликтов)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = state.candidatePattern ?: "",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Нижняя панель действий (One-Tap Save)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Отмена")
                }

                Button(
                    onClick = { viewModel.dispatch(EditorUiIntent.SaveAndActivate) },
                    enabled = state.canSave && !state.isValidationInProgress && !state.isSaving,
                    modifier = Modifier.weight(2f)
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Сохранить и активировать")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
