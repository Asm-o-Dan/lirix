package com.lirix.app.feature.lyrics

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.select.Selector
import java.net.URI
import java.net.URLDecoder
import java.text.Normalizer
import java.util.Locale

data class RankedCandidate(
    val url: String,
    val label: String,
    val score: Float,
    /** Position in the search page; the site's own order breaks ties between versions of one song. */
    val order: Int
)

sealed interface CandidateRanking {
    data class Selected(val best: RankedCandidate, val candidates: List<RankedCandidate>) : CandidateRanking
    data class LowConfidence(val best: RankedCandidate?, val candidates: List<RankedCandidate>) : CandidateRanking
    data class Ambiguous(val candidates: List<RankedCandidate>) : CandidateRanking
    data object NoCandidates : CandidateRanking
    data class InvalidSelector(val selector: String) : CandidateRanking
}

/**
 * Deterministic search-result ranking (SPEC-TEACH-06 R7, §5.2 item 6).
 *
 * Score in 0..1: title-token coverage (0.6) + artist-token coverage (0.4) over link text,
 * `title` attribute and URL path; minus a penalty for version markers the track does not have
 * and for extra words in the link text. Below [MIN_CONFIDENCE] the result is `LowConfidence`;
 * two different songs within [AMBIGUITY_MARGIN] of each other are `Ambiguous`.
 */
object RuleCandidateRanker {
    const val MIN_CONFIDENCE = 0.6f
    const val AMBIGUITY_MARGIN = 0.08f
    private const val TITLE_WEIGHT = 0.6f
    private const val ARTIST_WEIGHT = 0.4f
    /** Title-only matching (unknown artist) is less certain than a title + artist match. */
    private const val TITLE_ONLY_FACTOR = 0.85f
    private const val VERSION_PENALTY = 0.25f
    private const val EXTRA_TOKEN_PENALTY = 0.1f
    private const val MAX_EXTRA_PENALTY = 0.3f
    private const val MAX_CONTEXT_CHARS = 300
    private const val MIN_ARTIST_COVERAGE = 0.5f
    private const val MIN_TITLE_PRECISION = 0.67f
    private const val BELOW_THRESHOLD = MIN_CONFIDENCE - 0.05f

    private val VERSION_MARKERS = setOf(
        "live", "remix", "acoustic", "cover", "instrumental", "karaoke", "demo", "unplugged",
        "ремикс", "кавер", "акустика", "акустическая", "минус", "минусовка", "караоке", "концерт", "инструментал"
    )
    private val NOISE_TOKENS = setOf(
        "перевод", "перевода", "текст", "текста", "песни", "песня", "слова", "lyrics", "lyric", "translation",
        "аккорды", "аккорд", "chords", "chord", "tabs", "tab", "song", "songs", "на", "гитаре", "для", "guitar",
        "русский", "русском", "english", "оригинал", "original", "html", "htm", "php"
    )
    private val UNKNOWN_ARTISTS = setOf("unknown artist", "unknown", "неизвестный исполнитель", "various artists", "")
    private val SERVICE_PATH = Regex("""(/search|[?&](q|query|s)=|/login|/register|/signup|/privacy|/terms|/contact|/about)""", RegexOption.IGNORE_CASE)
    private val FEATURING = Regex("""\s*[(\[]?\s*\b(feat\.?|ft\.?|featuring)\s+[^)\]]*[)\]]?""", RegexOption.IGNORE_CASE)
    private val REMASTER = Regex("""\s*(\([^)]*remaster[^)]*\)|\[[^]]*remaster[^]]*]|-\s*[^-]*remaster.*$)""", RegexOption.IGNORE_CASE)

