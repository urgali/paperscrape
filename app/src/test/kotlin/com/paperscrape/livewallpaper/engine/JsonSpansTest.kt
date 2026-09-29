package com.paperscrape.livewallpaper.engine

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scanner that finds where an unreadable saved theme's own text stands (item 18, v5.9A), and
 * the item that carries it. `UnreadableSavedThemeTest` holds the behaviour; this holds the parts.
 */
class JsonSpansTest {

    @Test
    fun `members and elements are the exact text of each value`() {
        val text = """ { "a" : [1, {"b":"x\"}"} , "c\\"] , "né" :-1.5e+3,"t":true,"z":null } """
        val members = JsonSpans.objectMembers(text, JsonSpans.skipSpace(text, 0))
        assertEquals(listOf("a", "né", "t", "z"), members.map { it.key })
        assertEquals("""[1, {"b":"x\"}"} , "c\\"]""", text.substring(members[0].start, members[0].end))
        assertEquals("-1.5e+3", text.substring(members[1].start, members[1].end))
        assertEquals("true", text.substring(members[2].start, members[2].end))
        assertEquals("null", text.substring(members[3].start, members[3].end))

        val array = members[0]
        val elements = JsonSpans.arrayElements(text, array.start).map { text.substring(it.start, it.end) }
        assertEquals(listOf("1", """{"b":"x\"}"}""", """"c\\""""), elements)
    }

    @Test
    fun `what org json tolerates and JSON does not makes the scanner throw rather than guess`() {
        for (lenient in listOf("{a:1}", "{\"a\":1;\"b\":2}", "{\"a\"=1}", "{\"a\":1 /* c */}", "{\"a\":'x'}", "{\"a\":1")) {
            val thrown = runCatching { DocumentSpans.of(lenient) }.exceptionOrNull()
            assertTrue("'$lenient' must not be scanned", thrown != null)
        }
    }

    @Test
    fun `a document's entries are found by container`() {
        val text = """{"schemaVersion":5,"overrides":{"x":{"k":1},"y":[]},"customThemes":[{"i":1},2],"unreadableEntries":[{"e":0}]}"""
        val spans = DocumentSpans.of(text)
        assertEquals(listOf("x", "y"), spans.overrides.map { it.key })
        assertEquals(listOf("""{"i":1}""", "2"), spans.customThemes.map { text.substring(it.start, it.end) })
        assertEquals(listOf("""{"e":0}"""), spans.unreadableEntries.map { text.substring(it.start, it.end) })
    }

    @Test
    fun `an item says where its entry came from and carries it verbatim`() {
        val entry = """{"id" : "custom:9","name":"a \/ b"}"""
        val item = UnreadableThemeEntry.of(UnreadableThemeEntry.FROM_OVERRIDES, "winter", 3, entry)
        assertEquals("overrides", item.from)
        assertEquals("winter", item.key)
        assertEquals(3, item.schemaVersion)
        assertEquals(entry, item.entryJson)
        assertEquals("the item is JSON", "winter", JSONObject(item.itemJson).getString("key"))

        val standalone = UnreadableThemeEntry.of(UnreadableThemeEntry.FROM_CUSTOM_THEMES, null, 5, "7")
        assertNull(standalone.key)
        assertEquals("7", standalone.entryJson)

        val nonsense = UnreadableThemeEntry("not an item")
        assertNull(nonsense.from)
        assertNull(nonsense.schemaVersion)
        assertNull(nonsense.entryJson)
    }
}
