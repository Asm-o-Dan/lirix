package com.example.npc.extract.universal

import com.example.npc.core.text.model.KeywordKind
import com.example.npc.core.text.model.Token
import com.example.npc.core.text.model.TokenType
import com.example.npc.core.text.TokenStream
import com.example.npc.core.text.lexicon.LexiconRepository
import com.example.npc.extract.universal.model.AmountCandidate
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Вектор признаков кандидата для солвера ролей.
 */
data class CandidateFeatures(
    val candidate: AmountCandidate,
    val distanceToNearestKeyword: Int,
    val nearestKeywordKind: KeywordKind?,
    val hasBalanceAnchorLeft: Boolean,
    val hasFeeAnchor: Boolean,
    val hasCardAnchor: Boolean,
    val isFirstInLine: Boolean,
    val isLastInLine: Boolean,
    val lineIndex: Int,
    val hasExplicitSign: Boolean,
    val isExplicitPlus: Boolean,
    val isExplicitMinus: Boolean
)

/**
 * Плотный вектор контекстных и позиционных признаков для солвера ролей.
 */
data class FeatureVector(
    val candidateCount: Int,
    val distancesToBalanceKeyword: IntArray,
    val distancesToFeeKeyword: IntArray,
    val distancesToRefundKeyword: IntArray,
    val distancesToDebitKeyword: IntArray,
    val distancesToCreditKeyword: IntArray,
    val hasBalanceAnchorLeft: BooleanArray,
    val hasFeeAnchor: BooleanArray,
    val hasCardAnchor: BooleanArray,
    val isFirstInLine: BooleanArray,
    val isLastInLine: BooleanArray,
    val hasExplicitSign: BooleanArray,
    val isExplicitPlus: BooleanArray,
    val isExplicitMinus: BooleanArray,
    val hasCardMask: Boolean,
    val hasPromoKeyword: Boolean,
    val hasOtpKeyword: Boolean,
    val hasPercentToken: Boolean
) {
    init {
        require(candidateCount in 0..MAX_CANDIDATES) {
            "Candidate count exceeds maximum ($candidateCount > $MAX_CANDIDATES)"
        }
    }

    companion object {
        const val MAX_CANDIDATES = 4
    }
}

/**
 * Экстрактор контекстных и позиционных признаков для кандидатов на суммы.
 */
interface FeatureExtractor {

    fun extract(candidates: List<AmountCandidate>, tokens: TokenStream): List<CandidateFeatures>

    fun computeVector(
        candidates: List<AmountCandidate>,
        tokens: TokenStream,
        lexicon: LexiconRepository? = null
    ): FeatureVector

    companion object : FeatureExtractor by DefaultFeatureExtractor() {
        fun create(): FeatureExtractor = DefaultFeatureExtractor()
    }
}

class DefaultFeatureExtractor : FeatureExtractor {

    override fun extract(
        candidates: List<AmountCandidate>,
        tokens: TokenStream
    ): List<CandidateFeatures> {
        if (candidates.isEmpty() || tokens.size == 0) return emptyList()

        val results = ArrayList<CandidateFeatures>(candidates.size)

        for (candidate in candidates) {
            val idx = candidate.tokenIndex

            // 1. Поиск ближайшего ключевого слова в окне [-5, +5]
            var minDistance = Int.MAX_VALUE
            var nearestKind: KeywordKind? = null

            val startWindow = max(0, idx - 5)
            val endWindow = min(tokens.size - 1, idx + 5)

            for (w in startWindow..endWindow) {
                if (w == idx) continue
                val tok = tokens[w]
                val kind = tok.keywordKind
                if (kind != null) {
                    val dist = abs(w - idx)
                    if (dist < minDistance) {
                        minDistance = dist
                        nearestKind = kind
                    }
                }
            }

            // 2. hasBalanceAnchorLeft: маркер BALANCE слева в окне [idx - 3, idx - 1]
            var hasBalanceAnchor = false
            val balStart = max(0, idx - 3)
            for (w in balStart until idx) {
                if (tokens[w].keywordKind == KeywordKind.BALANCE) {
                    hasBalanceAnchor = true
                    break
                }
            }

            // 3. hasFeeAnchor: маркер FEE в окне [idx - 3, idx + 3]
            var hasFee = false
            val feeStart = max(0, idx - 3)
            val feeEnd = min(tokens.size - 1, idx + 3)
            for (w in feeStart..feeEnd) {
                if (tokens[w].keywordKind == KeywordKind.FEE) {
                    hasFee = true
                    break
                }
            }

            // 4. hasCardAnchor: CARD_MASK в окне [idx - 4, idx + 4]
            var hasCard = false
            val cardStart = max(0, idx - 4)
            val cardEnd = min(tokens.size - 1, idx + 4)
            for (w in cardStart..cardEnd) {
                if (tokens[w].type == TokenType.CARD_MASK) {
                    hasCard = true
                    break
                }
            }

            // 5. Позиция в строке и lineIndex
            var lineIdx = 0
            var prevNewlineIdx = -1
            for (w in 0 until idx) {
                if (tokens[w].type == TokenType.NEWLINE) {
                    lineIdx++
                    prevNewlineIdx = w
                }
            }

            var nextNewlineIdx = tokens.size
            for (w in idx until tokens.size) {
                if (tokens[w].type == TokenType.NEWLINE) {
                    nextNewlineIdx = w
                    break
                }
            }

            // isFirstInLine: до кандидата в текущей строке нет других смысловых токенов (только знаки/пунктуация)
            var isFirst = true
            for (w in (prevNewlineIdx + 1) until idx) {
                val t = tokens[w]
                if (t.type != TokenType.SIGN && t.type != TokenType.PUNCT && t != tokens[candidate.currencyTokenIndex]) {
                    isFirst = false
                    break
                }
            }

            // isLastInLine: после кандидата и его валюты в строке нет других смысловых токенов
            val maxCandidateToken = max(idx, candidate.currencyTokenIndex)
            var isLast = true
            for (w in (maxCandidateToken + 1) until nextNewlineIdx) {
                val t = tokens[w]
                if (t.type != TokenType.SIGN && t.type != TokenType.PUNCT) {
                    isLast = false
                    break
                }
            }

            // 6. Явный знак (+ или -)
            var hasSign = false
            var isPlus = false
            var isMinus = false

            if (idx - 1 >= 0 && tokens[idx - 1].type == TokenType.SIGN) {
                hasSign = true
                val signText = tokens[idx - 1].text
                isPlus = signText.contains('+')
                isMinus = signText.contains('-')
            } else if (candidate.span.start > 0) {
                val candText = tokens[idx].text
                if (candText.startsWith('+')) {
                    hasSign = true
                    isPlus = true
                } else if (candText.startsWith('-')) {
                    hasSign = true
                    isMinus = true
                }
            }

            results.add(
                CandidateFeatures(
                    candidate = candidate,
                    distanceToNearestKeyword = minDistance,
                    nearestKeywordKind = nearestKind,
                    hasBalanceAnchorLeft = hasBalanceAnchor,
                    hasFeeAnchor = hasFee,
                    hasCardAnchor = hasCard,
                    isFirstInLine = isFirst,
                    isLastInLine = isLast,
                    lineIndex = lineIdx,
                    hasExplicitSign = hasSign,
                    isExplicitPlus = isPlus,
                    isExplicitMinus = isMinus
                )
            )
        }

        return results
    }

