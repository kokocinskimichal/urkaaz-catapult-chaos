package com.urkaaaz.localization

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.w3c.dom.Element

class LocalizationResourceParityTest {
    @Test
    fun stringKeysPluralFormsAndPositionalArgumentsMatchAcrossLocales() {
        val catalogs = mapOf(
            "en" to readCatalog(File("src/main/res/values/strings.xml")),
            "pl" to readCatalog(File("src/main/res/values-pl/strings.xml")),
            "es" to readCatalog(File("src/main/res/values-es/strings.xml")),
        )
        val english = catalogs.getValue("en")

        catalogs.forEach { (locale, catalog) ->
            assertEquals("resource keys for $locale", english.keys, catalog.keys)
            english.forEach { (key, reference) ->
                val translated = catalog.getValue(key)
                assertEquals("$locale resource kind for $key", reference.kind, translated.kind)
                if (reference.kind == "plurals") {
                    assertEquals(
                        "$locale plural forms for $key",
                        REQUIRED_PLURAL_FORMS.getValue(locale),
                        translated.values.keys,
                    )
                    val expectedArguments = formatArguments(reference.values.values.first())
                    translated.values.forEach { (quantity, text) ->
                        assertEquals(
                            "$locale format arguments for $key ($quantity)",
                            expectedArguments,
                            formatArguments(text),
                        )
                    }
                } else {
                    assertEquals(
                        "$locale format arguments for $key",
                        formatArguments(reference.values.getValue("value")),
                        formatArguments(translated.values.getValue("value")),
                    )
                }
            }
        }
        assertFalse(File("src/main/res/values-en").exists())
    }

    private fun readCatalog(file: File): Map<String, ResourceEntry> {
        require(file.isFile) { "missing localization resource file: ${file.path}" }
        val documentFactory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        val resources = documentFactory.newDocumentBuilder().parse(file).documentElement
        val entries = mutableMapOf<String, ResourceEntry>()
        for (index in 0 until resources.childNodes.length) {
            val node = resources.childNodes.item(index)
            if (node !is Element) continue
            val name = node.getAttribute("name")
            val entry = when (node.tagName) {
                "string" -> ResourceEntry(
                    kind = "string",
                    values = mapOf("value" to node.textContent),
                )
                "plurals" -> {
                    val values = mutableMapOf<String, String>()
                    for (itemIndex in 0 until node.childNodes.length) {
                        val item = node.childNodes.item(itemIndex)
                        if (item is Element && item.tagName == "item") {
                            values[item.getAttribute("quantity")] = item.textContent
                        }
                    }
                    ResourceEntry(kind = "plurals", values = values)
                }
                else -> error("unexpected resource element ${node.tagName}")
            }
            check(entries.put(name, entry) == null) { "duplicate resource key: $name" }
        }
        return entries
    }

    private fun formatArguments(value: String): Set<String> =
        FORMAT_ARGUMENT.findAll(value)
            .map { match ->
                val argumentIndex = requireNotNull(match.groups[1]?.value) {
                    "format argument must use a positional index: $value"
                }
                "$argumentIndex:${match.groups[2]?.value}"
            }
            .toSet()

    private data class ResourceEntry(
        val kind: String,
        val values: Map<String, String>,
    )

    private companion object {
        val REQUIRED_PLURAL_FORMS = mapOf(
            "en" to setOf("one", "other"),
            "pl" to setOf("one", "few", "many", "other"),
            "es" to setOf("one", "many", "other"),
        )
        val FORMAT_ARGUMENT = Regex("%(\\d+)\\$[-#+ 0,(]*\\d*(?:\\.\\d+)?([a-zA-Z])")
    }
}
