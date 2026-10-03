package com.example.npc.core.model.normalize

import com.example.npc.core.model.DeduplicationKey
import com.example.npc.core.model.Event
import com.example.npc.core.model.Lang
import com.example.npc.core.model.RawEvent
import com.example.npc.core.model.SourceId
import com.example.npc.core.model.ThreadKey
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

object EventNormalizer {
    /**
     * Очистка сырого текста:
     * 1. null / empty -> ""
     * 2. Удаление невидимых символов [\u200B-\u200D\uFEFF\u00AD]
     * 3. \r\n и \r -> \n
     * 4. Замена горизонтальных пробелов (\t, \u00A0, \u2000-\u200A и т.д.) на одиночный пробел ' '
     * 5. trim() каждой строки
     * 6. Схлопывание \n{3,} в \n\n
     * 7. Финальный trim()
     */
    fun cleanText(rawText: String?): String {
        if (rawText.isNullOrEmpty()) return ""

        // 1. Remove zero-width & invisible chars
        val invisibleRegex = Regex("[\u200B-\u200D\uFEFF\u00AD]")
        val stripped = rawText.replace(invisibleRegex, "")
        if (stripped.isEmpty()) return ""

        // 2. Unify line breaks
        val unifiedNewlines = stripped.replace("\r\n", "\n").replace('\r', '\n')

        // 3. Process lines & horizontal spaces
        val horizontalSpaceRegex = Regex("[\t\u0020\u00A0\u2000-\u200A]+")
        val lines = unifiedNewlines.split('\n').map { line ->
            line.replace(horizontalSpaceRegex, " ").trim()
        }

        // 4. Collapse 3+ newlines to 2 (\n\n)
        val joined = lines.joinToString("\n")
        val collapsedNewlines = joined.replace(Regex("\n{3,}"), "\n\n")

        return collapsedNewlines.trim()
    }

    /**
     * Быстрое детерминированное определение языка (RU, EN, UNK):
     * 1. Подсчёт кириллических (\u0400..\u04FF) и латинских (a..z, A..Z) символов.
     * 2. Если суммарно букв < 3 -> Lang.UNK.
     * 3. Если доля кириллицы >= 0.70 -> Lang.RU.
     * 4. Если доля латиницы >= 0.70 -> Lang.EN.
     * 5. Иначе -> Lang.UNK.
     */
    fun detectLang(text: String): Lang {
        var cyrillicCount = 0
        var latinCount = 0

        for (c in text) {
            when {
                c in '\u0400'..'\u04FF' -> cyrillicCount++
                c in 'a'..'z' || c in 'A'..'Z' -> latinCount++
            }
        }

        val totalLetters = cyrillicCount + latinCount
        if (totalLetters < 3) return Lang.UNK

        val cyrillicRatio = cyrillicCount.toDouble() / totalLetters.toDouble()
        val latinRatio = latinCount.toDouble() / totalLetters.toDouble()

        return when {
            cyrillicRatio >= 0.70 -> Lang.RU
            latinRatio >= 0.70 -> Lang.EN
            else -> Lang.UNK
        }
    }

    /**
     * Фабричный метод создания нормализованного события из RawEvent:
     * 1. cleanText(text) -> normalizedText
     * 2. detectLang(normalizedText) -> lang
     * 3. Конструирование Event(id = 0L, rawId = rawEvent.id, ts = rawEvent.receivedAt, title, text, normalizedText, lang, threadKey, isUpdateOf)
     */
    fun normalize(
        rawEvent: RawEvent,
        title: String,
        text: String,
        threadKey: ThreadKey? = null,
        isUpdateOf: Long? = null
    ): Event {
        val normalized = cleanText(text)
        val lang = detectLang(normalized)
        return Event(
            id = 0L,
            rawId = rawEvent.id,
            ts = rawEvent.receivedAt,
            title = title,
            text = text,
            normalizedText = normalized,
            lang = lang,
            threadKey = threadKey,
            isUpdateOf = isUpdateOf
        )
    }

    /**
     * Расчёт детерминированного криптографического хеша дедупликации (SHA-256) входящего сырого события:
     * 1. Валидация входных аргументов (не пустые / не blank)
     * 2. Валидация payloadJson как JSON-объекта (начинается с '{' и заканчивается '}')
     * 3. Канонизация payloadJson (исключение полей "postTime" и "when", сортировка ключей по ASCII, компактный формат)
     * 4. Вычисление SHA-256 от канонического JSON в UTF-8
     * 5. Формирование композитной строки: "${source.value}|$packageName|$payloadHash"
     * 6. Вычисление финального SHA-256 от композитной строки в UTF-8
     * 7. Возврат DeduplicationKey(finalHash)
     */
    fun computeDeduplicationKey(
        source: SourceId,
        packageName: String,
        payloadJson: String
    ): DeduplicationKey {
        require(source.value.isNotBlank()) { "source value must not be blank" }
        require(packageName.isNotBlank()) { "packageName must not be blank" }
        require(payloadJson.isNotBlank()) { "payloadJson must not be blank" }

        val trimmed = payloadJson.trim()
        require(trimmed.startsWith("{") && trimmed.endsWith("}")) {
            "payloadJson must be a valid JSON Object"
        }

        val canonicalPayload = canonicalizeJson(trimmed)
        val payloadHash = sha256Hex(canonicalPayload)

        val composite = "${source.value}|$packageName|$payloadHash"
        val finalHash = sha256Hex(composite)

        return DeduplicationKey(finalHash)
    }

