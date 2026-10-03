package com.example.npc.pipeline.runtime.node.builtin

import com.example.npc.core.model.finance.CurrencyCode
import com.example.npc.core.model.finance.FinancialTransaction
import com.example.npc.core.model.finance.Money
import com.example.npc.core.model.finance.TransactionStatus
import com.example.npc.core.model.finance.TransactionDirectionResolver
import com.example.npc.core.model.finance.ExtractorKind
import com.example.npc.core.model.finance.TxStatus
import com.example.npc.core.model.finance.TransactionType
import com.example.npc.pipeline.nodes.api.effect.EffectBuffer
import com.example.npc.pipeline.nodes.api.executor.NodeExecutor
import com.example.npc.pipeline.nodes.api.executor.StepResult
import com.example.npc.pipeline.nodes.api.frame.Frame
import com.example.npc.pipeline.runtime.hotswap.ActiveGenerationProvider
import com.example.npc.pipeline.runtime.hotswap.CompiledTemplate
import java.time.Instant

/**
 * Исполнитель узла extract.template_bank на горячем пути конвейера.
 * 
 * Осуществляет префильтрацию по sourceKey (packageName) за O(1) и литералам requiredLiterals.
 * При совпадении собирает FinancialTransaction и пишет в регистр R_TX (outTxRefSlot).
 */
