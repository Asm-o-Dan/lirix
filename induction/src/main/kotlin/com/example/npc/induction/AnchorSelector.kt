package com.example.npc.induction

import com.example.npc.induction.model.LiteralSegment
import com.example.npc.induction.model.Segment
import com.example.npc.induction.model.SegmentedSequence
import com.example.npc.induction.model.SlotSegment
import com.example.npc.induction.model.SlotType

/**
 * Выбор якорных литералов и отсечение динамических префиксов/постфиксов (ADR-273).
 */
object AnchorSelector {

    /**
     * Находит первый значащий литерал длиной >= minLength для использования в качестве якорного префикса.
     */
    fun findFirstAnchor(segments: List<Segment>, minLength: Int = 3): LiteralSegment? {
        for (seg in segments) {
            if (seg is LiteralSegment) {
                val words = seg.text.trim().split(java.util.regex.Pattern.compile("\\s+"))
                if (words.any { it.length >= minLength && it.any { c -> c.isLetter() } }) {
                    return seg
                }
            }
        }
        return null
    }

    /**
     * Отсекает хвостовые незначащие литералы после последнего целевого слота (Tail Trimming).
     * Это защищает шаблон от поломки при смене банком рекламных постфиксов.
     */
    fun trimTail(segments: List<Segment>): List<Segment> {
        val lastSlotIdx = segments.indexOfLast { it is SlotSegment }
        if (lastSlotIdx == -1 || lastSlotIdx == segments.size - 1) {
            return segments
        }

        // Если последний слот - MERCHANT, ему нужен правый ограничитель или завершающий литерал
        val lastSlot = segments[lastSlotIdx] as SlotSegment
        if (lastSlot.role == SlotType.MERCHANT) {
            val nextSignificantIdx = segments.indices.firstOrNull {
                it > lastSlotIdx && (segments[it] is LiteralSegment && segments[it].text.trim().isNotEmpty())
            }
            if (nextSignificantIdx != null) {
                return segments.subList(0, nextSignificantIdx + 1)
            }
        }

        return segments.subList(0, lastSlotIdx + 1)
    }

    /**
     * Отсекает шумный заголовок до первого якорного литерала (Head Trimming).
     */
    fun trimHead(segments: List<Segment>): List<Segment> {
        val firstSlotIdx = segments.indexOfFirst { it is SlotSegment }
        if (firstSlotIdx == -1) return segments

        val firstAnchorIdx = segments.indices.firstOrNull { idx ->
            idx <= firstSlotIdx && segments[idx] is LiteralSegment && segments[idx].text.trim().length >= 3
        }

        return if (firstAnchorIdx != null) {
            segments.subList(firstAnchorIdx, segments.size)
        } else {
            segments.subList(firstSlotIdx, segments.size)
        }
    }

    /**
     * Применяет комплексное кадрирование (Head + Tail Trimming) к SegmentedSequence.
     */
    fun trim(sequence: SegmentedSequence): SegmentedSequence {
        val trimmed = trimTail(trimHead(sequence.segments))
        val totalLength = trimmed.sumOf { it.text.length }
        return SegmentedSequence(trimmed, totalLength)
    }
}