    internal fun canonicalizeJson(json: String): String {
        val trimmed = json.trim()
        require(trimmed.startsWith("{") && trimmed.endsWith("}")) {
            "payloadJson must be a valid JSON Object"
        }
        val parser = JsonParser(trimmed)
        val parsed = parser.parseTopLevelObject()
        return parsed.toCanonicalString(isTopLevel = true)
    }

    internal fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

private sealed class JsonValue {
    data class JsonObject(val entries: Map<String, JsonValue>) : JsonValue()
    data class JsonArray(val elements: List<JsonValue>) : JsonValue()
    data class JsonString(val value: String) : JsonValue()
    data class JsonNumber(val raw: String) : JsonValue()
    data class JsonBoolean(val value: Boolean) : JsonValue()
    object JsonNull : JsonValue()

    fun toCanonicalString(isTopLevel: Boolean = false): String {
        return when (this) {
            is JsonObject -> {
                val filtered = if (isTopLevel) {
                    entries.filterKeys { it != "postTime" && it != "when" }
                } else {
                    entries
                }
                val sorted = filtered.toSortedMap()
                val inner = sorted.entries.joinToString(",") { (k, v) ->
                    "\"${escapeJsonString(k)}\":${v.toCanonicalString(false)}"
                }
                "{$inner}"
            }
            is JsonArray -> {
                val inner = elements.joinToString(",") { it.toCanonicalString(false) }
                "[$inner]"
            }
            is JsonString -> "\"${escapeJsonString(value)}\""
            is JsonNumber -> canonicalizeNumber(raw)
            is JsonBoolean -> value.toString()
            is JsonNull -> "null"
        }
    }
}

private fun escapeJsonString(s: String): String {
    val sb = StringBuilder()
    for (ch in s) {
        when (ch) {
            '\\' -> sb.append("\\\\")
            '"' -> sb.append("\\\"")
            '\b' -> sb.append("\\b")
            '\u000C' -> sb.append("\\f")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> {
                if (ch < ' ') {
                    sb.append(String.format(Locale.ROOT, "\\u%04x", ch.code))
                } else {
                    sb.append(ch)
                }
            }
        }
    }
    return sb.toString()
}

private fun canonicalizeNumber(raw: String): String {
    return try {
        val bd = BigDecimal(raw)
        if (bd.compareTo(BigDecimal.ZERO) == 0) {
            "0"
        } else {
            bd.stripTrailingZeros().toPlainString()
        }
    } catch (_: Exception) {
        raw
    }
}

private class JsonParser(private val src: String) {
    private var pos = 0

    private fun skipWhitespace() {
        while (pos < src.length && src[pos] in " \t\r\n") {
            pos++
        }
    }

    private fun peek(): Char? {
        skipWhitespace()
        return if (pos < src.length) src[pos] else null
    }

    private fun consume(): Char {
        skipWhitespace()
        if (pos >= src.length) {
            throw IllegalArgumentException("Unexpected end of JSON input")
        }
        return src[pos++]
    }

    private fun expect(expected: Char) {
        val ch = consume()
        if (ch != expected) {
            throw IllegalArgumentException("Expected '$expected' but found '$ch' at position $pos")
        }
    }

    fun parseTopLevelObject(): JsonValue.JsonObject {
        skipWhitespace()
        if (peek() != '{') {
            throw IllegalArgumentException("payloadJson must be a valid JSON Object")
        }
        val obj = parseObject()
        skipWhitespace()
        if (pos != src.length) {
            throw IllegalArgumentException("Unexpected trailing content at position $pos")
        }
        return obj
    }

    private fun parseValue(): JsonValue {
        val ch = peek() ?: throw IllegalArgumentException("Unexpected end of JSON input")
        return when (ch) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> JsonValue.JsonString(parseString())
            't', 'f' -> parseBoolean()
            'n' -> parseNull()
            '-', in '0'..'9' -> parseNumber()
            else -> throw IllegalArgumentException("Unexpected character '$ch' at position $pos")
        }
    }

    private fun parseObject(): JsonValue.JsonObject {
        expect('{')
        val map = mutableMapOf<String, JsonValue>()
        skipWhitespace()
        if (peek() == '}') {
            consume()
            return JsonValue.JsonObject(map)
        }
        while (true) {
            skipWhitespace()
            if (peek() != '"') {
                throw IllegalArgumentException("Expected string key at position $pos")
            }
            val key = parseString()
            skipWhitespace()
            expect(':')
            val value = parseValue()
            map[key] = value

            skipWhitespace()
            val next = peek()
            if (next == ',') {
                consume()
                skipWhitespace()
                if (peek() == '}') {
                    throw IllegalArgumentException("Trailing comma in object at position $pos")
                }
            } else if (next == '}') {
                consume()
                break
            } else {
                throw IllegalArgumentException("Expected ',' or '}' at position $pos")
            }
        }
        return JsonValue.JsonObject(map)
    }

