package com.example.npc.core.model.classify

/**
 * Результат выполнения семантической классификации события.
 *
 * @property category Итоговая доменная категория.
 * @property confidence Степень уверенности (от 0.0 до 1.0).
 * @property engine Механизм, определивший категорию.
 * @property contentFingerprint SHA-256 хэш шаблона текста события.
 */
data class ClassificationResult(
    val category: Category,
    val confidence: Double,
    val engine: Engine,
    val contentFingerprint: String
) {
    init {
        require(confidence in 0.0..1.0) { "confidence must be in 0.0..1.0 (got $confidence)" }
        require(contentFingerprint.matches(Regex("^[0-9a-f]{64}$"))) {
            "contentFingerprint must be 64-char hex SHA-256 (got '$contentFingerprint')"
        }
    }

    val typedConfidence: Confidence get() = Confidence(confidence)

    companion object {
        fun unclassified(fingerprint: String): ClassificationResult =
            ClassificationResult(
                category = Category.UNCLASSIFIED,
                confidence = 0.0,
                engine = Engine.NONE,
                contentFingerprint = fingerprint
            )

        fun fromPrototype(prototype: UserPrototype): ClassificationResult =
            ClassificationResult(
                category = prototype.category,
                confidence = 1.0,
                engine = Engine.PROTOTYPE,
                contentFingerprint = prototype.fingerprint
            )
    }
}
