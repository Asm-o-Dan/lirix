package com.example.npc.feature.editor

import com.example.npc.core.model.finance.TransactionType
import com.example.npc.core.text.DefaultTokenStream
import com.example.npc.core.text.Lexer
import com.example.npc.core.text.TextNormalizer
import com.example.npc.core.text.model.KeywordKind
import com.example.npc.core.text.model.TokenType
import com.example.npc.domain.mvi.BaseViewModel
import com.example.npc.feature.editor.model.EditorTokenUi
import com.example.npc.feature.editor.model.EditorUiEffect
import com.example.npc.feature.editor.model.EditorUiIntent
import com.example.npc.feature.editor.model.EditorUiState
import com.example.npc.induction.LintResult
import com.example.npc.induction.RightBoundedRule
import com.example.npc.induction.TemplateBuilder
import com.example.npc.induction.TemplateLint
import com.example.npc.induction.TokenSegmenter
import com.example.npc.induction.model.BuiltTemplate
import com.example.npc.induction.model.OpTypeResolution
import com.example.npc.induction.model.SlotAssignment
import com.example.npc.induction.model.SlotType
import com.example.npc.induction.model.TokenRole
import com.example.npc.induction.validator.ExpectedSlot
import com.example.npc.induction.validator.HistoricalEventSample
import com.example.npc.induction.validator.ReplayValidator
import com.example.npc.induction.validator.RoundTripResult
import com.example.npc.induction.validator.RoundTripValidator
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

typealias EditorState = EditorUiState
typealias EditorIntent = EditorUiIntent
typealias EditorEffect = EditorUiEffect

/**
 * MVI ViewModel One-Tap редактора шаблонов.
 * Реализован на базе [BaseViewModel] / Store.kt.
 * Обеспечивает индукцию RE2/J шаблона с дебаунсом 150 мс и Replay-валидацию.
 */
