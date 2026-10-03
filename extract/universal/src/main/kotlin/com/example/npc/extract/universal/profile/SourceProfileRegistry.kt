package com.example.npc.extract.universal.profile

import com.example.npc.core.model.finance.CurrencyCode

/**
 * Реестр профилей источников уведомлений для контекстного разрешения региональных валют.
 */
interface SourceProfileRegistry {
    /**
     * Получить профиль источника по его Android package name.
     */
    fun getProfile(packageName: String): SourceProfile

    /**
     * Зарегистрировать или переопределить профиль для пакета.
     */
    fun registerProfile(profile: SourceProfile)
}

/**
 * Реализация реестра в памяти со встроенными профилями для MAIB, APB, Сбербанк/Присбанк ПМР и банков РФ.
 */
class InMemorySourceProfileRegistry : SourceProfileRegistry {

    private val profiles = HashMap<String, SourceProfile>()

    init {
        // MAIB (Молдова) -> MDL
        registerProfile(
            SourceProfile(
                packageName = "md.maib.maibank",
                isKnownBankingApp = true,
                defaultCurrency = CurrencyCode.MDL,
                regionalAmbiguityResolver = { rawText ->
                    val lower = rawText.lowercase()
                    if (lower.contains("mdl") || lower.contains("лей")) CurrencyCode.MDL else null
                }
            )
        )

        // Агропромбанк (ПМР) -> RUP
        registerProfile(
            SourceProfile(
                packageName = "com.apb.mobile",
                isKnownBankingApp = true,
                defaultCurrency = CurrencyCode.RUP,
                regionalAmbiguityResolver = { rawText ->
                    val lower = rawText.lowercase()
                    if (lower.contains("руб") || lower.contains("р.")) CurrencyCode.RUP else null
                }
            )
        )

        // Сбербанк/Присбанк (ПМР) -> RUP
        registerProfile(
            SourceProfile(
                packageName = "com.prisbank.app",
                isKnownBankingApp = true,
                defaultCurrency = CurrencyCode.RUP,
                regionalAmbiguityResolver = { rawText ->
                    val lower = rawText.lowercase()
                    if (lower.contains("руб") || lower.contains("р.")) CurrencyCode.RUP else null
                }
            )
        )

        // Сбербанк РФ -> RUB
        registerProfile(
            SourceProfile(
                packageName = "ru.sberbankmobile",
                isKnownBankingApp = true,
                defaultCurrency = CurrencyCode.RUB,
                regionalAmbiguityResolver = { rawText ->
                    val lower = rawText.lowercase()
                    if (lower.contains("руб") || lower.contains("р.")) CurrencyCode.RUB else null
                }
            )
        )

        // Т-Банк (Тинькофф) РФ -> RUB
        registerProfile(
            SourceProfile(
                packageName = "com.idamob.tinkoff.android",
                isKnownBankingApp = true,
                defaultCurrency = CurrencyCode.RUB,
                regionalAmbiguityResolver = { rawText ->
                    val lower = rawText.lowercase()
                    if (lower.contains("руб") || lower.contains("р.")) CurrencyCode.RUB else null
                }
            )
        )
    }

    override fun getProfile(packageName: String): SourceProfile {
        return profiles[packageName] ?: SourceProfile(
            packageName = packageName,
            isKnownBankingApp = false,
            defaultCurrency = null,
            regionalAmbiguityResolver = { rawText ->
                val lower = rawText.lowercase()
                if (lower.contains("руб") || lower.contains("р.")) CurrencyCode.RUB else null
            }
        )
    }

    override fun registerProfile(profile: SourceProfile) {
        profiles[profile.packageName] = profile
    }
}
