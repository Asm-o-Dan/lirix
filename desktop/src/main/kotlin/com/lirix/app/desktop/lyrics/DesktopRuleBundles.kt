package com.lirix.app.desktop.lyrics

import com.lirix.app.storage.CustomLyricsRuleEntity
import kotlinx.serialization.json.*
import java.security.MessageDigest

/** Compatible with the Android v1 declarative rule bundle/checksum. */
object DesktopRuleBundles {
    private fun checksum(root: JsonObject): String {
        val content = root["content"]!!.jsonObject
        val meta = root["meta"]?.jsonObject
        fun JsonObject?.text(key: String) = this?.get(key)?.jsonPrimitive?.contentOrNull?.takeUnless { it == "null" }?.trim().orEmpty()
        val schema = root["schemaVersion"]?.jsonPrimitive?.int ?: 1
        val version = meta?.get("version")?.jsonPrimitive?.int ?: 1
        val canonical = "v=$schema|d=${root.text("domain").lowercase()}|s=${content.text("selector")}|c=${content.text("chordsSelector")}|u=${root["search"]?.jsonObject.text("urlTemplate")}|a=${meta.text("author").ifBlank { "local" }}|ver=$version"
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray()).joinToString("") { "%02x".format(it) }
    }
    fun export(rule: CustomLyricsRuleEntity): String {
        val config = Json.parseToJsonElement(rule.ruleJson).jsonObject
        val root = JsonObject(config + mapOf(
            "schemaVersion" to JsonPrimitive(1), "id" to JsonPrimitive(rule.id), "name" to JsonPrimitive(rule.name),
            "domain" to JsonPrimitive(rule.domain), "meta" to buildJsonObject { put("author", rule.author); put("version", rule.version); put("appVersion", "1.4.0"); put("exportedAt", System.currentTimeMillis()) }
        ))
        return Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), JsonObject(root + ("checksum" to JsonPrimitive(checksum(root)))))
    }
    fun import(text: String): CustomLyricsRuleEntity {
        require(text.length <= 500_000) { "Файл правила слишком большой" }
        val root = Json.parseToJsonElement(text).jsonObject
        require(root["schemaVersion"]?.jsonPrimitive?.int == 1) { "Неподдерживаемая версия правила" }
        val domain = root["domain"]!!.jsonPrimitive.content.trim().lowercase()
        require(domain.matches(Regex("[a-z0-9][a-z0-9.-]*\\.[a-z]{2,}"))) { "Некорректный домен" }
        val selector = root["content"]!!.jsonObject["selector"]!!.jsonPrimitive.content
        require(selector.isNotBlank() && selector.length <= 4000) { "Некорректный селектор" }
        require(root["checksum"]?.jsonPrimitive?.content?.equals(checksum(root), true) == true) { "Контрольная сумма правила не совпадает" }
        root["search"]?.jsonObject?.get("urlTemplate")?.jsonPrimitive?.contentOrNull?.let { template ->
            val uri = java.net.URI(template.replace("{query}", "test").replace("{artist}", "test").replace("{title}", "test"))
            require(uri.scheme in setOf("http", "https") && uri.host?.removePrefix("www.") == domain.removePrefix("www.") && uri.userInfo == null) { "Поиск должен оставаться на домене правила" }
        }
        return CustomLyricsRuleEntity(root["id"]?.jsonPrimitive?.contentOrNull ?: java.util.UUID.randomUUID().toString(), domain,
            root["name"]!!.jsonPrimitive.content, root.toString(), author = root["meta"]?.jsonObject?.get("author")?.jsonPrimitive?.contentOrNull ?: "local")
    }
}
