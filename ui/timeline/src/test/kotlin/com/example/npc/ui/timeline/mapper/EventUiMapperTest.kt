package com.example.npc.ui.timeline.mapper

import com.example.npc.core.model.DeduplicationKey
import com.example.npc.core.model.Event
import com.example.npc.core.model.Lang
import com.example.npc.core.model.RawEvent
import com.example.npc.core.model.SourceHealth
import com.example.npc.core.model.SourceId
import com.example.npc.core.model.ThreadKey
import com.example.npc.ui.timeline.R
import com.example.npc.ui.timeline.model.SourceHealthStatus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class EventUiMapperTest {

    private val zoneId = ZoneId.of("UTC")

    @Test
    fun `toUiModel with rawEvent maps correctly using rawEvent source`() {
        val now = Instant.parse("2026-09-26T12:00:00Z")
        val event = Event(
            id = 1L,
            rawId = 10L,
            ts = now,
            title = "Банк",
            text = "Списание 500р",
            normalizedText = "списание 500р",
            lang = Lang.RU,
            threadKey = ThreadKey("some_key"),
            isUpdateOf = null
        )
        val rawEvent = RawEvent(
            id = 10L,
            seq = 1L,
            source = SourceId.SMS,
            packageName = "android.telephony.sms",
            receivedAt = now,
            payloadJson = "{}",
            hash = DeduplicationKey("a".repeat(64))
        )

        val uiModel = EventUiMapper.toUiModel(event, rawEvent, zoneId)

        uiModel.id shouldBe 1L
        uiModel.rawId shouldBe 10L
        uiModel.displayTitle shouldBe "Банк"
        uiModel.displayText shouldBe "Списание 500р"
        uiModel.normalizedText shouldBe "списание 500р"
        uiModel.sourceName shouldBe "SMS"
        uiModel.sourceIconRes shouldBe R.drawable.ic_source_sms
        uiModel.langLabel shouldBe "RU"
        uiModel.isUpdate shouldBe false
        uiModel.threadKey shouldBe "some_key"
    }

    @Test
    fun `toUiModel without rawEvent resolves source from threadKey prefixes`() {
        val now = Instant.parse("2026-09-26T12:00:00Z")

        val smsEvent = Event(
            id = 1L,
            rawId = 10L,
            ts = now,
            title = "SMS Title",
            text = "SMS Text",
            normalizedText = "sms text",
            lang = Lang.EN,
            threadKey = ThreadKey("sms:+79991112233"),
            isUpdateOf = 5L
        )
        val smsUiModel = EventUiMapper.toUiModel(smsEvent, zoneId)
        smsUiModel.sourceName shouldBe "SMS"
        smsUiModel.sourceIconRes shouldBe R.drawable.ic_source_sms
        smsUiModel.isUpdate shouldBe true
        smsUiModel.langLabel shouldBe "EN"

        val mediaEvent = Event(
            id = 2L,
            rawId = 11L,
            ts = now,
            title = "Song",
            text = "Artist",
            normalizedText = "artist",
            lang = Lang.UNK,
            threadKey = ThreadKey("media:spotify"),
            isUpdateOf = null
        )
        val mediaUiModel = EventUiMapper.toUiModel(mediaEvent, zoneId)
        mediaUiModel.sourceName shouldBe "Медиа"
        mediaUiModel.sourceIconRes shouldBe R.drawable.ic_source_media
        smsUiModel.langLabel shouldBe "EN"
        mediaUiModel.langLabel shouldBe "UNK"

        val notifEvent = Event(
            id = 3L,
            rawId = 12L,
            ts = now,
            title = "Telegram",
            text = "Hello",
            normalizedText = "hello",
            lang = Lang.RU,
            threadKey = ThreadKey("org.telegram.messenger:conv:1"),
            isUpdateOf = null
        )
        val notifUiModel = EventUiMapper.toUiModel(notifEvent, zoneId)
        notifUiModel.sourceName shouldBe "Уведомление"
        notifUiModel.sourceIconRes shouldBe R.drawable.ic_source_notification
    }

    @Test
    fun `toUiModel falls back to default title when title is blank`() {
        val now = Instant.parse("2026-09-26T12:00:00Z")
        val blankEvent = Event(
            id = 1L,
            rawId = 10L,
            ts = now,
            title = "   ",
            text = "Текст",
            normalizedText = "текст",
            lang = Lang.RU,
            threadKey = null,
            isUpdateOf = null
        )

        val uiModel = EventUiMapper.toUiModel(blankEvent, zoneId)

        uiModel.displayTitle shouldBe "Без названия"
        uiModel.sourceName shouldBe "Уведомление"
    }

    @Test
    fun `toUiModel formats timeLabel correctly for today`() {
        val now = Instant.now()
        val event = Event(
            id = 1L,
            rawId = 10L,
            ts = now,
            title = "Title",
            text = "Text",
            normalizedText = "text",
            lang = Lang.RU,
            threadKey = null,
            isUpdateOf = null
        )

        val uiModel = EventUiMapper.toUiModel(event, ZoneId.systemDefault())

        uiModel.timeLabel.matches(Regex("\\d{2}:\\d{2}")) shouldBe true
    }

    @Test
    fun `toHealthUiModel calculates GREEN, YELLOW, and RED statuses properly`() {
        val now = Instant.parse("2026-09-26T12:00:00Z")

        val healthy = SourceHealth(
            source = SourceId.NOTIFICATION,
            lastEventAt = now.minus(5, ChronoUnit.MINUTES),
            events24h = 42,
            lastError = null,
            queueDepth = 2
        )
        val healthyUi = EventUiMapper.toHealthUiModel(healthy, now, zoneId)
        healthyUi.status shouldBe SourceHealthStatus.GREEN
        healthyUi.status shouldBe SourceHealthStatus.HEALTHY
        healthyUi.displayName shouldBe "Уведомления"
        healthyUi.events24h shouldBe 42
        healthyUi.events24hLabel shouldBe "24ч: 42"
        healthyUi.lastEventTimeLabel shouldBe "5 мин назад"
        healthyUi.queueDepth shouldBe 2
        healthyUi.lastError shouldBe null

        val warningQueue = SourceHealth(
            source = SourceId.SMS,
            lastEventAt = now.minus(1, ChronoUnit.MINUTES),
            events24h = 10,
            lastError = null,
            queueDepth = 10
        )
        val warningQueueUi = EventUiMapper.toHealthUiModel(warningQueue, now, zoneId)
        warningQueueUi.status shouldBe SourceHealthStatus.YELLOW
        warningQueueUi.status shouldBe SourceHealthStatus.WARNING
        warningQueueUi.displayName shouldBe "SMS"

        val warningStale = SourceHealth(
            source = SourceId.MEDIA,
            lastEventAt = null,
            events24h = 0,
            lastError = null,
            queueDepth = 0
        )
        val warningStaleUi = EventUiMapper.toHealthUiModel(warningStale, now, zoneId)
        warningStaleUi.status shouldBe SourceHealthStatus.YELLOW
        warningStaleUi.status shouldBe SourceHealthStatus.WARNING
        warningStaleUi.displayName shouldBe "Медиа"
        warningStaleUi.lastEventTimeLabel shouldBe "нет событий"

        val redError = SourceHealth(
            source = SourceId.NOTIFICATION,
            lastEventAt = now,
            events24h = 5,
            lastError = "NotificationListenerService disconnected",
            queueDepth = 0
        )
        val redErrorUi = EventUiMapper.toHealthUiModel(redError, now, zoneId)
        redErrorUi.status shouldBe SourceHealthStatus.RED
        redErrorUi.status shouldBe SourceHealthStatus.CRITICAL
        redErrorUi.lastError shouldBe "NotificationListenerService disconnected"

        val redQueue = SourceHealth(
            source = SourceId.SMS,
            lastEventAt = now,
            events24h = 5,
            lastError = null,
            queueDepth = 25
        )
        val redQueueUi = EventUiMapper.toHealthUiModel(redQueue, now, zoneId)
        redQueueUi.status shouldBe SourceHealthStatus.RED
        redQueueUi.status shouldBe SourceHealthStatus.CRITICAL
    }

    @Test
    fun `toHealthUiModel formats relative time correctly`() {
        val now = Instant.parse("2026-09-26T12:00:00Z")

        val justNow = SourceHealth(
            source = SourceId.NOTIFICATION,
            lastEventAt = now.minusSeconds(30),
            events24h = 1,
            lastError = null,
            queueDepth = 0
        )
        EventUiMapper.toHealthUiModel(justNow, now, zoneId).lastEventTimeLabel shouldBe "только что"

        val minutesAgo = SourceHealth(
            source = SourceId.NOTIFICATION,
            lastEventAt = now.minus(15, ChronoUnit.MINUTES),
            events24h = 1,
            lastError = null,
            queueDepth = 0
        )
        EventUiMapper.toHealthUiModel(minutesAgo, now, zoneId).lastEventTimeLabel shouldBe "15 мин назад"

        val hoursAgo = SourceHealth(
            source = SourceId.NOTIFICATION,
            lastEventAt = now.minus(3, ChronoUnit.HOURS),
            events24h = 1,
            lastError = null,
            queueDepth = 0
        )
        EventUiMapper.toHealthUiModel(hoursAgo, now, zoneId).lastEventTimeLabel shouldBe "3 ч назад"
    }

    @Test
    fun `formatJsonPretty preserves content for valid and invalid json`() {
        val rawObject = """{"title":"Test","count":1}"""
        val result = EventUiMapper.formatJsonPretty(rawObject)
        result shouldContain """"title""""
        result shouldContain """"Test""""

        val rawArray = """[1,2,3]"""
        val arrayResult = EventUiMapper.formatJsonPretty(rawArray)
        arrayResult shouldContain "1"

        val invalidJson = "not a json string"
        EventUiMapper.formatJsonPretty(invalidJson) shouldBe invalidJson
    }
}
