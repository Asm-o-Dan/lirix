package com.lirix.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lirix.app.feature.lyrics.ScraperRuleBundleCodec
import com.lirix.app.storage.CustomLyricsRuleEntity
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * TDD Unit-tests for ScraperRuleBundleCodec:
 * Spec: TASK-RUL-01 / .sdd/intake/TRACK_D_SCRAPER_COMMUNITY.md
 *
 * - Roundtrip serialization and deserialization.
 * - Pretty vs compact formatting.
 * - SHA-256 integrity checksum verification and anti-tampering detection.
 * - Missing required fields and malformed JSON.
 * - Schema version validation.
 * - Anti-injection security (XSS, script tags, event handlers in selectors).
 * - Domain validation and SSRF/local network guards.
 * - Search URL template security (HTTPS only, matching domain).
 * - Header sanitization (forbidden auth/cookie headers, CRLF injection).
 */
@RunWith(AndroidJUnit4::class)
class ScraperRuleBundleCodecTest {

    private fun createSampleRule(): CustomLyricsRuleEntity {
        return CustomLyricsRuleEntity(
            id = "rule_amalgama_lab_com_1234",
            domain = "amalgama-lab.com",
            name = "Амальгама (Русский перевод)",
            ruleJson = """
                {
                    "domain": "amalgama-lab.com",
                    "name": "Амальгама (Русский перевод)",
                    "search": {
                        "urlTemplate": "https://amalgama-lab.com/search?q={query}",
                        "resultListSelector": ".search-results a"
                    },
                    "content": {
                        "selector": "#texts .string_ru",
                        "stripSelectors": ["script", "style", ".ads", "button"],
                        "chordsSelector": null,
                        "lineBreakStrategy": "PRESERVE_BR"
                    }
                }
            """.trimIndent(),
            isEnabled = true,
            priority = 100,
            isBuiltIn = false,
            author = "lirix_community",
            version = 1
        )
    }

    @Test
    fun test_serialize_and_deserialize_valid_bundle_roundtrip() {
        val rule = createSampleRule()
        val json = ScraperRuleBundleCodec.serialize(rule, pretty = true)

        assertNotNull(json)
        assertTrue(json.contains("amalgama-lab.com"))
        assertTrue(json.contains("checksum"))

        // Deserialize to entity
        val entityResult = ScraperRuleBundleCodec.deserializeAndValidate(json)
        assertTrue("Deserialization should succeed: ${entityResult.exceptionOrNull()?.message}", entityResult.isSuccess)

        val entity = entityResult.getOrThrow()
        assertEquals("amalgama-lab.com", entity.domain)
        assertEquals("Амальгама (Русский перевод)", entity.name)
        assertEquals("lirix_community", entity.author)
        assertEquals(1, entity.version)
        assertTrue(entity.isEnabled)

        // Deserialize to bundle
        val bundleResult = ScraperRuleBundleCodec.deserializeBundle(json)
        assertTrue(bundleResult.isSuccess)
        val bundle = bundleResult.getOrThrow()
        assertEquals("#texts .string_ru", bundle.contentSelector)
        assertEquals(listOf("script", "style", ".ads", "button"), bundle.stripSelectors)
        assertEquals("https://amalgama-lab.com/search?q={query}", bundle.searchUrlTemplate)
        assertEquals(".search-results a", bundle.searchResultSelector)
    }

    @Test
    fun test_pretty_vs_compact_serialization() {
        val rule = createSampleRule()
        val prettyJson = ScraperRuleBundleCodec.serialize(rule, pretty = true)
        val compactJson = ScraperRuleBundleCodec.serialize(rule, pretty = false)

        assertTrue("Pretty JSON must contain newlines", prettyJson.contains("\n"))
        assertFalse("Compact JSON must not contain newlines", compactJson.contains("\n"))

        val prettyEntity = ScraperRuleBundleCodec.deserializeAndValidate(prettyJson).getOrThrow()
        val compactEntity = ScraperRuleBundleCodec.deserializeAndValidate(compactJson).getOrThrow()

        assertEquals(prettyEntity.domain, compactEntity.domain)
        assertEquals(prettyEntity.name, compactEntity.name)
        assertEquals(prettyEntity.ruleJson, compactEntity.ruleJson)
    }

