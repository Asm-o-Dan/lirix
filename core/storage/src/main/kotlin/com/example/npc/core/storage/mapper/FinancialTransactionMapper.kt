package com.example.npc.core.storage.mapper

import com.example.npc.core.model.finance.CurrencyCode
import com.example.npc.core.model.finance.FinancialTransaction
import com.example.npc.core.model.finance.Money
import com.example.npc.core.model.finance.TransactionStatus
import com.example.npc.core.model.finance.TransactionType
import com.example.npc.core.storage.entity.FinancialTransactionEntity
import java.time.Instant

object FinancialTransactionMapper {

    fun toEntity(domain: FinancialTransaction): FinancialTransactionEntity {
        val tmplId = domain.templateId ?: if (domain.extractorId.startsWith("template:")) {
            domain.extractorId.removePrefix("template:")
        } else null
        val extKind = if (domain.extractorKind == com.example.npc.core.model.finance.ExtractorKind.TEMPLATE || tmplId != null) {
            com.example.npc.core.model.finance.ExtractorKind.TEMPLATE.name
        } else {
            domain.extractorKind.name
        }

        return FinancialTransactionEntity(
            id = domain.id,
            eventId = domain.eventId,
            bank = domain.bank,
            direction = domain.type.name,
            amountMinor = domain.amount.minor,
            currency = domain.amount.currency.value,
            balanceMinor = domain.balance?.minor,
            balanceCurrency = domain.balance?.currency?.value,
            merchant = domain.merchant,
            accountMask = domain.accountMask,
            occurredAt = domain.occurredAt.toEpochMilli(),
            extractorId = domain.extractorId,
            extractorVersion = domain.extractorVersion,
            createdAt = domain.createdAt.toEpochMilli(),
            extractorKind = extKind,
            templateId = tmplId
        )
    }

    fun toDomain(entity: FinancialTransactionEntity, rawText: String = entity.merchant ?: entity.bank): FinancialTransaction {
        val currencyCode = CurrencyCode.ofOrNull(entity.currency) ?: CurrencyCode.RUP
        val balanceCurrencyCode = entity.balanceCurrency?.let { CurrencyCode.ofOrNull(it) } ?: currencyCode

        val balance = if (entity.balanceMinor != null) {
            Money(entity.balanceMinor, balanceCurrencyCode)
        } else {
            null
        }

        val tmplId = entity.templateId ?: if (entity.extractorId.startsWith("template:")) {
            entity.extractorId.removePrefix("template:")
        } else null
        val extKind = if (entity.extractorKind.equals("TEMPLATE", ignoreCase = true) || tmplId != null) {
            com.example.npc.core.model.finance.ExtractorKind.TEMPLATE
        } else {
            com.example.npc.core.model.finance.ExtractorKind.fromStringOrDefault(entity.extractorKind)
        }

        return FinancialTransaction(
            id = entity.id,
            eventId = entity.eventId?.takeIf { it > 0L },
            bank = entity.bank,
            type = TransactionType.fromStringOrNull(entity.direction) ?: TransactionType.DEBIT,
            amount = Money(entity.amountMinor, currencyCode),
            balance = balance,
            merchant = entity.merchant,
            accountMask = entity.accountMask,
            status = TransactionStatus.COMPLETED,
            occurredAt = Instant.ofEpochMilli(entity.occurredAt),
            extractorId = entity.extractorId,
            extractorVersion = entity.extractorVersion,
            rawText = rawText.ifBlank { entity.bank },
            createdAt = Instant.ofEpochMilli(entity.createdAt),
            extractorKind = extKind,
            templateId = tmplId
        )
    }
}
