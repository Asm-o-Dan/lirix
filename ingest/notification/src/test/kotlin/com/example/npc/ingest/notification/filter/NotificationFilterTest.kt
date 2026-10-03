package com.example.npc.ingest.notification.filter

import android.app.Notification
import com.example.npc.ingest.notification.model.FilterRejectionReason
import com.example.npc.ingest.notification.model.NotificationExtrasData
import com.example.npc.ingest.notification.model.NotificationRawPayload
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

class NotificationFilterTest {

    private val hostPackage = "com.example.npc"

    private fun createPayload(
        packageName: String = "com.whatsapp",
        flags: Int = 0,
        extras: NotificationExtrasData = NotificationExtrasData(
            title = "Alice",
            text = "Hello there",
            bigText = null,
            textLines = emptyList(),
            subText = null,
            infoText = null,
            progressMax = 0,
            progressCurrent = 0,
            isProgressIndeterminate = false,
            conversationTitle = null,
            messagingStyle = null
        )
    ) = NotificationRawPayload(
        seq = 1L,
        packageName = packageName,
        id = 10,
        tag = null,
        key = "0|com.whatsapp|10|null|1000",
        groupKey = null,
        postTimeEpochMs = 1_700_000_000_000L,
        flags = flags,
        channelId = "messages",
        extras = extras,
        receivedAt = Instant.now()
    )

    @Test
    fun `evaluate rejects own package`() {
        val payload = createPayload(packageName = hostPackage)
        val decision = NotificationFilter.evaluate(payload, hostPackage)

        decision.isAccepted shouldBe false
        decision.rejectionReason shouldBe FilterRejectionReason.OWN_PACKAGE
    }

    @Test
    fun `evaluate rejects ongoing notifications`() {
        val payload = createPayload(flags = Notification.FLAG_ONGOING_EVENT)
        val decision = NotificationFilter.evaluate(payload, hostPackage)

        decision.isAccepted shouldBe false
        decision.rejectionReason shouldBe FilterRejectionReason.ONGOING_EVENT
    }

    @Test
    fun `evaluate rejects foreground service notifications`() {
        val payload = createPayload(flags = Notification.FLAG_FOREGROUND_SERVICE)
        val decision = NotificationFilter.evaluate(payload, hostPackage)

        decision.isAccepted shouldBe false
        decision.rejectionReason shouldBe FilterRejectionReason.FOREGROUND_SERVICE
    }

    @Test
    fun `evaluate rejects notifications with determinate progress bar`() {
        val extras = NotificationExtrasData(
            title = "Download",
            text = "Downloading...",
            bigText = null,
            textLines = emptyList(),
            subText = null,
            infoText = null,
            progressMax = 100,
            progressCurrent = 45,
            isProgressIndeterminate = false,
            conversationTitle = null,
            messagingStyle = null
        )
        val payload = createPayload(extras = extras)
        val decision = NotificationFilter.evaluate(payload, hostPackage)

        decision.isAccepted shouldBe false
        decision.rejectionReason shouldBe FilterRejectionReason.PROGRESS_BAR
    }

    @Test
    fun `evaluate rejects notifications with indeterminate progress bar`() {
        val extras = NotificationExtrasData(
            title = "Processing",
            text = "Please wait",
            bigText = null,
            textLines = emptyList(),
            subText = null,
            infoText = null,
            progressMax = 0,
            progressCurrent = 0,
            isProgressIndeterminate = true,
            conversationTitle = null,
            messagingStyle = null
        )
        val payload = createPayload(extras = extras)
        val decision = NotificationFilter.evaluate(payload, hostPackage)

        decision.isAccepted shouldBe false
        decision.rejectionReason shouldBe FilterRejectionReason.PROGRESS_BAR
    }

    @Test
    fun `evaluate rejects group summary notifications`() {
        val payload = createPayload(flags = Notification.FLAG_GROUP_SUMMARY)
        val decision = NotificationFilter.evaluate(payload, hostPackage)

        decision.isAccepted shouldBe false
        decision.rejectionReason shouldBe FilterRejectionReason.GROUP_SUMMARY
    }

    @Test
    fun `evaluate rejects notifications with empty text content`() {
        val extras = NotificationExtrasData(
            title = null,
            text = null,
            bigText = null,
            textLines = emptyList(),
            subText = null,
            infoText = null,
            progressMax = 0,
            progressCurrent = 0,
            isProgressIndeterminate = false,
            conversationTitle = null,
            messagingStyle = null
        )
        val payload = createPayload(extras = extras)
        val decision = NotificationFilter.evaluate(payload, hostPackage)

        decision.isAccepted shouldBe false
        decision.rejectionReason shouldBe FilterRejectionReason.EMPTY_CONTENT
    }

    @Test
    fun `evaluate accepts legitimate notification`() {
        val payload = createPayload()
        val decision = NotificationFilter.evaluate(payload, hostPackage)

        decision.isAccepted shouldBe true
        decision.rejectionReason shouldBe null
    }
}
