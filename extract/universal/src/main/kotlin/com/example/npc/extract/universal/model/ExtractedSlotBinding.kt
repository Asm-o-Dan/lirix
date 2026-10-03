package com.example.npc.extract.universal.model

/**
 * Метаданные извлеченного слота для валидации и динамической индукции шаблонов.
 */
data class ExtractedSlotBinding(
    val role: SlotRole,
    val tokenIndex: Int,
    val text: String,
    val spanStart: Int,
    val spanEnd: Int
)
