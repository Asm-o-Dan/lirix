package com.example.npc.extract.universal

import com.example.npc.core.model.finance.FinancialTransaction
import com.example.npc.core.model.finance.Money
import com.example.npc.core.model.finance.TransactionStatus
import com.example.npc.core.model.finance.TransactionType
import com.example.npc.core.text.NormalizedText
import com.example.npc.core.text.TextSpan
import com.example.npc.core.text.TokenStream
import com.example.npc.core.text.model.TokenType
import com.example.npc.extract.universal.gate.SafetyGate
import com.example.npc.extract.universal.model.RoleAssignmentResult
import com.example.npc.extract.universal.model.SlotRole
import com.example.npc.extract.universal.profile.InMemorySourceProfileRegistry
import com.example.npc.extract.universal.profile.SourceProfile
import com.example.npc.extract.universal.profile.SourceProfileRegistry
import java.time.Instant

/**
 * Назначенный слот с метаданными.
 */
data class ExtractedSlot(
    val role: SlotRole,
    val span: TextSpan,
    val rawText: String,
    val confidence: Float
)

/**
 * Результат выполнения UniversalExtractor.
 */
data class UniversalExtractionResult(
    val verdict: ExtractionVerdict,
    val transaction: FinancialTransaction?,
    val slots: List<ExtractedSlot>,
    val confidence: Float,
    val features: Map<String, Float> = emptyMap(),
    val isRefund: Boolean = false,
    val vetoReason: String? = null
)

/**
 * Отказоустойчивый универсальный экстрактор финансовых данных на базе слотов и лексикона.
 */
interface UniversalExtractor {
    fun extract(
        normalizedText: NormalizedText,
        tokenStream: TokenStream,
        sourceProfile: SourceProfile
    ): UniversalExtractionResult

    companion object : UniversalExtractor by DefaultUniversalExtractor() {
        fun create(
            registry: SourceProfileRegistry = InMemorySourceProfileRegistry(),
            safetyGate: SafetyGate = SafetyGate.Default
        ): UniversalExtractor = DefaultUniversalExtractor(registry, safetyGate)
    }
}

