package com.example.npc.extract.finance

import com.example.npc.core.model.Event
import com.example.npc.core.model.extract.CurrencyResolver
import com.example.npc.core.model.extract.ExtractorInput
import com.example.npc.core.model.extract.FinanceExtractor
import com.example.npc.core.model.extract.ParsedFinanceResult
import com.example.npc.core.model.finance.CurrencyCode
import com.example.npc.core.model.finance.FinancialTransaction
import com.example.npc.core.model.finance.TransactionStatus
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
    fun runExtraction(event: Event, packageName: String): FinancialTransaction? {
        val extractor = selectExtractor(packageName, event.title) ?: return null

        val sanitizedText = RegionalTextSanitizer.sanitize(event.text)
        if (sanitizedText.isBlank()) return null

        val resolver = object : CurrencyResolver {
            override fun resolve(token: String, contextPackage: String?): CurrencyCode? {
                return BankCurrencyResolver.resolve(token, contextPackage ?: packageName)
            }
        }

        val input = ExtractorInput(
            text = sanitizedText,
            senderOrTitle = event.title,
            postedAt = event.ts,
            currencyResolver = resolver
        )

        val result = circuitBreaker.execute(extractor.id) {
            extractor.extract(input)
        }.getOrNull() ?: return null

        return when (result) {
            is ParsedFinanceResult.Success -> {
                val bal = result.balance
                val safeBalance = if (bal != null && bal.currency == result.amount.currency) {
                    bal
                } else null

                FinancialTransaction(
                    id = 0L,
                    eventId = if (event.id > 0L) event.id else null,
                    bank = extractor.supportedBank,
                    type = result.type,
                    amount = result.amount,
                    balance = safeBalance,
                    merchant = result.merchant,
                    accountMask = result.accountMask,
                    status = result.status,
                    occurredAt = event.ts,
                    extractorId = extractor.id,
                    extractorVersion = extractor.version,
                    rawText = event.text
                )
            }
            is ParsedFinanceResult.Declined -> {
                FinancialTransaction(
                    id = 0L,
                    eventId = if (event.id > 0L) event.id else null,
                    bank = extractor.supportedBank,
                    type = result.type,
                    amount = result.amount,
                    balance = null,
                    merchant = result.merchant,
                    accountMask = result.accountMask,
                    status = TransactionStatus.DECLINED,
                    occurredAt = event.ts,
                    extractorId = extractor.id,
                    extractorVersion = extractor.version,
                    rawText = event.text
                )
            }
            is ParsedFinanceResult.NotApplicable, is ParsedFinanceResult.Failed -> null
        }
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
                result
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
