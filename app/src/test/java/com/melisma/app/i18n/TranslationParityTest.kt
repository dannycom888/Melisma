package com.melisma.app.i18n

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the Simplified Chinese UI translation: `values/strings.xml` and
 * `values-zh/strings.xml` must carry exactly the same keys, with matching
 * format specifiers and no empty translations, so no screen shows a mix of
 * English and Chinese (or crashes on a missing format argument).
 */
class TranslationParityTest {

    private fun loadStrings(localeDir: String): Map<String, String> {
        val file = File("src/main/res/$localeDir/strings.xml")
        assertTrue("missing $file (run from the app module dir)", file.isFile)
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val out = mutableMapOf<String, String>()
        val nodes = doc.getElementsByTagName("string")
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            val name = node.attributes.getNamedItem("name").nodeValue
            out[name] = node.textContent
        }
        return out
    }

    private fun formatSpecs(text: String): List<String> {
        // Matches %1$s, %2$d, %s, %.1f, %% (escaped literal percent, skipped).
        return Regex("""%(?:\d+\$)?[0-9.]*[a-zA-Z]""").findAll(text)
            .map { it.value }
            .filter { it != "%%" }
            .toList()
    }

    @Test
    fun `values and values-zh have identical string keys`() {
        val en = loadStrings("values")
        val zh = loadStrings("values-zh")
        assertEquals("missing in values-zh: ${en.keys - zh.keys}", en.keys, zh.keys)
        assertTrue("expected hundreds of strings, got ${en.size}", en.size >= 500)
    }

    @Test
    fun `format specifiers match between English and Chinese`() {
        val en = loadStrings("values")
        val zh = loadStrings("values-zh")
        val mismatches = en.keys.filter { key ->
            formatSpecs(en.getValue(key)) != formatSpecs(zh.getValue(key))
        }
        assertEquals("format-specifier mismatch in: $mismatches", emptyList<String>(), mismatches)
    }

    @Test
    fun `no empty Chinese translations`() {
        val zh = loadStrings("values-zh")
        val empty = zh.filterValues { it.isBlank() }.keys
        assertEquals("empty translations: $empty", emptyList<String>(), empty.toList())
    }
}
