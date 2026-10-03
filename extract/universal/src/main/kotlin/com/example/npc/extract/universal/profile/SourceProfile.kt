package com.example.npc.extract.universal.profile

import com.example.npc.core.model.finance.CurrencyCode

/**
 * Профиль источника для разрешения контекстных неоднозначностей валют и доверия.
 */
data class SourceProfile(
    val packageName: String,
    val isKnownBankingApp: Boolean,
    val defaultCurrency: CurrencyCode?,
    val regionalAmbiguityResolver: (String) -> CurrencyCode? = { null }
)
