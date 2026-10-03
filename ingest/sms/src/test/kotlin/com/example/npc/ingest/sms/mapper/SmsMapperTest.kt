package com.example.npc.ingest.sms.mapper

import android.database.Cursor
import android.provider.Telephony
import android.telephony.SmsMessage
import com.example.npc.core.model.DeduplicationKey
import com.example.npc.core.model.Lang
import com.example.npc.core.model.SourceId
import com.example.npc.core.model.ThreadKey
import com.example.npc.ingest.sms.SmsContract
import com.example.npc.ingest.sms.model.SmsRawPayload
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.time.Instant

class SmsMapperTest {

    @Test
    fun `toPayloadJson produces RFC 8259 canonical JSON without subId`() {
        val originAddress = "+79991234567"
        val body = "Код подтверждения: 1234"
        val timestampMillis = 1_774_567_890_000L

        val json = SmsMapper.toPayloadJson(
            originAddress = originAddress,
            body = body,
            timestampMillis = timestampMillis,
            subId = null
        )

        json shouldBe """{"originatingAddress":"+79991234567","messageBody":"Код подтверждения: 1234","timestampMillis":1774567890000}"""
    }

    @Test
    fun `toPayloadJson includes subId when provided`() {
        val originAddress = "Sberbank"
        val body = "Баланс: 10000р"
        val timestampMillis = 1_774_567_891_000L
        val subId = 2

        val json = SmsMapper.toPayloadJson(
            originAddress = originAddress,
            body = body,
            timestampMillis = timestampMillis,
            subId = subId
        )

        json shouldBe """{"originatingAddress":"Sberbank","messageBody":"Баланс: 10000р","timestampMillis":1774567891000,"subId":2}"""
    }

    @Test
    fun `toPayloadJson properly escapes quotes, newlines, and special characters`() {
        val originAddress = "ООО \"Рога и Копыта\""
        val body = "Заказ #42:\n\"Товар\\Услуга\"\tготов!"
        val timestampMillis = 1_774_567_892_000L

        val json = SmsMapper.toPayloadJson(
            originAddress = originAddress,
            body = body,
            timestampMillis = timestampMillis,
            subId = 1
        )

        json shouldContain """"originatingAddress":"ООО \"Рога и Копыта\"""""
        json shouldContain """"messageBody":"Заказ #42:\n\"Товар\\Услуга\"\tготов!""""
        json shouldContain """"timestampMillis":1774567892000"""
        json shouldContain """"subId":1"""
    }

    @Test
    fun `toRawEvent maps SmsRawPayload to RawEvent with SourceId SMS and expected package name`() {
        val receivedAt = Instant.parse("2026-09-26T06:00:00Z")
        val payload = SmsRawPayload(
            seq = 101L,
            originAddress = "+79991112233",
            body = "Тестовое SMS",
            timestampMillis = 1_774_567_890_000L,
            receivedAt = receivedAt,
            subId = 1
        )
        val dedupKey = DeduplicationKey("a".repeat(64))

        val rawEvent = SmsMapper.toRawEvent(payload, dedupKey)

        rawEvent.id shouldBe 0L
        rawEvent.seq shouldBe 101L
        rawEvent.source shouldBe SourceId.SMS
        rawEvent.packageName shouldBe "android.telephony.sms"
        rawEvent.receivedAt shouldBe receivedAt
        rawEvent.hash shouldBe dedupKey
        rawEvent.payloadJson shouldBe SmsMapper.toPayloadJson(
            originAddress = payload.originAddress,
            body = payload.body,
            timestampMillis = payload.timestampMillis,
            subId = payload.subId
        )
    }

    @Test
    fun `toEvent normalizes Russian SMS, cleans control characters, and sets ThreadKey`() {
        val rawEvent = com.example.npc.core.model.RawEvent(
            id = 55L,
            seq = 1L,
            source = SourceId.SMS,
            packageName = "android.telephony.sms",
            receivedAt = Instant.parse("2026-09-26T06:05:00Z"),
            payloadJson = "{}",
            hash = DeduplicationKey("b".repeat(64))
        )
        val contract = SmsContract(
            originAddress = " 900 ",
            body = "Покупка\u200B 250р\uFEFF Пятерочка",
            timestampMillis = 1_774_567_890_000L
        )

        val event = SmsMapper.toEvent(rawEvent, contract)

        event.id shouldBe 0L
        event.rawId shouldBe 55L
        event.ts shouldBe Instant.ofEpochMilli(1_774_567_890_000L)
        event.title shouldBe " 900 "
        event.text shouldBe "Покупка\u200B 250р\uFEFF Пятерочка"
        event.normalizedText shouldBe "Покупка 250р Пятерочка"
        event.lang shouldBe Lang.RU
        event.threadKey shouldBe ThreadKey("900")
        event.isUpdateOf shouldBe null
    }

