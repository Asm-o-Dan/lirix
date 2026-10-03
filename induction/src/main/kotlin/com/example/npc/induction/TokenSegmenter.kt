package com.example.npc.induction

import com.example.npc.core.text.NormalizedText
import com.example.npc.core.text.TokenStream
import com.example.npc.core.text.model.Token
import com.example.npc.core.text.model.TokenType
import com.example.npc.induction.model.LiteralSegment
import com.example.npc.induction.model.Segment
import com.example.npc.induction.model.SegmentedSequence
import com.example.npc.induction.model.SlotAssignment
import com.example.npc.induction.model.SlotRole
import com.example.npc.induction.model.SlotSegment
import com.example.npc.induction.model.SlotType
import com.example.npc.induction.model.TokenRole
import com.example.npc.induction.model.VariableSegment
import com.example.npc.induction.model.VariableType
import com.example.npc.induction.model.WhitespaceSegment

// Реэкспорт моделей для удобного использования в com.example.npc.induction
typealias Segment = Segment
typealias LiteralSegment = LiteralSegment
typealias SlotSegment = SlotSegment
typealias VariableSegment = VariableSegment
typealias WhitespaceSegment = WhitespaceSegment
typealias SegmentedSequence = SegmentedSequence
typealias VariableType = VariableType
typealias TokenRole = TokenRole
typealias SlotType = SlotType
typealias SlotRole = SlotRole
typealias SlotAssignment = SlotAssignment

/**
 * Выполняет структурированное разбиение последовательности токенов сообщения на функциональные
 * сегменты (LITERAL, SLOT, VARIABLE, WHITESPACE) на основе разметки слотов SlotAssignments.
 */
object TokenSegmenter {

    fun segment(
        tokens: TokenStream,
        slotAssignments: List<SlotAssignment>
    ): SegmentedSequence = segment(tokens, slotAssignments, null)

    fun segment(
        tokens: TokenStream,
        slotAssignments: List<SlotAssignment>,
        normalizedText: NormalizedText
    ): SegmentedSequence = segment(tokens, slotAssignments, normalizedText.normalized)

    fun segment(
        tokens: TokenStream,
        slotAssignments: List<SlotAssignment>,
        originalText: String?
    ): SegmentedSequence {
        require(tokens.size >= 0) { "TokenStream size must be non-negative" }

        // 1. Валидация входных слотов
        validateSlotAssignments(tokens, slotAssignments)

        if (tokens.size == 0) {
            val totalLen = originalText?.length ?: 0
            if (totalLen == 0) {
                return SegmentedSequence(emptyList(), 0)
            }
            val hasNl = originalText?.contains('\n') ?: false
            val singleSeg = WhitespaceSegment(hasNewline = hasNl, tokens = emptyList(), text = originalText!!)
            return SegmentedSequence(listOf(singleSeg), totalLen)
        }

        // 2. Построение маппингов слотов
        val slotMap = HashMap<Int, SlotAssignment>()
        val allSlotIndices = HashSet<Int>()
        for (assignment in slotAssignments) {
            val minIdx = assignment.tokenIndices.minOrNull() ?: continue
            slotMap[minIdx] = assignment
            allSlotIndices.addAll(assignment.tokenIndices)
        }

        val totalLength = originalText?.length ?: tokens[tokens.size - 1].span.end
        val rawSegments = ArrayList<Segment>()
        var i = 0
        var lastTextEnd = 0

        // 3. Основной проход по токенам
        while (i < tokens.size) {
            if (i in slotMap) {
                val assignment = slotMap[i]!!
                val slotTokens = assignment.tokenIndices.sorted().map { tokens[it] }
                val slotStart = slotTokens.first().span.start
                val slotEnd = slotTokens.last().span.end

                if (slotStart > lastTextEnd) {
                    val gapText = extractSlice(tokens, originalText, lastTextEnd, slotStart)
                    rawSegments.add(LiteralSegment(emptyList(), gapText))
                }

                val slotText = extractSlice(tokens, originalText, slotStart, slotEnd)
                rawSegments.add(SlotSegment(assignment.slot, slotTokens, slotText))
                lastTextEnd = slotEnd
                i += slotTokens.size
            } else if (tokens[i].type == TokenType.DATE) {
                val tok = tokens[i]
                if (tok.span.start > lastTextEnd) {
                    val gapText = extractSlice(tokens, originalText, lastTextEnd, tok.span.start)
                    rawSegments.add(LiteralSegment(emptyList(), gapText))
                }
                rawSegments.add(
                    VariableSegment(
                        VariableType.DATE,
                        listOf(tok),
                        extractSlice(tokens, originalText, tok.span.start, tok.span.end)
                    )
                )
                lastTextEnd = tok.span.end
                i++
            } else if (tokens[i].type == TokenType.TIME) {
                val tok = tokens[i]
                if (tok.span.start > lastTextEnd) {
                    val gapText = extractSlice(tokens, originalText, lastTextEnd, tok.span.start)
                    rawSegments.add(LiteralSegment(emptyList(), gapText))
                }
                rawSegments.add(
                    VariableSegment(
                        VariableType.TIME,
                        listOf(tok),
                        extractSlice(tokens, originalText, tok.span.start, tok.span.end)
                    )
                )
                lastTextEnd = tok.span.end
                i++
            } else if (tokens[i].type == TokenType.NEWLINE) {
                val tok = tokens[i]
                if (tok.span.start > lastTextEnd) {
                    val gapText = extractSlice(tokens, originalText, lastTextEnd, tok.span.start)
                    rawSegments.add(LiteralSegment(emptyList(), gapText))
                }
                rawSegments.add(
                    WhitespaceSegment(
                        hasNewline = true,
                        tokens = listOf(tok),
                        text = extractSlice(tokens, originalText, tok.span.start, tok.span.end)
                    )
                )
                lastTextEnd = tok.span.end
                i++
            } else {
                // Литеральная цепочка токенов
                val litSegment = collectLiteralRun(
                    tokens = tokens,
                    startIndex = i,
                    slotIndices = allSlotIndices,
                    lastEnd = lastTextEnd,
                    originalText = originalText,
                    totalLength = totalLength
                )
                rawSegments.add(litSegment)
                lastTextEnd += litSegment.text.length
                i += litSegment.tokens.size
            }
        }

        // Хвостовой остаток текста, если есть
        if (lastTextEnd < totalLength) {
            val tailText = extractSlice(tokens, originalText, lastTextEnd, totalLength)
            rawSegments.add(LiteralSegment(emptyList(), tailText))
        }

        // 4. Схлопывание смежных литералов
        val collapsed = ArrayList<Segment>()
        for (seg in rawSegments) {
            if (collapsed.isNotEmpty() && collapsed.last() is LiteralSegment && seg is LiteralSegment) {
                val prev = collapsed.removeAt(collapsed.size - 1) as LiteralSegment
                collapsed.add(LiteralSegment(prev.tokens + seg.tokens, prev.text + seg.text))
            } else {
                collapsed.add(seg)
            }
        }

        return SegmentedSequence(collapsed, totalLength)
    }

