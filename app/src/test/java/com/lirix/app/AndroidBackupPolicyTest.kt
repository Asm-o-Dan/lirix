package com.lirix.app

import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Tests the actual shipped policies, including security boundaries and persistence coverage. */
class AndroidBackupPolicyTest {
    private val main = File("src/main").takeIf { it.isDirectory } ?: File("app/src/main")
    private val androidNs = "http://schemas.android.com/apk/res/android"
    private val userPrefs = setOf("lirix_preferences", "lirix_translations", "lirix_ui_prompter_prefs",
        "lirix_sync_prefs", "media_sync_offsets")
    private val devicePrefs = setOf("lirix_trusted_devices", "lirix_device_addrs")

    private fun xml(path: String): Element = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    }.newDocumentBuilder().parse(File(main, path)).documentElement

    private fun Element.children(tag: String): List<Element> =
        (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }.filter { it.tagName == tag }

    private data class Entry(val domain: String, val path: String)
    private fun Element.entries(tag: String) = children(tag).map {
        Entry(it.getAttribute("domain"), it.getAttribute("path").trimEnd('/'))
    }.toSet()

    private fun policies() = listOf(
        xml("res/xml/backup_rules.xml"), xml("res/xml-v28/backup_rules.xml"),
        xml("res/xml/data_extraction_rules.xml").children("cloud-backup").single(),
        xml("res/xml/data_extraction_rules.xml").children("device-transfer").single()
    )

    @Test fun manifestEnablesOnlySystemManagedVersionSafeBackup() {
        val app = xml("AndroidManifest.xml").children("application").single()
        assertEquals("true", app.getAttributeNS(androidNs, "allowBackup"))
        assertEquals("@xml/backup_rules", app.getAttributeNS(androidNs, "fullBackupContent"))
        assertEquals("@xml/data_extraction_rules", app.getAttributeNS(androidNs, "dataExtractionRules"))
        assertNotEquals("true", app.getAttributeNS(androidNs, "restoreAnyVersion"))
        assertNotEquals("true", app.getAttributeNS(androidNs, "backupInForeground"))
        assertEquals("", app.getAttributeNS(androidNs, "backupAgent"))
    }

    @Test fun allTransportsPreserveDatabaseIncludingUncheckpointedWalAndUserPreferences() {
        val expected = setOf("event_engine.db", "event_engine.db-wal", "event_engine.db-shm", "event_engine.db-journal")
            .map { Entry("database", it) }.toSet() + userPrefs.map { Entry("sharedpref", "$it.xml") } + Entry("file", "karaoke-studio-drafts")
        policies().forEach { policy ->
            assertEquals(expected, policy.entries("include"))
            // No broad root exclusion may silently remove explicitly included child files.
            val excluded = policy.entries("exclude")
            assertTrue(excluded.all { it.path.isNotBlank() && it.path != "." && !it.path.startsWith("/") })
            expected.forEach { included ->
                assertTrue("Expected backup denied: $included", policy.permits(included))
                val physical = physicalPath(included)
                assertTrue("Included data shadowed by exclusion: $included", excluded.none {
                    val denied = physicalPath(it)
                    physical == denied || physical.startsWith("$denied/")
                })
            }
        }
    }

    @Test fun studioDraftsAreIncludedButAudioAndDerivedDataAreNot() {
        policies().forEach { policy ->
            assertTrue(policy.permits(Entry("file", "karaoke-studio-drafts/song.json")))
            assertTrue(policy.permits(Entry("file", "karaoke-studio-drafts/song.json.archive-original")))
            assertFalse(policy.permits(Entry("file", "karaoke-studio-drafts-neighbor/song.json")))
            assertFalse(policy.permits(Entry("file", "karaoke-studio-waveforms/cache.bin")))
            assertFalse(policy.permits(Entry("external", "Music/song.mp3")))
        }
    }

    // Resolve Android backup domains to their actual credential-encrypted app-relative paths.
    private fun physicalPath(entry: Entry) = when (entry.domain) {
        "database" -> "databases/${entry.path}"
        "sharedpref" -> "shared_prefs/${entry.path}"
        "file" -> "files/${entry.path}"
        "root" -> entry.path
        else -> "${entry.domain}/${entry.path}"
    }

    private fun Element.permits(entry: Entry): Boolean {
        val candidate = physicalPath(entry)
        fun matches(rule: Entry): Boolean {
            val path = physicalPath(rule).removeSuffix("/.")
            return candidate == path || candidate.startsWith("$path/") || path == "."
        }
        return entries("include").any(::matches) && entries("exclude").none(::matches)
    }

    @Test fun secretsCachesUnknownFilesAndOtherDatabasesAreDenied() {
        val forbidden = devicePrefs.map { Entry("sharedpref", "$it.xml") } + listOf(
            Entry("sharedpref", "future_credentials.xml"), Entry("database", "future_keys.db"),
            Entry("file", "album_art/cover.webp"), Entry("file", "keys/private.key"),
            Entry("root", ".signing/key.jks"), Entry("root", "cache/shares/poster.png"),
            Entry("external", "music.mp3"), Entry("device_sharedpref", "identity.xml")
        )
        policies().forEach { policy ->
            forbidden.forEach { entry -> assertFalse("Unexpected backup of $entry", policy.permits(entry)) }
            userPrefs.forEach { name -> assertTrue(policy.permits(Entry("sharedpref", "$name.xml"))) }
            assertTrue(policy.permits(Entry("database", "event_engine.db")))
            // Exact includes already deny everything else; redundant out-of-scope exclusions fail release lint.
            assertTrue(policy.entries("exclude").isEmpty())
        }
    }

    @Test fun encryptionFlagsRespectApiAvailabilityAndDoNotDisableModernDeviceTransfer() {
        assertTrue(xml("res/xml/backup_rules.xml").children("include").all { !it.hasAttribute("requireFlags") })
        assertTrue(xml("res/xml-v28/backup_rules.xml").children("include").all {
            it.getAttribute("requireFlags") == "clientSideEncryption"
        })
        val modern = xml("res/xml/data_extraction_rules.xml")
        assertEquals("true", modern.children("cloud-backup").single().getAttribute("disableIfNoEncryptionCapabilities"))
        val transfer = modern.children("device-transfer").single()
        assertFalse(transfer.hasAttribute("disableIfNoEncryptionCapabilities"))
        assertTrue(transfer.children("include").all { !it.hasAttribute("requireFlags") })
    }

    @Test fun everyCurrentPreferenceStoreHasAnExplicitUserOrDevicePolicy() {
        val appSources = File(main, "java").walkTopDown().filter { it.extension == "kt" }.toList()
        // Shared Android theme store uses the same explicitly named preference file.
        val sharedSources = File(main, "../../../../shared/src/androidMain/kotlin").canonicalFile
            .takeIf { it.isDirectory } ?: File(main, "../../../shared/src/androidMain/kotlin").canonicalFile
        assertTrue("Shared Android persistence sources unavailable", sharedSources.isDirectory)
        val sources = appSources + sharedSources.walkTopDown().filter { it.extension == "kt" }.toList()
        val namedCalls = Regex("getSharedPreferences\\(\\s*\"([^\"]+)\"")
        val namedDefaults = Regex("(?:PREFS_NAME\\s*=|prefsName:\\s*String\\s*=)\\s*\"([^\"]+)\"")
        val names = sources.flatMap { file ->
            val text = file.readText()
            namedCalls.findAll(text).map { it.groupValues[1] }.toList() +
                namedDefaults.findAll(text).map { it.groupValues[1] }.toList()
        }.toSet()
        assertEquals("Review backup policy when adding persistent preferences", userPrefs + devicePrefs, names)
    }
}