class DefaultUniversalExtractor(
    private val profileRegistry: SourceProfileRegistry = InMemorySourceProfileRegistry(),
    private val safetyGate: SafetyGate = SafetyGate.Default
) : UniversalExtractor {

    override fun extract(
        normalizedText: NormalizedText,
        tokenStream: TokenStream,
        sourceProfile: SourceProfile
    ): UniversalExtractionResult {
        if (tokenStream.size == 0 || normalizedText.normalized.isBlank()) {
            return UniversalExtractionResult(
                verdict = ExtractionVerdict.REJECT,
                transaction = null,
                slots = emptyList(),
                confidence = 0.0f,
                vetoReason = "Empty text or token stream"
            )
        }

        // 1. Генерация кандидатов сумм (Currency-bound constraint, ADR-302)
        val candidates = AmountCandidateGenerator.generate(tokenStream, sourceProfile.packageName)
        if (candidates.isEmpty()) {
            return UniversalExtractionResult(
                verdict = ExtractionVerdict.REJECT,
                transaction = null,
                slots = emptyList(),
                confidence = 0.0f,
                vetoReason = "No currency-bound amount candidates found"
            )
        }

        // 2. Извлечение контекстных признаков
        val candidateFeatures = FeatureExtractor.extract(candidates, tokenStream)

        // 3. Разрешение типа операции (DECLINED > REFUND > TRANSFER > CREDIT > DEBIT)
        val opType = OpTypeResolver.resolve(tokenStream)

        // 4. Комбинаторное решение ролей слотов (TX_AMOUNT vs BALANCE)
        val solution: RoleAssignmentResult? = RoleAssignmentSolver.solve(candidateFeatures)
        if (solution == null) {
            return UniversalExtractionResult(
                verdict = ExtractionVerdict.REJECT,
                transaction = null,
                slots = emptyList(),
                confidence = 0.0f,
                vetoReason = "Role assignment solver failed to find valid configuration"
            )
        }

        // 5. Защитный шлюз (SafetyGate: OTP & Promo Veto, Source Prior)
        val safetyCheck = safetyGate.evaluate(
            tokens = tokenStream,
            solution = solution,
            opType = opType,
            sourcePackage = sourceProfile.packageName,
            isKnownBankingApp = sourceProfile.isKnownBankingApp
        )

        if (safetyCheck.verdict == ExtractionVerdict.REJECT) {
            return UniversalExtractionResult(
                verdict = ExtractionVerdict.REJECT,
                transaction = null,
                slots = emptyList(),
                confidence = safetyCheck.finalScore,
                isRefund = opType.isRefund,
                vetoReason = safetyCheck.reason
            )
        }

        // 6. Формирование извлеченных слотов
        val extractedSlots = ArrayList<ExtractedSlot>()
        val txCand = solution.txAmount
        extractedSlots.add(
            ExtractedSlot(
                role = SlotRole.TX_AMOUNT,
                span = txCand.span,
                rawText = normalizedText.normalized.substring(
                    txCand.span.start.coerceIn(0, normalizedText.normalized.length),
                    txCand.span.end.coerceIn(0, normalizedText.normalized.length)
                ),
                confidence = solution.confidence
            )
        )

        val balCand = solution.balance
        if (balCand != null) {
            extractedSlots.add(
                ExtractedSlot(
                    role = SlotRole.BALANCE,
                    span = balCand.span,
                    rawText = normalizedText.normalized.substring(
                        balCand.span.start.coerceIn(0, normalizedText.normalized.length),
                        balCand.span.end.coerceIn(0, normalizedText.normalized.length)
                    ),
                    confidence = 0.90f
                )
            )
        }

        // 7. Извлечение маски карты
        var cardMask: String? = null
        for (i in 0 until tokenStream.size) {
            val token = tokenStream[i]
            if (token.type == TokenType.CARD_MASK) {
                cardMask = token.text
                break
            }
        }

        // 8. Извлечение мерчанта (токены слов между суммой и картой / остатком)
        val merchant = extractMerchant(tokenStream, txCand.tokenIndex)

        // 9. Сборка FinancialTransaction
        val moneyAmount = Money(txCand.minorUnits, txCand.currencyCode)
        val moneyBalance = balCand?.let {
            if (it.currencyCode == txCand.currencyCode) {
                Money(it.minorUnits, it.currencyCode)
            } else null
        }

        val txStatus = if (opType.isDeclined) TransactionStatus.DECLINED else TransactionStatus.SUCCESS
        val txType = if (opType.isDeclined) {
            TransactionType.DEBIT
        } else {
            opType.transactionType
        }

        val transaction = FinancialTransaction(
            id = 0L,
            eventId = null,
            bank = sourceProfile.packageName.ifBlank { "UNIVERSAL" },
            type = txType,
            amount = moneyAmount,
            balance = moneyBalance,
            merchant = merchant,
            accountMask = cardMask,
            status = txStatus,
            occurredAt = Instant.now(),
            extractorId = "universal.extractor",
            extractorVersion = 1,
            rawText = normalizedText.normalized
        )

        return UniversalExtractionResult(
            verdict = safetyCheck.verdict,
            transaction = transaction,
            slots = extractedSlots,
            confidence = safetyCheck.finalScore,
            isRefund = opType.isRefund,
            vetoReason = null
        )
    }

    private fun extractMerchant(tokens: TokenStream, txAmountTokenIdx: Int): String? {
        val candidateWords = ArrayList<String>()
        // Поиск слов после суммы, но до ключевых слов остатка/карты
        for (i in (txAmountTokenIdx + 1) until tokens.size) {
            val token = tokens[i]
            if (token.type == TokenType.CARD_MASK || token.keywordKind != null) {
                break
            }
            if ((token.type == TokenType.WORD || token.type == TokenType.URL) && token.text.length > 1) {
                candidateWords.add(token.text)
            }
        }
        return if (candidateWords.isNotEmpty()) candidateWords.joinToString(" ") else null
    }
}
