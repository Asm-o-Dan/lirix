package com.example.npc.core.storage.mapper

import com.example.npc.core.model.DeduplicationKey
import com.example.npc.core.model.Event
import com.example.npc.core.model.Lang
import com.example.npc.core.model.RawEvent
import com.example.npc.core.model.SourceHealth
import com.example.npc.core.model.SourceId
import com.example.npc.core.model.ThreadKey
import com.example.npc.core.model.classify.Category
import com.example.npc.core.model.classify.UserPrototype
import com.example.npc.core.model.finance.CurrencyCode
import com.example.npc.core.model.finance.FinancialTransaction
import com.example.npc.core.model.finance.Money
import com.example.npc.core.model.finance.TransactionStatus
import com.example.npc.core.model.finance.TransactionType
import com.example.npc.core.storage.entity.EventEntity
import com.example.npc.core.storage.entity.FinancialTransactionEntity
import com.example.npc.core.storage.entity.RawEventEntity
import com.example.npc.core.storage.entity.SourceHealthEntity
import com.example.npc.core.storage.entity.UserPrototypeEntity
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

class MappersTest {

    @Nested
    inner class RawEventMapperTests {
        @Test
        fun `toDomain maps all fields correctly`() {
            val entity = RawEventEntity(
                id = 42L,
                seq = 101L,
                source = "notification",
                packageName = "org.telegram.messenger",
                receivedAt = 1_700_000_000_000L,
                payloadJson = """{"title":"Hello"}""",
                hash = "a".repeat(64)
            )

            val domain = RawEventMapper.toDomain(entity)

            domain.id shouldBe 42L
            domain.seq shouldBe 101L
            domain.source shouldBe SourceId("notification")
            domain.packageName shouldBe "org.telegram.messenger"
            domain.receivedAt shouldBe Instant.ofEpochMilli(1_700_000_000_000L)
            domain.payloadJson shouldBe """{"title":"Hello"}"""
            domain.hash shouldBe DeduplicationKey("a".repeat(64))
        }

        @Test
        fun `toEntity maps all fields correctly`() {
            val domain = RawEvent(
                id = 99L,
                seq = 202L,
                source = SourceId("sms"),
                packageName = "com.google.android.apps.messaging",
                receivedAt = Instant.ofEpochMilli(1_700_000_500_000L),
                payloadJson = """{"body":"Code: 1234"}""",
                hash = DeduplicationKey("b".repeat(64))
            )

            val entity = RawEventMapper.toEntity(domain)

            entity.id shouldBe 99L
            entity.seq shouldBe 202L
            entity.source shouldBe "sms"
            entity.packageName shouldBe "com.google.android.apps.messaging"
            entity.receivedAt shouldBe 1_700_000_500_000L
            entity.payloadJson shouldBe """{"body":"Code: 1234"}"""
            entity.hash shouldBe "b".repeat(64)
        }
    }