    @Test
    fun test_tampering_detection_checksum_mismatch() {
        val rule = createSampleRule()
        val validJson = ScraperRuleBundleCodec.serialize(rule, pretty = true)

        // Attacker tampered with the selector without updating checksum
        val tamperedJson = validJson.replace("#texts .string_ru", ".injected_malicious_selector")

        val result = ScraperRuleBundleCodec.deserializeAndValidate(tamperedJson)
        assertTrue("Tampered bundle must fail verification", result.isFailure)

        val exception = result.exceptionOrNull()
        assertTrue(
            "Expected SecurityException or checksum mismatch, got: $exception",
            exception is SecurityException && exception.message?.contains("Checksum mismatch") == true
        )
    }

    @Test
    fun test_missing_checksum_fails_validation() {
        val rule = createSampleRule()
        val validJson = ScraperRuleBundleCodec.serialize(rule, pretty = false)

        val jsonObject = JSONObject(validJson).apply {
            remove("checksum")
            optJSONObject("meta")?.remove("checksum")
        }

        val result = ScraperRuleBundleCodec.deserializeAndValidate(jsonObject.toString())
        assertTrue("Bundle missing checksum must fail", result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
    }

    @Test
    fun test_reject_missing_required_fields() {
        // Missing domain
        val jsonNoDomain = """
            {
                "schemaVersion": 1,
                "name": "Test",
                "content": { "selector": ".lyrics" },
                "meta": { "checksum": "abc" }
            }
        """.trimIndent()
        assertTrue(ScraperRuleBundleCodec.deserializeAndValidate(jsonNoDomain).isFailure)

        // Missing content selector
        val jsonNoSelector = """
            {
                "schemaVersion": 1,
                "domain": "example.com",
                "name": "Test",
                "content": {},
                "meta": { "checksum": "abc" }
            }
        """.trimIndent()
        assertTrue(ScraperRuleBundleCodec.deserializeAndValidate(jsonNoSelector).isFailure)

        // Empty string
        assertTrue(ScraperRuleBundleCodec.deserializeAndValidate("").isFailure)
    }

    @Test
    fun test_reject_malformed_json() {
        val malformed = "{ this is not a json }"
        val result = ScraperRuleBundleCodec.deserializeAndValidate(malformed)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun test_reject_unsupported_schema_version() {
        val rule = createSampleRule()
        val validJson = ScraperRuleBundleCodec.serialize(rule)
        val wrongVersionJson = JSONObject(validJson).apply {
            put("schemaVersion", 2)
        }.toString()

        val result = ScraperRuleBundleCodec.deserializeAndValidate(wrongVersionJson)
        assertTrue("Unsupported schema version must fail", result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Unsupported schemaVersion") == true)
    }

    @Test
    fun test_injection_protection_in_selectors() {
        val rule = createSampleRule()
        val baseJson = ScraperRuleBundleCodec.serialize(rule)

        // 1. Script tag injection
        val scriptAttack = JSONObject(baseJson).apply {
            getJSONObject("content").put("selector", "<script>alert('xss')</script>")
            val newChecksum = ScraperRuleBundleCodec.computeChecksum(1, "amalgama-lab.com", "<script>alert('xss')</script>")
            put("checksum", newChecksum)
            getJSONObject("meta").put("checksum", newChecksum)
        }.toString()

        val scriptResult = ScraperRuleBundleCodec.deserializeAndValidate(scriptAttack)
        assertTrue("Script tag must be rejected", scriptResult.isFailure)
        assertTrue(scriptResult.exceptionOrNull() is SecurityException)

        // 2. JavaScript scheme
        val jsSchemeAttack = JSONObject(baseJson).apply {
            getJSONObject("content").put("selector", "javascript:alert(1)")
            val newChecksum = ScraperRuleBundleCodec.computeChecksum(1, "amalgama-lab.com", "javascript:alert(1)")
            put("checksum", newChecksum)
            getJSONObject("meta").put("checksum", newChecksum)
        }.toString()

        val jsResult = ScraperRuleBundleCodec.deserializeAndValidate(jsSchemeAttack)
        assertTrue("javascript: scheme in selector must be rejected", jsResult.isFailure)
        assertTrue(jsResult.exceptionOrNull() is SecurityException)

        // 3. Event handler in strip selector
        val eventHandlerAttack = JSONObject(baseJson).apply {
            getJSONObject("content").put("stripSelectors", org.json.JSONArray(listOf(".ads", "div[onload=attack()]")))
        }.toString()

        val eventResult = ScraperRuleBundleCodec.deserializeAndValidate(eventHandlerAttack)
        assertTrue("Event handler in selector must be rejected", eventResult.isFailure)
    }

    @Test
    fun test_domain_validation_security() {
        val rule = createSampleRule()
        val baseJson = ScraperRuleBundleCodec.serialize(rule)

        // 1. Localhost guard
        val localhostAttack = JSONObject(baseJson).apply {
            put("domain", "localhost")
            val newChecksum = ScraperRuleBundleCodec.computeChecksum(1, "localhost", "#texts .string_ru")
            put("checksum", newChecksum)
            getJSONObject("meta").put("checksum", newChecksum)
        }.toString()
        assertTrue(ScraperRuleBundleCodec.deserializeAndValidate(localhostAttack).isFailure)

        // 2. Loopback IP guard
        val ipAttack = JSONObject(baseJson).apply {
            put("domain", "127.0.0.1")
            val newChecksum = ScraperRuleBundleCodec.computeChecksum(1, "127.0.0.1", "#texts .string_ru")
            put("checksum", newChecksum)
            getJSONObject("meta").put("checksum", newChecksum)
        }.toString()
        assertTrue(ScraperRuleBundleCodec.deserializeAndValidate(ipAttack).isFailure)

        // 3. Disallowed scheme in domain
        val schemeAttack = JSONObject(baseJson).apply {
            put("domain", "javascript:void(0)")
        }.toString()
        assertTrue(ScraperRuleBundleCodec.deserializeAndValidate(schemeAttack).isFailure)
    }

    @Test
    fun test_search_url_template_security() {
        val rule = createSampleRule()
        val baseJson = ScraperRuleBundleCodec.serialize(rule)

        // 1. Insecure HTTP template
        val httpAttack = JSONObject(baseJson).apply {
            getJSONObject("search").put("urlTemplate", "http://amalgama-lab.com/search?q={query}")
            val newChecksum = ScraperRuleBundleCodec.computeChecksum(
                1, "amalgama-lab.com", "#texts .string_ru",
                searchUrlTemplate = "http://amalgama-lab.com/search?q={query}"
            )
            put("checksum", newChecksum)
            getJSONObject("meta").put("checksum", newChecksum)
        }.toString()
        val httpResult = ScraperRuleBundleCodec.deserializeAndValidate(httpAttack)
        assertTrue("Non-HTTPS search template must be rejected", httpResult.isFailure)
        assertTrue(httpResult.exceptionOrNull() is SecurityException)

        // 2. Mismatching domain template
        val mismatchAttack = JSONObject(baseJson).apply {
            getJSONObject("search").put("urlTemplate", "https://evil-phishing-site.com/search?q={query}")
            val newChecksum = ScraperRuleBundleCodec.computeChecksum(
                1, "amalgama-lab.com", "#texts .string_ru",
                searchUrlTemplate = "https://evil-phishing-site.com/search?q={query}"
            )
            put("checksum", newChecksum)
            getJSONObject("meta").put("checksum", newChecksum)
        }.toString()
        val mismatchResult = ScraperRuleBundleCodec.deserializeAndValidate(mismatchAttack)
        assertTrue("Mismatching search URL host must be rejected", mismatchResult.isFailure)
        assertTrue(mismatchResult.exceptionOrNull() is SecurityException)
    }

    @Test
    fun test_forbidden_headers_security() {
        val rule = createSampleRule()
        val baseJson = ScraperRuleBundleCodec.serialize(rule)

        // Attempt to pass Cookie header
        val cookieAttack = JSONObject(baseJson).apply {
            val headers = JSONObject().apply {
                put("Cookie", "session_token=secret123")
            }
            put("headers", headers)
        }.toString()

        val result = ScraperRuleBundleCodec.deserializeAndValidate(cookieAttack)
        assertTrue("Sensitive Cookie header must be rejected", result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
    }
}
