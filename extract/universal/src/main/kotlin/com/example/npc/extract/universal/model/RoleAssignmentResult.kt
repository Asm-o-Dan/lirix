package com.example.npc.extract.universal.model

/**
 * Роли, назначаемые числовым слотам денежных сумм.
 */
enum class SlotRole {
    TX_AMOUNT,
    BALANCE,
    FEE,
    OTHER
}

/**
 * Назначение роли конкретному кандидату на сумму.
 */
data class RoleAssignment(
    val candidate: AmountCandidate,
    val role: SlotRole,
    val logProbability: Double
)

/**
 * Результат комбинаторного назначения ролей кандидатам.
 */
data class RoleAssignmentResult(
    val assignments: List<RoleAssignment>,
    val txAmount: AmountCandidate,
    val balance: AmountCandidate?,
    val fee: AmountCandidate?,
    val otherAmounts: List<AmountCandidate> = emptyList(),
    val totalScore: Double = 0.0,
    val confidence: Float = 0.90f
) {
    val logLikelihood: Double get() = totalScore
}

typealias SolverSolution = RoleAssignmentResult
