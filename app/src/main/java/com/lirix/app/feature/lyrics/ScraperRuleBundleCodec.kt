package com.lirix.app.feature.lyrics

import com.lirix.app.storage.CustomLyricsRuleEntity
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale

/**
 * Portable rule package model for sharing and exchanging scraper rules across Lirix clients.
 * Spec: TASK-RUL-01 / .sdd/intake/TRACK_D_SCRAPER_COMMUNITY.md
 *
 * @param schemaVersion Schema version (v1)
 * @param id Unique rule identifier / slug
 * @param name Human-readable name of the rule
 * @param domain Target website domain (e.g. "amalgama-lab.com")
 * @param contentSelector CSS selector for target lyrics container
 * @param stripSelectors List of CSS selectors to remove (ads, scripts, etc.)
 * @param chordsSelector Optional selector for chord spans/lines
 * @param lineBreakStrategy Formatting strategy ("PRESERVE_BR" or "PRE_TAG")
 * @param searchUrlTemplate Optional search URL template containing {query}, {artist}, {title}
 * @param searchResultSelector Optional selector to find the song link in search results
 * @param headers Custom HTTP headers (e.g. User-Agent)
 * @param author Creator handle or "local"
 * @param version Rule version
 * @param exportedAt Timestamp of export in milliseconds
 * @param appVersion Version of Lirix app producing this bundle
 * @param checksum SHA-256 integrity signature of the bundle
 */
data class ScraperRuleBundle(
    val schemaVersion: Int = 1,
    val id: String? = null,
    val name: String,
    val domain: String,
    val contentSelector: String,
    val stripSelectors: List<String> = emptyList(),
    val chordsSelector: String? = null,
    val lineBreakStrategy: String = "PRESERVE_BR",
    val searchUrlTemplate: String? = null,
    val searchResultSelector: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val author: String = "local",
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val appVersion: String = "1.0.0",
    val checksum: String = ""
)

/**
 * Codec for serializing, deserializing, validating, and verifying integrity of ScraperRuleBundle.
 * Spec: TASK-RUL-01 / .sdd/architecture_lyr_community.md
 */
object ScraperRuleBundleCodec {

    const val CURRENT_SCHEMA_VERSION = 1
    const val CURRENT_APP_VERSION = "1.0.0"
    const val SCHEMA_URI = "https://quicknobel.app/schemas/lyrics-rule-v1.json"

    private val DOMAIN_REGEX = Regex("""^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$""")
    private val IPV4_REGEX = Regex("""^(\d{1,3}\.){3}\d{1,3}$""")
    private val SAFE_SELECTOR_REGEX = Regex("""^[a-zA-Z0-9_\-\.\#\:\,\>\+\~\s\=\*\"\'\[\]\(\)\^\$\/]+$""")
    private val DISALLOWED_SCHEMES = listOf("javascript:", "file:", "data:", "content:", "vbscript:", "intent:")
    private val FORBIDDEN_HEADERS = setOf("cookie", "set-cookie", "authorization", "proxy-authorization", "host")

