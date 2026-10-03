package com.example.npc.core.text

/**
 * Результат нормализации входящего текста с картой смещений к исходной строке.
 */
data class NormalizedText(
    val original: String,
    val normalized: String,
    val keyForm: String, // lowercase, stripped diacritics, homoglyphs mapped
    val offsetMap: OffsetMap
)
