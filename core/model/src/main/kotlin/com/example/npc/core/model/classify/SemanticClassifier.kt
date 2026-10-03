package com.example.npc.core.model.classify

import com.example.npc.core.model.Event

/**
 * Контракт детерминированного семантического классификатора событий.
 *
 * Реализует двухфазную чистую классификацию:
 * 1. Prototype-First: при наличии в БД прототипа с supportCount >= 2 возвращает его категорию с confidence = 1.0.
 * 2. Rule-Based Heuristics: при отсутствии прототипа применяет статические правила на основе пакета и текста.
 */
interface SemanticClassifier {

    /**
     * Выполняет классификацию события с учетом ранее найденного пользовательского прототипа.
     *
     * @param event Структурированное событие.
     * @param prototype Опциональный пользовательский прототип для данного пакета и fingerprint (если найден в хранилище).
     * @return Детерминированный результат классификации ClassificationResult.
     */
    fun classify(event: Event, prototype: UserPrototype?): ClassificationResult

    fun classify(event: Event, packageName: String, prototype: UserPrototype?): ClassificationResult =
        classify(event, prototype)
}
