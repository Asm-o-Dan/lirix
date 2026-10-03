package com.example.npc.extract.universal

import com.example.npc.core.model.finance.TransactionType
import com.example.npc.core.model.finance.TransactionDirectionResolver
import com.example.npc.core.text.model.KeywordKind
import com.example.npc.core.text.model.Token
import com.example.npc.core.text.TokenStream
import com.example.npc.core.text.lexicon.LexiconLoader
import com.example.npc.core.text.lexicon.LexiconRepository

/**
 * Результат разрешения доменного типа операции.
 */
data class OpTypeResolution(
    val transactionType: TransactionType,
    val isRefund: Boolean,
    val isDeclined: Boolean,
    val dominantKeyword: Token?,
    val confidence: Float,
    val dominantKeywordKind: KeywordKind? = dominantKeyword?.keywordKind,
    val isSuppressed: Boolean = false,
    val reason: String? = null
) {
    val dominantKeywordText: String? get() = dominantKeyword?.text
}

/**
 * Резолвер доменного типа финансовой операции по семантическим стеммам и ключевым словам.
 * Delegates to the shared evidence resolver; missing/conflicting direction remains UNKNOWN.
 */
interface OpTypeResolver {

    fun resolve(tokenStream: TokenStream): OpTypeResolution

    fun resolve(tokenStream: TokenStream, lexicon: LexiconRepository): OpTypeResolution

    companion object : OpTypeResolver by DefaultOpTypeResolver() {
        fun create(): OpTypeResolver = DefaultOpTypeResolver()
    }
}

class DefaultOpTypeResolver(
    private val defaultLexicon: LexiconRepository = LexiconLoader.default()
) : OpTypeResolver {

    override fun resolve(tokenStream: TokenStream): OpTypeResolution =
        resolve(tokenStream, defaultLexicon)

    override fun resolve(
        tokenStream: TokenStream,
        lexicon: LexiconRepository
    ): OpTypeResolution {
        // Retain original separators, including compact money, rather than inventing whitespace.
        val text = buildString {
            for (token in tokenStream) {
                while (length < token.span.start) append(' ')
                append(token.text)
            }
        }
        val resolution = TransactionDirectionResolver.resolve(body = text)
        val kind = when {
            resolution.isDeclined -> KeywordKind.DECLINED
            resolution.isRefund -> KeywordKind.REFUND
            resolution.type == TransactionType.CREDIT -> KeywordKind.CREDIT
            resolution.type == TransactionType.DEBIT -> KeywordKind.DEBIT
            resolution.type == TransactionType.TRANSFER -> KeywordKind.TRANSFER
            else -> null
        }
        val dominant = tokenStream.firstOrNull {
            kind != null && (it.keywordKind ?: lexicon.matchKeyword(it.text.lowercase())) == kind
        }
        return OpTypeResolution(
            transactionType = resolution.type,
            isRefund = resolution.isRefund,
            isDeclined = resolution.isDeclined,
            dominantKeyword = dominant,
            confidence = resolution.confidence,
            dominantKeywordKind = kind,
            isSuppressed = resolution.isSuppressed,
            reason = resolution.reason
        )
    }
}
