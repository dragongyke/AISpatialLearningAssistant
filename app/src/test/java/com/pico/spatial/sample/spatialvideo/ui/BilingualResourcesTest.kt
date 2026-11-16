package com.pico.spatial.sample.spatialvideo.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class BilingualResourcesTest {
    @Test
    fun chineseMatchesDefaultKeysAndFormatArguments() {
        val res = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
        fun strings(qualifier: String): Map<String, String> {
            val files = checkNotNull(File(res, qualifier).listFiles { f -> f.extension == "xml" })
            return buildMap {
                for (file in files) {
                    val nodes =
                        DocumentBuilderFactory.newInstance()
                            .newDocumentBuilder()
                            .parse(file)
                            .getElementsByTagName("string")
                    for (index in 0 until nodes.length) {
                        val node = nodes.item(index) as Element
                        if (node.getAttribute("translatable") != "false") {
                            val key = node.getAttribute("name")
                            assertTrue(
                                "Duplicate $key in $qualifier",
                                put(key, node.textContent) == null
                            )
                        }
                    }
                }
            }
        }
        val english = strings("values")
        val chinese = strings("values-zh")
        assertEquals(english.keys, chinese.keys)
        val argument = Regex("""%(\d+)\$[-+ 0,(]*\d*(?:\.\d+)?([a-zA-Z])""")
        for ((name, original) in english) {
            fun arguments(text: String) =
                argument
                    .findAll(text)
                    .map { it.groupValues.drop(1).joinToString(":") }
                    .sorted()
                    .toList()
            assertEquals(name, arguments(original), arguments(chinese.getValue(name)))
            assertTrue("Missing translation: $name", chinese.getValue(name).isNotBlank())
        }
    }
}