class TemplateBankNodeExecutor(
    val inTextSlot: Int,
    val inPackageNameSlot: Int,
    val outTxRefSlot: Int,
    val generationProvider: ActiveGenerationProvider,
    val nextPc: Int
) : NodeExecutor {

    override fun execute(frame: Frame, buffer: EffectBuffer): StepResult {
        // 1. Проверяем, не заполнена ли уже транзакция статическим экстрактором
        if (outTxRefSlot in frame.refs.indices && frame.refs[outTxRefSlot] != null) {
            return StepResult.jump(nextPc)
        }

        // 2. Извлекаем входной текст и пакет приложения
        val textReg = if (inTextSlot in frame.texts.indices) frame.texts[inTextSlot] else return StepResult.jump(nextPc)
        if (textReg.length == 0) return StepResult.jump(nextPc)

        val packageName = if (inPackageNameSlot in frame.refs.indices) frame.refs[inPackageNameSlot] as? String ?: "" else ""
        if (packageName.isBlank()) return StepResult.jump(nextPc)

        // 3. Получаем актуальный банк поколения
        val bank = generationProvider.current().bank
        val textStr = textReg.materializeString()

        // 4. Поиск сначала в overrideTemplates, затем в fallbackTemplates
        val overrideTemplates = bank.overrideTemplatesBySource[packageName] ?: emptyList()
        val fallbackTemplates = bank.fallbackTemplatesBySource[packageName] ?: emptyList()

        val matchedTx = matchTemplates(overrideTemplates, textStr, packageName)
            ?: matchTemplates(fallbackTemplates, textStr, packageName)

        if (matchedTx != null) {
            frame.refs[outTxRefSlot] = matchedTx
        }

        return StepResult.jump(nextPc)
    }

    private fun matchTemplates(
        templates: List<CompiledTemplate>,
        text: String,
        sourcePackage: String
    ): FinancialTransaction? {
        for (template in templates) {
            // Быстрый строковый префильтр: все обязательные литералы обязаны присутствовать
            var literalsMatch = true
            for (lit in template.requiredLiterals) {
                if (!text.contains(lit, ignoreCase = true)) {
                    literalsMatch = false
                    break
                }
            }
            if (!literalsMatch) continue

            // 1. Конвейер слотовых регулярок (Slot-Decomposed Regex Pipeline, OPT-PIPE-001)
            val pipeline = template.resolvedPipeline
            if (pipeline != null) {
                // Быстрая проверка совпадения якорного правила
                if (!pipeline.matchesAnchor(text)) {
                    continue
                }

                // Извлечение слотов через специализированные экстракторы
                val extracted = pipeline.extract(text) ?: continue

                val currency = resolveCurrency(
                    extracted.currency ?: template.constants["currency"] ?: template.constants["defaultCurrency"],
                    sourcePackage
                )
                val minorUnits = parseAmountToMinor(extracted.amount, currency)
                val balanceMinor = extracted.balance?.let { parseAmountToMinor(it, currency) }

                val direction = TransactionDirectionResolver.resolve(
                    body = text,
                    explicitType = TransactionType.fromStringOrNull(
                        template.constants["opType"] ?: template.constants["transactionType"]
                    )
                )
                if (direction.isSuppressed) continue

                return FinancialTransaction(
                    id = 0L,
                    eventId = null,
                    bank = sourcePackage,
                    type = direction.type,
                    isRefund = direction.isRefund || (direction.type == TransactionType.CREDIT && template.constants["isRefund"] == "true"),
                    amount = Money(minorUnits, currency),
                    balance = balanceMinor?.let { Money(it, currency) },
                    merchant = extracted.merchant,
                    accountMask = extracted.cardMask,
                    status = if (direction.isDeclined) TransactionStatus.DECLINED else TransactionStatus.SUCCESS,
                    occurredAt = Instant.now(),
                    extractorId = "template:${template.id}",
                    extractorVersion = 1,
                    rawText = text,
                    extractorKind = ExtractorKind.TEMPLATE,
                    templateId = template.id,
                    txStatus = if (direction.type == TransactionType.UNKNOWN) TxStatus.SUGGESTED else TxStatus.CONFIRMED_AUTO
                )
            }

            // 2. Классическое RE2/J монолитное сопоставление (обратная совместимость)
            val matcher = template.pattern.matcher(text)
            if (matcher.find()) {
                val amountStr = extractGroupSafely(matcher, "amount") 
                    ?: extractGroupSafely(matcher, "TX_AMOUNT") 
                    ?: extractGroupSafely(matcher, "tx_amount") 
                    ?: continue
                val currStr = extractGroupSafely(matcher, "curr") ?: extractGroupSafely(matcher, "currency")
                val cardStr = extractGroupSafely(matcher, "card") 
                    ?: extractGroupSafely(matcher, "mask") 
                    ?: extractGroupSafely(matcher, "CARD_MASK") 
                    ?: extractGroupSafely(matcher, "card_mask")
                val balStr = extractGroupSafely(matcher, "bal") 
                    ?: extractGroupSafely(matcher, "balance") 
                    ?: extractGroupSafely(matcher, "BALANCE")
                val merchantStr = extractGroupSafely(matcher, "merchant") ?: extractGroupSafely(matcher, "MERCHANT")

                val currency = resolveCurrency(
                    currStr ?: template.constants["currency"] ?: template.constants["defaultCurrency"],
                    sourcePackage
                )
                val minorUnits = parseAmountToMinor(amountStr, currency)
                val balanceMinor = balStr?.let { parseAmountToMinor(it, currency) }

                val direction = TransactionDirectionResolver.resolve(
                    body = text,
                    explicitType = TransactionType.fromStringOrNull(
                        template.constants["opType"] ?: template.constants["transactionType"]
                    )
                )
                if (direction.isSuppressed) continue

                return FinancialTransaction(
                    id = 0L,
                    eventId = null,
                    bank = sourcePackage,
                    type = direction.type,
                    isRefund = direction.isRefund || (direction.type == TransactionType.CREDIT && template.constants["isRefund"] == "true"),
                    amount = Money(minorUnits, currency),
                    balance = balanceMinor?.let { Money(it, currency) },
                    merchant = merchantStr,
                    accountMask = cardStr,
                    status = if (direction.isDeclined) TransactionStatus.DECLINED else TransactionStatus.SUCCESS,
                    occurredAt = Instant.now(),
                    extractorId = "template:${template.id}",
                    extractorVersion = 1,
                    rawText = text,
                    extractorKind = ExtractorKind.TEMPLATE,
                    templateId = template.id,
                    txStatus = if (direction.type == TransactionType.UNKNOWN) TxStatus.SUGGESTED else TxStatus.CONFIRMED_AUTO
                )
            }
        }
        return null
    }

    private fun extractGroupSafely(matcher: com.google.re2j.Matcher, name: String): String? {
        return try {
            matcher.group(name)
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveCurrency(currStr: String?, sourcePackage: String): CurrencyCode {
        val lower = currStr?.lowercase() ?: ""
        return when {
            lower.contains("mdl") || lower.contains("лей") -> CurrencyCode.MDL
            lower.contains("rup") -> CurrencyCode.RUP
            lower.contains("rub") -> CurrencyCode.RUB
            lower.contains("usd") || lower.contains("$") -> CurrencyCode.USD
            lower.contains("eur") || lower.contains("€") -> CurrencyCode.EUR
            lower.contains("руб") || lower.contains("р.") -> {
                if (sourcePackage.contains("apb") || sourcePackage.contains("prisbank")) CurrencyCode.RUP else CurrencyCode.RUB
            }
            else -> CurrencyCode.MDL
        }
    }

    private fun parseAmountToMinor(amountStr: String, currency: CurrencyCode): Long {
        val clean = amountStr.replace(" ", "").replace("\u00A0", "").replace("\u202F", "")
        return if (clean.contains(',')) {
            val parts = clean.split(',')
            val intPart = parts[0].toLongOrNull() ?: 0L
            val fracPart = if (parts.size > 1) parts[1].padEnd(2, '0').take(2).toLongOrNull() ?: 0L else 0L
            intPart * 100L + fracPart
        } else if (clean.contains('.')) {
            val parts = clean.split('.')
            val intPart = parts[0].toLongOrNull() ?: 0L
            val fracPart = if (parts.size > 1) parts[1].padEnd(2, '0').take(2).toLongOrNull() ?: 0L else 0L
            intPart * 100L + fracPart
        } else {
            (clean.toLongOrNull() ?: 0L) * 100L
        }
    }
}
