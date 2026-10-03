package com.example.npc.classify.rules

import com.example.npc.core.model.Event
import com.example.npc.core.model.SourceId
import com.example.npc.core.model.classify.Category
import com.example.npc.core.model.classify.ClassificationResult
import com.example.npc.core.model.classify.Engine
import com.example.npc.core.model.classify.PackageGatedRouter
import java.util.Locale

/**
 * Эвристический детерминированный классификатор категорий по правилам.
 * Применяется при отсутствии подтвержденного пользовательского прототипа (supportCount < 2).
 */
class RuleBasedCategoryClassifier(
    private val router: PackageGatedRouter
) {

    private val financeKeywords: Set<String> = setOf(
        "списание", "пополнение", "оплата", "перевод", "баланс", "остаток",
        "карта", "счет", "чек", "покупка", "зачисление", "клевер", "clever",
        "tranzactie", "achitare", "transfer", "disponibil", "card", "cont",
        "refuzata", "esuata", "cumparare", "depunere", "rup", "mdl", "rub", "eur", "usd",
        "руб", "лей"
    )

    private val musicPackages: Set<String> = setOf(
        "ru.yandex.music",
        "app.revanced.android.youtube",
        "com.google.android.apps.youtube.music",
        "com.spotify.music",
        "com.shaiban.audioplayer.mplayer",
        "com.vkontakte.music",
        "org.videolan.vlc",
        "com.apple.android.music",
        "deezer.android.app",
        "com.google.android.youtube"
    )

    private val servicePackages: Set<String> = setOf(
        "ru.yandex.weatherplugin",
        "com.google.android.apps.weather",
        "org.mozilla.firefox",
        "com.android.chrome",
        "com.google.android.gm",
        "com.google.android.apps.maps",
        "ru.yandex.taxi",
        "com.ubercab",
        "com.deliveryclub",
        "ru.yandex.eda",
        "com.eventengine.app.debug",
        "android"
    )

    private val serviceKeywords: Set<String> = setOf(
        "погода", "°c", "°f", "ветер", "дождь", "ясно", "облачно", "ощущается как",
        "снег", "градус", "давление", "влажность", "водитель", "автомобиль",
        "курьер", "заказ в пути", "доставка"
    )

    private val dialerPackages: Set<String> = setOf(
        "com.google.android.dialer",
        "com.android.phone",
        "com.android.server.telecom"
    )

    private val adKeywords: Set<String> = setOf(
        "скидка", "скидки", "распродажа", "акция", "промокод", "кэшбэк", "бонус"
    )

    /**
     * Основной метод классификации события по эвристическим правилам.
     */
    fun classifyByRules(
        event: Event,
        packageName: String,
        fingerprint: String
    ): ClassificationResult {
        val cleanPkg = packageName.trim().lowercase(Locale.ROOT)
        val combinedText = "${event.title} ${event.text}".take(4096).lowercase(Locale.ROOT)
        val sourceId = if (cleanPkg.isEmpty() || cleanPkg in PackageGatedRouterImpl.SMS_PACKAGES) {
            SourceId.SMS
        } else {
            SourceId.NOTIFICATION
        }

        // 1. Проверка категории FINANCE через строгий шлюз PackageGatedRouter
        if (router.canClassifyAsFinance(cleanPkg, event.title, sourceId)) {
            val hasFinMarker = financeKeywords.any { combinedText.contains(it) }
            val confidence = if (hasFinMarker) 0.98 else 0.90
            return ClassificationResult(
                category = Category.FINANCE,
                confidence = confidence,
                engine = Engine.RULES,
                contentFingerprint = fingerprint
            )
        }

        // 2. Проверка категории MUSIC (Медиаплееры и музыкальные сервисы)
        if (cleanPkg in musicPackages) {
            return ClassificationResult(
                category = Category.MUSIC,
                confidence = 0.95,
                engine = Engine.RULES,
                contentFingerprint = fingerprint
            )
        }

        // 3. Проверка категории COMMUNICATION (Мессенджеры, звонки, личные SMS)
        if (router.isMessengerBlacklisted(cleanPkg)) {
            return ClassificationResult(
                category = Category.COMMUNICATION,
                confidence = 0.90,
                engine = Engine.RULES,
                contentFingerprint = fingerprint
            )
        }
        if (cleanPkg in dialerPackages) {
            return ClassificationResult(
                category = Category.COMMUNICATION,
                confidence = 0.98,
                engine = Engine.RULES,
                contentFingerprint = fingerprint
            )
        }
        if (sourceId == SourceId.SMS || cleanPkg in PackageGatedRouterImpl.SMS_PACKAGES) {
            return ClassificationResult(
                category = Category.COMMUNICATION,
                confidence = 0.85,
                engine = Engine.RULES,
                contentFingerprint = fingerprint
            )
        }

        // 4. Проверка категории SERVICES (Погода, такси, доставка, браузер, системные пуши)
        if (cleanPkg in servicePackages || serviceKeywords.any { combinedText.contains(it) }) {
            val isWeather = serviceKeywords.any { combinedText.contains(it) }
            return ClassificationResult(
                category = Category.SERVICES,
                confidence = if (isWeather) 0.95 else 0.85,
                engine = Engine.RULES,
                contentFingerprint = fingerprint
            )
        }

        // 5. Проверка категории ADVERTISEMENT (Акции, скидки, промокоды)
        if (adKeywords.any { combinedText.contains(it) }) {
            return ClassificationResult(
                category = Category.ADVERTISEMENT,
                confidence = 0.80,
                engine = Engine.RULES,
                contentFingerprint = fingerprint
            )
        }

        // 6. Fallback: UNCLASSIFIED
        return ClassificationResult.unclassified(fingerprint)
    }
}
