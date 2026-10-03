package com.example.npc.ui.timeline.mapper

import com.example.npc.core.model.Event
import com.example.npc.core.model.Lang
import com.example.npc.core.model.RawEvent
import com.example.npc.core.model.SourceHealth
import com.example.npc.core.model.SourceId
import com.example.npc.core.model.classify.Category
import com.example.npc.core.model.classify.Engine
import com.example.npc.core.model.finance.Direction
import com.example.npc.core.model.finance.FinancialTransaction
import com.example.npc.core.model.finance.TransactionStatus
import com.example.npc.ui.timeline.R
import com.example.npc.ui.timeline.model.EventUiModel
import com.example.npc.ui.timeline.model.FinancialTransactionUiModel
import com.example.npc.ui.timeline.model.SourceHealthStatus
import com.example.npc.ui.timeline.model.SourceHealthUiModel
import com.example.npc.ui.timeline.model.TransactionStatusUi
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

class EventUiMapper {

    fun toUiModel(
        event: Event,
        transaction: FinancialTransaction?
    ): EventUiModel = Companion.toUiModel(event, transaction)

    fun toTransactionUiModel(
        transaction: FinancialTransaction
    ): FinancialTransactionUiModel = Companion.toTransactionUiModel(transaction)

    companion object {

        private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
        private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd MMM, HH:mm")

        private val AMOUNT_FORMATTER = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US).apply {
            groupingSeparator = ' '
            decimalSeparator = '.'
        })

        fun toUiModel(
            event: Event,
            zoneId: ZoneId = ZoneId.systemDefault()
        ): EventUiModel = toUiModel(event, null, null, zoneId)

        fun toUiModel(
            event: Event,
            transaction: FinancialTransaction?
        ): EventUiModel = toUiModel(event, null, transaction, ZoneId.systemDefault())

        fun toUiModel(
            event: Event,
            rawEvent: RawEvent?,
            zoneId: ZoneId = ZoneId.systemDefault()
        ): EventUiModel = toUiModel(event, rawEvent, null, zoneId)

        fun toUiModel(
            event: Event,
            rawEvent: RawEvent? = null,
            transaction: FinancialTransaction? = null,
            zoneId: ZoneId = ZoneId.systemDefault()
        ): EventUiModel {
            val (sourceName, iconRes) = if (rawEvent != null) {
                when (rawEvent.source.value) {
                    SourceId.SMS.value -> Pair("SMS", R.drawable.ic_source_sms)
                    SourceId.MEDIA.value -> Pair("Медиа", R.drawable.ic_source_media)
                    else -> Pair("Уведомление", R.drawable.ic_source_notification)
                }
            } else {
                resolveSourceInfo(event.threadKey?.value)
            }
            val timeLabel = formatTimestamp(event.ts, zoneId)
            val langLabel = when (event.lang) {
                Lang.RU -> "RU"
                Lang.EN -> "EN"
                Lang.UNK -> "UNK"
            }
            val displayTitle = if (event.title.isBlank()) "Без названия" else event.title.trim()

            val category = if (event.category != Category.UNCLASSIFIED) {
                event.category
            } else if (transaction != null) {
                Category.FINANCE
            } else {
                Category.UNCLASSIFIED
            }

            val confidence = if (event.confidence > 0.0f) {
                event.confidence
            } else if (transaction != null) {
                1.0f
            } else {
                0.0f
            }

            val engineUsed = if (event.engineUsed != Engine.NONE) {
                event.engineUsed
            } else if (transaction != null) {
                Engine.RULES
            } else {
                Engine.NONE
            }

            val isUserCorrected = event.isUserCorrected

            val financialDataUi = transaction?.let { toTransactionUiModel(it) }

            return EventUiModel(
                id = event.id,
                rawId = event.rawId,
                displayTitle = displayTitle,
                displayText = event.text,
                normalizedText = event.normalizedText,
                timeLabel = timeLabel,
                sourceName = sourceName,
                sourceIconRes = iconRes,
                langLabel = langLabel,
                isUpdate = event.isUpdateOf != null,
                isUpdateOf = event.isUpdateOf,
                threadKey = event.threadKey?.value,
                packageName = rawEvent?.packageName,
                payloadJson = rawEvent?.payloadJson,
                category = category,
                confidence = confidence,
                engineUsed = engineUsed,
                isUserCorrected = isUserCorrected,
                contentFingerprint = event.contentFingerprint,
                financialData = financialDataUi,
                pipelineRevisionId = event.pipelineRevisionId
            )
        }

        fun toTransactionUiModel(txn: FinancialTransaction): FinancialTransactionUiModel {
            val majorAmount = txn.amount.toMajorBigDecimal()
            val formattedNumber = AMOUNT_FORMATTER.format(majorAmount)

            val signedFormattedAmount = when (txn.type) {
                Direction.DEBIT -> "-$formattedNumber"
                Direction.CREDIT -> "+$formattedNumber"
                Direction.TRANSFER, Direction.UNKNOWN -> formattedNumber
            }

            val formattedBalance = txn.balance?.let { bal ->
                val balNumber = AMOUNT_FORMATTER.format(bal.toMajorBigDecimal())
                "Остаток: $balNumber ${bal.currency.symbol}"
            }

            val isDeclined = txn.status == TransactionStatus.DECLINED || isDeclinedTransaction(txn)
            val statusUi = if (isDeclined) {
                TransactionStatusUi.DECLINED
            } else {
                TransactionStatusUi.COMPLETED
            }

            return FinancialTransactionUiModel(
                id = txn.id,
                bank = txn.bank,
                direction = txn.type,
                formattedAmount = signedFormattedAmount,
                formattedBalance = formattedBalance,
                merchant = txn.merchant,
                accountMask = txn.accountMask,
                status = statusUi,
                isDeclined = isDeclined,
                currencyCode = txn.amount.currency.value,
                currencySymbol = txn.amount.currency.symbol,
                extractorInfo = "${txn.extractorId} v${txn.extractorVersion}"
            )
        }

        private fun isDeclinedTransaction(txn: FinancialTransaction): Boolean {
            val m = txn.merchant?.uppercase(Locale.ROOT).orEmpty()
            return m.contains("DECLINED") || m.contains("ОТКАЗ") || m.contains("REFUZATA")
        }

        fun toHealthUiModel(
            health: SourceHealth,
            now: Instant = Instant.now(),
            zoneId: ZoneId = ZoneId.systemDefault()
        ): SourceHealthUiModel {
            val status = resolveHealthStatus(health, now)
            val (displayName, iconRes) = when (health.source.value) {
                SourceId.SMS.value -> Pair("SMS", R.drawable.ic_source_sms)
                SourceId.MEDIA.value -> Pair("Медиа", R.drawable.ic_source_media)
                else -> Pair("Уведомления", R.drawable.ic_source_notification)
            }
            val lastEventLabel = health.lastEventAt?.let { formatRelativeTime(it, now, zoneId) } ?: "нет событий"

            return SourceHealthUiModel(
                sourceId = health.source.value,
                displayName = displayName,
                iconRes = iconRes,
                status = status,
                events24h = health.events24h,
                events24hLabel = "24ч: ${health.events24h}",
                lastEventTimeLabel = lastEventLabel,
                queueDepth = health.queueDepth,
                lastError = health.lastError
            )
        }

        fun resolveHealthStatus(health: SourceHealth, now: Instant = Instant.now()): SourceHealthStatus {
            if (!health.lastError.isNullOrBlank() || health.queueDepth > 20) {
                return SourceHealthStatus.RED
            }
            val isStale = health.lastEventAt == null || ChronoUnit.HOURS.between(health.lastEventAt, now) >= 24
            if (isStale && health.events24h == 0 && health.queueDepth == 0) {
                return SourceHealthStatus.YELLOW
            }
            if (health.queueDepth in 6..20) {
                return SourceHealthStatus.YELLOW
            }
            return SourceHealthStatus.GREEN
        }

        private fun resolveSourceInfo(threadKey: String?): Pair<String, Int> {
            if (threadKey == null) {
                return Pair("Уведомление", R.drawable.ic_source_notification)
            }
            return when {
                threadKey.startsWith("sms:") -> Pair("SMS", R.drawable.ic_source_sms)
                threadKey.startsWith("media:") -> Pair("Медиа", R.drawable.ic_source_media)
                else -> Pair("Уведомление", R.drawable.ic_source_notification)
            }
        }

        private fun formatTimestamp(timestamp: Instant, zoneId: ZoneId): String {
            val eventZdt = timestamp.atZone(zoneId)
            val nowZdt = Instant.now().atZone(zoneId)
            return if (eventZdt.toLocalDate() == nowZdt.toLocalDate()) {
                eventZdt.format(TIME_FORMATTER)
            } else {
                eventZdt.format(DATE_TIME_FORMATTER)
            }
        }

        private fun formatRelativeTime(timestamp: Instant, now: Instant, zoneId: ZoneId): String {
            val minutes = ChronoUnit.MINUTES.between(timestamp, now)
            return when {
                minutes < 1 -> "только что"
                minutes < 60 -> "$minutes мин назад"
                minutes < 1440 -> "${minutes / 60} ч назад"
                else -> formatTimestamp(timestamp, zoneId)
            }
        }

        fun formatJsonPretty(rawJson: String): String {
            val trimmed = rawJson.trim()
            if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
                return rawJson
            }
            return try {
                val jsonElement = org.json.JSONObject(trimmed)
                val res = jsonElement.toString(2)
                if (res.isNotBlank()) res else prettyPrintJson(trimmed)
            } catch (e: Throwable) {
                try {
                    val jsonArray = org.json.JSONArray(trimmed)
                    val res = jsonArray.toString(2)
                    if (res.isNotBlank()) res else prettyPrintJson(trimmed)
                } catch (e2: Throwable) {
                    try {
                        prettyPrintJson(trimmed)
                    } catch (e3: Throwable) {
                        rawJson
                    }
                }
            }
        }

        private fun prettyPrintJson(json: String, indent: String = "  "): String {
            val sb = StringBuilder()
            var indentLevel = 0
            var inQuotes = false
            var isEscaped = false

            for (i in json.indices) {
                val char = json[i]
                if (isEscaped) {
                    sb.append(char)
                    isEscaped = false
                    continue
                }
                if (char == '\\') {
                    sb.append(char)
                    isEscaped = true
                    continue
                }
                if (char == '"') {
                    inQuotes = !inQuotes
                    sb.append(char)
                    continue
                }
                if (inQuotes) {
                    sb.append(char)
                    continue
                }

                when (char) {
                    '{', '[' -> {
                        sb.append(char)
                        sb.append("\n")
                        indentLevel++
                        sb.append(indent.repeat(indentLevel))
                    }
                    '}', ']' -> {
                        sb.append("\n")
                        indentLevel = (indentLevel - 1).coerceAtLeast(0)
                        sb.append(indent.repeat(indentLevel))
                        sb.append(char)
                    }
                    ',' -> {
                        sb.append(char)
                        sb.append("\n")
                        sb.append(indent.repeat(indentLevel))
                    }
                    ':' -> {
                        sb.append(": ")
                    }
                    ' ', '\t', '\n', '\r' -> {
                    }
                    else -> {
                        sb.append(char)
                    }
                }
            }
            return sb.toString()
        }
    }
}
