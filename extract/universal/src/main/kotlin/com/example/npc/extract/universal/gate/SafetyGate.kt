package com.example.npc.extract.universal.gate

import com.example.npc.core.text.TokenStream
import com.example.npc.core.text.model.TokenType
import com.example.npc.extract.universal.ExtractionVerdict
import com.example.npc.extract.universal.OpTypeResolution
import com.example.npc.extract.universal.SafetyCheckResult
import com.example.npc.extract.universal.model.RoleAssignmentResult

/**
 * Защитный шлюз для фильтрации ложных срабатываний и классификации вердиктов (ACCEPT, SUGGEST, REJECT).
 */
class SafetyGate(
    private val vetoRules: List<VetoRule> = listOf(OtpVetoRule, PromoVetoRule, CatalogVetoRule)
) {

    companion object {
        val Default = SafetyGate()

        private val KNOWN_BANK_PACKAGES = setOf(
            "com.apb.mobile",
            "com.prisbank.app",
            "md.maib.maibank",
            "ru.sberbankmobile",
            "com.idamob.tinkoff.android",
            "ru.vtb24.mobilebanking"
        )

        private val KNOWN_NON_BANK_PACKAGES = setOf(
            "org.telegram.messenger",
            "org.thunderdog.challegram",
            "com.whatsapp",
            "com.viber.voip",
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.instagram.android"
        )
    }

    /**
     * Оценка надежности извлечения и вынесение вердикта.
     */
    fun evaluate(
        tokens: TokenStream,
        solution: RoleAssignmentResult?,
        opType: OpTypeResolution,
        sourcePackage: String = "",
        isKnownBankingApp: Boolean = KNOWN_BANK_PACKAGES.contains(sourcePackage)
    ): SafetyCheckResult {
        // 1. Проверка наличия решения
        if (solution == null) {
            return SafetyCheckResult(
                verdict = ExtractionVerdict.REJECT,
                finalScore = 0.0f,
                isOtpVeto = false,
                isPromoVeto = false,
                reason = "No valid TX_AMOUNT candidate"
            )
        }

        // 2. Прогон правил Veto
        for (rule in vetoRules) {
            val veto = rule.evaluate(tokens, solution, opType, sourcePackage)
            if (veto.isVetoed) {
                val score = if (veto.isOtp) 0.0f else if (veto.isPromo) 0.10f else 0.20f
                return SafetyCheckResult(
                    verdict = ExtractionVerdict.REJECT,
                    finalScore = score,
                    isOtpVeto = veto.isOtp,
                    isPromoVeto = veto.isPromo,
                    reason = veto.reason
                )
            }
        }

        // 3. Расчет скора
        val baseOpScore = opType.confidence * 0.40f
        val solverScore = solution.confidence * 0.40f

        val hasCardMask = tokens.any { it.type == TokenType.CARD_MASK }
        val cardBonus = if (hasCardMask) 0.10f else 0.0f

        val hasBalance = solution.balance != null
        val balanceBonus = if (hasBalance) 0.10f else 0.0f

        var rawScore = baseOpScore + solverScore + cardBonus + balanceBonus

        // 4. Учет фактора источника
        if (isKnownBankingApp) {
            rawScore += 0.05f
        } else if (KNOWN_NON_BANK_PACKAGES.contains(sourcePackage)) {
            rawScore *= 0.60f
        }

        val finalScore = rawScore.coerceIn(0.0f, 1.0f)

        // 5. Определение вердикта
        val verdict = when {
            finalScore >= 0.80f -> ExtractionVerdict.ACCEPT
            finalScore >= 0.50f -> ExtractionVerdict.SUGGEST
            else -> ExtractionVerdict.REJECT
        }

        return SafetyCheckResult(
            verdict = verdict,
            finalScore = finalScore,
            isOtpVeto = false,
            isPromoVeto = false,
            reason = null
        )
    }
}
