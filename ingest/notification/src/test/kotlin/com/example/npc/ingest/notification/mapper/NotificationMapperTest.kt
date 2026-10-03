package com.example.npc.ingest.notification.mapper

import com.example.npc.ingest.notification.model.MessagingMessageData
import com.example.npc.ingest.notification.model.MessagingStyleData
import com.example.npc.ingest.notification.model.NotificationExtrasData
import com.example.npc.ingest.notification.model.NotificationRawPayload
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import java.time.Instant

class NotificationMapperTest {

    @Test
    fun `toPayloadJson serializes all payload fields to JSON`() {
        val messaging = MessagingStyleData(
            conversationTitle = "Dev Team",
            isGroupConversation = true,
            messages = listOf(
                MessagingMessageData("Deploy ready \"v1\"", 1_700_000_100_000L, "Lead")
            ),
            historicMessages = emptyList()
        )
        val extras = NotificationExtrasData(
            title = "Notification Title",
            text = "Notification Text with \n newline",
            bigText = "Big text content",
            textLines = listOf("Line 1", "Line 2"),
            subText = "SubText",
            infoText = "Info",
            progressMax = 100,
            progressCurrent = 20,
            isProgressIndeterminate = false,
            conversationTitle = "Dev Team",
            messagingStyle = messaging
        )
        val payload = NotificationRawPayload(
            seq = 101L,
            packageName = "org.telegram.messenger",
            id = 5,
            tag = "dev_tag",
            key = "0|org.telegram.messenger|5|dev_tag|1000",
            groupKey = "group_1",
            postTimeEpochMs = 1_700_000_000_000L,
            flags = 16,
            channelId = "channel_general",
            extras = extras,
            receivedAt = Instant.ofEpochMilli(1_700_000_001_000L)
        )

        val json = NotificationMapper.toPayloadJson(payload)

        json shouldContain """"seq":101"""
        json shouldContain """"packageName":"org.telegram.messenger""""
        json shouldContain """"id":5"""
        json shouldContain """"tag":"dev_tag""""
        json shouldContain """"title":"Notification Title""""
        json shouldContain """"conversationTitle":"Dev Team""""
        json shouldContain """"messages":[{"text":"Deploy ready \"v1\"","timestampMillis":1700000100000,"senderName":"Lead"}]"""
    }
}
