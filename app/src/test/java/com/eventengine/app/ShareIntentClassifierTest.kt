package com.eventengine.app

import com.eventengine.app.classifier.ShareIntentClassifier
import com.eventengine.app.classifier.SharedMediaPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TDD Unit-tests for TASK-LYR-04-B:
 * - Differentiating URL vs Lyrics text from Android Share Intent (ACTION_SEND).
 * - Trimming leading/trailing whitespace.
 * - Cleaning URL from tracking parameters (utm_*, ref, yclid, fbclid).
 * - Extracting URLs from mixed text messages.
 * - Multi-line texts classification as LyricsText.
 */
class ShareIntentClassifierTest {

    @Test
    fun test_http_url_classified_as_web_url() {
        val raw = "https://amalgama-lab.com/songs/e/eminem/mockingbird.html"
        val payload = ShareIntentClassifier.classifySharedText(raw)
        assertTrue(payload is SharedMediaPayload.WebUrl)
        assertEquals("https://amalgama-lab.com/songs/e/eminem/mockingbird.html", (payload as SharedMediaPayload.WebUrl).url)
    }

    @Test
    fun test_url_with_leading_whitespace_trimmed() {
        val raw = "   https://genius.com/Linkin-park-numb-lyrics   \n"
        val payload = ShareIntentClassifier.classifySharedText(raw)
        assertTrue(payload is SharedMediaPayload.WebUrl)
        assertEquals("https://genius.com/Linkin-park-numb-lyrics", (payload as SharedMediaPayload.WebUrl).url)
    }

    @Test
    fun test_multiline_text_classified_as_lyrics() {
        val lyrics = """
            Yeah, I know sometimes things may not make sense now
            But hey, what daddy always tell you?
            Straighten up little soldier
            Stiffen up that upper lip
        """.trimIndent()

        val payload = ShareIntentClassifier.classifySharedText(lyrics)
        assertTrue(payload is SharedMediaPayload.LyricsText)
        assertEquals(lyrics, (payload as SharedMediaPayload.LyricsText).text)
    }

    @Test
    fun test_url_sanitization_removes_tracking_parameters() {
        val rawWithUtm = "https://genius.com/track?utm_source=share&utm_medium=android_app&utm_campaign=mobile&ref=yandex#verse-1"
        val payload = ShareIntentClassifier.classifySharedText(rawWithUtm)
        assertTrue(payload is SharedMediaPayload.WebUrl)
        val cleanedUrl = (payload as SharedMediaPayload.WebUrl).url
        assertEquals("https://genius.com/track#verse-1", cleanedUrl)
    }

    @Test
    fun test_mixed_text_with_url_and_description() {
        // Many apps share like: "Check out this song: https://textpesni.com/song123 on TextPesni"
        val mixed = "Check out this song: https://textpesni.com/song123 on TextPesni"
        val payload = ShareIntentClassifier.classifySharedText(mixed)
        // If it's a short share message containing an HTTP link and not verse lyrics, it should extract the WebUrl
        assertTrue(payload is SharedMediaPayload.WebUrl)
        assertEquals("https://textpesni.com/song123", (payload as SharedMediaPayload.WebUrl).url)
    }

    @Test
    fun test_plain_single_line_lyrics_without_url() {
        val singleLine = "Just a small town girl, living in a lonely world"
        val payload = ShareIntentClassifier.classifySharedText(singleLine)
        assertTrue(payload is SharedMediaPayload.LyricsText)
        assertEquals(singleLine, (payload as SharedMediaPayload.LyricsText).text)
    }
}
