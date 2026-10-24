package com.lirix.app.ui.i18n

import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class LocaleResourceParityTest {
    private fun strings(folder: String): Map<String, String> {
        val root = File("src/main/res/$folder/strings.xml").takeIf { it.isFile }
            ?: File("app/src/main/res/$folder/strings.xml")
        val document = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }.newDocumentBuilder().parse(root)
        val elements = document.getElementsByTagName("string")
        val entries = (0 until elements.length).map { elements.item(it) as Element }.map { it.getAttribute("name") to it.textContent }
        assertEquals("Duplicate resource names in $folder", entries.size, entries.map { it.first }.toSet().size)
        return entries.toMap()
    }
    @Test fun englishAndRussianNamesAndFormatArgumentsMatch() {
        val english = strings("values")
        val russian = strings("values-ru")
        assertEquals(english.keys, russian.keys)
        val arguments = Regex("%[0-9]+\\$[dsf]")
        english.forEach { (name, text) ->
            assertEquals(name, arguments.findAll(text).map { it.value }.toList().sorted(),
                arguments.findAll(russian.getValue(name)).map { it.value }.toList().sorted())
        }
    }
    @Test fun realHostKeepsLocaleAndLayoutDirectionChangesInPlace() {
        val manifest = File("src/main/AndroidManifest.xml").takeIf { it.isFile }
            ?: File("app/src/main/AndroidManifest.xml")
        val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(manifest)
        val activities = document.getElementsByTagName("activity")
        val host = (0 until activities.length).map { activities.item(it) as Element }
            .single { it.getAttributeNS("http://schemas.android.com/apk/res/android", "name") == ".MainActivity" }
        assertEquals(setOf("locale", "layoutDirection"), host.getAttributeNS("http://schemas.android.com/apk/res/android", "configChanges").split('|').toSet())
    }

}
