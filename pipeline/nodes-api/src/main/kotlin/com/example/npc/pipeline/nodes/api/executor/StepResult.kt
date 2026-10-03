package com.example.npc.pipeline.nodes.api.executor

/**
 * Безаллокационный упакованный результат выполнения узла (64-битный Long).
 * - Старшие 32 бита: код операции [op] (NEXT, JUMP, HALT, FAIL).
 * - Младшие 32 бита: целочисленный аргумент [arg] (targetPc, reason, errorCode).
 */
@JvmInline
value class StepResult private constructor(val bits: Long) {
    val op: Int get() = (bits ushr 32).toInt()
    val arg: Int get() = bits.toInt()

    val isNext: Boolean get() = op == OP_NEXT
    val isJump: Boolean get() = op == OP_JUMP
    val isHalt: Boolean get() = op == OP_HALT
    val isFail: Boolean get() = op == OP_FAIL

    companion object {
        const val OP_NEXT: Int = 0
        const val OP_JUMP: Int = 1
        const val OP_HALT: Int = 2
        const val OP_FAIL: Int = 3

        val NEXT: StepResult = StepResult(OP_NEXT.toLong() shl 32)

        fun jump(targetPc: Int): StepResult {
            require(targetPc >= 0) { "targetPc must be non-negative: $targetPc" }
            return StepResult((OP_JUMP.toLong() shl 32) or (targetPc.toLong() and 0xFFFF_FFFFL))
        }

        fun halt(reason: Int = 0): StepResult =
            StepResult((OP_HALT.toLong() shl 32) or (reason.toLong() and 0xFFFF_FFFFL))

        fun fail(errorCode: Int): StepResult =
            StepResult((OP_FAIL.toLong() shl 32) or (errorCode.toLong() and 0xFFFF_FFFFL))
    }
}
