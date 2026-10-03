package com.example.npc.extract.universal.gate

import com.example.npc.core.text.TokenStream
import com.example.npc.core.text.model.KeywordKind
import com.example.npc.core.text.model.TokenType
import com.example.npc.extract.universal.OpTypeResolution
import com.example.npc.extract.universal.model.RoleAssignmentResult

/**
 * Результат проверки правила вето.
 */
data class VetoResult(
    val isVetoed: Boolean,
    val isOtp: Boolean = false,
    val isPromo: Boolean = false,
    val reason: String? = null
) {
    companion object {
        val Allowed = VetoResult(isVetoed = false)
        fun otpVeto(reason: String) = VetoResult(isVetoed = true, isOtp = true, reason = reason)
        fun promoVeto(reason: String) = VetoResult(isVetoed = true, isPromo = true, reason = reason)
        fun otherVeto(reason: String) = VetoResult(isVetoed = true, reason = reason)
    }
}

/**
 * Интерфейс правила вето для отсечения ложных срабатываний.
 */
interface VetoRule {
    fun evaluate(
        tokens: TokenStream,
        solution: RoleAssignmentResult?,
        opType: OpTypeResolution,
        sourcePackage: String
    ): VetoResult
}

/**
 * Вето на одноразовые пароли подтверждения (OTP).
 * Если в сообщении обнаружен токен KEYWORD(OTP) и 4-8 значный изолированный числовой код.
 */
object OtpVetoRule : VetoRule {
    private val OTP_KEYWORDS = setOf("код", "cod", "code", "otp", "parola", "пароль", "паролем")

    override fun evaluate(
        tokens: TokenStream,
        solution: RoleAssignmentResult?,
        opType: OpTypeResolution,
        sourcePackage: String
    ): VetoResult {
        var hasOtpKeyword = false
        var hasIsolatedCode = false

        for (i in 0 until tokens.size) {
            val token = tokens[i]
            if (token.keywordKind == KeywordKind.OTP ||
                (token.type == TokenType.WORD && OTP_KEYWORDS.contains(token.text.lowercase()))
            ) {
                hasOtpKeyword = true
            }

            if (token.type == TokenType.NUMBER) {
                val cleanDigits = token.text.filter { it.isDigit() }
                // 4-8 значный код, не являющийся частью распознанной суммы транзакции с валютой
                val isTxAmountToken = solution?.txAmount?.tokenIndex == i
                val isBalanceToken = solution?.balance?.tokenIndex == i
                if (cleanDigits.length in 4..8 && !isTxAmountToken && !isBalanceToken) {
                    hasIsolatedCode = true
                }
            }
        }

        if (hasOtpKeyword && hasIsolatedCode) {
            return VetoResult.otpVeto("OTP code detected in financial context")
        }
        return VetoResult.Allowed
    }
}

/**
 * Вето на рекламные промо-рассылки.
 * Наличие токена KEYWORD(PROMO) или знака %, скидочных слов без маски карты и без баланса.
 */
object PromoVetoRule : VetoRule {
    private val PROMO_STEMS = listOf("скидк", "акци", "reducer", "promo", "cashback до", "распродаж", "discount")

    override fun evaluate(
        tokens: TokenStream,
        solution: RoleAssignmentResult?,
        opType: OpTypeResolution,
        sourcePackage: String
    ): VetoResult {
        var hasPromoKeyword = false
        var hasCardMask = false
        var hasBalance = solution?.balance != null

        for (i in 0 until tokens.size) {
            val token = tokens[i]
            if (token.type == TokenType.CARD_MASK) {
                hasCardMask = true
            }
            if (token.keywordKind == KeywordKind.BALANCE) {
                hasBalance = true
            }
            if (token.keywordKind == KeywordKind.PROMO || token.type == TokenType.PERCENT) {
                hasPromoKeyword = true
            } else if (token.type == TokenType.WORD) {
                val lower = token.text.lowercase()
                if (PROMO_STEMS.any { lower.contains(it) }) {
                    hasPromoKeyword = true
                }
            }
        }

        if (hasPromoKeyword && !hasCardMask && !hasBalance) {
            return VetoResult.promoVeto("Promo broadcast message detected")
        }
        return VetoResult.Allowed
    }
}

/**
 * Вето на каталоги и прайс-листы (более 4 валютных чисел без якорных признаков).
 */
object CatalogVetoRule : VetoRule {
    override fun evaluate(
        tokens: TokenStream,
        solution: RoleAssignmentResult?,
        opType: OpTypeResolution,
        sourcePackage: String
    ): VetoResult {
        val totalCandidates = (solution?.assignments?.size ?: 0)
        val hasCard = tokens.any { it.type == TokenType.CARD_MASK }
        val hasBalance = solution?.balance != null

        if (totalCandidates > 4 && !hasCard && !hasBalance) {
            return VetoResult.otherVeto("Catalog/price list detected (too many currency amounts)")
        }
        return VetoResult.Allowed
    }
}
