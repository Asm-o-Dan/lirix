package com.example.npc.extract.finance

import com.example.npc.core.model.Event
import com.example.npc.core.model.extract.CurrencyResolver
import com.example.npc.core.model.extract.ExtractorInput
import com.example.npc.core.model.extract.FinanceExtractor
import com.example.npc.core.model.extract.ParsedFinanceResult
import com.example.npc.core.model.finance.CurrencyCode
import com.example.npc.core.model.finance.FinancialTransaction
import com.example.npc.core.model.finance.TransactionStatus
import com.example.npc.core.model.finance.TransactionType
import com.example.npc.core.model.finance.TransactionDirectionResolver
import com.example.npc.core.model.finance.TxStatus
import com.example.npc.core.text.TextNormalizer
import com.example.npc.core.text.Lexer
import com.example.npc.extract.universal.UniversalExtractor
import com.example.npc.extract.universal.profile.InMemorySourceProfileRegistry
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Песочница изолированного исполнения банковских экстракторов.
 * Обеспечивает санитизацию, защиту таймаутом и изоляцию сбоев через CircuitBreaker.
 */
class IsolatedExtractorRunner(
    private val extractors: List<FinanceExtractor>,
    private val circuitBreaker: CircuitBreaker = CircuitBreaker()
) {

    /**
     * Основная точка входа для конвейера обработки событий.
     */
    private val universalExtractor = UniversalExtractor.create()
    private val sourceProfiles = InMemorySourceProfileRegistry()

    fun runExtraction(event: Event, packageName: String): FinancialTransaction? {
        if (!circuitBreaker.canExecute()) return null
        val direction = TransactionDirectionResolver.resolve(event.title, event.text)
        if (direction.isSuppressed) return null
        val extractor = selectExtractor(packageName, event.title)
        val sanitizedText = RegionalTextSanitizer.sanitize(event.text)
        val resolver = object : CurrencyResolver {
            override fun resolve(token: String, contextPackage: String?): CurrencyCode? =
                BankCurrencyResolver.resolve(token, contextPackage ?: packageName)
        }
        val input = ExtractorInput(sanitizedText, event.title, event.ts, resolver)
        val result = extractor?.let {
            circuitBreaker.execute(it.id) { it.extract(input) }.getOrNull()
        }
        val rawText = event.text.ifBlank { event.title }
        val parsed = when (result) {
            is ParsedFinanceResult.Success -> FinancialTransaction(
                eventId = event.id.takeIf { it > 0L }, bank = extractor!!.supportedBank,
                type = direction.type, amount = result.amount,
                balance = result.balance?.takeIf { it.currency == result.amount.currency },
                merchant = result.merchant, accountMask = result.accountMask,
                status = if (direction.isDeclined) TransactionStatus.DECLINED else result.status,
                occurredAt = event.ts, extractorId = extractor.id,
                extractorVersion = maxOf(2, extractor.version), rawText = rawText,
                isRefund = direction.isRefund,
                txStatus = if (direction.type == TransactionType.UNKNOWN) TxStatus.SUGGESTED else TxStatus.CONFIRMED_AUTO
            )
            is ParsedFinanceResult.Declined -> FinancialTransaction(
                eventId = event.id.takeIf { it > 0L }, bank = extractor!!.supportedBank,
                type = direction.type, amount = result.amount, balance = null,
                merchant = result.merchant, accountMask = result.accountMask,
                status = TransactionStatus.DECLINED, occurredAt = event.ts,
                extractorId = extractor.id, extractorVersion = maxOf(2, extractor.version), rawText = rawText,
                isRefund = direction.isRefund,
                txStatus = if (direction.type == TransactionType.UNKNOWN) TxStatus.SUGGESTED else TxStatus.CONFIRMED_AUTO
            )
            else -> null
        }
        if (parsed != null) return parsed

        // The live orchestrator already enforces source trust. Keep the existing bank-specific
        // amount parsers, then fall back for unrecognized formats at ANY admitted bank.
        return circuitBreaker.execute("universal.extractor") {
            val combined = listOf(event.title, event.text).filter { it.isNotBlank() }.joinToString("\n")
            if (combined.isBlank() || combined.length > 4096) return@execute null
            val normalized = TextNormalizer.normalize(combined)
            val extracted = universalExtractor.extract(
                normalized, Lexer.tokenize(normalized), sourceProfiles.getProfile(packageName)
            )
            extracted.transaction?.copy(
                eventId = event.id.takeIf { it > 0L },
                bank = extractor?.supportedBank ?: packageName.ifBlank { "UNIVERSAL" },
                occurredAt = event.ts, rawText = rawText, extractorVersion = 2
            )
        }.getOrNull()
    }

    /**
     * Асинхронный запуск одного экстрактора с корутинным таймаутом (для специфических нужд конвейера).
     */
    suspend fun run(
        extractor: FinanceExtractor,
        input: ExtractorInput
    ): ParsedFinanceResult {
        if (!circuitBreaker.canExecute()) {
            return ParsedFinanceResult.Failed("Circuit breaker OPEN for extractor: ${extractor.id}")
        }

        val direction = TransactionDirectionResolver.resolve(input.senderOrTitle, input.text)
        if (direction.isSuppressed) return ParsedFinanceResult.NotApplicable
        val sanitizedText = RegionalTextSanitizer.sanitize(input.text)
        val sanitizedInput = input.copy(text = sanitizedText)

        return try {
            val result = withTimeoutOrNull(circuitBreaker.timeBudgetMs) {
                extractor.extract(sanitizedInput)
            }

            if (result == null) {
                circuitBreaker.recordFailure()
                ParsedFinanceResult.Failed("Execution timeout exceeded (${circuitBreaker.timeBudgetMs} ms) for ${extractor.id}")
            } else {
                circuitBreaker.recordSuccess()
                when (result) {
                    is ParsedFinanceResult.Success -> result.copy(
                        type = direction.type,
                        status = if (direction.isDeclined) TransactionStatus.DECLINED else result.status
                    )
                    is ParsedFinanceResult.Declined -> result.copy(type = direction.type)
                    else -> result
                }
            }
        } catch (t: Throwable) {
            circuitBreaker.recordFailure()
            ParsedFinanceResult.Failed("Extractor exception: ${t.message}")
        }
    }

    private fun selectExtractor(packageName: String, senderOrTitle: String?): FinanceExtractor? {
        val pkg = packageName.lowercase()
        return when {
            pkg.contains("apb") -> extractors.firstOrNull { it.supportedBank == "APB" }
            pkg.contains("prisbank") || pkg.contains("sberbank.pmr") -> extractors.firstOrNull { it.supportedBank == "PRISBANK" }
            pkg.contains("maib") -> extractors.firstOrNull { it.supportedBank == "MAIB" }
            pkg.contains("messaging") || pkg.contains("mms") || pkg.contains("sms") -> {
                extractors.firstOrNull { it.id == "bank.sms" || it.supportedBank == "GENERIC_BANK_SMS" }
            }
            else -> {
                val sender = senderOrTitle?.lowercase() ?: ""
                when {
                    sender.contains("apb") || sender.contains("агропром") -> extractors.firstOrNull { it.supportedBank == "APB" }
                    sender.contains("prisbank") || sender.contains("сбербанк пмр") -> extractors.firstOrNull { it.supportedBank == "PRISBANK" }
                    sender.contains("maib") -> extractors.firstOrNull { it.supportedBank == "MAIB" }
                    sender == "900" || sender.contains("sber") || sender.contains("tinkoff") -> {
                        extractors.firstOrNull { it.id == "bank.sms" || it.supportedBank == "GENERIC_BANK_SMS" }
                    }
                    else -> extractors.firstOrNull {
                        it.supportedBank.equals(packageName, ignoreCase = true) || it.id.equals(packageName, ignoreCase = true)
                    }
                }
            }
        }
    }
}
