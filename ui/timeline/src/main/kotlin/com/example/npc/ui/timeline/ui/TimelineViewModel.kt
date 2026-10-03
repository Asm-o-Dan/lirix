package com.example.npc.ui.timeline.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.npc.core.model.Event
import com.example.npc.core.model.SourceHealth
import com.example.npc.core.model.classify.Category
import com.example.npc.core.model.finance.FinancialTransaction
import com.example.npc.core.storage.StorageGateway
import com.example.npc.ui.timeline.mapper.EventUiMapper
import com.example.npc.ui.timeline.model.CategoryFilter
import com.example.npc.ui.timeline.model.EventUiModel
import com.example.npc.ui.timeline.model.SourceFilter
import com.example.npc.ui.timeline.model.SourceHealthUiModel
import com.example.npc.ui.timeline.model.TimelineUiEffect
import com.example.npc.ui.timeline.model.TimelineUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.example.npc.core.storage.bank.DynamicTemplateDraft
import com.example.npc.core.storage.bank.TemplateBankManager
import com.example.npc.core.storage.bank.TemplateState
import com.example.npc.core.model.backfill.BackfillCriteria
import com.example.npc.core.model.backfill.BackfillTargetTemplate
import com.example.npc.domain.usecase.TemplateBackfillEngine
import com.example.npc.feature.editor.model.EditorUiState
import com.example.npc.induction.model.BuiltTemplate
import java.time.Instant

