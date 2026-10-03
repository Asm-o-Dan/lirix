package com.example.npc.feature.diagnostics.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.npc.feature.diagnostics.model.DiagnosticsUiEffect
import com.example.npc.feature.diagnostics.model.DiagnosticsUiIntent
import com.example.npc.feature.diagnostics.model.DiagnosticsUiState
import com.example.npc.feature.diagnostics.model.QuarantinedTemplateUi
import com.example.npc.feature.diagnostics.model.TraceRowUi
import com.example.npc.feature.diagnostics.vm.DiagnosticsViewModel
import kotlinx.coroutines.flow.collectLatest

@Composable
fun OrchestratorDiagnosticsScreen(
    modifier: Modifier = Modifier,
    onBackClick: (() -> Unit)? = null,
    onOpenTemplateInEditor: ((templateId: String) -> Unit)? = null
) {
    val viewModel = remember { DiagnosticsViewModel() }
    OrchestratorDiagnosticsScreen(
        viewModel = viewModel,
        modifier = modifier,
        onBackClick = onBackClick,
        onOpenTemplateInEditor = onOpenTemplateInEditor
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrchestratorDiagnosticsScreen(
    viewModel: DiagnosticsViewModel,
    modifier: Modifier = Modifier,
    onBackClick: (() -> Unit)? = null,
    onOpenTemplateInEditor: ((templateId: String) -> Unit)? = null
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Автоматическая остановка сэмплинга рантайма при уходе экрана в фон (onStop / onDispose)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    viewModel.dispatch(DiagnosticsUiIntent.StartPolling)
                }
                Lifecycle.Event.ON_STOP -> {
                    viewModel.dispatch(DiagnosticsUiIntent.StopPolling)
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.dispatch(DiagnosticsUiIntent.StopPolling)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is DiagnosticsUiEffect.ShowToast -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
                is DiagnosticsUiEffect.BreakerResetSuccess -> {
                    snackbarHostState.showSnackbar("Предохранитель ${effect.nodeId} сброшен в CLOSED")
                }
                is DiagnosticsUiEffect.TemplateUnquarantined -> {
                    snackbarHostState.showSnackbar("Шаблон ${effect.templateId} разблокирован в ACTIVE")
                }
                is DiagnosticsUiEffect.OpenTemplateInEditor -> {
                    onOpenTemplateInEditor?.invoke(effect.templateId)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    if (onBackClick != null) {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier.testTag("diagnostics_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Назад"
                            )
                        }
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Диагностика Orchestrator",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (state.isPollingActive && state.isLiveUpdateEnabled) Color(0xFF4CAF50)
                                    else Color(0xFF9E9E9E)
                                )
                                .testTag("live_sampling_indicator")
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.dispatch(DiagnosticsUiIntent.ToggleLiveUpdate(!state.isLiveUpdateEnabled))
                        },
                        modifier = Modifier.testTag("toggle_live_button")
                    ) {
                        Icon(
                            imageVector = if (state.isLiveUpdateEnabled) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (state.isLiveUpdateEnabled) "Пауза сэмплинга" else "Возобновить сэмплинг",
                            tint = if (state.isLiveUpdateEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                    IconButton(
                        onClick = { viewModel.dispatch(DiagnosticsUiIntent.RefreshManual) },
                        modifier = Modifier.testTag("refresh_manual_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Обновить сейчас"
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors()
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { paddingValues ->
        DiagnosticsContent(
            state = state,
            onResetBreaker = { nodeId -> viewModel.dispatch(DiagnosticsUiIntent.ResetCircuitBreaker(nodeId)) },
            onUnquarantine = { tmplId -> viewModel.dispatch(DiagnosticsUiIntent.UnquarantineTemplate(tmplId)) },
            onEditTemplate = { tmplId -> onOpenTemplateInEditor?.invoke(tmplId) },
            onTogglePause = { viewModel.dispatch(DiagnosticsUiIntent.ToggleLiveUpdate(!state.isLiveUpdateEnabled)) },
            onSelectNodeFilter = { node -> viewModel.dispatch(DiagnosticsUiIntent.FilterTracesByNode(node)) },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        )
    }
}

@Composable
fun DiagnosticsContent(
    state: DiagnosticsUiState,
    onResetBreaker: (String) -> Unit,
    onUnquarantine: (String) -> Unit,
    onEditTemplate: (String) -> Unit,
    onTogglePause: () -> Unit,
    onSelectNodeFilter: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.testTag("diagnostics_scroll_content"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Карточка активного поколения
        item {
            ActiveGenerationCard(state = state)
        }

        // 2. Метрики очереди Ingest
        item {
            QueueMetricsCard(state = state)
        }

        // 3. Алерт пушей Finance без извлеченного payload (если есть)
        item {
            FinanceWithoutPayloadAlert(count = state.financeWithoutPayloadAlertCount)
        }

        // 4. Статус предохранителей NodeCircuitBreaker
        item {
            Text(
                text = "Предохранители узлов (Circuit Breakers)",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (state.breakers.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("breakers_empty_card"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        text = "Предохранители не зарегистрированы в OrchestratorProbe",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(state.breakers, key = { it.nodeId }) { breaker ->
                BreakerStatusRow(
                    breaker = breaker,
                    onResetClick = onResetBreaker
                )
            }
        }

        // 5. Секция карантина (QUARANTINED)
        item {
            QuarantineSection(
                quarantinedTemplates = state.quarantinedTemplates,
                onUnquarantine = onUnquarantine,
                onEdit = onEditTemplate
            )
        }

        // 6. Список трейсов TraceRing (последние 128 записей)
        item {
            TracesSectionHeader(
                isLive = state.isLiveUpdateEnabled,
                onTogglePause = onTogglePause,
                selectedNode = state.selectedNodeFilter,
                availableNodes = state.availableNodeFilters,
                onSelectNode = onSelectNodeFilter,
                traceCount = state.recentTraces.size
            )
        }

        if (state.recentTraces.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("traces_empty_card"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        text = "Трейсы кольцевого буфера TraceRing пусты",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(state.recentTraces, key = { "${it.seq}_${it.eventId}_${it.nodeId}" }) { trace ->
                TraceRecordRow(trace = trace)
            }
        }
    }
}

/**
 * 1. Карточка активного поколения: ревизия конвейера, версия банка шаблонов, число шаблонов ACTIVE, SHADOW, QUARANTINED.
 */
@Composable
fun ActiveGenerationCard(
    state: DiagnosticsUiState,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("active_generation_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Активное поколение",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Rev #${state.activePipelineRevision}",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("pipeline_revision_text")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Версия банка шаблонов: v${state.activeBankVersion}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("bank_version_text")
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Счетчики статусов шаблонов
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusBadge(
                    label = "ACTIVE",
                    count = state.activeTemplatesCount,
                    backgroundColor = Color(0xFFE8F5E9),
                    textColor = Color(0xFF2E7D32),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("badge_active_templates")
                )
                StatusBadge(
                    label = "SHADOW",
                    count = state.shadowTemplatesCount,
                    backgroundColor = Color(0xFFE3F2FD),
                    textColor = Color(0xFF1565C0),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("badge_shadow_templates")
                )
                StatusBadge(
                    label = "QUARANTINED",
                    count = state.quarantinedTemplatesCount,
                    backgroundColor = if (state.quarantinedTemplatesCount > 0) Color(0xFFFFEBEE) else Color(0xFFF5F5F5),
                    textColor = if (state.quarantinedTemplatesCount > 0) Color(0xFFC62828) else Color(0xFF757575),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("badge_quarantined_templates")
                )
            }
        }
    }
}

@Composable
fun StatusBadge(
    label: String,
    count: Int,
    backgroundColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$count",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor
                )
            )
        }
    }
}

/**
 * 2. Метрики очереди: текущий размер очереди Channel, событий в минуту.
 */
@Composable
fun QueueMetricsCard(
    state: DiagnosticsUiState,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("queue_metrics_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Очередь Ingest Channel",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = "Поток обработки в реальном времени",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${state.ingestQueueSize} в буфере",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (state.ingestQueueSize > 500) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("queue_size_text")
                )
                Text(
                    text = "${state.ingestEventsPerMinute} соб./мин",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    ),
                    modifier = Modifier.testTag("events_per_minute_text")
                )
            }
        }
    }
}