    private fun validateSlotAssignments(tokens: TokenStream, slotAssignments: List<SlotAssignment>) {
        val seenIndices = HashSet<Int>()
        for (assignment in slotAssignments) {
            require(assignment.tokenIndices.isNotEmpty()) {
                "Slot assignment cannot have empty token indices"
            }
            val sorted = assignment.tokenIndices.sorted()
            for (idx in sorted) {
                if (idx < 0 || idx >= tokens.size) {
                    throw IllegalArgumentException("Token index $idx is out of bounds [0, ${tokens.size})")
                }
                if (!seenIndices.add(idx)) {
                    throw IllegalArgumentException("Overlapping slot assignments detected at token index $idx")
                }
            }
            // Проверка непрерывности токенов в рамках одного слота
            for (j in 0 until sorted.size - 1) {
                if (sorted[j + 1] != sorted[j] + 1) {
                    throw IllegalArgumentException("Slot assignment token indices must be contiguous: ${assignment.tokenIndices}")
                }
            }
        }
    }

    private fun collectLiteralRun(
        tokens: TokenStream,
        startIndex: Int,
        slotIndices: Set<Int>,
        lastEnd: Int,
        originalText: String?,
        totalLength: Int
    ): LiteralSegment {
        val litTokens = ArrayList<Token>()
        var k = startIndex
        while (k < tokens.size) {
            if (k in slotIndices) break
            val type = tokens[k].type
            if (type == TokenType.DATE || type == TokenType.TIME || type == TokenType.NEWLINE) {
                break
            }
            litTokens.add(tokens[k])
            k++
        }

        val nextStart = if (k < tokens.size) {
            tokens[k].span.start
        } else {
            totalLength
        }

        val text = extractSlice(tokens, originalText, lastEnd, nextStart)
        return LiteralSegment(litTokens, text)
    }

    private fun collectLiteralRun(
        tokens: TokenStream,
        startIndex: Int,
        slotIndices: Set<Int>
    ): LiteralSegment {
        val litTokens = ArrayList<Token>()
        var k = startIndex
        while (k < tokens.size) {
            if (k in slotIndices) break
            val type = tokens[k].type
            if (type == TokenType.DATE || type == TokenType.TIME || type == TokenType.NEWLINE) {
                break
            }
            litTokens.add(tokens[k])
            k++
        }
        val text = if (litTokens.isEmpty()) "" else {
            val start = litTokens.first().span.start
            val end = litTokens.last().span.end
            extractSlice(tokens, null, start, end)
        }
        return LiteralSegment(litTokens, text)
    }

    private fun extractSlice(
        tokens: TokenStream,
        fullText: String?,
        start: Int,
        end: Int
    ): String {
        if (start >= end) return ""
        if (fullText != null) {
            val safeStart = start.coerceIn(0, fullText.length)
            val safeEnd = end.coerceIn(safeStart, fullText.length)
            return fullText.substring(safeStart, safeEnd)
        }

        val sb = StringBuilder()
        var curr = start
        val inRangeTokens = tokens.tokensInRange(start, end)
        for (tok in inRangeTokens) {
            if (tok.span.start > curr) {
                sb.append(" ".repeat(tok.span.start - curr))
                curr = tok.span.start
            }
            val tokSubStart = maxOf(0, curr - tok.span.start)
            val tokSubEnd = minOf(tok.text.length, end - tok.span.start)
            if (tokSubStart < tokSubEnd) {
                val part = tok.text.substring(tokSubStart, tokSubEnd)
                sb.append(part)
                curr += part.length
            }
        }
        if (curr < end) {
            sb.append(" ".repeat(end - curr))
        }
        return sb.toString()
    }
}