    /**
     * Computes deterministic SHA-256 checksum over canonical fields of the rule bundle.
     */
    fun computeChecksum(
        schemaVersion: Int,
        domain: String,
        contentSelector: String,
        chordsSelector: String? = null,
        searchUrlTemplate: String? = null,
        author: String = "local",
        version: Int = 1
    ): String {
        val cleanChords = chordsSelector?.trim()?.takeIf { it.isNotBlank() && it != "null" }.orEmpty()
        val cleanUrl = searchUrlTemplate?.trim()?.takeIf { it.isNotBlank() && it != "null" }.orEmpty()
        val cleanAuthor = author.trim().ifBlank { "local" }
        val canonical = "v=$schemaVersion|d=${domain.trim().lowercase(Locale.ROOT)}|s=${contentSelector.trim()}|c=$cleanChords|u=$cleanUrl|a=$cleanAuthor|ver=$version"
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Serializes a CustomLyricsRuleEntity into a portable JSON string.
     * @param rule Entity from Room database
     * @param pretty If true, formats JSON with 2-space indents
     */
    fun serialize(rule: CustomLyricsRuleEntity, pretty: Boolean = true): String {
        val config = CustomRuleLyricsProvider.parseConfig(rule.ruleJson, rule.domain, rule.name)
        val cleanDomain = sanitizeDomain(rule.domain)
        val cleanChords = config.chordsSelector?.takeIf { it.isNotBlank() && it != "null" }
        val cleanUrl = config.searchUrlTemplate?.takeIf { it.isNotBlank() && it != "null" }
        val checksum = computeChecksum(
            schemaVersion = CURRENT_SCHEMA_VERSION,
            domain = cleanDomain,
            contentSelector = config.contentSelector,
            chordsSelector = cleanChords,
            searchUrlTemplate = cleanUrl,
            author = rule.author,
            version = rule.version
        )

        val bundle = ScraperRuleBundle(
            schemaVersion = CURRENT_SCHEMA_VERSION,
            id = rule.id,
            name = rule.name.ifBlank { cleanDomain },
            domain = cleanDomain,
            contentSelector = config.contentSelector,
            stripSelectors = config.stripSelectors,
            chordsSelector = cleanChords,
            lineBreakStrategy = config.lineBreakStrategy,
            searchUrlTemplate = cleanUrl,
            searchResultSelector = config.searchResultSelector?.takeIf { it.isNotBlank() && it != "null" },
            headers = mapOf("User-Agent" to "Lirix/$CURRENT_APP_VERSION (Mobile; Android)"),
            author = rule.author,
            version = rule.version,
            exportedAt = System.currentTimeMillis(),
            appVersion = CURRENT_APP_VERSION,
            checksum = checksum
        )

        return serializeBundle(bundle, pretty)
    }

    /**
     * Serializes a ScraperRuleBundle to JSON string.
     */
    fun serializeBundle(bundle: ScraperRuleBundle, pretty: Boolean = true): String {
        val root = JSONObject().apply {
            put("\$schema", SCHEMA_URI)
            put("schemaVersion", bundle.schemaVersion)
            put("id", bundle.id ?: "rule_${bundle.domain.replace('.', '_')}_${System.currentTimeMillis() % 100000}")
            put("name", bundle.name)
            put("domain", bundle.domain)

            if (!bundle.searchUrlTemplate.isNullOrBlank() || !bundle.searchResultSelector.isNullOrBlank()) {
                val searchObj = JSONObject().apply {
                    bundle.searchUrlTemplate?.let { put("urlTemplate", it) }
                    bundle.searchResultSelector?.let { put("resultListSelector", it) }
                }
                put("search", searchObj)
            }

            val contentObj = JSONObject().apply {
                put("selector", bundle.contentSelector)
                put("stripSelectors", JSONArray(bundle.stripSelectors))
                bundle.chordsSelector?.let { put("chordsSelector", it) }
                put("lineBreakStrategy", bundle.lineBreakStrategy)
            }
            put("content", contentObj)

            if (bundle.headers.isNotEmpty()) {
                val headersObj = JSONObject()
                bundle.headers.forEach { (k, v) -> headersObj.put(k, v) }
                put("headers", headersObj)
            }

            val metaObj = JSONObject().apply {
                put("author", bundle.author)
                put("version", bundle.version)
                put("exportedAt", bundle.exportedAt)
                put("appVersion", bundle.appVersion)
                put("checksum", bundle.checksum)
            }
            put("meta", metaObj)
            put("checksum", bundle.checksum)
        }

        return if (pretty) root.toString(2) else root.toString()
    }

    /**
     * Deserializes and validates a JSON string into a ScraperRuleBundle.
     */
    fun deserializeBundle(jsonStr: String): Result<ScraperRuleBundle> {
        return runCatching {
            if (jsonStr.isBlank()) {
                throw IllegalArgumentException("Rule JSON string cannot be empty or blank")
            }

            val root = try {
                JSONObject(jsonStr)
            } catch (e: Exception) {
                throw IllegalArgumentException("Malformed JSON: ${e.message}", e)
            }

            // 1. Schema version validation
            val schemaVer = root.optInt("schemaVersion", root.optInt("version", CURRENT_SCHEMA_VERSION))
            if (schemaVer != CURRENT_SCHEMA_VERSION) {
                throw IllegalArgumentException("Unsupported schemaVersion: $schemaVer. Expected v$CURRENT_SCHEMA_VERSION")
            }

            // 2. Extract content & search sub-objects
            val contentObj = root.optJSONObject("content")
            val searchObj = root.optJSONObject("search")
            val metaObj = root.optJSONObject("meta")
            val headersObj = root.optJSONObject("headers")

            // 3. Extract and sanitize domain
            val rawDomain = root.optString("domain")
                .ifBlank { root.optString("host") }
            if (rawDomain.isBlank()) {
                throw IllegalArgumentException("Missing required field: 'domain'")
            }
            val domain = validateAndSanitizeDomain(rawDomain)

            // 4. Extract name
            val name = root.optString("name")
                .ifBlank { root.optString("title") }
                .ifBlank { domain }

            // 5. Extract content selector
            val contentSelector = (contentObj?.optString("selector")
                ?: root.optString("contentSelector")
                ?: root.optString("selector")).trim()
            if (contentSelector.isBlank()) {
                throw IllegalArgumentException("Missing required field: 'content.selector'")
            }
            validateSelector(contentSelector, "content.selector")

            // 6. Extract stripSelectors
            val stripList = mutableListOf<String>()
            val stripArray = contentObj?.optJSONArray("stripSelectors") ?: root.optJSONArray("stripSelectors")
            if (stripArray != null) {
                for (i in 0 until stripArray.length()) {
                    val s = stripArray.optString(i).trim()
                    if (s.isNotBlank()) {
                        validateSelector(s, "stripSelectors[$i]")
                        stripList.add(s)
                    }
                }
            }

            // 7. Extract chordsSelector
            val chordsSelector = (contentObj?.optString("chordsSelector")
                ?: root.optString("chordsSelector")).trim().takeIf { it.isNotBlank() && it != "null" }
            if (chordsSelector != null) {
                validateSelector(chordsSelector, "chordsSelector")
            }

            val lineBreakStrategy = contentObj?.optString("lineBreakStrategy", "PRESERVE_BR")
                ?: root.optString("lineBreakStrategy", "PRESERVE_BR")

            // 8. Extract search URL template & selector
            val searchUrlTemplate = (searchObj?.optString("urlTemplate")
                ?: root.optString("searchUrlTemplate")).trim().takeIf { it.isNotBlank() && it != "null" }
            if (searchUrlTemplate != null) {
                validateSearchUrlTemplate(searchUrlTemplate, domain)
            }

            val searchResultSelector = (searchObj?.optString("resultListSelector")
                ?: root.optString("searchResultSelector")).trim().takeIf { it.isNotBlank() && it != "null" }
            if (searchResultSelector != null) {
                validateSelector(searchResultSelector, "searchResultSelector")
            }

            // 9. Extract headers
            val headersMap = mutableMapOf<String, String>()
            if (headersObj != null) {
                val keys = headersObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = headersObj.optString(k)
                    validateHeader(k, v)
                    headersMap[k] = v
                }
            }

            // 10. Extract metadata
            val author = metaObj?.optString("author")
                ?: root.optString("author", "local")
            val ruleVersion = metaObj?.optInt("version", root.optInt("ruleVersion", 1)) ?: 1
            val exportedAt = metaObj?.optLong("exportedAt", root.optLong("exportedAt", System.currentTimeMillis()))
                ?: System.currentTimeMillis()
            val appVersion = metaObj?.optString("appVersion")
                ?: root.optString("appVersion", CURRENT_APP_VERSION)
            val id = root.optString("id").takeIf { it.isNotBlank() }

            // 11. Extract and verify checksum
            val providedChecksum = (metaObj?.optString("checksum")
                ?: root.optString("checksum")).trim()
            if (providedChecksum.isBlank()) {
                throw SecurityException("Missing checksum: rule bundle signature is required for integrity verification")
            }

            val expectedChecksum = computeChecksum(
                schemaVersion = schemaVer,
                domain = domain,
                contentSelector = contentSelector,
                chordsSelector = chordsSelector,
                searchUrlTemplate = searchUrlTemplate,
                author = author,
                version = ruleVersion
            )

            if (!providedChecksum.equals(expectedChecksum, ignoreCase = true)) {
                throw SecurityException("Checksum mismatch: bundle has been modified or corrupted (expected $expectedChecksum, got $providedChecksum)")
            }

            ScraperRuleBundle(
                schemaVersion = schemaVer,
                id = id,
                name = name,
                domain = domain,
                contentSelector = contentSelector,
                stripSelectors = stripList,
                chordsSelector = chordsSelector,
                lineBreakStrategy = lineBreakStrategy,
                searchUrlTemplate = searchUrlTemplate,
                searchResultSelector = searchResultSelector,
                headers = headersMap,
                author = author,
                version = ruleVersion,
                exportedAt = exportedAt,
                appVersion = appVersion,
                checksum = providedChecksum
            )
        }
    }

