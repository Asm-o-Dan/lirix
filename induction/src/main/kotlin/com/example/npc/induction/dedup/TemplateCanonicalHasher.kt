package com.example.npc.induction.dedup

import java.security.MessageDigest

/**
 * Детерминированный генератор канонического SHA-256 хеша для динамических шаблонов.
 */
object TemplateCanonicalHasher {

    fun hash(
        sourceKey: String,
        pattern: String,
        bindingsJson: String = "",
        constantsJson: String = ""
    ): String {
        // Нормализация пробелов в регулярном выражении
        val normalizedPattern = pattern.trim().replace(Regex("""\s+"""), " ")
        val canonicalInput = "$sourceKey:$normalizedPattern:$bindingsJson:$constantsJson"

        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(canonicalInput.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
