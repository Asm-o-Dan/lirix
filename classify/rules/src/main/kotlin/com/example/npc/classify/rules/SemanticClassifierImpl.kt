package com.example.npc.classify.rules

import com.example.npc.core.model.Event
import com.example.npc.core.model.classify.ClassificationResult
import com.example.npc.core.model.classify.PackageGatedRouter
import com.example.npc.core.model.classify.SemanticClassifier
import com.example.npc.core.model.classify.UserPrototype

/**
 * Фасадный координатор семантической классификации событий.
 *
 * Реализует двухфазный пайплайн:
 * 1. Prototype-First: при наличии подтвержденного прототипа (supportCount >= 2) выполняет мгновенный
 *    short-circuit с confidence = 1.0 (Engine.PROTOTYPE), минуя эвристический классификатор.
 * 2. Rule-Based Fallback: при отсутствии подтвержденного прототипа вычисляет канонический
 *    fingerprint контента за O(n) и делегирует классификацию эвристическому RuleBasedCategoryClassifier.
 */
class SemanticClassifierImpl(
    private val router: PackageGatedRouter,
    private val ruleClassifier: RuleBasedCategoryClassifier
) : SemanticClassifier {

    override fun classify(event: Event, prototype: UserPrototype?): ClassificationResult {
        return classify(event, prototype?.packageName.orEmpty(), prototype)
    }

    override fun classify(event: Event, packageName: String, prototype: UserPrototype?): ClassificationResult {
        // 1. Фаза 1 — Проверка пользовательского прототипа (Short-Circuit)
        val prototypeResult = PrototypeStage.match(prototype)
        if (prototypeResult != null) {
            return prototypeResult
        }

        // 2. Определение имени пакета и вычисление детерминированного 64-символьного SHA-256 fingerprint
        val effectivePackage = packageName.ifEmpty { prototype?.packageName.orEmpty() }
        val fingerprint = prototype?.fingerprint ?: Fingerprinter.calculateFingerprint(
            packageName = effectivePackage,
            sender = event.title.takeIf { it.isNotBlank() },
            text = event.text.ifEmpty { event.title }
        )

        // 3. Фаза 2 — Эвристические правила классификации с пакетным гейтингом
        return ruleClassifier.classifyByRules(event, effectivePackage, fingerprint)
    }
}