    private fun parseArray(): JsonValue.JsonArray {
        expect('[')
        val list = mutableListOf<JsonValue>()
        skipWhitespace()
        if (peek() == ']') {
            consume()
            return JsonValue.JsonArray(list)
        }
        while (true) {
            val value = parseValue()
            list.add(value)
            skipWhitespace()
            val next = peek()
            if (next == ',') {
                consume()
                skipWhitespace()
                if (peek() == ']') {
                    throw IllegalArgumentException("Trailing comma in array at position $pos")
                }
            } else if (next == ']') {
                consume()
                break
            } else {
                throw IllegalArgumentException("Expected ',' or ']' at position $pos")
            }
        }
        return JsonValue.JsonArray(list)
    }

    private fun parseString(): String {
        expect('"')
        val sb = StringBuilder()
        while (pos < src.length) {
            val ch = src[pos++]
            if (ch == '"') {
                return sb.toString()
            }
            if (ch == '\\') {
                if (pos >= src.length) throw IllegalArgumentException("Unterminated escape sequence at position $pos")
                val esc = src[pos++]
                when (esc) {
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    '/' -> sb.append('/')
                    'b' -> sb.append('\b')
                    'f' -> sb.append('\u000C')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    'u' -> {
                        if (pos + 4 > src.length) throw IllegalArgumentException("Incomplete unicode escape at position $pos")
                        val hex = src.substring(pos, pos + 4)
                        pos += 4
                        val code = hex.toIntOrNull(16) ?: throw IllegalArgumentException("Invalid unicode escape: $hex")
                        sb.append(code.toChar())
                    }
                    else -> throw IllegalArgumentException("Invalid escape sequence: \\$esc at position $pos")
                }
            } else {
                if (ch < ' ') {
                    throw IllegalArgumentException("Unescaped control character in string at position $pos")
                }
                sb.append(ch)
            }
        }
        throw IllegalArgumentException("Unterminated string literal")
    }

    private fun parseBoolean(): JsonValue.JsonBoolean {
        skipWhitespace()
        if (src.startsWith("true", pos)) {
            val endPos = pos + 4
            if (endPos == src.length || isDelimiter(src[endPos])) {
                pos = endPos
                return JsonValue.JsonBoolean(true)
            }
        }
        if (src.startsWith("false", pos)) {
            val endPos = pos + 5
            if (endPos == src.length || isDelimiter(src[endPos])) {
                pos = endPos
                return JsonValue.JsonBoolean(false)
            }
        }
        throw IllegalArgumentException("Invalid boolean literal at position $pos")
    }

    private fun parseNull(): JsonValue.JsonNull {
        skipWhitespace()
        if (src.startsWith("null", pos)) {
            val endPos = pos + 4
            if (endPos == src.length || isDelimiter(src[endPos])) {
                pos = endPos
                return JsonValue.JsonNull
            }
        }
        throw IllegalArgumentException("Invalid null literal at position $pos")
    }

    private fun parseNumber(): JsonValue.JsonNumber {
        skipWhitespace()
        val start = pos
        if (pos < src.length && src[pos] == '-') {
            pos++
        }
        if (pos >= src.length) {
            throw IllegalArgumentException("Invalid number at position $pos")
        }
        if (src[pos] == '0') {
            pos++
            if (pos < src.length && src[pos] in '0'..'9') {
                throw IllegalArgumentException("Leading zeros not allowed in number at position $pos")
            }
        } else if (src[pos] in '1'..'9') {
            while (pos < src.length && src[pos] in '0'..'9') {
                pos++
            }
        } else {
            throw IllegalArgumentException("Invalid number at position $pos")
        }

        if (pos < src.length && src[pos] == '.') {
            pos++
            if (pos >= src.length || src[pos] !in '0'..'9') {
                throw IllegalArgumentException("Invalid fraction in number at position $pos")
            }
            while (pos < src.length && src[pos] in '0'..'9') {
                pos++
            }
        }

        if (pos < src.length && (src[pos] == 'e' || src[pos] == 'E')) {
            pos++
            if (pos < src.length && (src[pos] == '+' || src[pos] == '-')) {
                pos++
            }
            if (pos >= src.length || src[pos] !in '0'..'9') {
                throw IllegalArgumentException("Invalid exponent in number at position $pos")
            }
            while (pos < src.length && src[pos] in '0'..'9') {
                pos++
            }
        }

        if (pos < src.length && !isDelimiter(src[pos])) {
            throw IllegalArgumentException("Unexpected character '${src[pos]}' after number at position $pos")
        }

        val raw = src.substring(start, pos)
        return JsonValue.JsonNumber(raw)
    }

    private fun isDelimiter(c: Char): Boolean {
        return c in " \t\r\n,]}"
    }
}
