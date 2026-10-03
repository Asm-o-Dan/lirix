package com.example.npc.classify.rules

import com.example.npc.core.model.SourceId
import com.example.npc.core.model.classify.PackageGatedRouter
import java.util.Locale

/**
 * Реализация строгой фильтрации и изоляции источников по пакетам и отправителям.
 * Исключает ложноположительные срабатывания (кейс InTour Telegram и Яндекс.Погода).
 */
class PackageGatedRouterImpl : PackageGatedRouter {

    override fun canClassifyAsFinance(packageName: String, senderOrTitle: String?, sourceId: SourceId): Boolean {
        // 1. Жёсткая аппаратная блокировка: для медиа-источников финансы запрещены безусловно
        if (sourceId == SourceId.MEDIA) {
            return false
        }

        val cleanPkg = packageName.trim().lowercase(Locale.ROOT)

        // 2. Жёсткая блокировка мессенджеров и соцсетей
        if (isMessengerBlacklisted(cleanPkg)) {
            return false
        }

        // 3. Авторизованные мобильные приложения банков (белый список)
        if (cleanPkg in PackageGatedRouter.BANK_PACKAGES) {
            return true
        }

        // 4. Банковские SMS: системные SMS-клиенты или прямой захват SMS
        val isSmsApp = cleanPkg in SMS_PACKAGES
        val isDirectSms = sourceId == SourceId.SMS || cleanPkg.isEmpty()
        if (isSmsApp || isDirectSms) {
            val normalizedSender = senderOrTitle?.trim()?.uppercase(Locale.ROOT).orEmpty()
            return normalizedSender in PackageGatedRouter.BANK_SMS_SENDERS
        }

        return false
    }

    override fun isMessengerBlacklisted(packageName: String): Boolean {
        val cleanPkg = packageName.trim().lowercase(Locale.ROOT)
        return cleanPkg in PackageGatedRouter.MESSENGER_PACKAGES
    }

    companion object {
        val SMS_PACKAGES: Set<String> = setOf(
            "com.google.android.apps.messaging",
            "com.android.mms"
        )
    }
}