    @Nested
    inner class EventMapperTests {
        @Test
        fun `toDomain maps all fields correctly with non-null values`() {
            val entity = EventEntity(
                id = 10L,
                rawId = 5L,
                ts = 1_700_000_100_000L,
                title = "Payment",
                text = "Paid $100",
                normalizedText = "Paid 100",
                lang = "EN",
                threadKey = "chat_123",
                isUpdateOf = 9L
            )

            val domain = EventMapper.toDomain(entity)

            domain.id shouldBe 10L
            domain.rawId shouldBe 5L
            domain.ts shouldBe Instant.ofEpochMilli(1_700_000_100_000L)
            domain.title shouldBe "Payment"
            domain.text shouldBe "Paid $100"
            domain.normalizedText shouldBe "Paid 100"
            domain.lang shouldBe Lang.EN
            domain.threadKey shouldBe ThreadKey("chat_123")
            domain.isUpdateOf shouldBe 9L
        }

        @Test
        fun `toDomain maps null threadKey and isUpdateOf`() {
            val entity = EventEntity(
                id = 11L,
                rawId = 6L,
                ts = 1_700_000_200_000L,
                title = "Alert",
                text = "Notice",
                normalizedText = "Notice",
                lang = "RU",
                threadKey = null,
                isUpdateOf = null
            )

            val domain = EventMapper.toDomain(entity)

            domain.threadKey shouldBe null
            domain.isUpdateOf shouldBe null
            domain.lang shouldBe Lang.RU
        }

        @Test
        fun `toDomain falls back to Lang UNK for unrecognized language codes`() {
            val entity = EventEntity(
                id = 12L,
                rawId = 7L,
                ts = 1_700_000_300_000L,
                title = "Title",
                text = "Text",
                normalizedText = "Text",
                lang = "FRENCH_INVALID",
                threadKey = null,
                isUpdateOf = null
            )

            val domain = EventMapper.toDomain(entity)

            domain.lang shouldBe Lang.UNK
        }

        @Test
        fun `toEntity maps all fields correctly`() {
            val domain = Event(
                id = 15L,
                rawId = 8L,
                ts = Instant.ofEpochMilli(1_700_000_400_000L),
                title = "New Song",
                text = "Playing",
                normalizedText = "Playing",
                lang = Lang.RU,
                threadKey = ThreadKey("media_session_1"),
                isUpdateOf = 14L
            )

            val entity = EventMapper.toEntity(domain)

            entity.id shouldBe 15L
            entity.rawId shouldBe 8L
            entity.ts shouldBe 1_700_000_400_000L
            entity.title shouldBe "New Song"
            entity.text shouldBe "Playing"
            entity.normalizedText shouldBe "Playing"
            entity.lang shouldBe "RU"
            entity.threadKey shouldBe "media_session_1"
            entity.isUpdateOf shouldBe 14L
            entity.category shouldBe "UNCLASSIFIED"
            entity.confidence shouldBe 0.0
            entity.engineUsed shouldBe "NONE"
            entity.isUserCorrected shouldBe false
            entity.contentFingerprint shouldBe null
            entity.pipelineRevisionId shouldBe null
        }

        @Test
        fun `toEntity maps Phase 1 and Phase 2 fields correctly when provided`() {
            val domain = Event(
                id = 20L,
                rawId = 12L,
                ts = Instant.ofEpochMilli(1_700_000_500_000L),
                title = "Transfer",
                text = "Transfer received",
                normalizedText = "transfer received",
                lang = Lang.RU,
                threadKey = null,
                isUpdateOf = null,
                category = com.example.npc.core.model.classify.Category.FINANCE,
                confidence = 0.95f,
                engineUsed = com.example.npc.core.model.classify.Engine.RULES,
                isUserCorrected = true,
                contentFingerprint = "c".repeat(64),
                pipelineRevisionId = 42L
            )

            val entity = EventMapper.toEntity(domain)

            entity.category shouldBe "FINANCE"
            entity.confidence.toFloat() shouldBe 0.95f
            entity.engineUsed shouldBe "RULES"
            entity.isUserCorrected shouldBe true
            entity.contentFingerprint shouldBe "c".repeat(64)
            entity.pipelineRevisionId shouldBe 42L

            val backToDomain = EventMapper.toDomain(entity)
            backToDomain.category shouldBe com.example.npc.core.model.classify.Category.FINANCE
            backToDomain.confidence shouldBe 0.95f
            backToDomain.engineUsed shouldBe com.example.npc.core.model.classify.Engine.RULES
            backToDomain.isUserCorrected shouldBe true
            backToDomain.contentFingerprint shouldBe "c".repeat(64)
            backToDomain.pipelineRevisionId shouldBe 42L
        }

        @Test
        fun `toDomain falls back to default Category and Engine on unknown values`() {
            val entity = EventEntity(
                id = 21L,
                rawId = 13L,
                ts = 1_700_000_600_000L,
                title = "Unknown",
                text = "Unknown",
                normalizedText = "Unknown",
                lang = "RU",
                threadKey = null,
                isUpdateOf = null,
                category = "INVALID_CATEGORY_NAME",
                confidence = 0.5,
                engineUsed = "INVALID_ENGINE_NAME",
                isUserCorrected = false,
                contentFingerprint = null,
                pipelineRevisionId = null
            )

            val domain = EventMapper.toDomain(entity)
            domain.category shouldBe com.example.npc.core.model.classify.Category.UNCLASSIFIED
            domain.engineUsed shouldBe com.example.npc.core.model.classify.Engine.NONE
        }
    }

