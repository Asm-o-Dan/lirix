package com.example.npc.induction.validator

import com.example.npc.induction.model.BuiltTemplate
import com.google.re2j.Matcher
import com.google.re2j.Pattern
import com.google.re2j.PatternSyntaxException

/**
 * Описание ожидаемого значения слота для проверки Round-Trip.
 */
data class ExpectedSlot(
    val slotName: String,
    val expectedText: String
)

/**
 * Результат проверки Round-Trip верификации шаблона.
 */
sealed interface RoundTripResult {
    data object Success : RoundTripResult

    data class SlotMismatch(
        val slotName: String,
        val expectedValue: String,
        val actualValue: String?
    ) : RoundTripResult

    data class NoMatchOnOriginal(val pattern: String) : RoundTripResult

    data class NegativeCorpusViolation(
        val falsePositiveEventText: String
    ) : RoundTripResult
}

/**
 * Обязательный гейт верификации сгенерированного динамического шаблона RE2/J:
 * 1. Прогон по исходному сообщению sampleText с побайтовым сличением слотов.
 * 2. Прогон по негативному корпусу на отсутствие ложных срабатываний.
 */
object RoundTripValidator {

    fun validate(
        template: BuiltTemplate,
        originalNormalizedText: String,
        expectedSlots: List<ExpectedSlot>,
        negativeCorpus: List<String> = emptyList()
    ): RoundTripResult {
        val pattern: Pattern = try {
            Pattern.compile(template.pattern)
        } catch (e: PatternSyntaxException) {
            return RoundTripResult.NoMatchOnOriginal(template.pattern)
        }

        // 1. Поиск на оригинальном тексте
        val matcher = pattern.matcher(originalNormalizedText)
        if (!matcher.find()) {
            return RoundTripResult.NoMatchOnOriginal(template.pattern)
        }

        // 2. Сверка каждого слота
        for (slot in expectedSlots) {
            val actual = extractGroupSafely(matcher, slot.slotName)
            if (actual != slot.expectedText) {
                return RoundTripResult.SlotMismatch(
                    slotName = slot.slotName,
                    expectedValue = slot.expectedText,
                    actualValue = actual
                )
            }
        }

        // 3. Проверка негативного корпуса
        for (negText in negativeCorpus) {
            val negMatcher = pattern.matcher(negText)
            if (negMatcher.find()) {
                return RoundTripResult.NegativeCorpusViolation(negText)
            }
        }

        return RoundTripResult.Success
    }

    private fun extractGroupSafely(matcher: Matcher, groupName: String): String? {
        return try {
            matcher.group(groupName)
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: IllegalStateException) {
            null
        }
    }
}
