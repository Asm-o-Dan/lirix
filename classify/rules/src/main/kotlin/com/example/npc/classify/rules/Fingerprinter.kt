package com.example.npc.classify.rules

import java.security.MessageDigest
import java.util.Locale

/**
 * Детерминированный генератор отпечатков контента за O(n) без бэктрекинга регулярных выражений.
 * Приводит текст к устойчивому каноническому шаблону, заменяя динамические числовые параметры на '#'.
 */
object Fingerprinter {

    private const val MAX_INPUT_CHARS = 1024

    /**
     * Создает устойчивый шаблон из текста, заменяя числовые переменные и разделители на '#',
     * схлопывая пробелы, табуляции и неразрывные пробелы (NBSP, Narrow NBSP).
     */
    fun createTemplate(text: String, title: String? = null): String {
        val raw = if (title.isNullOrBlank()) text else "$title $text"
        val bounded = raw.take(MAX_INPUT_CHARS).lowercase(Locale.ROOT)

        return buildString(bounded.length) {
            var lastWasDigit = false
            for (ch in bounded) {
                when {
                    ch.isDigit() -> {
                        if (!lastWasDigit) append('#')
                        lastWasDigit = true
                    }
                    ch == ',' || ch == '.' -> {
                        // Разделитель внутри числа поглощается маской '#'
                        if (!lastWasDigit) append(ch)
                    }
                    ch.isWhitespace() || ch == '\u00A0' || ch == '\u202F' -> {
                        if (isNotEmpty() && last() != ' ') append(' ')
                        lastWasDigit = false
                    }
                    else -> {
                        append(ch)
                        lastWasDigit = false
                    }
                }
            }
        }.trim()
    }

    /**
     * Вычисляет детерминированный 64-символьный SHA-256 хеш отпечатка:
     * SHA-256("${cleanPkg}|${cleanSender}|${template}")
     */
    fun calculateFingerprint(packageName: String, sender: String?, text: String): String {
        val cleanPkg = packageName.trim().lowercase(Locale.ROOT)
        val cleanSender = sender?.trim()?.lowercase(Locale.ROOT).orEmpty()
        val template = createTemplate(text)
        val rawComposite = "$cleanPkg|$cleanSender|$template"

        val digest = MessageDigest.getInstance("SHA-256").digest(rawComposite.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Псевдоним / перегрузка для вычисления хэша с опциональным заголовком.
     */
    fun computeFingerprint(packageName: String, text: String, title: String? = null): String {
        return calculateFingerprint(packageName, title, text)
    }
}