@OptIn(FlowPreview::class)
class EditorViewModel(
    val eventId: Long,
    val packageName: String,
    val rawText: String,
    initialTokens: List<EditorTokenUi>? = null,
    private val historySamples: List<HistoricalEventSample> = emptyList(),
    private val onSaveTemplate: (suspend (BuiltTemplate, EditorUiState) -> Unit)? = null,
    coroutineScope: CoroutineScope? = null,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) : BaseViewModel<EditorUiState, EditorUiIntent, EditorUiEffect>(
    initialState = EditorUiState(
        eventId = eventId,
        packageName = packageName,
        rawText = rawText,
        tokens = (initialTokens ?: tokenizeRawText(rawText)).toPersistentList()
    ),
    coroutineScope = coroutineScope
) {

    private val historyStack = ArrayDeque<ImmutableList<EditorTokenUi>>()
    private val validationTrigger = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1)
    private var lastBuiltTemplate: BuiltTemplate? = null

    private val validationJob = viewModelScope.launch {
        validationTrigger
            .debounce(150)
            .collectLatest {
                runInductionPipeline()
            }
    }

    init {
        triggerValidation()
    }

    override fun close() {
        validationJob.cancel()
        super.close()
    }

    /**
     * Алиас для send(intent) согласно спеке overview.md
     */
    fun send(intent: EditorUiIntent) = dispatch(intent)

    override fun handleIntent(intent: EditorUiIntent) {
        when (intent) {
            is EditorUiIntent.AssignTokenRole -> {
                // Сохраняем состояние токенов для отмены (Undo)
                if (historyStack.size >= 20) {
                    historyStack.removeFirst()
                }
                historyStack.addLast(currentState.tokens)

                val updatedTokens = currentState.tokens.mapIndexed { idx, token ->
                    if (idx == intent.tokenIndex) {
                        token.copy(role = intent.role, assignedSlot = intent.slot)
                    } else if ((intent.slot == SlotType.TX_AMOUNT || intent.slot == SlotType.AMOUNT) &&
                        (token.assignedSlot == SlotType.TX_AMOUNT || token.assignedSlot == SlotType.AMOUNT)
                    ) {
                        // Ровно одна сумма операции TX_AMOUNT допускается
                        token.copy(role = TokenRole.LITERAL, assignedSlot = null)
                    } else {
                        token
                    }
                }.toPersistentList()

                updateState {
                    it.copy(
                        tokens = updatedTokens,
                        canUndo = true,
                        isValidationInProgress = true
                    )
                }
                triggerValidation()
            }

            is EditorUiIntent.ToggleRefund -> {
                updateState {
                    it.copy(
                        isRefund = intent.isRefund,
                        isValidationInProgress = true
                    )
                }
                triggerValidation()
            }

            is EditorUiIntent.ChangeOpType -> {
                updateState {
                    it.copy(
                        detectedOpType = intent.opType,
                        isValidationInProgress = true
                    )
                }
                triggerValidation()
            }

            is EditorUiIntent.SaveAndActivate -> {
                val state = currentState
                if (!state.canSave || state.isSaving) return

                val template = lastBuiltTemplate
                if (template == null) {
                    emitEffect(EditorUiEffect.ShowToast("Шаблон не скомпилирован"))
                    return
                }

                updateState { it.copy(isSaving = true) }

                viewModelScope.launch {
                    try {
                        onSaveTemplate?.invoke(template, state)
                        val templateId = "tmpl_${state.eventId}_${System.currentTimeMillis()}"
                        emitEffect(
                            EditorUiEffect.SavedSuccessfully(
                                templateId = templateId,
                                affectedEventsCount = state.positiveMatchesCount
                            )
                        )
                        emitEffect(EditorUiEffect.ShowToast("Шаблон сохранен и активирован"))
                        emitEffect(EditorUiEffect.CloseSheet)
                    } catch (e: Exception) {
                        updateState { it.copy(isSaving = false) }
                        emitEffect(EditorUiEffect.ShowToast("Ошибка сохранения: ${e.message}"))
                    }
                }
            }

            is EditorUiIntent.Undo -> {
                if (historyStack.isNotEmpty()) {
                    val previousTokens = historyStack.removeLast()
                    updateState {
                        it.copy(
                            tokens = previousTokens,
                            canUndo = historyStack.isNotEmpty(),
                            isValidationInProgress = true
                        )
                    }
                    triggerValidation()
                }
            }

            is EditorUiIntent.Dismiss -> {
                emitEffect(EditorUiEffect.CloseSheet)
            }
        }
    }

    private fun triggerValidation() {
        validationTrigger.tryEmit(Unit)
    }

    private suspend fun runInductionPipeline() = withContext(defaultDispatcher) {
        val state = currentState
        val tokens = state.tokens

        // 1. Проверяем наличие ровно 1 суммы операции (AMOUNT или TX_AMOUNT)
        val amountTokens = tokens.filter {
            it.assignedSlot == SlotType.AMOUNT || it.assignedSlot == SlotType.TX_AMOUNT
        }

        if (amountTokens.isEmpty()) {
            updateState {
                it.copy(
                    isValidationInProgress = false,
                    candidatePattern = null,
                    validationError = "Выберите сумму операции (TX_AMOUNT)",
                    canSave = false
                )
            }
            return@withContext
        }

        if (amountTokens.size > 1) {
            updateState {
                it.copy(
                    isValidationInProgress = false,
                    candidatePattern = null,
                    validationError = "Разрешена только одна сумма операции",
                    canSave = false
                )
            }
            return@withContext
        }

        try {
            // 2. Создаем TokenStream и SlotAssignments
            val normText = TextNormalizer.normalize(rawText)
            val stream = Lexer.tokenize(normText)

            val slotAssignments = tokens.mapNotNull { token ->
                val slot = token.assignedSlot ?: return@mapNotNull null
                val streamIdx = if (token.index < stream.size) token.index else null
                if (streamIdx != null) {
                    SlotAssignment(slot = slot, tokenIndex = streamIdx)
                } else null
            }

            val sequence = TokenSegmenter.segment(
                tokens = stream,
                slotAssignments = slotAssignments,
                originalText = rawText
            )

            val txType = try {
                TransactionType.valueOf(state.detectedOpType.uppercase())
            } catch (_: Exception) {
                TransactionType.DEBIT
            }

            val opType = OpTypeResolution(
                transactionType = txType,
                isRefund = state.isRefund
            )

            // 3. Синтез шаблона через TemplateBuilder (гибкий паттерн + спецификация слотового конвейера)
            val monolithicBuilt = TemplateBuilder.build(sequence, opType = opType)
            val decomposed = TemplateBuilder.buildDecomposedSpec(sequence, opType = opType)
            val built = monolithicBuilt.copy(decomposedSpec = decomposed)

            // 4. Применяем правило правой границы RightBoundedRule
            val bounded = RightBoundedRule.enforce(sequence.segments, built.pattern)
            val candidateTemplate = built.copy(
                pattern = bounded.correctedPattern,
                decomposedSpec = built.decomposedSpec
            )

            // 5. Статический линтинг TemplateLint
            val lintResult = TemplateLint.lint(candidateTemplate)
            if (lintResult is LintResult.Failed) {
                val errorMsg = lintResult.errors.firstOrNull()?.message ?: "Ошибка линтинга шаблона"
                updateState {
                    it.copy(
                        isValidationInProgress = false,
                        candidatePattern = candidateTemplate.pattern,
                        validationError = errorMsg,
                        canSave = false
                    )
                }
                return@withContext
            }

            // 6. Round-Trip валидация на исходном тексте
            val expectedSlots = slotAssignments.map { assignment ->
                val expectedText = if (assignment.tokenIndices.isNotEmpty() && assignment.tokenIndices[0] < stream.size) {
                    val tok = stream[assignment.tokenIndices[0]]
                    val raw = rawText.substring(tok.span.start.coerceIn(0, rawText.length), tok.span.end.coerceIn(0, rawText.length))
                    if (assignment.slot == SlotType.CARD_MASK) {
                        val digits = raw.filter { it.isDigit() }
                        if (digits.length >= 4) digits.takeLast(4) else raw
                    } else {
                        raw
                    }
                } else ""
                val groupName = when (assignment.slot) {
                    SlotType.AMOUNT, SlotType.TX_AMOUNT -> "amount"
                    SlotType.CURRENCY -> "curr"
                    SlotType.CARD_MASK -> "mask"
                    SlotType.BALANCE -> "bal"
                    SlotType.BALANCE_CURRENCY -> "balcurr"
                    SlotType.MERCHANT -> "merchant"
                    SlotType.FEE -> "fee"
                    SlotType.OTHER -> "other"
                }
                ExpectedSlot(slotName = groupName, expectedText = expectedText)
            }

            val roundTrip = RoundTripValidator.validate(candidateTemplate, rawText, expectedSlots)
            if (roundTrip is RoundTripResult.NoMatchOnOriginal) {
                updateState {
                    it.copy(
                        isValidationInProgress = false,
                        candidatePattern = candidateTemplate.pattern,
                        validationError = "Шаблон не совпадает с исходным сообщением",
                        canSave = false
                    )
                }
                return@withContext
            }

            // 7. Replay-валидация на историческом корпусе
            val replayReport = ReplayValidator.validate(
                template = candidateTemplate,
                targetPackage = packageName,
                historySamples = historySamples
            )

            lastBuiltTemplate = candidateTemplate

            val hasConflicts = replayReport.conflictCount > 0
            val hasNegViolations = replayReport.negativeViolationsCount > 0

            val validationErr = when {
                hasNegViolations -> "Шаблон ложно срабатывает на ${replayReport.negativeViolationsCount} нефинансовых событиях"
                hasConflicts -> "Конфликт с ${replayReport.conflictCount} существующими шаблонами"
                else -> null
            }

            val canSave = validationErr == null && !hasConflicts && !hasNegViolations

            updateState {
                it.copy(
                    isValidationInProgress = false,
                    candidatePattern = candidateTemplate.pattern,
                    positiveMatchesCount = replayReport.positiveMatchesCount,
                    conflictCount = replayReport.conflictCount,
                    validationError = validationErr,
                    canSave = canSave
                )
            }
        } catch (e: Exception) {
            updateState {
                it.copy(
                    isValidationInProgress = false,
                    candidatePattern = null,
                    validationError = "Сбой индукции: ${e.message}",
                    canSave = false
                )
            }
        }
    }

    companion object {
        fun tokenizeRawText(rawText: String): List<EditorTokenUi> {
            if (rawText.isBlank()) return emptyList()
            val normText = TextNormalizer.normalize(rawText)
            val stream = Lexer.tokenize(normText)

            var assignedTxAmount = false
            var awaitingTxCurrency = false
            var awaitingBalance = false
            var awaitingBalanceCurrency = false
            var awaitingFee = false

            return stream.mapIndexed { idx, token ->
                val txt = normText.normalized.substring(
                    token.span.start.coerceIn(0, normText.normalized.length),
                    token.span.end.coerceIn(0, normText.normalized.length)
                )

                val (role, slot) = when {
                    token.type == TokenType.CARD_MASK -> {
                        awaitingTxCurrency = false
                        awaitingBalanceCurrency = false
                        TokenRole.SLOT to SlotType.CARD_MASK
                    }
                    awaitingBalanceCurrency && isCurrencyToken(token) -> {
                        awaitingBalanceCurrency = false
                        TokenRole.SLOT to SlotType.BALANCE_CURRENCY
                    }
                    awaitingTxCurrency && isCurrencyToken(token) -> {
                        awaitingTxCurrency = false
                        TokenRole.SLOT to SlotType.CURRENCY
                    }
                    awaitingBalance && token.type == TokenType.NUMBER -> {
                        awaitingBalance = false
                        awaitingBalanceCurrency = true
                        awaitingTxCurrency = false
                        TokenRole.SLOT to SlotType.BALANCE
                    }
                    awaitingFee && token.type == TokenType.NUMBER -> {
                        awaitingFee = false
                        awaitingTxCurrency = false
                        awaitingBalanceCurrency = false
                        TokenRole.SLOT to SlotType.FEE
                    }
                    !assignedTxAmount && !awaitingBalance && !awaitingFee && token.type == TokenType.NUMBER -> {
                        assignedTxAmount = true
                        awaitingTxCurrency = true
                        awaitingBalanceCurrency = false
                        TokenRole.SLOT to SlotType.TX_AMOUNT
                    }
                    else -> {
                        val isSep = token.type == TokenType.PUNCT || token.type == TokenType.SIGN || token.text.isBlank()
                        if (isBalanceKeyword(token)) {
                            awaitingBalance = true
                            awaitingFee = false
                            awaitingTxCurrency = false
                            awaitingBalanceCurrency = false
                        } else if (isFeeKeyword(token)) {
                            awaitingFee = true
                            awaitingBalance = false
                            awaitingTxCurrency = false
                            awaitingBalanceCurrency = false
                        } else if (!isSep) {
                            awaitingBalance = false
                            awaitingFee = false
                            awaitingTxCurrency = false
                            awaitingBalanceCurrency = false
                        }
                        TokenRole.LITERAL to null
                    }
                }

                EditorTokenUi(
                    index = idx,
                    text = txt,
                    role = role,
                    assignedSlot = slot,
                    isAnchorKeyword = token.type == TokenType.KEYWORD,
                    isSelectable = true
                )
            }
        }

        private fun isBalanceKeyword(token: com.example.npc.core.text.model.Token): Boolean {
            if (token.keywordKind == KeywordKind.BALANCE) return true
            val lower = token.text.lowercase().trimEnd(':', '.')
            return lower in setOf("остаток", "баланс", "sold", "доступно", "доступный")
        }

        private fun isFeeKeyword(token: com.example.npc.core.text.model.Token): Boolean {
            if (token.keywordKind == KeywordKind.FEE) return true
            val lower = token.text.lowercase().trimEnd(':', '.')
            return lower in setOf("комиссия", "fee", "taxa")
        }

        private fun isCurrencyToken(token: com.example.npc.core.text.model.Token): Boolean {
            if (token.type == TokenType.CURRENCY) return true
            val clean = token.text.trimEnd('.', ',', ';', ':', ' ')
            val upper = clean.uppercase()
            return upper in setOf("MDL", "RUP", "USD", "EUR", "RUB", "LEI", "ЛЕЙ", "ЛЕЕВ", "РУБ", "Р", "$", "€")
        }
    }
}
