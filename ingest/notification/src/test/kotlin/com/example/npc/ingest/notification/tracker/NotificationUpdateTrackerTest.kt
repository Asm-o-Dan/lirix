package com.example.npc.ingest.notification.tracker

import com.example.npc.core.model.ThreadKey
import com.example.npc.core.storage.StorageGateway
import com.example.npc.ingest.notification.model.MessagingMessageData
import com.example.npc.ingest.notification.model.MessagingStyleData
import com.example.npc.ingest.notification.model.NotificationExtrasData
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NotificationUpdateTrackerTest {

    private val storageGateway: StorageGateway = mockk(relaxed = true)
    private lateinit var tracker: NotificationUpdateTracker

    @BeforeEach
    fun setUp() {
        tracker = NotificationUpdateTracker(storageGateway)
    }

    @Test
    fun `computeThreadKey uses conversationTitle when messagingStyle is present`() {
        val messaging = MessagingStyleData(
            conversationTitle = "Family Group",
            isGroupConversation = true,
            messages = listOf(MessagingMessageData("Hello", 1000L, "Bob")),
            historicMessages = emptyList()
        )
        val extras = NotificationExtrasData(
            title = "Bob",
            text = "Hello",
            bigText = null,
            textLines = emptyList(),
            subText = null,
            infoText = null,
            progressMax = 0,
            progressCurrent = 0,
            isProgressIndeterminate = false,
            conversationTitle = "Family Group",
            messagingStyle = messaging
        )

        val key = tracker.computeThreadKey("org.telegram.messenger", 10, null, extras)
        key shouldBe ThreadKey("org.telegram.messenger:conv:Family Group")
    }

    @Test
    fun `computeThreadKey uses package, tag and id for generic notifications`() {
        val extras = NotificationExtrasData(
            title = "Alert",
            text = "System alert",
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

        val key = tracker.computeThreadKey("com.google.android.calendar", 42, "event_tag", extras)
        key shouldBe ThreadKey("com.google.android.calendar:notif:event_tag:42")
    }

    @Test
    fun `recordEventMapping and resolvePreviousEventId returns cached event id`() = runTest {
        val threadKey = ThreadKey("com.whatsapp:notif::100")
        val notificationKey = "0|com.whatsapp|100|null|101"

        tracker.resolvePreviousEventId(threadKey, notificationKey) shouldBe null

        tracker.recordEventMapping(threadKey, notificationKey, 555L)

        tracker.resolvePreviousEventId(threadKey, notificationKey) shouldBe 555L
    }

    @Test
    fun `evictNotification removes cached mapping`() = runTest {
        val threadKey = ThreadKey("com.whatsapp:notif::200")
        val notificationKey = "0|com.whatsapp|200|null|102"

        tracker.recordEventMapping(threadKey, notificationKey, 777L)
        tracker.resolvePreviousEventId(threadKey, notificationKey) shouldBe 777L

        tracker.evictNotification(notificationKey)
        tracker.resolvePreviousEventId(threadKey, notificationKey) shouldBe null
    }
}
