package com.example.npc.extract.universal

import com.example.npc.extract.universal.model.AmountCandidate
import com.example.npc.extract.universal.model.RoleAssignment
import com.example.npc.extract.universal.model.RoleAssignmentResult
import com.example.npc.extract.universal.model.SlotRole
import com.example.npc.extract.universal.model.SolverSolution
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

typealias SlotRole = SlotRole
typealias RoleAssignment = RoleAssignment
typealias RoleAssignmentResult = RoleAssignmentResult
typealias SolverSolution = SolverSolution

/**
 * Оптимальный вероятностный солвер распределения ролей (TX_AMOUNT, BALANCE, FEE, OTHER)
 * для кандидатов на суммы через комбинаторный перебор допустимых конфигураций.
 */
interface RoleAssignmentSolver {

    /**
     * Вычисляет оптимальное распределение ролей с конфигурацией скоринга по умолчанию.
     */
    fun solve(features: List<CandidateFeatures>): RoleAssignmentResult?

    /**
     * Вычисляет оптимальное распределение ролей с заданной конфигурацией скоринга.
     */
    fun solve(features: List<CandidateFeatures>, config: ScoringConfig): RoleAssignmentResult?

    companion object : RoleAssignmentSolver by DefaultRoleAssignmentSolver() {
        fun create(): RoleAssignmentSolver = DefaultRoleAssignmentSolver()
    }
}

class DefaultRoleAssignmentSolver : RoleAssignmentSolver {

    override fun solve(features: List<CandidateFeatures>): RoleAssignmentResult? =
        solve(features, ScoringConfig.DEFAULT)

    override fun solve(
        features: List<CandidateFeatures>,
        config: ScoringConfig
    ): RoleAssignmentResult? {
        if (features.isEmpty()) return null

        val boundedFeatures = features.take(MAX_SOLVER_CANDIDATES)
        val k = boundedFeatures.size

        // Быстрый путь для одиночного кандидата
        if (k == 1) {
            val feat = boundedFeatures[0]
            val score = computeLogProb(feat, SlotRole.TX_AMOUNT, 0, config)
            val assignment = RoleAssignment(feat.candidate, SlotRole.TX_AMOUNT, score)
            val conf = calculateConfidence(score, 1, hasBalance = false, config)

            return RoleAssignmentResult(
                assignments = listOf(assignment),
                txAmount = feat.candidate,
                balance = null,
                fee = null,
                otherAmounts = emptyList(),
                totalScore = score,
                confidence = conf
            )
        }

        var bestScore = Double.NEGATIVE_INFINITY
        var bestRoles: Array<SlotRole>? = null

        // Комбинаторный перебор допустимых назначений:
        // 1. Выбираем, кто получает TX_AMOUNT (строго 1 кандидат)
        for (txIdx in 0 until k) {
            // 2. Выбираем, кто получает BALANCE (максимум 1 кандидат, или никто)
            // balIdx == -1 означает, что роль BALANCE никому не назначена
            val possibleBalIndices = (-1 until k).filter { it != txIdx }

            for (balIdx in possibleBalIndices) {
                // Если кандидат претендует на BALANCE, он обязан иметь якорь или быть в конце отдельной строки
                if (balIdx != -1) {
                    val balFeat = boundedFeatures[balIdx]
                    val isEligibleForBalance = balFeat.hasBalanceAnchorLeft ||
                            (balFeat.isLastInLine && balFeat.lineIndex > 0)
                    if (!isEligibleForBalance) continue
                }

                // 3. Для оставшихся кандидатов перебираем роли FEE или OTHER
                val remainingIndices = (0 until k).filter { it != txIdx && it != balIdx }
                val numRemaining = remainingIndices.size

                val numCombinations = 1 shl numRemaining
                for (mask in 0 until numCombinations) {
                    val roles = Array(k) { SlotRole.OTHER }
                    roles[txIdx] = SlotRole.TX_AMOUNT
                    if (balIdx != -1) {
                        roles[balIdx] = SlotRole.BALANCE
                    }

                    for (r in 0 until numRemaining) {
                        val candIdx = remainingIndices[r]
                        val isFee = ((mask shr r) and 1) == 1
                        if (isFee && boundedFeatures[candIdx].hasFeeAnchor) {
                            roles[candIdx] = SlotRole.FEE
                        } else {
                            roles[candIdx] = SlotRole.OTHER
                        }
                    }

                    // Вычисляем суммарный логарифм правдоподобия
                    var currentScore = 0.0
                    for (i in 0 until k) {
                        currentScore += computeLogProb(boundedFeatures[i], roles[i], i, config)
                    }

                    if (currentScore > bestScore) {
                        bestScore = currentScore
                        bestRoles = roles
                    }
                }
            }
        }

        if (bestRoles == null) return null

        // Сборка решения
        val assignments = ArrayList<RoleAssignment>(k)
        var txAmount: AmountCandidate? = null
        var balance: AmountCandidate? = null
        var fee: AmountCandidate? = null
        val other = ArrayList<AmountCandidate>()

        for (i in 0 until k) {
            val cand = boundedFeatures[i].candidate
            val role = bestRoles[i]
            val logP = computeLogProb(boundedFeatures[i], role, i, config)
            assignments.add(RoleAssignment(cand, role, logP))

            when (role) {
                SlotRole.TX_AMOUNT -> txAmount = cand
                SlotRole.BALANCE -> balance = cand
                SlotRole.FEE -> fee = cand
                SlotRole.OTHER -> other.add(cand)
            }
        }

        val requiredTx = txAmount ?: boundedFeatures[0].candidate
        val conf = calculateConfidence(bestScore, k, balance != null, config)

        return RoleAssignmentResult(
            assignments = assignments,
            txAmount = requiredTx,
            balance = balance,
            fee = fee,
            otherAmounts = other,
            totalScore = bestScore,
            confidence = conf
        )
    }