    override fun computeVector(
        candidates: List<AmountCandidate>,
        tokens: TokenStream,
        lexicon: LexiconRepository?
    ): FeatureVector {
        val boundedCandidates = candidates.take(FeatureVector.MAX_CANDIDATES)
        val featuresList = extract(boundedCandidates, tokens)
        val count = featuresList.size

        val distBalance = IntArray(count) { findDistanceToKeyword(tokens, boundedCandidates[it].tokenIndex, KeywordKind.BALANCE) }
        val distFee = IntArray(count) { findDistanceToKeyword(tokens, boundedCandidates[it].tokenIndex, KeywordKind.FEE) }
        val distRefund = IntArray(count) { findDistanceToKeyword(tokens, boundedCandidates[it].tokenIndex, KeywordKind.REFUND) }
        val distDebit = IntArray(count) { findDistanceToKeyword(tokens, boundedCandidates[it].tokenIndex, KeywordKind.DEBIT) }
        val distCredit = IntArray(count) { findDistanceToKeyword(tokens, boundedCandidates[it].tokenIndex, KeywordKind.CREDIT) }

        val hasBalAnchor = BooleanArray(count) { featuresList[it].hasBalanceAnchorLeft }
        val hasFeeAnchor = BooleanArray(count) { featuresList[it].hasFeeAnchor }
        val hasCardAnchor = BooleanArray(count) { featuresList[it].hasCardAnchor }
        val isFirst = BooleanArray(count) { featuresList[it].isFirstInLine }
        val isLast = BooleanArray(count) { featuresList[it].isLastInLine }
        val hasSign = BooleanArray(count) { featuresList[it].hasExplicitSign }
        val isPlus = BooleanArray(count) { featuresList[it].isExplicitPlus }
        val isMinus = BooleanArray(count) { featuresList[it].isExplicitMinus }

        var hasCardMask = false
        var hasPromo = false
        var hasOtp = false
        var hasPercent = false

        for (i in 0 until tokens.size) {
            val t = tokens[i]
            if (t.type == TokenType.CARD_MASK) hasCardMask = true
            if (t.keywordKind == KeywordKind.PROMO) hasPromo = true
            if (t.keywordKind == KeywordKind.OTP) hasOtp = true
            if (t.type == TokenType.PERCENT) hasPercent = true
        }

        return FeatureVector(
            candidateCount = count,
            distancesToBalanceKeyword = distBalance,
            distancesToFeeKeyword = distFee,
            distancesToRefundKeyword = distRefund,
            distancesToDebitKeyword = distDebit,
            distancesToCreditKeyword = distCredit,
            hasBalanceAnchorLeft = hasBalAnchor,
            hasFeeAnchor = hasFeeAnchor,
            hasCardAnchor = hasCardAnchor,
            isFirstInLine = isFirst,
            isLastInLine = isLast,
            hasExplicitSign = hasSign,
            isExplicitPlus = isPlus,
            isExplicitMinus = isMinus,
            hasCardMask = hasCardMask,
            hasPromoKeyword = hasPromo,
            hasOtpKeyword = hasOtp,
            hasPercentToken = hasPercent
        )
    }

    private fun findDistanceToKeyword(
        tokens: TokenStream,
        centerIdx: Int,
        targetKind: KeywordKind
    ): Int {
        var minDistance = Int.MAX_VALUE
        for (i in 0 until tokens.size) {
            if (tokens[i].keywordKind == targetKind) {
                val d = abs(i - centerIdx)
                if (d < minDistance) {
                    minDistance = d
                }
            }
        }
        return minDistance
    }
}
