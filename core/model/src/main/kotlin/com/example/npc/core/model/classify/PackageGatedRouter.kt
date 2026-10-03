package com.example.npc.core.model.classify

import com.example.npc.core.model.SourceId

/**
 * Контракт строгой фильтрации и изоляции источников по пакетам и отправителям.
 * Исключает ложноположительные срабатывания (кейс InTour Telegram и Яндекс.Погода).
 */
interface PackageGatedRouter {

    /**
     * Проверяет, разрешено ли источнику претендовать на категорию FINANCE.
     *
     * @param packageName Имя Android-пакета (например: "com.apb.mobile", "com.radolyn.ayugram").
     * @param senderOrTitle Имя отправителя или заголовок (для SMS: "APB", "900", "InTour").
     * @param sourceId Подсистема сбора (NOTIFICATION, SMS, MEDIA).
     * @return true ТОЛЬКО если источник входит в доверенный финансовый белый список.
     */
    fun canClassifyAsFinance(packageName: String, senderOrTitle: String?, sourceId: SourceId): Boolean

    fun isFinanceAllowed(packageName: String, sender: String?, sourceId: SourceId): Boolean =
        canClassifyAsFinance(packageName, sender, sourceId)

    /**
     * Проверяет, входит ли пакет в жесткий черный список мессенджеров/соцсетей,
     * для которых категория FINANCE безусловно заблокирована.
     */
    fun isMessengerBlacklisted(packageName: String): Boolean

    companion object {
        /** Доверенные банковские приложения (пакеты) */
        val BANK_PACKAGES: Set<String> = setOf(
            "com.apb.mobile",          // Агропромбанк ПМР
            "com.prisbank.app",        // Приднестровский Сбербанк
            "md.maib.maibank"          // BC MAIB Молдова
        )

        /** Доверенные отправители для банковских SMS */
        val BANK_SMS_SENDERS: Set<String> = setOf(
            "APB", "AGROPROMBANK", "PRISBANK", "SBERBANK", "MAIB", "900"
        )

        /** Пакеты мессенджеров (жесткий запрет FINANCE) */
        val MESSENGER_PACKAGES: Set<String> = setOf(
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
    }
}
