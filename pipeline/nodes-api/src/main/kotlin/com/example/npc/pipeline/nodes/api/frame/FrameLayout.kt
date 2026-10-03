package com.example.npc.pipeline.nodes.api.frame

/**
 * Топология регистровой памяти, вычисленная компилятором для скомпилированного конвейера.
 */
class FrameLayout(
    val longSlots: Int,
    val doubleSlots: Int,
    val refSlots: Int,
    val textSlots: Int,
    val textCapacity: Int = 1024,
    val requiredInputMask: Long = 0L
) {
    init {
        require(longSlots >= 0) { "longSlots must be non-negative: $longSlots" }
        require(doubleSlots >= 0) { "doubleSlots must be non-negative: $doubleSlots" }
        require(refSlots >= 0) { "refSlots must be non-negative: $refSlots" }
        require(textSlots >= 0) { "textSlots must be non-negative: $textSlots" }
        require(textCapacity > 0) { "textCapacity must be positive: $textCapacity" }
    }

    companion object {
        const val MASK_INPUT_TITLE: Long = 1L shl 0
        const val MASK_INPUT_TEXT: Long = 1L shl 1
        const val MASK_INPUT_SENDER: Long = 1L shl 2
        const val MASK_INPUT_POST_TIME: Long = 1L shl 3
        const val MASK_INPUT_PACKAGE: Long = 1L shl 4
        const val MASK_INPUT_CHANNEL_ID: Long = 1L shl 5
    }
}
