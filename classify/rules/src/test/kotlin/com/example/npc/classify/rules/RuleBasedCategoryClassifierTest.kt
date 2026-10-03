package com.example.npc.classify.rules

import com.example.npc.core.model.Event
import com.example.npc.core.model.Lang
import com.example.npc.core.model.classify.Category
import com.example.npc.core.model.classify.Engine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class RuleBasedCategoryClassifierTest {

    private lateinit var router: PackageGatedRouterImpl
    private lateinit var classifier: RuleBasedCategoryClassifier

    private val sampleFp = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    private val now = Instant.parse("2026-09-27T12:00:00Z")

    @BeforeEach
    fun setUp() {
        router = PackageGatedRouterImpl()
        classifier = RuleBasedCategoryClassifier(router)
    }

    private fun createEvent(title: String, text: String): Event {
        return Event(
            id = 1L,
            rawId = 1L,
            ts = now,
            title = title,
            text = text,
            normalizedText = text.lowercase(),
            lang = Lang.RU,
            threadKey = null,
            isUpdateOf = null
        )
    }

    @Test
    fun `classifies APB bank transaction push as FINANCE with high confidence`() {
        val event = createEvent(
            title = "Агропромбанк",
            text = "Покупка по карте **5576 на сумму 5,47 RUP Баланс 422,01 RUP"
        )

        val result = classifier.classifyByRules(event, "com.apb.mobile", sampleFp)

        assertEquals(Category.FINANCE, result.category)
        assertTrue(result.confidence >= 0.90, "Confidence for bank push must be >= 0.90")
        assertEquals(Engine.RULES, result.engine)
        assertEquals(sampleFp, result.contentFingerprint)
    }

    @Test
    fun `classifies MAIB bank push as FINANCE with high confidence`() {
        val event = createEvent(
            title = "maibank",
            text = "Оплата на сумму 664 MDL в Temu.com с карты ***1555 прошла успешно. Доступный остаток: 66.83 EUR."
        )

        val result = classifier.classifyByRules(event, "md.maib.maibank", sampleFp)

        assertEquals(Category.FINANCE, result.category)
        assertTrue(result.confidence >= 0.90)
        assertEquals(Engine.RULES, result.engine)
    }

    @Test
    fun `classifies Telegram or AyuGram chat push as COMMUNICATION and NEVER as FINANCE`() {
        // Even with money words like "499 евро", InTour spam from Telegram MUST NOT be FINANCE!
        val inTourEvent = createEvent(
            title = "InTour Тур агентство ПМР",
            text = "Вылет из Кишинева, от 499 евро, Турция 5 звезд все включено!"
        )

        val result = classifier.classifyByRules(inTourEvent, "com.radolyn.ayugram", sampleFp)

        assertNotEquals(Category.FINANCE, result.category, "AyuGram message must NEVER be classified as FINANCE")
        assertEquals(Engine.RULES, result.engine)
    }

    @Test
    fun `classifies Yandex Weather notification as SERVICES and NOT FINANCE`() {
        val weatherEvent = createEvent(
            title = "Яндекс.Погода",
            text = "+10°C, ощущается как +8°C, ветер 3 м/с"
        )

        val result = classifier.classifyByRules(weatherEvent, "ru.yandex.weatherplugin", sampleFp)

        assertEquals(Category.SERVICES, result.category)
        assertNotEquals(Category.FINANCE, result.category, "Weather push must NOT be classified as FINANCE")
    }

    @Test
    fun `classifies media player notification as MUSIC`() {
        val musicEvent = createEvent(
            title = "Linkin Park",
            text = "Numb - Meteora"
        )

        val result = classifier.classifyByRules(musicEvent, "ru.yandex.music", sampleFp)

        assertEquals(Category.MUSIC, result.category)
        assertTrue(result.confidence >= 0.85)
    }

    @Test
    fun `classifies unknown package with unclassified text as UNCLASSIFIED or OTHER`() {
        val unknownEvent = createEvent(
            title = "RandomApp",
            text = "Some random generic text without keywords"
        )

        val result = classifier.classifyByRules(unknownEvent, "com.random.app", sampleFp)

        assertTrue(
            result.category == Category.UNCLASSIFIED || result.category == Category.OTHER,
            "Unknown events should be UNCLASSIFIED or OTHER, got ${result.category}"
        )
        assertEquals(0.0, result.confidence)
    }
}
