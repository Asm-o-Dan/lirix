package com.example.npc.extract.universal

import com.example.npc.core.model.finance.TransactionType
import com.example.npc.core.text.model.KeywordKind
import com.example.npc.core.text.model.Token
import com.example.npc.core.text.model.TokenType
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
    val dominantKeywordKind: KeywordKind? = dominantKeyword?.keywordKind
) {
    val dominantKeywordText: String? get() = dominantKeyword?.text
}

/**
 * Резолвер доменного типа финансовой операции по семантическим стеммам и ключевым словам.
 * Реализует строгую иерархию специфичности:
 * DECLINED > REFUND > TRANSFER > CREDIT > DEBIT (ADR-185/ADR-306).
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
        if (tokenStream.size == 0) {
            return OpTypeResolution(
                transactionType = TransactionType.DEBIT,
                isRefund = false,
                isDeclined = false,
                dominantKeyword = null,
                confidence = 0.50f
            )
        }

        // 1. Сбор ключевых слов из потока токенов
        val keywords = ArrayList<Pair<Token, KeywordKind>>()

        for (i in 0 until tokenStream.size) {
            val token = tokenStream[i]
            val existingKind = token.keywordKind
            if (existingKind != null) {
                keywords.add(token to existingKind)
            } else if (token.type == TokenType.WORD) {
                val matchedKind = lexicon.matchKeyword(token.text.lowercase())
                if (matchedKind != null) {
                    keywords.add(token to matchedKind)
                }
            }
        }

        // 2. Иерархия специфичности: DECLINED > REFUND > TRANSFER > CREDIT > DEBIT

        // Шаг 1: DECLINED (Отказ / Refuz / Respins)
        val declined = keywords.firstOrNull { it.second == KeywordKind.DECLINED }
        if (declined != null) {
            return OpTypeResolution(
                transactionType = TransactionType.DEBIT,
                isRefund = false,
                isDeclined = true,
                dominantKeyword = declined.first,
                confidence = 0.98f,
                dominantKeywordKind = KeywordKind.DECLINED
            )
        }

        // Шаг 2: REFUND (Возврат / Restituire / Rambursare / Reversal)
        val refund = keywords.firstOrNull { it.second == KeywordKind.REFUND }
        if (refund != null) {
            return OpTypeResolution(
                transactionType = TransactionType.CREDIT,
                isRefund = true,
                isDeclined = false,
                dominantKeyword = refund.first,
                confidence = 0.96f,
                dominantKeywordKind = KeywordKind.REFUND
            )
        }

        // Шаг 3: TRANSFER (Перевод / Transfer)
        val transfer = keywords.firstOrNull { it.second == KeywordKind.TRANSFER }
        if (transfer != null) {
            return OpTypeResolution(
                transactionType = TransactionType.TRANSFER,
                isRefund = false,
                isDeclined = false,
                dominantKeyword = transfer.first,
                confidence = 0.92f,
                dominantKeywordKind = KeywordKind.TRANSFER
            )
        }

        // Шаг 4: CREDIT (Пополнение / Зачисление / Alimentare / Incasare)
        val credit = keywords.firstOrNull { it.second == KeywordKind.CREDIT }
        if (credit != null) {
            return OpTypeResolution(
                transactionType = TransactionType.CREDIT,
                isRefund = false,
                isDeclined = false,
                dominantKeyword = credit.first,
                confidence = 0.92f,
                dominantKeywordKind = KeywordKind.CREDIT
            )
        }

        // Шаг 5: DEBIT (Покупка / Оплата / Plata / Achizitie / Списание)
        val debit = keywords.firstOrNull { it.second == KeywordKind.DEBIT }
        if (debit != null) {
            return OpTypeResolution(
                transactionType = TransactionType.DEBIT,
                isRefund = false,
                isDeclined = false,
                dominantKeyword = debit.first,
                confidence = 0.90f,
                dominantKeywordKind = KeywordKind.DEBIT
            )
        }

        // 3. Фоллбэк: проверка явных знаков перед суммами (+ / -)
        for (i in 0 until tokenStream.size) {
            val token = tokenStream[i]
            if (token.type == TokenType.SIGN) {
                if (token.text.contains('+')) {
                    return OpTypeResolution(
                        transactionType = TransactionType.CREDIT,
                        isRefund = false,
                        isDeclined = false,
                        dominantKeyword = token,
                        confidence = 0.70f
                    )
                } else if (token.text.contains('-')) {
                    return OpTypeResolution(
                        transactionType = TransactionType.DEBIT,
                        isRefund = false,
                        isDeclined = false,
                        dominantKeyword = token,
                        confidence = 0.70f
                    )
                }
            }
        }

        // 4. Дефолтный фоллбэк: DEBIT с пониженной уверенностью
        return OpTypeResolution(
            transactionType = TransactionType.DEBIT,
            isRefund = false,
            isDeclined = false,
            dominantKeyword = null,
            confidence = 0.60f
        )
    }
}