    fun rank(
        html: String,
        pageUrl: String,
        selector: String?,
        title: String,
        artist: String,
        domain: String
    ): CandidateRanking {
        val doc = Jsoup.parse(html, pageUrl)
        val anchors: List<Element> = if (!selector.isNullOrBlank()) {
            val selected = try {
                doc.select(selector)
            } catch (_: Selector.SelectorParseException) {
                return CandidateRanking.InvalidSelector(selector)
            }
            selected.flatMap { el -> if (el.normalName() == "a") listOf(el) else el.select("a[href]") }
        } else {
            doc.select("a[href]").filter { a -> a.parents().none { it.normalName() in setOf("header", "nav", "footer", "form") } }
        }

        val pageKey = stripFragment(pageUrl)
        val titleTokens = tokens(cleanTitle(title))
        val trackVersionTokens = tokens(title).filter { it in VERSION_MARKERS }.toSet()
        val artistTokens = normalize(artist).takeIf { it !in UNKNOWN_ARTISTS }?.let(::tokens).orEmpty()
        if (titleTokens.isEmpty()) return CandidateRanking.NoCandidates

        val byUrl = LinkedHashMap<String, RankedCandidate>()
        // Song identity = link text plus its own result row, so equal titles by different artists differ.
        val identities = HashMap<String, String>()
        anchors.forEachIndexed { index, a ->
            val url = candidateUrl(a, domain) ?: return@forEachIndexed
            val pathAndQuery = "/" + url.substringAfter("://").substringAfter('/', "")
            if (url == pageKey || SERVICE_PATH.containsMatchIn(pathAndQuery)) return@forEachIndexed
            val label = listOf(a.text(), a.attr("title")).filter { it.isNotBlank() }.distinct().joinToString(" ").trim()
            val context = resultContext(a)
            val score = score(label, context, url, titleTokens, artistTokens, trackVersionTokens)
            val existing = byUrl[url]
            if (existing == null || score > existing.score) {
                identities[url] = identity("$label $context")
                byUrl[url] = RankedCandidate(url, label.ifBlank { existing?.label.orEmpty() }, score, existing?.order ?: index)
            }
        }
        if (byUrl.isEmpty()) return CandidateRanking.NoCandidates

        val sorted = byUrl.values.sortedWith(compareByDescending<RankedCandidate> { it.score }.thenBy { it.order })
        val best = sorted.first()
        if (best.score < MIN_CONFIDENCE) return CandidateRanking.LowConfidence(best, sorted)

        val nearTies = sorted.filter { it.score >= MIN_CONFIDENCE && best.score - it.score < AMBIGUITY_MARGIN }
        val bestIdentity = identities[best.url]
        val differentSongs = nearTies.filter { identities[it.url] != bestIdentity }
        if (differentSongs.isNotEmpty()) return CandidateRanking.Ambiguous(nearTies)
        // Near ties naming the same song (e.g. two chord arrangements) follow the site's order.
        val chosen = nearTies.minByOrNull { it.order } ?: best
        return CandidateRanking.Selected(chosen, sorted)
    }

    /**
     * Text of the result row/card around the link: many sites print the artist next to the
     * link (`<a>Title</a> - <b>Artist</b>`). Used for matching only, not for the extra-word penalty.
     */
    private fun resultContext(a: Element): String {
        val container = a.parents().take(4).firstOrNull { p ->
            p.normalName() in setOf("li", "tr", "article") || p.className().contains("result", ignoreCase = true)
        } ?: return ""
        // A container holding several results would lend one row's artist to its neighbours.
        if (container.select("a[href]").size != 1) return ""
        return container.text().take(MAX_CONTEXT_CHARS)
    }