    @Test
    fun `toEvent normalizes English SMS with detected EN lang and trimmed ThreadKey`() {
        val rawEvent = com.example.npc.core.model.RawEvent(
            id = 56L,
            seq = 2L,
            source = SourceId.SMS,
            packageName = "android.telephony.sms",
            receivedAt = Instant.parse("2026-09-26T06:10:00Z"),
            payloadJson = "{}",
            hash = DeduplicationKey("c".repeat(64))
        )
        val contract = SmsContract(
            originAddress = "+12025550199  ",
            body = "G-998811   is your Google verification code.",
            timestampMillis = 1_774_567_895_000L
        )

        val event = SmsMapper.toEvent(rawEvent, contract)

        event.id shouldBe 0L
        event.rawId shouldBe 56L
        event.ts shouldBe Instant.ofEpochMilli(1_774_567_895_000L)
        event.title shouldBe "+12025550199  "
        event.text shouldBe "G-998811   is your Google verification code."
        event.normalizedText shouldBe "G-998811 is your Google verification code."
        event.lang shouldBe Lang.EN
        event.threadKey shouldBe ThreadKey("+12025550199")
        event.isUpdateOf shouldBe null
    }

    @Test
    fun `fromCursor correctly extracts ADDRESS, BODY, and DATE columns`() {
        val cursor = mockk<Cursor>()
        every { cursor.getColumnIndex(Telephony.Sms.ADDRESS) } returns 0
        every { cursor.getColumnIndex(Telephony.Sms.BODY) } returns 1
        every { cursor.getColumnIndex(Telephony.Sms.DATE) } returns 2

        every { cursor.isNull(0) } returns false
        every { cursor.isNull(1) } returns false
        every { cursor.isNull(2) } returns false

        every { cursor.getString(0) } returns " +79998887766 "
        every { cursor.getString(1) } returns "Ваш баланс: 100р"
        every { cursor.getLong(2) } returns 1_774_567_890_000L

        val contract = SmsMapper.fromCursor(cursor)

        contract.originAddress shouldBe "+79998887766"
        contract.body shouldBe "Ваш баланс: 100р"
        contract.timestampMillis shouldBe 1_774_567_890_000L
    }

    @Test
    fun `fromCursor falls back to UNKNOWN, empty body, and 0L when columns are null or missing`() {
        val cursor = mockk<Cursor>()
        every { cursor.getColumnIndex(Telephony.Sms.ADDRESS) } returns 0
        every { cursor.getColumnIndex(Telephony.Sms.BODY) } returns 1
        every { cursor.getColumnIndex(Telephony.Sms.DATE) } returns -1

        every { cursor.isNull(0) } returns true
        every { cursor.isNull(1) } returns true

        val contract = SmsMapper.fromCursor(cursor)

        contract.originAddress shouldBe "UNKNOWN"
        contract.body shouldBe ""
        contract.timestampMillis shouldBe 0L
    }

    @Test
    fun `fromSmsMessages concatenates multipart messages in order`() {
        val msg1 = mockk<SmsMessage>()
        val msg2 = mockk<SmsMessage>()

        every { msg1.displayOriginatingAddress } returns "AlfaBank"
        every { msg1.originatingAddress } returns "AlfaBank"
        every { msg1.timestampMillis } returns 1_774_567_890_000L
        every { msg1.displayMessageBody } returns "Часть 1. "
        every { msg1.messageBody } returns "Часть 1. "

        every { msg2.displayMessageBody } returns "Часть 2."
        every { msg2.messageBody } returns "Часть 2."

        val contract = SmsMapper.fromSmsMessages(arrayOf(msg1, msg2))

        contract shouldBe SmsContract(
            originAddress = "AlfaBank",
            body = "Часть 1. Часть 2.",
            timestampMillis = 1_774_567_890_000L
        )
    }

    @Test
    fun `fromSmsMessages returns null for null or empty array`() {
        SmsMapper.fromSmsMessages(null) shouldBe null
        SmsMapper.fromSmsMessages(emptyArray()) shouldBe null
    }
}
