package com.eventengine.app.classifier

/**
 * Models for payload delivered via Android Share Intent (ACTION_SEND).
 * Spec: TASK-LYR-04-B / .sdd/architecture_lyr_community.md
 */
sealed interface SharedMediaPayload {
    data class LyricsText(val text: String) : SharedMediaPayload
    data class WebUrl(val url: String) : SharedMediaPayload
}

/**
 * Classifier that differentiates between web URLs and song lyrics from incoming shared text.
 * Handles URL extraction from mixed text, whitespace trimming, and URL tracking parameter sanitization.
 */
object ShareIntentClassifier {

    private val TRACKING_PARAMS = setOf("ref", "yclid", "fbclid", "gclid", "ysclid")
    private val URL_REGEX = Regex("""https?://[^\s"'<>]+""", RegexOption.IGNORE_CASE)

    fun classifySharedText(raw: String): SharedMediaPayload {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) {
            return SharedMediaPayload.LyricsText("")
        }

        // If multiline lyrics (3+ lines) and doesn't start with URL protocol, treat as LyricsText
        val nonBlankLines = trimmed.lines().count { it.isNotBlank() }
        if (nonBlankLines >= 3 && !trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            return SharedMediaPayload.LyricsText(trimmed)
        }

        // Check if string contains an HTTP/HTTPS URL
        val match = URL_REGEX.find(trimmed)
        if (match != null) {
            val sanitized = sanitizeUrl(match.value)
            return SharedMediaPayload.WebUrl(sanitized)
        }

        return SharedMediaPayload.LyricsText(trimmed)
    }

    fun sanitizeUrl(rawUrl: String): String {
        val hashIdx = rawUrl.indexOf('#')
        val fragment = if (hashIdx != -1) rawUrl.substring(hashIdx) else ""
        val withoutHash = if (hashIdx != -1) rawUrl.substring(0, hashIdx) else rawUrl

        val queryIdx = withoutHash.indexOf('?')
        if (queryIdx == -1) return rawUrl

        val baseUrl = withoutHash.substring(0, queryIdx)
        val queryString = withoutHash.substring(queryIdx + 1)
        if (queryString.isBlank()) return "$baseUrl$fragment"

        val filteredParams = queryString.split('&').filter { param ->
            val key = param.substringBefore('=').trim().lowercase()
            !key.startsWith("utm_") && key !in TRACKING_PARAMS
        }

        val newQuery = if (filteredParams.isNotEmpty()) "?" + filteredParams.joinToString("&") else ""
        return "$baseUrl$newQuery$fragment"
    }
}
