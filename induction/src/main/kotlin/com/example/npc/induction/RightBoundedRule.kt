package com.example.npc.induction

import com.example.npc.induction.model.LiteralSegment
import com.example.npc.induction.model.Segment
import com.example.npc.induction.model.SlotSegment
import com.example.npc.induction.model.SlotType
import com.example.npc.induction.model.WhitespaceSegment

data class BoundingEnforcementResult(
    val isValid: Boolean,
    val correctedPattern: String,
    val diagnostic: String?
)

/**
 * Проверяет и гарантирует соблюдение правила правой ограниченности (Right-Bounded Rule, ADR-273)
 * для открытых и ленивых захватывающих групп (MERCHANT).
 */
object RightBoundedRule {

    fun enforce(segments: List<Segment>, rawPattern: String): BoundingEnforcementResult {
        require(rawPattern.isNotEmpty()) { "rawPattern must not be empty" }

        // 1. Найти индекс сегмента SlotSegment с ролью MERCHANT
        val merchantIndex = segments.indexOfFirst { it is SlotSegment && it.role == SlotType.MERCHANT }
        if (merchantIndex == -1) {
            return BoundingEnforcementResult(isValid = true, correctedPattern = rawPattern, diagnostic = null)
        }

        // 2. Найти следующий за ним значащий сегмент
        val nextSegment = findNextSignificantSegment(segments, merchantIndex)

        // 3. Анализ следующего сегмента
        if (nextSegment == null) {
            // Мерчант в самом конце текста сообщения
            return if (rawPattern.endsWith("(?:\n|$)") || rawPattern.contains("(?P<merchant>[^\\n]{2,64}?)(?:\\n|$)")) {
                BoundingEnforcementResult(isValid = true, correctedPattern = rawPattern, diagnostic = null)
            } else {
                val corrected = if (rawPattern.contains("(?P<merchant>${SafeFragments.MERCHANT})")) {
                    rawPattern.replace(
                        "(?P<merchant>${SafeFragments.MERCHANT})",
                        "(?P<merchant>${SafeFragments.MERCHANT})(?:\\n|$)"
                    )
                } else {
                    rawPattern + "(?:\\n|$)"
                }
                BoundingEnforcementResult(isValid = true, correctedPattern = corrected, diagnostic = "Appended line-end bound")
            }
        }

        if (nextSegment is WhitespaceSegment && nextSegment.hasNewline) {
            // За мерчантом следует перевод строки
            return if (rawPattern.contains("(?P<merchant>[^\\n]{2,64}?)(?:\\n|$)")) {
                BoundingEnforcementResult(isValid = true, correctedPattern = rawPattern, diagnostic = null)
            } else {
                val corrected = if (rawPattern.contains("(?P<merchant>${SafeFragments.MERCHANT})")) {
                    rawPattern.replace(
                        "(?P<merchant>${SafeFragments.MERCHANT})",
                        "(?P<merchant>${SafeFragments.MERCHANT})(?:\\n|$)"
                    )
                } else {
                    rawPattern + "(?:\\n|$)"
                }
                BoundingEnforcementResult(isValid = true, correctedPattern = corrected, diagnostic = "Appended line-end bound")
            }
        }

        if (nextSegment is LiteralSegment && nextSegment.text.trim().isNotEmpty()) {
            // Ограничен литералом справа
            return BoundingEnforcementResult(isValid = true, correctedPattern = rawPattern, diagnostic = null)
        }

        if (nextSegment is SlotSegment) {
            // Прямое соседство с другим слотом без разделяющего литерала
            return BoundingEnforcementResult(
                isValid = false,
                correctedPattern = rawPattern,
                diagnostic = "Merchant slot must be bounded by a literal (found adjacent slot: ${nextSegment.slotName})"
            )
        }

        return BoundingEnforcementResult(isValid = true, correctedPattern = rawPattern, diagnostic = null)
    }

    private fun findNextSignificantSegment(segments: List<Segment>, currentIndex: Int): Segment? {
        for (i in (currentIndex + 1) until segments.size) {
            val s = segments[i]
            if (s is LiteralSegment && s.text.trim().isEmpty()) {
                if (s.text.contains('\n')) {
                    return WhitespaceSegment(hasNewline = true, text = "\n")
                }
                continue // пропускаем пустые пробелы
            }
            return s
        }
        return null
    }
}