    @Nested
    inner class FinancialTransactionMapperTests {
        @Test
        fun `toEntity maps all fields correctly with balance and merchant`() {
            val domain = FinancialTransaction(
                id = 10L,
                eventId = 20L,
                bank = "APB",
                type = TransactionType.DEBIT,
                amount = Money(1550L, CurrencyCode.RUP),
                balance = Money(40000L, CurrencyCode.RUP),
                merchant = "Sheriff-15",
                accountMask = "**5576",
                status = TransactionStatus.COMPLETED,
                occurredAt = Instant.ofEpochMilli(1_700_000_100_000L),
                extractorId = "apb.notification",
                extractorVersion = 1,
                rawText = "Payment 15.50 RUP Sheriff-15",
                createdAt = Instant.ofEpochMilli(1_700_000_101_000L)
            )

            val entity = FinancialTransactionMapper.toEntity(domain)

            entity.id shouldBe 10L
            entity.eventId shouldBe 20L
            entity.bank shouldBe "APB"
            entity.direction shouldBe "DEBIT"
            entity.amountMinor shouldBe 1550L
            entity.currency shouldBe "RUP"
            entity.balanceMinor shouldBe 40000L
            entity.balanceCurrency shouldBe "RUP"
            entity.merchant shouldBe "Sheriff-15"
            entity.accountMask shouldBe "**5576"
            entity.occurredAt shouldBe 1_700_000_100_000L
            entity.extractorId shouldBe "apb.notification"
            entity.extractorVersion shouldBe 1
            entity.createdAt shouldBe 1_700_000_101_000L
        }

        @Test
        fun `toDomain maps entity with nullable fields`() {
            val entity = FinancialTransactionEntity(
                id = 15L,
                eventId = null,
                bank = "PRISBANK",
                direction = "CREDIT",
                amountMinor = 250000L,
                currency = "RUB",
                balanceMinor = null,
                balanceCurrency = null,
                merchant = null,
                accountMask = null,
                occurredAt = 1_700_000_200_000L,
                extractorId = "pris.sms",
                extractorVersion = 2,
                createdAt = 1_700_000_202_000L
            )

            val domain = FinancialTransactionMapper.toDomain(entity)

            domain.id shouldBe 15L
            domain.eventId shouldBe null
            domain.bank shouldBe "PRISBANK"
            domain.type shouldBe TransactionType.CREDIT
            domain.amount shouldBe Money(250000L, CurrencyCode.RUB)
            domain.balance shouldBe null
            domain.merchant shouldBe null
            domain.accountMask shouldBe null
            domain.occurredAt shouldBe Instant.ofEpochMilli(1_700_000_200_000L)
            domain.extractorId shouldBe "pris.sms"
            domain.extractorVersion shouldBe 2
        }

        @Test
        fun `roundtrip domain to entity to domain preserves values`() {
            val domain = FinancialTransaction(
                id = 5L,
                eventId = 99L,
                bank = "MAIB",
                type = TransactionType.TRANSFER,
                amount = Money(5000L, CurrencyCode.MDL),
                balance = Money(15000L, CurrencyCode.MDL),
                merchant = "P2P",
                accountMask = "*9999",
                status = TransactionStatus.COMPLETED,
                occurredAt = Instant.ofEpochMilli(1_700_000_300_000L),
                extractorId = "maib.push",
                extractorVersion = 1,
                rawText = "Transfer 50.00 MDL",
                createdAt = Instant.ofEpochMilli(1_700_000_305_000L)
            )

            val entity = FinancialTransactionMapper.toEntity(domain)
            val restored = FinancialTransactionMapper.toDomain(entity, rawText = domain.rawText)

            restored.id shouldBe domain.id
            restored.eventId shouldBe domain.eventId
            restored.bank shouldBe domain.bank
            restored.type shouldBe domain.type
            restored.amount shouldBe domain.amount
            restored.balance shouldBe domain.balance
            restored.merchant shouldBe domain.merchant
            restored.accountMask shouldBe domain.accountMask
            restored.occurredAt shouldBe domain.occurredAt
            restored.extractorId shouldBe domain.extractorId
            restored.extractorVersion shouldBe domain.extractorVersion
            restored.rawText shouldBe domain.rawText
        }
    }

