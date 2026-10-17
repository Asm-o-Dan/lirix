package com.lirix.app.desktop

import com.lirix.app.desktop.lyrics.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class DesktopCustomSourcesTest {
    @Test fun extractionPreservesLineBreaksAndRemovesNoise() {
        val html = "<article><div class='lyrics'>Первая строка<br>Вторая строка<p>Третья строка</p><button>Купить</button><script>noise()</script></div></article>"
        val text = DesktopCustomSources.extractHtml(html, ".lyrics")
        assertTrue(text.contains("Первая строка\nВторая строка"))
        assertFalse(text.contains("Купить"))
        assertFalse(text.contains("noise"))
    }
    @Test fun androidCompatibleRuleRoundtripAndTamperDetection() {
        val rule = DesktopCustomSources.rule("Тест", "https://example.org/song", "div.lyrics", "https://example.org/search?q={query}")
        val exported = DesktopRuleBundles.export(rule)
        val restored = DesktopRuleBundles.import(exported)
        assertEquals(rule.domain, restored.domain)
        assertEquals(rule.name, restored.name)
        try { DesktopRuleBundles.import(exported.replace("div.lyrics", "div.advert")); fail("Tampered selector accepted") }
        catch (_: IllegalArgumentException) { }
    }
}