class TimelineViewModel(
    private val storageGateway: StorageGateway,
    private val mapper: EventUiMapper = EventUiMapper(),
    val templateBankManager: TemplateBankManager? = null,
    val backfillEngine: TemplateBackfillEngine? = null
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedSourceFilter = MutableStateFlow(SourceFilter.ALL)
    private val _selectedCategoryFilter = MutableStateFlow(CategoryFilter.ALL)
    private val _selectedEventForDetails = MutableStateFlow<EventUiModel?>(null)
    private val _eventForCategoryCorrection = MutableStateFlow<EventUiModel?>(null)
    private val _isDeleteConfirmationVisible = MutableStateFlow(false)
    private val _isExporting = MutableStateFlow(false)

    private val _effects = MutableSharedFlow<TimelineUiEffect>(extraBufferCapacity = 64)
    val effects: SharedFlow<TimelineUiEffect> = _effects.asSharedFlow()
    val effect: SharedFlow<TimelineUiEffect> get() = effects

    private data class FilterParams(
        val query: String,
        val sourceFilter: SourceFilter,
        val categoryFilter: CategoryFilter
    )

    private val _filterParamsFlow = combine(
        _searchQuery,
        _selectedSourceFilter,
        _selectedCategoryFilter
    ) { query, srcFilter, catFilter ->
        FilterParams(query, srcFilter, catFilter)
    }

    private data class CombinedData(
        val events: List<EventUiModel>,
        val health: List<SourceHealthUiModel>,
        val filterParams: FilterParams
    )

    private val _currentLimit = MutableStateFlow(100)
    val currentLimit: StateFlow<Int> = _currentLimit.asStateFlow()

    fun loadMore(step: Int = 100) {
        _currentLimit.value += step
    }

    private fun safeObserveTransactions(limit: Int): Flow<List<FinancialTransaction>> = flow {
        var hasEmitted = false
        try {
            storageGateway.observeTransactions(limit).collect {
                hasEmitted = true
                emit(it)
            }
        } finally {
            if (!hasEmitted) {
                emit(emptyList())
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val _eventsFlow = _currentLimit.flatMapLatest { limit ->
        storageGateway.observeEvents(limit = limit)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val _transactionsFlow = _currentLimit.flatMapLatest { limit ->
        safeObserveTransactions(limit)
    }

    private fun safeObserveSourceHealth(): Flow<List<SourceHealth>> = flow {
        var hasEmitted = false
        try {
            storageGateway.observeSourceHealth().collect {
                hasEmitted = true
                emit(it)
            }
        } finally {
            if (!hasEmitted) {
                emit(emptyList())
            }
        }
    }

    private val _filteredDataFlow = combine(
        _eventsFlow,
        _transactionsFlow,
        safeObserveSourceHealth(),
        _filterParamsFlow
    ) { rawEvents: List<Event>, transactions: List<FinancialTransaction>, rawHealth: List<SourceHealth>, params: FilterParams ->
        val txnsByEventId = transactions.filter { it.eventId != null }.associateBy { it.eventId!! }
        val healthUiList = rawHealth.map { EventUiMapper.toHealthUiModel(it) }

        val eventUiList = rawEvents.map { event ->
            val txn = txnsByEventId[event.id]
            EventUiMapper.toUiModel(event = event, rawEvent = null, transaction = txn)
        }

        val filtered = eventUiList.filter { item ->
            // 1. Фильтр источника
            val matchesSource = when (params.sourceFilter) {
                SourceFilter.ALL -> true
                SourceFilter.NOTIFICATION -> item.source.equals("notification", ignoreCase = true) || item.source.contains("уведомлен", ignoreCase = true)
                SourceFilter.SMS -> item.source.equals("sms", ignoreCase = true)
                SourceFilter.MEDIA -> item.source.equals("media", ignoreCase = true) || item.source.contains("медиа", ignoreCase = true)
            }

            // 2. Фильтр категории
            val matchesCategory = when (params.categoryFilter) {
                CategoryFilter.ALL -> true
                else -> params.categoryFilter.category == null || item.category == params.categoryFilter.category
            }

            // 3. Поисковый запрос
            val matchesQuery = if (params.query.isBlank()) {
                true
            } else {
                val q = params.query.trim()
                item.title.contains(q, ignoreCase = true) ||
                    item.text.contains(q, ignoreCase = true) ||
                    (item.packageName?.contains(q, ignoreCase = true) == true) ||
                    item.normalizedText.contains(q, ignoreCase = true) ||
                    (item.financialData?.merchant?.contains(q, ignoreCase = true) == true) ||
                    (item.financialData?.formattedAmount?.contains(q, ignoreCase = true) == true)
            }

            matchesSource && matchesCategory && matchesQuery
        }

        CombinedData(filtered, healthUiList, params)
    }

    val uiState: StateFlow<TimelineUiState> = combine(
        _filteredDataFlow,
        _selectedEventForDetails,
        _eventForCategoryCorrection,
        _isDeleteConfirmationVisible,
        _isExporting
    ) { data, detailsEvent, correctionEvent, isDeleteVisible, isExporting ->
        TimelineUiState(
            events = data.events,
            sourceHealth = data.health,
            selectedSourceFilter = data.filterParams.sourceFilter,
            selectedCategoryFilter = data.filterParams.categoryFilter,
            searchQuery = data.filterParams.query,
            isLoading = false,
            selectedEventForDetails = detailsEvent,
            eventForCategoryCorrection = correctionEvent,
            isDeleteConfirmationVisible = isDeleteVisible,
            isExporting = isExporting
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = TimelineUiState(isLoading = true)
    )

    val state: StateFlow<TimelineUiState> get() = uiState

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onSearchQueryChange(query: String) = onSearchQueryChanged(query)

    fun onFilterSelected(filter: SourceFilter) {
        _selectedSourceFilter.value = filter
    }

    fun onSourceFilterSelected(filter: SourceFilter) = onFilterSelected(filter)

    fun onCategoryFilterSelected(filter: CategoryFilter) {
        _selectedCategoryFilter.value = filter
    }

    fun onEventClicked(event: EventUiModel) {
        _selectedEventForDetails.value = event
        viewModelScope.launch {
            try {
                val raw = storageGateway.getRawEventByEventId(event.id)
                if (raw != null && _selectedEventForDetails.value?.id == event.id) {
                    _selectedEventForDetails.value = _selectedEventForDetails.value?.copy(
                        packageName = raw.packageName,
                        payloadJson = raw.payloadJson
                    )
                }
            } catch (_: Exception) {}
        }
    }

    fun onDismissEventDetails() {
        _selectedEventForDetails.value = null
    }

    fun onOpenCategoryCorrection(event: EventUiModel) {
        _eventForCategoryCorrection.value = event
    }

    fun onDismissCategoryCorrection() {
        _eventForCategoryCorrection.value = null
    }

    fun onCategoryCorrected(eventId: Long, newCategory: Category) {
        val targetEvent = _eventForCategoryCorrection.value ?: _selectedEventForDetails.value
        _eventForCategoryCorrection.value = null

        viewModelScope.launch {
            try {
                val pkg = targetEvent?.packageName?.takeIf { it.isNotBlank() } ?: "unknown.package"
                val fp = targetEvent?.contentFingerprint.orEmpty()
                storageGateway.recordUserCorrection(
                    eventId = eventId,
                    packageName = pkg,
                    contentFingerprint = fp,
                    newCategory = newCategory,
                    correctedAt = Instant.now()
                )
                _effects.emit(TimelineUiEffect.ShowSnackbar("Категория обновлена на ${newCategory.name}"))
            } catch (e: Exception) {
                _effects.emit(TimelineUiEffect.ShowSnackbar("Ошибка сохранения: ${e.message}"))
            }
        }
    }

    fun onUserCorrectionSubmitted(eventId: Long, newCategory: Category) = onCategoryCorrected(eventId, newCategory)

    fun onDeleteAllClicked() {
        _isDeleteConfirmationVisible.value = true
    }

    fun onDeleteAllRequested() = onDeleteAllClicked()

    fun onDismissDeleteDialog() {
        _isDeleteConfirmationVisible.value = false
    }

    fun onConfirmDeleteAll() {
        _isDeleteConfirmationVisible.value = false
        viewModelScope.launch {
            try {
                storageGateway.deleteAll()
                _effects.emit(TimelineUiEffect.ShowSnackbar("Все данные успешно удалены"))
            } catch (e: Exception) {
                _effects.emit(TimelineUiEffect.ShowSnackbar("Ошибка удаления: ${e.message}"))
            }
        }
    }

    fun onExportJsonClicked() {
        viewModelScope.launch {
            try {
                _isExporting.value = true
                val json = storageGateway.exportAllToJson()
                _effects.emit(TimelineUiEffect.ShareJsonExport(json))
            } catch (e: Exception) {
                _effects.emit(TimelineUiEffect.ShowSnackbar("Ошибка экспорта: ${e.message}"))
            } finally {
                _isExporting.value = false
            }
        }
    }

    fun onExportRequested() = onExportJsonClicked()

    fun onCopyPayloadJson(rawPayloadJson: String) {
        viewModelScope.launch {
            _effects.emit(TimelineUiEffect.CopyToClipboard(rawPayloadJson, "JSON Payload"))
        }
    }

    suspend fun saveTemplate(template: BuiltTemplate, state: EditorUiState): String {
        val constantsJson = buildString {
            append("{")
            val items = template.constants.entries.map { (k, v) ->
                "\"${k.escapeJson()}\":\"${v.escapeJson()}\""
            }
            append(items.joinToString(","))
            append("}")
        }

        val amountFormatJson = buildString {
            append("{")
            val parts = mutableListOf<String>()
            parts.add("\"decimalSeparator\":\"${template.amountFormat.decimalSeparator?.toString()?.escapeJson() ?: ""}\"")
            parts.add("\"groupingSeparator\":\"${template.amountFormat.groupingSeparator?.toString()?.escapeJson() ?: ""}\"")
            append(parts.joinToString(","))
            append("}")
        }

        val bindingsJson = buildString {
            append("{")
            val parts = mutableListOf<String>()
            template.namedGroups.forEach { group ->
                parts.add("\"${group.escapeJson()}\":\"${group.escapeJson()}\"")
            }
            template.decomposedSpec?.let { spec ->
                val slots = spec.slotRules.entries.joinToString(",") { (slot, rule) ->
                    "\"${slot.name.escapeJson()}\":\"${rule.escapeJson()}\""
                }
                parts.add("\"decomposedSpec\":{\"anchorPattern\":\"${spec.anchorPattern.escapeJson()}\",\"slotRules\":{$slots}}")
            }
            append(parts.joinToString(","))
            append("}")
        }

        val draft = DynamicTemplateDraft(
            sourceKey = state.packageName.ifBlank { "com.example.bank" },
            tier = "USER",
            origin = "USER",
            pattern = template.pattern,
            bindingsJson = bindingsJson,
            constantsJson = constantsJson,
            amountFormatJson = amountFormatJson,
            specificity = 1.0f,
            sampleEventId = state.eventId.toString(),
            priority = 200
        )

        val templateId = if (templateBankManager != null) {
            templateBankManager.registerDraft(draft, initialState = TemplateState.ACTIVE)
        } else {
            "tmpl_${state.eventId}_${System.currentTimeMillis()}"
        }

        // Ретроактивная переобработка (бэкфилл) существующих событий для данного банка/источника
        val affectedCount = if (backfillEngine != null) {
            try {
                val targetTemplate = BackfillTargetTemplate(
                    id = templateId,
                    sourcePackage = draft.sourceKey,
                    pattern = draft.pattern,
                    requiredLiterals = template.requiredLiterals,
                    constants = template.constants,
                    priority = draft.priority
                )
                val report = backfillEngine.executeBackfill(
                    template = targetTemplate,
                    criteria = BackfillCriteria.Default
                )
                report.extractedTransactionsCount
            } catch (_: Exception) {
                0
            }
        } else {
            0
        }

        val snackbarMessage = if (affectedCount > 0) {
            "Шаблон успешно сохранен и активирован (обновлено транзакций: $affectedCount)"
        } else {
            "Шаблон успешно сохранен и активирован"
        }
        _effects.emit(TimelineUiEffect.ShowSnackbar(snackbarMessage))
        return templateId
    }

    private fun String.escapeJson(): String = buildString {
        for (ch in this@escapeJson) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000c' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
    }
}
