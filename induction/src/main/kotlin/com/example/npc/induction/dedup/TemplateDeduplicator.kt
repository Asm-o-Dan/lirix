package com.example.npc.induction.dedup

/**
 * Интерфейс поиска существующего шаблона по каноническому хешу.
 */
fun interface TemplateLookup {
    fun findByCanonicalHash(canonicalHash: String): String?
}

/**
 * Результат проверки дедупликации.
 */
sealed interface DeduplicationResult {
    data class Unique(val canonicalHash: String) : DeduplicationResult
    data class Duplicate(val existingTemplateId: String, val canonicalHash: String) : DeduplicationResult
}

/**
 * Компонент дедупликации динамических шаблонов.
 */
object TemplateDeduplicator {

    fun checkDuplicate(
        sourceKey: String,
        pattern: String,
        bindingsJson: String = "",
        constantsJson: String = "",
        lookup: TemplateLookup
    ): DeduplicationResult {
        val canonicalHash = TemplateCanonicalHasher.hash(sourceKey, pattern, bindingsJson, constantsJson)
        val existingId = lookup.findByCanonicalHash(canonicalHash)
        return if (existingId != null) {
            DeduplicationResult.Duplicate(existingId, canonicalHash)
        } else {
            DeduplicationResult.Unique(canonicalHash)
        }
    }
}
