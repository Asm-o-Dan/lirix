package com.example.npc.classify.rules

import com.example.npc.core.model.classify.ClassificationResult
import com.example.npc.core.model.classify.Engine
import com.example.npc.core.model.classify.UserPrototype

/**
 * Стадия Prototype-First классификации.
 * Проверяет наличие подтвержденного пользователем прототипа (supportCount >= 2).
 * При подтверждении возвращает результат с высшим приоритетом (confidence = 1.0, Engine.PROTOTYPE),
 * выполняя short-circuit и минуя эвристические правила.
 */
object PrototypeStage {

    /**
     * Проверяет пользовательский прототип на соответствие порогу уверенности.
     *
     * @param prototype Опциональный прототип из хранилища.
     * @return ClassificationResult при supportCount >= CONFIDENCE_THRESHOLD (2), иначе null.
     */
    fun match(prototype: UserPrototype?): ClassificationResult? {
        if (prototype == null) return null

        return if (prototype.supportCount >= UserPrototype.CONFIDENCE_THRESHOLD) {
            ClassificationResult(
                category = prototype.category,
                confidence = 1.0,
                engine = Engine.PROTOTYPE,
                contentFingerprint = prototype.fingerprint
            )
        } else {
            null
        }
    }
}