    private fun computeLogProb(
        feat: CandidateFeatures,
        role: SlotRole,
        candIndex: Int,
        config: ScoringConfig
    ): Double {
        return when (role) {
            SlotRole.TX_AMOUNT -> {
                if (feat.hasBalanceAnchorLeft) {
                    ln(0.0001) // Сильный штраф: якорь "Sold:" исключает транзакционную сумму
                } else if (feat.hasExplicitSign) {
                    ln(config.priorTxAmountWithSign)
                } else {
                    // Небольшой бонус первому кандидату при прочих равных
                    val posBonus = if (candIndex == 0) 1.05 else 1.0
                    ln(min(0.99, config.priorTxAmountNoAnchor * posBonus))
                }
            }
            SlotRole.BALANCE -> {
                if (feat.hasBalanceAnchorLeft) {
                    ln(config.priorBalanceWithAnchor)
                } else if (feat.isLastInLine && feat.lineIndex > 0) {
                    ln(0.50)
                } else {
                    ln(config.priorBalanceNoAnchor)
                }
            }
            SlotRole.FEE -> {
                if (feat.hasFeeAnchor) {
                    ln(config.priorFeeWithAnchor)
                } else {
                    ln(config.priorFeeNoAnchor)
                }
            }
            SlotRole.OTHER -> {
                ln(config.priorOther)
            }
        }
    }

    private fun calculateConfidence(
        totalScore: Double,
        candidateCount: Int,
        hasBalance: Boolean,
        config: ScoringConfig
    ): Float {
        val avgLogProb = totalScore / max(1, candidateCount)
        val pAvg = exp(avgLogProb).toFloat()
        val balanceBonus = if (hasBalance) config.bonusBalance else 0.0f
        return min(1.0f, max(0.50f, pAvg + balanceBonus))
    }

    companion object {
        const val MAX_SOLVER_CANDIDATES = 4
    }
}