    private fun score(
        label: String,
        context: String,
        url: String,
        titleTokens: List<String>,
        artistTokens: List<String>,
        trackVersionTokens: Set<String>
    ): Float {
        val labelTokens = tokens(label)
        val pathTokens = tokens(decode(runCatching { URI(url).rawPath }.getOrNull().orEmpty()))
        val all = (labelTokens + tokens(context) + pathTokens).toSet()
        val titleScore = coverage(titleTokens, all)
        val artistScore = if (artistTokens.isNotEmpty()) coverage(artistTokens, all) else null
        val base = if (artistScore != null) {
            TITLE_WEIGHT * titleScore + ARTIST_WEIGHT * artistScore
        } else {
            titleScore * TITLE_ONLY_FACTOR
        }
        val versionConflict = labelTokens.any { it in VERSION_MARKERS && it !in trackVersionTokens }
        val known = titleTokens.toSet() + artistTokens
        val extras = labelTokens.count { tok ->
            tok !in NOISE_TOKENS && tok !in VERSION_MARKERS && tok.none(Char::isDigit) && known.none { matches(it, tok) }
        }
        val penalty = (if (versionConflict) VERSION_PENALTY else 0f) + minOf(MAX_EXTRA_PENALTY, EXTRA_TOKEN_PENALTY * extras)
        val score = (base - penalty).coerceIn(0f, 1f)

        if (artistScore != null) {
            // A known artist must be confirmed: same title by someone else is a different song (AGENTS 7.4).
            if (artistScore < MIN_ARTIST_COVERAGE) return minOf(score, BELOW_THRESHOLD)
            // Most words of the link title (artist removed) must belong to the track title:
            // "Rain Dance" or "After the Rain" are other songs when looking for "Rain".
            val labelTitleWords = labelTokens.filter { tok ->
                tok !in NOISE_TOKENS && tok !in VERSION_MARKERS && tok.none(Char::isDigit) && artistTokens.none { matches(it, tok) }
            }
            if (labelTitleWords.isNotEmpty()) {
                val precision = labelTitleWords.count { tok -> titleTokens.any { matches(it, tok) } }.toFloat() / labelTitleWords.size
                if (precision < MIN_TITLE_PRECISION) return minOf(score, BELOW_THRESHOLD)
            }
        }
        return score
    }

    private fun coverage(needles: List<String>, haystack: Set<String>): Float {
        if (needles.isEmpty()) return 0f
        // URL slugs often glue words together ("mirasol" for "Mira Sol").
        if (needles.size > 1 && needles.joinToString("") in haystack) return 1f
        return needles.count { n -> haystack.any { matches(n, it) } }.toFloat() / needles.size
    }

    private fun matches(a: String, b: String): Boolean {
        if (a == b) return true
        if (a.length < 5 || b.length < 5 || kotlin.math.abs(a.length - b.length) > 1) return false
        return levenshteinAtMostOne(a, b)
    }

    private fun levenshteinAtMostOne(a: String, b: String): Boolean {
        var i = 0
        var j = 0
        var edits = 0
        while (i < a.length && j < b.length) {
            if (a[i] == b[j]) {
                i++; j++
                continue
            }
            if (++edits > 1) return false
            when {
                a.length > b.length -> i++
                a.length < b.length -> j++
                else -> { i++; j++ }
            }
        }
        return edits + (a.length - i) + (b.length - j) <= 1
    }

    private fun candidateUrl(a: Element, domain: String): String? {
        val raw = a.attr("href").trim()
        if (raw.isBlank() || raw.startsWith("#") || raw.startsWith("javascript:", true) || raw.startsWith("mailto:", true)) return null
        var abs = a.absUrl("href").ifBlank { return null }
        if (abs.startsWith("http://", ignoreCase = true)) abs = "https://" + abs.substring(7)
        abs = stripFragment(abs)
        return abs.takeIf { RuleUrlPolicy.check(it, domain) == null }
    }

    private fun stripFragment(url: String) = url.substringBefore('#')

    private fun identity(label: String): String =
        tokens(label).filter { it !in NOISE_TOKENS }.joinToString(" ")

    fun cleanTitle(title: String): String = title.replace(FEATURING, " ").replace(REMASTER, " ").trim()

    fun normalize(s: String): String {
        val decomposed = Normalizer.normalize(s.lowercase(Locale.ROOT).replace('ё', 'е').replace('й', '\u0000'), Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}+"""), "")
            .replace('\u0000', 'й')
        return decomposed.replace(Regex("""[^\p{L}\p{Nd}]+"""), " ").trim()
    }

    fun tokens(s: String): List<String> = normalize(s).split(' ').filter { it.isNotBlank() }

    private fun decode(s: String): String = runCatching { URLDecoder.decode(s.replace("+", "%2B"), "UTF-8") }.getOrDefault(s)
}
