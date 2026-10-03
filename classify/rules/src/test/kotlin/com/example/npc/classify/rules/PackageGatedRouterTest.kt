package com.example.npc.classify.rules

import com.example.npc.core.model.SourceId
import com.example.npc.core.model.classify.PackageGatedRouter
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PackageGatedRouterTest {

    private lateinit var router: PackageGatedRouter

    @BeforeEach
    fun setUp() {
        router = PackageGatedRouterImpl()
    }

    @Test
    fun `isMessengerBlacklisted returns true for all known messengers and social networks`() {
        val messengers = listOf(
            "com.radolyn.ayugram",
            "org.telegram.messenger",
            "org.telegram.plus",
            "org.thunderdog.challegram",
            "com.whatsapp",
            "com.whatsapp.w4b",
            "com.viber.voip",
            "org.signal.messenger",
            "com.facebook.orca",
            "com.vkontakte.android",
            "com.discord",
            "com.skype.raider",
            "com.slack"
        )

        for (pkg in messengers) {
            assertTrue(router.isMessengerBlacklisted(pkg), "Expected $pkg to be blacklisted as messenger")
        }
    }

    @Test
    fun `isMessengerBlacklisted returns false for banks and other apps`() {
        assertFalse(router.isMessengerBlacklisted("com.apb.mobile"))
        assertFalse(router.isMessengerBlacklisted("com.prisbank.app"))
        assertFalse(router.isMessengerBlacklisted("md.maib.maibank"))
        assertFalse(router.isMessengerBlacklisted("com.google.android.apps.messaging"))
        assertFalse(router.isMessengerBlacklisted("ru.yandex.weatherplugin"))
    }

    @Test
    fun `canClassifyAsFinance returns FALSE unconditionally for messenges (InTour bug fix)`() {
        // Even if message title or sender says "InTour 499 EUR" or "Bank Transfer", AyuGram is strictly blocked
        assertFalse(
            router.canClassifyAsFinance("com.radolyn.ayugram", "InTour", SourceId.NOTIFICATION),
            "AyuGram must NEVER be classified as finance"
        )
        assertFalse(
            router.canClassifyAsFinance("org.telegram.messenger", "Сбербанк Перевод", SourceId.NOTIFICATION),
            "Telegram must NEVER be classified as finance"
        )
        assertFalse(
            router.canClassifyAsFinance("com.whatsapp", "100$", SourceId.NOTIFICATION)
        )
        assertFalse(
            router.canClassifyAsFinance("com.viber.voip", "Оплата", SourceId.NOTIFICATION)
        )
    }

    @Test
    fun `canClassifyAsFinance returns TRUE for authorized bank mobile apps`() {
        assertTrue(router.canClassifyAsFinance("com.apb.mobile", null, SourceId.NOTIFICATION))
        assertTrue(router.canClassifyAsFinance("com.apb.mobile", "Агропромбанк", SourceId.NOTIFICATION))
        assertTrue(router.canClassifyAsFinance("com.prisbank.app", null, SourceId.NOTIFICATION))
        assertTrue(router.canClassifyAsFinance("md.maib.maibank", null, SourceId.NOTIFICATION))
    }

    @Test
    fun `canClassifyAsFinance returns TRUE for authorized bank SMS senders`() {
        val bankSenders = listOf("APB", "AGROPROMBANK", "PRISBANK", "SBERBANK", "MAIB", "900")

        for (sender in bankSenders) {
            // Via Google Messages notification
            assertTrue(
                router.canClassifyAsFinance("com.google.android.apps.messaging", sender, SourceId.NOTIFICATION),
                "Expected finance allowed for sender $sender in Google Messages"
            )
            // Via AOSP MMS notification
            assertTrue(
                router.canClassifyAsFinance("com.android.mms", sender.lowercase(), SourceId.NOTIFICATION),
                "Expected finance allowed for case-insensitive sender $sender"
            )
            // Via direct SMS ingest
            assertTrue(
                router.canClassifyAsFinance("", sender, SourceId.SMS),
                "Expected finance allowed for direct SMS with sender $sender"
            )
        }
    }

    @Test
    fun `canClassifyAsFinance returns FALSE for non-bank SMS senders`() {
        val nonBankSenders = listOf("+37377712345", "Mama", "Friend", "SpamTaxi", "ShopInfo", "Orange", "IDC")

        for (sender in nonBankSenders) {
            assertFalse(
                router.canClassifyAsFinance("com.google.android.apps.messaging", sender, SourceId.NOTIFICATION),
                "Personal or spam SMS sender $sender must NOT be classified as finance"
            )
            assertFalse(
                router.canClassifyAsFinance("", sender, SourceId.SMS)
            )
        }
    }

    @Test
    fun `canClassifyAsFinance returns FALSE for non-finance applications (Yandex Weather)`() {
        assertFalse(router.canClassifyAsFinance("ru.yandex.weatherplugin", "+10°C", SourceId.NOTIFICATION))
        assertFalse(router.canClassifyAsFinance("com.android.chrome", "Web notification", SourceId.NOTIFICATION))
        assertFalse(router.canClassifyAsFinance("com.spotify.music", "Music playing", SourceId.MEDIA))
    }

    @Test
    fun `canClassifyAsFinance returns FALSE for MEDIA source regardless of title`() {
        assertFalse(router.canClassifyAsFinance("ru.yandex.music", "APB Bank Song", SourceId.MEDIA))
    }
}
