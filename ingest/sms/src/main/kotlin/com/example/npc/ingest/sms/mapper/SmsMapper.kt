package com.example.npc.ingest.sms.mapper

import android.database.Cursor
import android.provider.Telephony
import android.telephony.SmsMessage
import com.example.npc.core.model.DeduplicationKey
import com.example.npc.core.model.Event
import com.example.npc.core.model.RawEvent
import com.example.npc.core.model.SourceId
import com.example.npc.core.model.ThreadKey
import com.example.npc.core.model.normalize.EventNormalizer
import com.example.npc.ingest.sms.SmsContract
import com.example.npc.ingest.sms.model.SmsRawPayload
import java.time.Instant

object SmsMapper {

    fun toPayloadJson(
        originAddress: String,
        body: String,
        timestampMillis: Long,
        subId: Int? = null
    ): String = buildString {
        append("{")
        append("\"originatingAddress\":\"").append(escapeJson(originAddress)).append("\",")
        append("\"messageBody\":\"").append(escapeJson(body)).append("\",")
        append("\"timestampMillis\":").append(timestampMillis)
        if (subId != null) {
            append(",\"subId\":").append(subId)
        }
        append("}")
    }

    fun toRawEvent(payload: SmsRawPayload, deduplicationKey: DeduplicationKey): RawEvent {
        return RawEvent(
            id = 0L,
            seq = payload.seq,
            source = SourceId.SMS,
            packageName = "android.telephony.sms",
            receivedAt = payload.receivedAt,
            payloadJson = toPayloadJson(
                originAddress = payload.originAddress,
                body = payload.body,
                timestampMillis = payload.timestampMillis,
                subId = payload.subId
            ),
            hash = deduplicationKey
        )
    }

    fun toEvent(rawEvent: RawEvent, contract: SmsContract): Event {
        val clean = EventNormalizer.cleanText(contract.body)
        val detectedLang = EventNormalizer.detectLang(clean)
        return Event(
            id = 0L,
            rawId = rawEvent.id,
            ts = Instant.ofEpochMilli(contract.timestampMillis),
            title = contract.originAddress,
            text = contract.body,
            normalizedText = clean,
            lang = detectedLang,
            threadKey = ThreadKey(contract.originAddress.trim()),
            isUpdateOf = null
        )
    }

    fun fromSmsMessages(messages: Array<SmsMessage>?): SmsContract? {
        if (messages.isNullOrEmpty()) return null
        val firstMsg = messages[0]
        val rawAddress = firstMsg.displayOriginatingAddress ?: firstMsg.originatingAddress
        val originAddress = if (rawAddress.isNullOrBlank()) "UNKNOWN" else rawAddress.trim()
        val timestamp = firstMsg.timestampMillis

        val sb = StringBuilder()
        for (msg in messages) {
            val partBody = msg.displayMessageBody ?: msg.messageBody ?: ""
            sb.append(partBody)
        }
        val fullBody = sb.toString()

        return SmsContract(
            originAddress = originAddress,
            body = fullBody,
            timestampMillis = maxOf(0L, timestamp)
        )
    }

    fun fromCursor(cursor: Cursor): SmsContract {
        val addressIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
        val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
        val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)

        val rawAddress = if (addressIdx >= 0 && !cursor.isNull(addressIdx)) cursor.getString(addressIdx) else null
        val originAddress = if (rawAddress.isNullOrBlank()) "UNKNOWN" else rawAddress.trim()

        val body = if (bodyIdx >= 0 && !cursor.isNull(bodyIdx)) cursor.getString(bodyIdx) ?: "" else ""

        val date = if (dateIdx >= 0 && !cursor.isNull(dateIdx)) cursor.getLong(dateIdx) else 0L
        val validDate = if (date >= 0L) date else 0L

        return SmsContract(
            originAddress = originAddress,
            body = body,
            timestampMillis = validDate
        )
    }

    private fun escapeJson(str: String): String {
        val sb = StringBuilder()
        for (c in str) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (c.code in 0..0x1F) {
                        sb.append(String.format("\\u%04x", c.code))
                    } else {
                        sb.append(c)
                    }
                }
            }
        }
        return sb.toString()
    }
}
