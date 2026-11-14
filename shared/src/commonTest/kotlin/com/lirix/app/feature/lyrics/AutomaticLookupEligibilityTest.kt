package com.lirix.app.feature.lyrics

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutomaticLookupEligibilityTest {
    @Test fun unknownArtistWithNumericTitleIsNotSearchedAutomatically() {
        for (artist in listOf("Unknown Artist", "unknown artist", "Неизвестный исполнитель", "", "  ", null)) {
            for (title in listOf("09", "001", " 7 ", "01 -", "#3", "(12)", "2.1")) {
                assertFalse(AutomaticLookupEligibility.isEligible(title, artist), "'$title' / '$artist'")
            }
        }
    }

    @Test fun knownArtistOrWordTitleStillSearches() {
        // Real songs with numeric titles by a known artist (seen in the device library).
        assertTrue(AutomaticLookupEligibility.isEligible("69", "Anacondaz"))
        assertTrue(AutomaticLookupEligibility.isEligible("01", "Филипп Волошин"))
        assertTrue(AutomaticLookupEligibility.isEligible("1979", "The Smashing Pumpkins"))
        // Unknown artist but a real title: title-only search keeps working.
        assertTrue(AutomaticLookupEligibility.isEligible("Tom's Diner (Cover)", "Unknown Artist"))
        assertTrue(AutomaticLookupEligibility.isEligible("Track 09", "Unknown Artist"))
        assertTrue(AutomaticLookupEligibility.isEligible("7 Rings", "Unknown Artist"))
        // Empty title is outside this rule.
        assertTrue(AutomaticLookupEligibility.isEligible("", "Unknown Artist"))
    }
}