/**
 * 3. Алерт financeWithoutPayload: счетчик пушей, классифицированных как Finance, но без извлеченных транзакций.
 */
@Composable
fun FinanceWithoutPayloadAlert(
    count: Int,
    modifier: Modifier = Modifier
) {
    if (count <= 0) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("finance_without_payload_alert"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFF3E0)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB74D))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Предупреждение",
                tint = Color(0xFFE65100),
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "financeWithoutPayload: $count пушей",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFBF360C)
                    ),
                    modifier = Modifier.testTag("finance_without_payload_title")
                )
                Text(
                    text = "Классифицированы как Finance, но транзакции не извлечены. Требуется калибровка шаблонов.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFE65100)
                )
            }
        }
    }
}

/**
 * 5. Секция карантина: список шаблонов в QUARANTINED с возможностью разблокировки в ACTIVE.
 */
@Composable
fun QuarantineSection(
    quarantinedTemplates: List<QuarantinedTemplateUi>,
    onUnquarantine: (String) -> Unit,
    onEdit: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("quarantine_section_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = if (quarantinedTemplates.isNotEmpty()) Color(0xFFD32F2F) else Color(0xFF388E3C),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Карантин шаблонов (${quarantinedTemplates.size})",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (quarantinedTemplates.isEmpty()) {
                Text(
                    text = "Карантин пуст — все шаблоны работают штатно",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("quarantine_empty_text")
                )
            } else {
                quarantinedTemplates.forEach { template ->
                    QuarantinedTemplateRow(
                        template = template,
                        onUnquarantine = { onUnquarantine(template.id) },
                        onEdit = { onEdit(template.id) },
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun QuarantinedTemplateRow(
    template: QuarantinedTemplateUi,
    onUnquarantine: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("quarantine_item_${template.id}"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = template.sourceKey,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = template.id.take(8),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.outline
                    )
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = template.pattern,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (!template.reason.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Причина: ${template.reason}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFFC62828),
                        fontSize = 11.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.testTag("edit_template_button_${template.id}")
                ) {
                    Text("В редактор", style = MaterialTheme.typography.labelSmall)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onUnquarantine,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    modifier = Modifier.testTag("unquarantine_button_${template.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.LockOpen,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("В ACTIVE", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/**
 * 6. Заголовок секции трейсов с кнопкой паузы и фильтрами по узлам.
 */
@Composable
fun TracesSectionHeader(
    isLive: Boolean,
    onTogglePause: () -> Unit,
    selectedNode: String?,
    availableNodes: List<String>,
    onSelectNode: (String?) -> Unit,
    traceCount: Int,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Трейсы конвейера TraceRing ($traceCount)",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            OutlinedButton(
                onClick = onTogglePause,
                modifier = Modifier.testTag("pause_traces_button")
            ) {
                Icon(
                    imageVector = if (isLive) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isLive) "Пауза" else "Live",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Горизонтальные чипы фильтрации по узлам
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().testTag("trace_filters_row")
        ) {
            item {
                FilterChip(
                    selected = selectedNode == null,
                    onClick = { onSelectNode(null) },
                    label = { Text("Все") },
                    modifier = Modifier.testTag("filter_node_all")
                )
            }
            items(availableNodes) { node ->
                FilterChip(
                    selected = selectedNode == node,
                    onClick = { onSelectNode(if (selectedNode == node) null else node) },
                    label = { Text(node) },
                    modifier = Modifier.testTag("filter_node_$node")
                )
            }
        }
    }
}

@Composable
fun TraceRecordRow(
    trace: TraceRowUi,
    modifier: Modifier = Modifier
) {
    val outcomeColor = when (trace.outcome) {
        "PASS" -> Color(0xFF2E7D32)
        "FAIL" -> Color(0xFFC62828)
        "BYPASS" -> Color(0xFF757575)
        "BREAKER_TRIP" -> Color(0xFFD50000)
        "COMMIT" -> Color(0xFF6A1B9A)
        "DROP" -> Color(0xFF1565C0)
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("trace_row_${trace.seq}"),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "#${trace.seq}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.outline
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = trace.nodeId,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                }
                Text(
                    text = "EventId: ${trace.eventId}${trace.templateId?.let { " | $it" } ?: ""}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = outcomeColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = trace.outcome,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = outcomeColor,
                            fontSize = 10.sp
                        ),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = "${trace.durationUs} µs",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                )
            }
        }
    }
}
