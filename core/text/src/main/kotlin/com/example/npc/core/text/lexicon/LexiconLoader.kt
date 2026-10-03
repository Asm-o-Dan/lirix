package com.example.npc.core.text.lexicon

import com.example.npc.core.text.model.KeywordKind
import java.io.InputStream
import java.nio.charset.StandardCharsets

object LexiconLoader {

    private val cachedDefault: LexiconRepository by lazy {
        loadFromResource("/lexicon_v1.json")
    }

    fun default(): LexiconRepository = cachedDefault

    fun loadFromResource(resourcePath: String = "/lexicon_v1.json"): LexiconRepository {
        val stream: InputStream = LexiconLoader::class.java.getResourceAsStream(resourcePath)
            ?: throw IllegalArgumentException("Lexicon resource not found: $resourcePath")
        val content = stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        return loadFromJson(content)
    }

    fun loadFromJson(json: String): LexiconRepository {
        val parsed = MiniJson.parse(json) as? MiniJson.JsonObject
            ?: throw IllegalArgumentException("Invalid lexicon JSON root, expected object")

        val version = (parsed.map["version"] as? MiniJson.JsonNumber)?.value?.toInt() ?: 1

        val keywordStems = HashMap<String, KeywordKind>()
        val keywordsObj = parsed.map["keywords"] as? MiniJson.JsonObject
        if (keywordsObj != null) {
            for ((kindName, stemsVal) in keywordsObj.map) {
                val kind = try {
                    KeywordKind.valueOf(kindName)
                } catch (e: IllegalArgumentException) {
                    continue
                }
                val stemsArr = stemsVal as? MiniJson.JsonArray ?: continue
                for (elem in stemsArr.list) {
                    if (elem is MiniJson.JsonString) {
                        keywordStems[elem.value] = kind
                    }
                }
            }
        }

        val exactCurrencies = HashMap<String, String>()
        val ambiguousCurrencies = HashMap<String, List<String>>()

        val currenciesObj = parsed.map["currencies"] as? MiniJson.JsonObject
        if (currenciesObj != null) {
            val exactObj = currenciesObj.map["exact"] as? MiniJson.JsonObject
            if (exactObj != null) {
                for ((code, stemsVal) in exactObj.map) {
                    val stemsArr = stemsVal as? MiniJson.JsonArray ?: continue
                    for (elem in stemsArr.list) {
                        if (elem is MiniJson.JsonString) {
                            exactCurrencies[elem.value] = code
                        }
                    }
                }
            }

            val ambiguousObj = currenciesObj.map["ambiguous"] as? MiniJson.JsonObject
            if (ambiguousObj != null) {
                for ((_, groupVal) in ambiguousObj.map) {
                    val group = groupVal as? MiniJson.JsonObject ?: continue
                    val codesArr = group.map["codes"] as? MiniJson.JsonArray ?: continue
                    val stemsArr = group.map["stems"] as? MiniJson.JsonArray ?: continue

                    val codes = codesArr.list.mapNotNull { (it as? MiniJson.JsonString)?.value }
                    for (elem in stemsArr.list) {
                        if (elem is MiniJson.JsonString) {
                            ambiguousCurrencies[elem.value] = codes
                        }
                    }
                }
            }
        }

        return CompactTrieLexiconRepository(
            version = version,
            keywordStems = keywordStems,
            exactCurrencies = exactCurrencies,
            ambiguousCurrencies = ambiguousCurrencies
        )
    }

    /**
     * Быстрый бескомпромиссный JSON-парсер без внешних зависимостей.
     */
    internal object MiniJson {
        sealed class Value
        data class JsonObject(val map: Map<String, Value>) : Value()
        data class JsonArray(val list: List<Value>) : Value()
        data class JsonString(val value: String) : Value()
        data class JsonNumber(val value: Long) : Value()

        fun parse(text: String): Value {
            val parser = Parser(text)
            return parser.parseValue()
        }

        private class Parser(private val s: String) {
            private var i = 0

            fun parseValue(): Value {
                skipWhitespace()
                if (i >= s.length) throw IllegalArgumentException("Unexpected end of JSON")
                return when (s[i]) {
                    '{' -> parseObject()
                    '[' -> parseArray()
                    '"' -> parseString()
                    in '0'..'9', '-' -> parseNumber()
                    else -> throw IllegalArgumentException("Unexpected character at $i: '${s[i]}'")
                }
            }

            private fun parseObject(): JsonObject {
                i++ // skip '{'
                val map = LinkedHashMap<String, Value>()
                skipWhitespace()
                if (i < s.length && s[i] == '}') {
                    i++
                    return JsonObject(map)
                }

                while (i < s.length) {
                    skipWhitespace()
                    if (s[i] != '"') throw IllegalArgumentException("Expected string key at $i, found '${s[i]}'")
                    val key = parseString().value
                    skipWhitespace()
                    if (i >= s.length || s[i] != ':') throw IllegalArgumentException("Expected ':' at $i")
                    i++ // skip ':'
                    val value = parseValue()
                    map[key] = value

                    skipWhitespace()
                    if (i < s.length && s[i] == ',') {
                        i++
                        continue
                    } else if (i < s.length && s[i] == '}') {
                        i++
                        break
                    } else {
                        throw IllegalArgumentException("Expected ',' or '}' at $i")
                    }
                }
                return JsonObject(map)
            }

            private fun parseArray(): JsonArray {
                i++ // skip '['
                val list = ArrayList<Value>()
                skipWhitespace()
                if (i < s.length && s[i] == ']') {
                    i++
                    return JsonArray(list)
                }

                while (i < s.length) {
                    val value = parseValue()
                    list.add(value)

                    skipWhitespace()
                    if (i < s.length && s[i] == ',') {
                        i++
                        continue
                    } else if (i < s.length && s[i] == ']') {
                        i++
                        break
                    } else {
                        throw IllegalArgumentException("Expected ',' or ']' at $i")
                    }
                }
                return JsonArray(list)
            }

            private fun parseString(): JsonString {
                i++ // skip '"'
                val sb = java.lang.StringBuilder()
                while (i < s.length) {
                    val c = s[i]
                    if (c == '"') {
                        i++
                        return JsonString(sb.toString())
                    } else if (c == '\\') {
                        i++
                        if (i >= s.length) throw IllegalArgumentException("Unterminated string escape")
                        when (val esc = s[i]) {
                            '"', '\\', '/' -> sb.append(esc)
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (i + 4 >= s.length) throw IllegalArgumentException("Invalid unicode escape")
                                val hex = s.substring(i + 1, i + 5)
                                sb.append(hex.toInt(16).toChar())
                                i += 4
                            }
                            else -> sb.append(esc)
                        }
                        i++
                    } else {
                        sb.append(c)
                        i++
                    }
                }
                throw IllegalArgumentException("Unterminated string")
            }

            private fun parseNumber(): JsonNumber {
                val start = i
                if (s[i] == '-') i++
                while (i < s.length && s[i] in '0'..'9') i++
                val numStr = s.substring(start, i)
                return JsonNumber(numStr.toLong())
            }

            private fun skipWhitespace() {
                while (i < s.length && (s[i] == ' ' || s[i] == '\t' || s[i] == '\r' || s[i] == '\n')) {
                    i++
                }
            }
        }
    }
}