    /**
     * Deserializes and validates a JSON string directly into a Room CustomLyricsRuleEntity.
     */
    fun deserializeAndValidate(jsonStr: String): Result<CustomLyricsRuleEntity> {
        return deserializeBundle(jsonStr).map { bundle ->
            val innerRuleJson = JSONObject().apply {
                put("domain", bundle.domain)
                put("name", bundle.name)
                if (!bundle.searchUrlTemplate.isNullOrBlank() || !bundle.searchResultSelector.isNullOrBlank()) {
                    put("search", JSONObject().apply {
                        bundle.searchUrlTemplate?.let { put("urlTemplate", it) }
                        bundle.searchResultSelector?.let { put("resultListSelector", it) }
                    })
                }
                put("content", JSONObject().apply {
                    put("selector", bundle.contentSelector)
                    put("stripSelectors", JSONArray(bundle.stripSelectors))
                    bundle.chordsSelector?.let { put("chordsSelector", it) }
                    put("lineBreakStrategy", bundle.lineBreakStrategy)
                })
            }.toString()

            val safeSlug = bundle.domain.replace(Regex("""[^a-zA-Z0-9]"""), "_")
            val ruleId = bundle.id?.takeIf { it.startsWith("rule_") && it.matches(Regex("""^[a-zA-Z0-9_\-]+$""")) }
                ?: "rule_${safeSlug}_${System.currentTimeMillis() % 100000}"

            CustomLyricsRuleEntity(
                id = ruleId,
                domain = bundle.domain,
                name = bundle.name,
                ruleJson = innerRuleJson,
                isEnabled = true,
                priority = 100,
                isBuiltIn = false,
                author = bundle.author,
                version = bundle.version,
                createdAt = bundle.exportedAt,
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    /**
     * Sanitizes domain by stripping protocols, ports, and trailing paths.
     */
    fun sanitizeDomain(domain: String): String {
        var clean = domain.trim().lowercase(Locale.ROOT)
        if (clean.startsWith("https://")) clean = clean.removePrefix("https://")
        if (clean.startsWith("http://")) clean = clean.removePrefix("http://")
        if (clean.contains("/")) clean = clean.substringBefore("/")
        if (clean.contains(":")) clean = clean.substringBefore(":")
        return clean.trimEnd('.')
    }

    private fun validateAndSanitizeDomain(rawDomain: String): String {
        for (scheme in DISALLOWED_SCHEMES) {
            if (rawDomain.lowercase(Locale.ROOT).contains(scheme)) {
                throw SecurityException("Security violation: disallowed URI scheme in domain '$rawDomain'")
            }
        }

        val domain = sanitizeDomain(rawDomain)
        if (domain.isBlank()) {
            throw IllegalArgumentException("Domain cannot be blank")
        }

        if (domain == "localhost" || domain == "127.0.0.1" || domain == "0.0.0.0" || IPV4_REGEX.matches(domain)) {
            throw SecurityException("Security violation: IP addresses and loopback hosts are not permitted ($domain)")
        }

        if (!DOMAIN_REGEX.matches(domain)) {
            throw IllegalArgumentException("Invalid domain format: '$domain'. Must be a valid Fully Qualified Domain Name (e.g. 'amalgama-lab.com')")
        }

        return domain
    }

    private fun validateSelector(selector: String, fieldName: String) {
        val lower = selector.lowercase(Locale.ROOT)
        for (scheme in DISALLOWED_SCHEMES) {
            if (lower.contains(scheme)) {
                throw SecurityException("Security violation: disallowed scheme in $fieldName: '$selector'")
            }
        }

        if (lower.contains("<script") || lower.contains("</script") || lower.contains("<img") ||
            lower.contains("expression(") || lower.contains("eval(") ||
            lower.contains("onload=") || lower.contains("onerror=") || lower.contains("onclick=") ||
            lower.contains("<svg") || lower.contains("<iframe")
        ) {
            throw SecurityException("Security violation: script/event-handler injection detected in $fieldName: '$selector'")
        }

        if (selector.contains("<") || (selector.contains(">") && !isValidCssCombinator(selector))) {
            throw SecurityException("Security violation: HTML tag injection detected in $fieldName: '$selector'")
        }

        if (!SAFE_SELECTOR_REGEX.matches(selector)) {
            throw IllegalArgumentException("Unsafe characters detected in $fieldName: '$selector'")
        }
    }

    private fun isValidCssCombinator(selector: String): Boolean {
        // Reject any '<'
        if (selector.contains("<")) return false
        // Split by '>' and check that all segments are non-empty CSS selectors
        val segments = selector.split('>')
        return segments.all { it.trim().isNotBlank() && SAFE_SELECTOR_REGEX.matches(it.trim()) }
    }

    private fun validateSearchUrlTemplate(template: String, domain: String) {
        val lower = template.lowercase(Locale.ROOT)
        for (scheme in DISALLOWED_SCHEMES) {
            if (lower.contains(scheme)) {
                throw SecurityException("Security violation: disallowed URI scheme in searchUrlTemplate")
            }
        }

        if (!lower.startsWith("https://")) {
            throw SecurityException("Security violation: searchUrlTemplate must strictly use the HTTPS protocol: '$template'")
        }

        val withoutProto = lower.removePrefix("https://")
        val host = withoutProto.substringBefore("/").substringBefore("?").substringBefore(":")
        val targetDomain = domain.lowercase(Locale.ROOT)

        if (host != targetDomain && !host.endsWith(".$targetDomain")) {
            throw SecurityException("Security violation: searchUrlTemplate host '$host' does not match rule domain '$targetDomain'")
        }
    }

    private fun validateHeader(name: String, value: String) {
        val lowerName = name.trim().lowercase(Locale.ROOT)
        if (lowerName in FORBIDDEN_HEADERS) {
            throw SecurityException("Security violation: sensitive HTTP header '$name' is prohibited in scraper bundles")
        }
        if (name.contains("\r") || name.contains("\n") || value.contains("\r") || value.contains("\n")) {
            throw SecurityException("Security violation: CRLF injection in HTTP headers")
        }
    }
}
