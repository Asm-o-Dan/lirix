package com.example.npc.core.model.classify

/**
 * Строго валидированный уровень уверенности классификации в диапазоне [0.0 .. 1.0].
 */
@JvmInline
value class Confidence(val value: Double) : Comparable<Confidence> {
    init {
        require(value in 0.0..1.0) { "Confidence value must be in range [0.0, 1.0], but was: $value" }
    }

    override fun compareTo(other: Confidence): Int = value.compareTo(other.value)

    companion object {
        val ZERO = Confidence(0.0)
        val MAXIMUM = Confidence(1.0)
        val HIGH_THRESHOLD = Confidence(0.85)
        val PROTOTYPE_CONFIDENCE = Confidence(1.0)
    }
}
