package com.eventengine.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eventengine.app.feature.lyrics.CustomRuleLyricsProvider
import com.eventengine.app.storage.AppDatabase
import com.eventengine.app.storage.CustomLyricsRuleEntity
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * TDD Unit-tests for TASK-LYR-04-D:
 * - Verifies declarative JSON rule generated in Teach Mode is valid and parsable.
 * - Verifies rule entity structure (isEnabled = true, priority = 100, correct domain).
 * - Verifies saving rule into Room database and retrieving it via getRuleById and getActiveRules.
 */
@RunWith(AndroidJUnit4::class)
class TeachModeSelectorTest {

    private lateinit var db: AppDatabase
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun test_generated_rule_json_is_valid_and_parsable() {
        val detectedDomain = "amalgama-lab.com"
        val selectedSelector = ".string_ru"

        val ruleJson = JSONObject().apply {
            put("domain", detectedDomain)
            put("name", detectedDomain)
            put("content", JSONObject().apply {
                put("selector", selectedSelector)
                put("stripSelectors", JSONArray(listOf("script", "style", ".ads", "button")))
            })
        }.toString()

        val config = CustomRuleLyricsProvider.parseConfig(ruleJson, detectedDomain, detectedDomain)

        assertEquals("amalgama-lab.com", config.domain)
        assertEquals("amalgama-lab.com", config.name)
        assertEquals(".string_ru", config.contentSelector)
        assertTrue(config.stripSelectors.contains("script"))
        assertTrue(config.stripSelectors.contains("style"))
        assertTrue(config.stripSelectors.contains(".ads"))
        assertTrue(config.stripSelectors.contains("button"))
    }

    @Test
    fun test_rule_entity_saved_with_correct_domain_and_priority() = runBlocking {
        val detectedDomain = "genius.com"
        val selectedSelector = "[data-lyrics-container='true']"
        val ruleId = "rule_genius_test"

        val ruleJson = JSONObject().apply {
            put("domain", detectedDomain)
            put("name", detectedDomain)
            put("content", JSONObject().apply {
                put("selector", selectedSelector)
                put("stripSelectors", JSONArray(listOf("script", "style")))
            })
        }.toString()

        val entity = CustomLyricsRuleEntity(
            id = ruleId,
            domain = detectedDomain,
            name = detectedDomain,
            ruleJson = ruleJson,
            isEnabled = true,
            priority = 100
        )

        db.lyricsDao().saveRule(entity)

        val loaded = db.lyricsDao().getRuleById(ruleId)
        assertNotNull(loaded)
        assertEquals(ruleId, loaded!!.id)
        assertEquals("genius.com", loaded.domain)
        assertTrue(loaded.isEnabled)
        assertEquals(100, loaded.priority)

        val activeRules = db.lyricsDao().getActiveRules()
        assertTrue(activeRules.any { it.id == ruleId })
    }
}
