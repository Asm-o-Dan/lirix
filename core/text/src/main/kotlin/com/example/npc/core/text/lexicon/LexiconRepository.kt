package com.example.npc.core.text.lexicon

import com.example.npc.core.text.model.KeywordKind

sealed interface CurrencyMatchResult {
    data class Exact(val currencyCode: String) : CurrencyMatchResult
    data class Ambiguous(val candidateCodes: List<String>) : CurrencyMatchResult
}

interface LexiconRepository {
    val version: Int
    fun matchKeyword(keyFormWord: String): KeywordKind?
    fun matchCurrency(keyFormWord: String): CurrencyMatchResult?
}

/**
 * Высокопроизводительная реализация LexiconRepository на базе сжатого префиксного дерева (Compact Trie).
 * Обеспечивает классификацию ключевых слов и валют за строго O(L) от длины входного слова.
 */
class CompactTrieLexiconRepository(
    override val version: Int,
    keywordStems: Map<String, KeywordKind>,
    exactCurrencies: Map<String, String>,
    ambiguousCurrencies: Map<String, List<String>>
) : LexiconRepository {

    private class TrieNode {
        var keyword: KeywordKind? = null
        val children = HashMap<Char, TrieNode>()
    }

    private val root = TrieNode()
    private val currencyMap = HashMap<String, CurrencyMatchResult>()

    init {
        for ((stem, kind) in keywordStems) {
            insertStem(stem, kind)
        }

        for ((word, code) in exactCurrencies) {
            currencyMap[word] = CurrencyMatchResult.Exact(code)
        }
        for ((word, codes) in ambiguousCurrencies) {
            currencyMap[word] = CurrencyMatchResult.Ambiguous(codes)
        }
    }

    private fun insertStem(stem: String, kind: KeywordKind) {
        var current = root
        for (i in stem.indices) {
            val ch = stem[i]
            current = current.children.getOrPut(ch) { TrieNode() }
        }
        val existing = current.keyword
        current.keyword = if (existing != null) selectPriority(existing, kind) else kind
    }

    override fun matchKeyword(keyFormWord: String): KeywordKind? {
        if (keyFormWord.length < 3) return null

        var current = root
        var bestMatch: KeywordKind? = null

        for (i in keyFormWord.indices) {
            val ch = keyFormWord[i]
            if (current.keyword != null) {
                bestMatch = current.keyword
            }
            val next = current.children[ch] ?: break
            current = next
        }

        if (current.keyword != null) {
            bestMatch = current.keyword
        }

        return bestMatch
    }

    override fun matchCurrency(keyFormWord: String): CurrencyMatchResult? {
        return currencyMap[keyFormWord]
    }

    companion object {
        fun selectPriority(k1: KeywordKind, k2: KeywordKind): KeywordKind {
            return if (priorityOf(k1) >= priorityOf(k2)) k1 else k2
        }

        private fun priorityOf(k: KeywordKind): Int = when (k) {
            KeywordKind.DECLINED -> 100
            KeywordKind.REFUND -> 90
            KeywordKind.TRANSFER -> 80
            KeywordKind.CREDIT -> 70
            KeywordKind.DEBIT -> 60
            KeywordKind.BALANCE -> 50
            KeywordKind.OTP -> 40
            KeywordKind.PROMO -> 30
            KeywordKind.FEE -> 20
        }
    }
}
