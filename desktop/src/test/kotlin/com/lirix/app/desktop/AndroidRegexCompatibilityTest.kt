package com.lirix.app.desktop

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Shared code runs on Android, whose ICU regex engine rejects Java's UNICODE_CHARACTER_CLASS
 * flag, inline (?U) included. The JVM accepts it, so desktop tests stay green while the app
 * crashes (DEFECT-153-01). This guard keeps such patterns out of code compiled for Android.
 */
class AndroidRegexCompatibilityTest {
    private val forbidden = listOf(
        Regex("""\(\?[a-zA-Z]*U[a-zA-Z]*[):]"""),
        Regex("""UNICODE_CHARACTER_CLASS"""),
    )

    @Test fun sharedAndAndroidCodeUsesNoUnicodeCharacterClassFlag() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").exists() }
        val sources = listOf("shared/src/commonMain", "shared/src/jvmShared", "shared/src/androidMain", "app/src/main")
            .map { File(root, it) }
            .filter { it.isDirectory }
        assertTrue("source folders not found", sources.isNotEmpty())
        val offenders = sources.flatMap { dir ->
            dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if (forbidden.any { it.containsMatchIn(line) }) "${file.relativeTo(root)}:${index + 1}" else null
                }
            }.toList()
        }
        assertTrue("Android cannot compile these patterns: $offenders", offenders.isEmpty())
    }
}
