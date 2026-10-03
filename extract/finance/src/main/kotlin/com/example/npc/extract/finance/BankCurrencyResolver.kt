package com.example.npc.extract.finance

import com.example.npc.core.model.extract.CurrencyResolver
import com.example.npc.core.model.finance.CurrencyCode
import java.util.Locale

object BankCurrencyResolver : CurrencyResolver {

    /**
     * Контекстно-зависимый маппинг валютных токенов.
     * Строго изолирует Приднестровский рубль (RUP) от Российского рубля (RUB).
     */
    override fun resolve(token: String, contextPackage: String?): CurrencyCode? {
        if (token.isBlank()) return null

        val rawToken = token.trim()
        val cleanToken = rawToken.lowercase(Locale.ROOT).removeSuffix(".")
        val ctx = contextPackage?.trim()?.lowercase(Locale.ROOT)

        // 1. Общемировые маркеры валют (независимо от контекста)
        if (cleanToken == "$" || cleanToken == "usd") return CurrencyCode.USD
        if (cleanToken == "€" || cleanToken == "eur") return CurrencyCode.EUR

        // 2. Контекст банка-отправителя
        if (ctx != null) {
            val isPmr = ctx.contains("apb") || ctx.contains("prisbank") || ctx.contains("pmr")
            val isRu = ctx == "900" || ctx.contains("sber") || ctx.contains("tinkoff") || ctx.contains("t-bank") || ctx.contains("vtb") || ctx.contains("alfa")
            val isMaib = ctx.contains("maib")

            if (isPmr) {
                return when (cleanToken) {
                    "руб", "р", "rup", "rub", "prb", "рублей", "рубля" -> CurrencyCode.RUP
                    "mdl", "лей", "lei", "l" -> CurrencyCode.MDL
                    "евро" -> CurrencyCode.EUR
                    "долларов", "долл" -> CurrencyCode.USD
                    else -> CurrencyCode.ofOrNull(cleanToken)
                }
            }

            if (isRu) {
                return when (cleanToken) {
                    "руб", "р", "₽", "rub", "рублей", "рубля" -> CurrencyCode.RUB
                    "mdl", "лей", "lei", "l" -> CurrencyCode.MDL
                    "евро" -> CurrencyCode.EUR
                    "долларов", "долл" -> CurrencyCode.USD
                    else -> CurrencyCode.ofOrNull(cleanToken)
                }
            }

            if (isMaib) {
                return when (cleanToken) {
                    "mdl", "лей", "lei", "l" -> CurrencyCode.MDL
                    "евро" -> CurrencyCode.EUR
                    "долларов", "долл" -> CurrencyCode.USD
                    "rup" -> CurrencyCode.RUP
                    "rub", "руб", "р", "₽" -> CurrencyCode.RUB
                    else -> CurrencyCode.ofOrNull(cleanToken)
                }
            }
        }

        // 3. Fallback без контекста или для общих случаев
        return when (cleanToken) {
            "rup" -> CurrencyCode.RUP
            "mdl", "лей", "lei", "l" -> CurrencyCode.MDL
            "rub", "₽" -> CurrencyCode.RUB
            else -> CurrencyCode.ofOrNull(cleanToken)
        }
    }
}
