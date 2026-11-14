package com.lirix.app.feature.lyrics

import com.lirix.app.analytics.WrappedStatsEngine

/**
 * Metadata that cannot identify a song must not trigger automatic lyrics retrieval (AGENTS 7.4).
 * Manual search and binding are unaffected. Spec: SPEC-MEDIA-FAV-01 D5.
 */
object AutomaticLookupEligibility {
    private val numericTitle = Regex("""^[\d\s._#()\-]*\d[\d\s._#()\-]*$""")

    fun isEligible(title: String?, artist: String?): Boolean {
        val cleanTitle = title?.trim().orEmpty()
        return !(WrappedStatsEngine.isUnknownArtist(artist) && numericTitle.matches(cleanTitle))
    }
}
