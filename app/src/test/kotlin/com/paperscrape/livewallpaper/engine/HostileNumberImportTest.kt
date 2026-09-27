package com.paperscrape.livewallpaper.engine

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BCK-03: a number that is not a number cannot get into the scene, or into the store.
 *
 * `org.json` coerces strings to numbers, so a theme or backup file containing `"depthFraction":
 * "NaN"` used to read back as [Double.NaN] and go straight into the layout. Every comparison against
 * NaN is false, so the object stopped being drawn or was drawn nowhere — and the value was
 * *persisted*, so it survived restarts and re-exports until somebody edited the key by hand.
 *
 * The first test here proves the premise rather than assuming it: `org.json` really does hand back
 * NaN for that string. The rest pin the two readers that now stand in the way.
 */
class HostileNumberImportTest {

    @Test
    fun `org json really does turn the string NaN into a number`() {
        // The premise. If a future org.json stops doing this the readers below become belt and
        // braces rather than the fix, and this test says so out loud instead of quietly passing.
        val hostile = JSONObject("""{"x":"NaN","y":"Infinity","z":"-Infinity"}""")
        assertTrue("x", hostile.getDouble("x").isNaN())
        assertTrue("y", hostile.getDouble("y").isInfinite())
        assertTrue("z", hostile.getDouble("z").isInfinite())
    }

    @Test
    fun `a required field that is not finite makes the document malformed`() {
        for (poison in listOf("NaN", "Infinity", "-Infinity")) {
            val json = JSONObject("""{"depthFraction":"$poison"}""")
            val thrown = runCatching { json.requireFinite("depthFraction") }.exceptionOrNull()
            assertTrue(
                "a $poison depthFraction must be refused, not passed on",
                thrown is IllegalArgumentException,
            )
        }
    }

    @Test
    fun `a required field that is finite is read unchanged`() {
        val json = JSONObject("""{"depthFraction":0.375,"negative":-2.5}""")
        assertEquals(0.375f, json.requireFinite("depthFraction"), 1e-6f)
        assertEquals(-2.5f, json.requireFinite("negative"), 1e-6f)
    }

    @Test
    fun `an optional field that is not finite falls back instead of poisoning the value`() {
        for (poison in listOf("NaN", "Infinity", "-Infinity")) {
            val json = JSONObject("""{"density":"$poison"}""")
            val read = json.optFinite("density", 0.5f)
            assertFalse("a $poison density must not survive as $read", read.isNaN())
            assertEquals("and must take the default", 0.5f, read, 1e-6f)
        }
    }

    /**
     * The value BCK-03 let through (v5.8): finite as a Double, infinite as the Float it is stored as.
     *
     * Both readers checked the Double and then narrowed it, so `1e300` passed and became Infinity,
     * and the next `JSONObject.put` of it threw -- a 173-byte backup closed the app on the BV6600
     * (assessment v5.7). A real JSON number on purpose: this classpath's reference org.json reads
     * the *string* "NaN" as absent in `optDouble` (Android's reads it as NaN), so the optional-field
     * test above falls back here even without the check, and only a number reaches the check itself.
     */
    @Test
    fun `a number finite as a Double and infinite as a Float is not finite either`() {
        val json = JSONObject("""{"big":1e300,"small":-1e300}""")
        for (field in listOf("big", "small")) {
            assertEquals("optional $field must take the default", 0.5f, json.optFinite(field, 0.5f), 1e-6f)
            val thrown = runCatching { json.requireFinite(field) }.exceptionOrNull()
            assertTrue("a required $field must be refused, not passed on", thrown is IllegalArgumentException)
        }
        // And the largest Float is still a Float: the check is about infinity, not about size.
        assertEquals(Float.MAX_VALUE, JSONObject("""{"m":${Float.MAX_VALUE.toDouble()}}""").requireFinite("m"))
    }

    @Test
    fun `an optional field keeps working exactly as before for valid and missing values`() {
        // Backward compatibility, stated as a test: every payload written before this change must
        // read back identically. Only the values that were never legal change behaviour.
        val json = JSONObject("""{"density":0.25}""")
        assertEquals("a present value", 0.25f, json.optFinite("density", 0.5f), 1e-6f)
        assertEquals("a missing value", 0.5f, JSONObject("{}").optFinite("density", 0.5f), 1e-6f)
        assertEquals("zero is a value, not an absence", 0f, JSONObject("""{"d":0}""").optFinite("d", 0.5f), 1e-6f)
    }

    @Test
    fun `a whole layout carrying NaN is refused rather than half-imported`() {
        val layout = JSONObject(
            """
            {"staticObjects":[{"type":"HOUSE","depthFraction":"NaN","tileFractionX":0.5}],"cars":[]}
            """.trimIndent(),
        )
        val parsed = runCatching { sceneObjectLayoutFromJson(layout) }
        assertTrue("a layout with a NaN coordinate must not parse", parsed.isFailure)
    }

    @Test
    fun `a clean layout still parses`() {
        val layout = JSONObject(
            """
            {"staticObjects":[{"type":"HOUSE","depthFraction":0.4,"tileFractionX":0.5}],"cars":[]}
            """.trimIndent(),
        )
        val parsed = runCatching { sceneObjectLayoutFromJson(layout) }
        assertTrue("a valid layout must still parse: ${parsed.exceptionOrNull()}", parsed.isSuccess)
        assertEquals(1, parsed.getOrThrow().staticObjects.size)
    }
}