    @Nested
    inner class UserPrototypeMapperTests {
        private val sampleFingerprint = "a".repeat(64)

        @Test
        fun `toEntity maps all fields correctly`() {
            val domain = UserPrototype(
                id = 1L,
                packageName = "com.radolyn.ayugram",
                fingerprint = sampleFingerprint,
                category = Category.ADVERTISEMENT,
                supportCount = 3,
                createdAt = Instant.ofEpochMilli(1_700_000_000_000L),
                lastSeenAt = Instant.ofEpochMilli(1_700_000_500_000L)
            )

            val entity = UserPrototypeMapper.toEntity(domain)

            entity.id shouldBe 1L
            entity.packageName shouldBe "com.radolyn.ayugram"
            entity.fingerprint shouldBe sampleFingerprint
            entity.category shouldBe "ADVERTISEMENT"
            entity.supportCount shouldBe 3
            entity.createdAt shouldBe 1_700_000_000_000L
            entity.lastSeenAt shouldBe 1_700_000_500_000L
        }

        @Test
        fun `toDomain maps entity correctly`() {
            val entity = UserPrototypeEntity(
                id = 2L,
                packageName = "org.telegram.messenger",
                fingerprint = sampleFingerprint,
                category = "COMMUNICATION",
                supportCount = 1,
                createdAt = 1_700_000_100_000L,
                lastSeenAt = 1_700_000_100_000L
            )

            val domain = UserPrototypeMapper.toDomain(entity)

            domain.id shouldBe 2L
            domain.packageName shouldBe "org.telegram.messenger"
            domain.fingerprint shouldBe sampleFingerprint
            domain.category shouldBe Category.COMMUNICATION
            domain.supportCount shouldBe 1
            domain.createdAt shouldBe Instant.ofEpochMilli(1_700_000_100_000L)
            domain.lastSeenAt shouldBe Instant.ofEpochMilli(1_700_000_100_000L)
        }

        @Test
        fun `roundtrip domain to entity to domain preserves values`() {
            val domain = UserPrototype(
                id = 7L,
                packageName = "com.example.bank",
                fingerprint = sampleFingerprint,
                category = Category.FINANCE,
                supportCount = 5,
                createdAt = Instant.ofEpochMilli(1_700_000_000_000L),
                lastSeenAt = Instant.ofEpochMilli(1_700_000_900_000L)
            )

            val entity = UserPrototypeMapper.toEntity(domain)
            val restored = UserPrototypeMapper.toDomain(entity)

            restored shouldBe domain
        }
    }

    @Nested
    inner class SourceHealthMapperTests {
        @Test
        fun `toDomain maps all fields correctly with non-null values`() {
            val entity = SourceHealthEntity(
                source = "notification",
                lastEventAt = 1_700_000_500_000L,
                events24h = 45,
                lastError = "Connection reset",
                queueDepth = 3
            )

            val domain = SourceHealthMapper.toDomain(entity)

            domain.source shouldBe SourceId("notification")
            domain.lastEventAt shouldBe Instant.ofEpochMilli(1_700_000_500_000L)
            domain.events24h shouldBe 45
            domain.lastError shouldBe "Connection reset"
            domain.queueDepth shouldBe 3
        }

        @Test
        fun `toDomain maps nullable fields`() {
            val entity = SourceHealthEntity(
                source = "sms",
                lastEventAt = null,
                events24h = 0,
                lastError = null,
                queueDepth = 0
            )

            val domain = SourceHealthMapper.toDomain(entity)

            domain.source shouldBe SourceId("sms")
            domain.lastEventAt shouldBe null
            domain.lastError shouldBe null
            domain.queueDepth shouldBe 0
        }

        @Test
        fun `toEntity maps all fields correctly`() {
            val domain = SourceHealth(
                source = SourceId("media"),
                lastEventAt = Instant.ofEpochMilli(1_700_000_600_000L),
                events24h = 12,
                lastError = null,
                queueDepth = 1
            )

            val entity = SourceHealthMapper.toEntity(domain)

            entity.source shouldBe "media"
            entity.lastEventAt shouldBe 1_700_000_600_000L
            entity.events24h shouldBe 12
            entity.lastError shouldBe null
            entity.queueDepth shouldBe 1
        }
    }
}
