package com.example.npc.core.text

import com.example.npc.core.text.model.KeywordKind
import com.example.npc.core.text.model.NumberInterpretation
import com.example.npc.core.text.model.Token
import com.example.npc.core.text.model.TokenType

typealias TokenType = TokenType
typealias KeywordKind = KeywordKind
typealias NumberInterpretation = NumberInterpretation
typealias Token = Token

/**
 * Неизменяемый упорядоченный поток токенов с возможностью навигации по индексам.
 */
interface TokenStream : Iterable<Token> {
    val size: Int
    operator fun get(index: Int): Token
    fun tokenAtOffset(offset: Int): Int?
    fun tokensInRange(start: Int, end: Int): List<Token>
}

/**
 * Высокопроизводительная реализация TokenStream на базе неизменяемого списка с бинарным поиском O(log N).
 */
class DefaultTokenStream(
    private val tokens: List<Token>
) : TokenStream {

    override val size: Int get() = tokens.size

    override fun get(index: Int): Token = tokens[index]

    override fun iterator(): Iterator<Token> = tokens.iterator()

    override fun tokenAtOffset(offset: Int): Int? {
        if (tokens.isEmpty()) return null
        var low = 0
        var high = tokens.size - 1

        while (low <= high) {
            val mid = (low + high) ushr 1
            val token = tokens[mid]
            when {
                offset < token.span.start -> high = mid - 1
                offset >= token.span.end -> low = mid + 1
                else -> return mid
            }
        }
        return null
    }

    override fun tokensInRange(start: Int, end: Int): List<Token> {
        if (tokens.isEmpty() || start >= end) return emptyList()

        // Binary search for first token with token.span.end > start
        var low = 0
        var high = tokens.size - 1
        var firstIdx = tokens.size

        while (low <= high) {
            val mid = (low + high) ushr 1
            if (tokens[mid].span.end > start) {
                firstIdx = mid
                high = mid - 1
            } else {
                low = mid + 1
            }
        }

        if (firstIdx >= tokens.size || tokens[firstIdx].span.start >= end) {
            return emptyList()
        }

        val result = ArrayList<Token>()
        var idx = firstIdx
        while (idx < tokens.size && tokens[idx].span.start < end) {
            result.add(tokens[idx])
            idx++
        }
        return result
    }
}
