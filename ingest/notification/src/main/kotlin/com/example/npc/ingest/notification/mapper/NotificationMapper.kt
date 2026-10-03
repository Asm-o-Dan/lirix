package com.example.npc.ingest.notification.mapper

import android.app.Notification
import android.os.Bundle
import android.os.Parcelable
import android.service.notification.StatusBarNotification
import com.example.npc.ingest.notification.model.MessagingMessageData
import com.example.npc.ingest.notification.model.MessagingStyleData
import com.example.npc.ingest.notification.model.NotificationExtrasData
import com.example.npc.ingest.notification.model.NotificationRawPayload
import java.time.Instant

object NotificationMapper {

    fun extractPayload(
        sbn: StatusBarNotification,
        seq: Long,
        receivedAt: Instant
    ): NotificationRawPayload {
        val notification = sbn.notification ?: Notification()
        val extrasData = extractExtrasData(notification)

        return NotificationRawPayload(
            seq = seq,
            packageName = sbn.packageName,
            id = sbn.id,
            tag = sbn.tag,
            key = sbn.key,
            groupKey = sbn.groupKey,
            postTimeEpochMs = sbn.postTime,
            flags = notification.flags,
            channelId = notification.channelId,
            extras = extrasData,
            receivedAt = receivedAt
        )
    }

    fun extractExtrasData(notification: Notification): NotificationExtrasData {
        val extras = notification.extras ?: Bundle()

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()

        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.mapNotNull { it?.toString() }
            ?: emptyList()

        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
        val infoText = extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString()

        val progressMax = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val progressCurrent = extras.getInt(Notification.EXTRA_PROGRESS, 0)
        val isProgressIndeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)

        val conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
        val messagingStyle = extractMessagingStyle(extras)

        return NotificationExtrasData(
            title = title,
            text = text,
            bigText = bigText,
            textLines = textLines,
            subText = subText,
            infoText = infoText,
            progressMax = progressMax,
            progressCurrent = progressCurrent,
            isProgressIndeterminate = isProgressIndeterminate,
            conversationTitle = conversationTitle,
            messagingStyle = messagingStyle
        )
    }

    fun extractMessagingStyle(extras: Bundle): MessagingStyleData? {
        val hasMessages = extras.containsKey(Notification.EXTRA_MESSAGES)
        val hasHistoric = extras.containsKey(Notification.EXTRA_HISTORIC_MESSAGES)
        if (!hasMessages && !hasHistoric) {
            return null
        }

        val messagesList = parseMessages(extras.getParcelableArray(Notification.EXTRA_MESSAGES))
        val historicList = parseMessages(extras.getParcelableArray(Notification.EXTRA_HISTORIC_MESSAGES))

        val conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
        val isGroup = extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)

        return MessagingStyleData(
            conversationTitle = conversationTitle,
            isGroupConversation = isGroup,
            messages = messagesList.sortedBy { it.timestampMillis },
            historicMessages = historicList.sortedBy { it.timestampMillis }
        )
    }

    private fun parseMessages(parcelables: Array<Parcelable>?): List<MessagingMessageData> {
        if (parcelables.isNullOrEmpty()) return emptyList()

        val result = mutableListOf<MessagingMessageData>()
        for (item in parcelables) {
            if (item is Bundle) {
                val text = item.getCharSequence("text")?.toString().orEmpty()
                val time = item.getLong("time", 0L)
                val sender = item.getCharSequence("sender")?.toString()
                    ?: (item.get("person") as? Bundle)?.getCharSequence("name")?.toString()
                result.add(MessagingMessageData(text = text, timestampMillis = time, senderName = sender))
            }
        }
        return result
    }

    fun toPayloadJson(payload: NotificationRawPayload): String = buildString {
        append("{")
        append("\"seq\":").append(payload.seq).append(",")
        append("\"packageName\":\"").append(escapeJson(payload.packageName)).append("\",")
        append("\"id\":").append(payload.id).append(",")
        append("\"tag\":").append(payload.tag?.let { "\"${escapeJson(it)}\"" } ?: "null").append(",")
        append("\"key\":\"").append(escapeJson(payload.key)).append("\",")
        append("\"groupKey\":").append(payload.groupKey?.let { "\"${escapeJson(it)}\"" } ?: "null").append(",")
        append("\"postTimeEpochMs\":").append(payload.postTimeEpochMs).append(",")
        append("\"flags\":").append(payload.flags).append(",")
        append("\"channelId\":").append(payload.channelId?.let { "\"${escapeJson(it)}\"" } ?: "null").append(",")
        append("\"receivedAt\":\"").append(payload.receivedAt).append("\",")
        append("\"extras\":")
        append(serializeExtras(payload.extras))
        append("}")
    }

    private fun serializeExtras(extras: NotificationExtrasData): String = buildString {
        append("{")
        append("\"title\":").append(extras.title?.let { "\"${escapeJson(it)}\"" } ?: "null").append(",")
        append("\"text\":").append(extras.text?.let { "\"${escapeJson(it)}\"" } ?: "null").append(",")
        append("\"bigText\":").append(extras.bigText?.let { "\"${escapeJson(it)}\"" } ?: "null").append(",")
        append("\"textLines\":[")
        extras.textLines.forEachIndexed { i, line ->
            if (i > 0) append(",")
            append("\"").append(escapeJson(line)).append("\"")
        }
        append("],")
        append("\"subText\":").append(extras.subText?.let { "\"${escapeJson(it)}\"" } ?: "null").append(",")
        append("\"infoText\":").append(extras.infoText?.let { "\"${escapeJson(it)}\"" } ?: "null").append(",")
        append("\"progressMax\":").append(extras.progressMax).append(",")
        append("\"progressCurrent\":").append(extras.progressCurrent).append(",")
        append("\"isProgressIndeterminate\":").append(extras.isProgressIndeterminate).append(",")
        append("\"conversationTitle\":").append(extras.conversationTitle?.let { "\"${escapeJson(it)}\"" } ?: "null").append(",")
        append("\"messagingStyle\":")
        if (extras.messagingStyle != null) {
            append(serializeMessagingStyle(extras.messagingStyle))
        } else {
            append("null")
        }
        append("}")
    }

    private fun serializeMessagingStyle(m: MessagingStyleData): String = buildString {
        append("{")
        append("\"conversationTitle\":").append(m.conversationTitle?.let { "\"${escapeJson(it)}\"" } ?: "null").append(",")
        append("\"isGroupConversation\":").append(m.isGroupConversation).append(",")
        append("\"messages\":[")
        m.messages.forEachIndexed { i, msg ->
            if (i > 0) append(",")
            append(serializeMessage(msg))
        }
        append("],")
        append("\"historicMessages\":[")
        m.historicMessages.forEachIndexed { i, msg ->
            if (i > 0) append(",")
            append(serializeMessage(msg))
        }
        append("]")
        append("}")
    }

    private fun serializeMessage(msg: MessagingMessageData): String = buildString {
        append("{")
        append("\"text\":\"").append(escapeJson(msg.text)).append("\",")
        append("\"timestampMillis\":").append(msg.timestampMillis).append(",")
        append("\"senderName\":").append(msg.senderName?.let { "\"${escapeJson(it)}\"" } ?: "null")
        append("}")
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
