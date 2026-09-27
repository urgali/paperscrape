package com.paperscrape.livewallpaper.prefs

import com.paperscrape.livewallpaper.engine.defaultCustomizationFor
import com.paperscrape.livewallpaper.engine.toJsonString
import java.io.File
import java.util.Random
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The two documents that enter this app from outside must never throw on the way in.**
 *
 * `parseAppBackup` and `parseThemeShare` both promise a *result* -- `Ok` or `Failed` -- and never
 * an exception. That promise is load-bearing: both call sites are inside a `scope.launch { }` with
 * no `catch` (`ui/AdvancedScreen.kt`, `ui/ThemeGalleryScreen.kt`), so a `Throwable` escaping a
 * parser does not become an error message, it closes the settings screen.
 *
 * ### Why a fuzzer and not more example documents
 *
 * The v5.3B security audit found that the promise was broken, and it found it by *breaking* the
 * documents rather than by reading the code. A 129-byte backup with `"colorDay1":"nope"` closed the
 * app: `engine/CustomThemeData.kt` read four of its dozens of colours with `getInt`, which throws
 * on a present-but-wrong value, where every sibling colour used `optInt`, which does not. Four
 * lines out of step with a dozen others is exactly the kind of thing example-based tests miss --
 * every example anybody thought to write had an integer there.
 *
 * **And it needs no attacker.** The audit found it by flipping one character at random, which is
 * what a half-finished download or an ageing SD card does. A backup is the thing you reach for
 * when something has *already* gone wrong; it is the worst possible moment for the app to vanish.
 *
 * So the defect is fixed *and* the check that found it stays: `BACKLOG` policy after v5.3B is that
 * a defect found by a new check earns that check a place in the suite.
 *
 * ### What it does
 *
 * From one valid document per format, 20 000 mutations each: values replaced by NULL, empty
 * strings, lone surrogates, `NaN`, `Infinity`, `1e300`, integers past the `Int` range, arrays where
 * objects belong, 200 nested brackets, random truncations and a random character flipped -- and
 * then, deterministically, **every number in the document** replaced in turn by `NaN`, `Infinity`
 * and ±`1e300`. Every escaping `Throwable` is counted and its first signature recorded under
 * `build/reports/fuzz/`.
 *
 * ### Accepted means writable (v5.8)
 *
 * "Never throws" was checked only up to the parser's `return`, and that is not where the app died.
 * The v5.7 assessment closed the app on the BV6600 with a **173-byte backup** whose one house had
 * `"scale":1e300`: finite as a `Double`, so the finiteness check let it through, infinite as the
 * `Float` it became, so the import's own staging copy -- `CustomThemeData.toJsonString` -- threw
 * `Forbidden numeric value: Infinity` a line later, outside any catch. So every document a parser
 * accepts is now also **written back** here, the way the import is about to, and a throw there
 * counts exactly like a throw in the parser. `1e300` joined the mutations, and the assessment's own
 * file is pinned by name below.
 *
 * **And adding the value was not enough.** With the fix reverted, the structural pass still came
 * back green: it reaches a number three objects deep about once in fifty thousand mutations, which
 * is also why it had never found this. The numeric pass walks every leaf instead, and with the fix
 * reverted it goes red (`consegna_v5_8a/registri`, mutation M3).
 *
 * ### The mutation control is not optional
 *
 * A green at the first run proves nothing on its own -- "zero exceptions" and "the fuzzer never
 * reached the parser" look identical from here. [fuzzerItselfCanGoRed] runs the same harness
 * against a deliberately unguarded parser and requires it to go **red**. If that one ever passes
 * silently, the two above are worthless.
 *
 * A note on the classpath: `org.json:json` is a unit-test-only dependency (see
 * `app/build.gradle.kts`) and Android's own implementation is Harmony-derived, not identical. The
 * audit confirmed this particular defect on the device with an instrumented test before fixing it;
 * for the general "never throws" shape the two implementations agree, and the JVM is where 60 000
 * parses in a few seconds are affordable.
 */
class ImportParserFuzzTest {

    private val outDir = File("build/reports/fuzz")

    /** Structural mutations applied to a valid document. */
    private fun mutate(node: Any?, rnd: Random, depth: Int = 0): Any? {
        if (depth > 6) return node
        return when (rnd.nextInt(15)) {
            0 -> JSONObject.NULL
            1 -> ""
            2 -> " ￿\uD800" // lone surrogate + non-character
            3 -> Double.NaN.toString()
            4 -> "Infinity"
            5 -> Long.MIN_VALUE
            6 -> Int.MAX_VALUE.toLong() + 1L
            7 -> -1
            8 -> JSONArray()
            9 -> JSONObject()
            10 -> JSONArray(listOf(1, 2, 3))
            11 -> "x".repeat(4096)
            12 -> true
            // Finite as a Double, infinite as a Float: the value the v5.7 assessment's 173-byte
            // backup carried, and the one no other mutation here produces.
            13 -> 1e300
            else -> when (node) {
                is JSONObject -> {
                    val keys = node.keys().asSequence().toList()
                    if (keys.isEmpty()) {
                        node
                    } else {
                        val k = keys[rnd.nextInt(keys.size)]
                        node.put(k, mutate(node.opt(k), rnd, depth + 1))
                        node
                    }
                }
                is JSONArray -> {
                    if (node.length() == 0) {
                        node
                    } else {
                        val i = rnd.nextInt(node.length())
                        node.put(i, mutate(node.opt(i), rnd, depth + 1))
                        node
                    }
                }
                else -> node
            }
        }
    }

    /**
     * Throws [mutations] at [parse] and returns how many `Throwable`s escaped.
     *
     * The seed is fixed so a failure is reproducible: a run that reports an escape can be replayed
     * exactly, which is how the original 129-byte document was minimised.
     */
    private fun fuzz(name: String, valid: String, parse: (String) -> Any): Int {
        val rnd = Random(SEED)
        val log = StringBuilder()
        log.append("# import parser fuzz: $name\n# seed=$SEED iterations=20000\n")
        log.append("# valid document parses to: ${parse(valid)::class.java.simpleName}\n")
        var crashes = 0
        val seen = HashSet<String>()

        // 1. structural mutations of a valid document
        repeat(12_000) {
            val root = JSONObject(valid)
            repeat(1 + rnd.nextInt(4)) { mutate(root, rnd) }
            val doc = root.toString()
            try {
                parse(doc)
            } catch (t: Throwable) {
                crashes++
                val sig = "${t::class.java.name}: ${t.message}"
                if (seen.add(sig)) {
                    log.append("\nESCAPED #$crashes  $sig\n")
                    log.append("  at ${t.stackTrace.take(4).joinToString("\n     ")}\n")
                    log.append("  document (first 600 chars): ${doc.take(600)}\n")
                }
            }
        }

        // 2. byte-level truncation and corruption of the valid document
        repeat(8_000) {
            val chars = valid.toCharArray()
            when (rnd.nextInt(3)) {
                0 -> { // truncate
                    val cut = rnd.nextInt(chars.size)
                    val doc = String(chars, 0, cut)
                    try {
                        parse(doc)
                    } catch (t: Throwable) {
                        crashes++
                        val sig = "TRUNC ${t::class.java.name}: ${t.message}"
                        if (seen.add(sig)) log.append("\nESCAPED(trunc) $sig\n  cut=$cut\n")
                    }
                }
                1 -> { // flip one character -- what a corrupted download looks like
                    val i = rnd.nextInt(chars.size)
                    chars[i] = rnd.nextInt(0x110000).toChar()
                    try {
                        parse(String(chars))
                    } catch (t: Throwable) {
                        crashes++
                        val sig = "FLIP ${t::class.java.name}: ${t.message}"
                        if (seen.add(sig)) log.append("\nESCAPED(flip) $sig\n  index=$i\n")
                    }
                }
                else -> { // splice in deep nesting
                    val doc = valid.dropLast(1) + ",\"deep\":" + "[".repeat(200) + "]".repeat(200) + "}"
                    try {
                        parse(doc)
                    } catch (t: Throwable) {
                        crashes++
                        val sig = "DEEP ${t::class.java.name}: ${t.message}"
                        if (seen.add(sig)) log.append("\nESCAPED(deep) $sig\n")
                    }
                }
            }
        }

        // 3. every number in the document, replaced in turn by every hostile number (v5.8).
        //
        // The structural pass above replaces a node *or* descends into it, one level at a time, so
        // a number three objects deep -- a house's density in a theme's customization -- is reached
        // about once in fifty thousand mutations. That is how `"scale":1e300` went unfound: this
        // fuzzer was already running when a 173-byte file closed the app. Walking the leaves makes
        // reaching every number certain rather than likely.
        var numbers = 0
        for (path in numericLeaves(JSONObject(valid))) {
            for (hostile in HOSTILE_NUMBERS) {
                numbers++
                val root = JSONObject(valid)
                replaceAt(root, path, hostile)
                val doc = root.toString()
                try {
                    parse(doc)
                } catch (t: Throwable) {
                    crashes++
                    val sig = "NUMBER ${t::class.java.name}: ${t.message}"
                    if (seen.add(sig)) log.append("\nESCAPED(number) $sig\n  at ${path.joinToString(".")} = $hostile\n")
                }
            }
        }
        log.append("\n# numeric pass: $numbers documents, one hostile number each\n")

        log.append("\n# TOTAL escaping throwables: $crashes  (distinct signatures: ${seen.size})\n")
        outDir.mkdirs()
        File(outDir, "fuzz_$name.txt").writeText(log.toString())
        return crashes
    }

    /** The path, key by key or index by index, to every number in [node]. */
    private fun numericLeaves(node: Any?, prefix: List<Any> = emptyList()): List<List<Any>> = when (node) {
        is JSONObject -> node.keys().asSequence().toList().flatMap { numericLeaves(node.opt(it), prefix + it) }
        is JSONArray -> (0 until node.length()).flatMap { numericLeaves(node.opt(it), prefix + it) }
        is Number -> listOf(prefix)
        else -> emptyList()
    }

    private fun replaceAt(root: Any, path: List<Any>, value: Any) {
        var node: Any = root
        for (step in path.dropLast(1)) node = if (node is JSONObject) node.get(step as String) else (node as JSONArray).get(step as Int)
        when (val last = path.last()) {
            is String -> (node as JSONObject).put(last, value)
            else -> (node as JSONArray).put(last as Int, value)
        }
    }

    private fun validBackup(): String {
        val themeId = "beach"
        return AppBackup(
            schemaVersion = AppBackup.SCHEMA_VERSION,
            appVersionName = "5.2",
            createdAtMillis = 1_700_000_000_000L,
            settings = AppBackup.BackupSettings(
                themeId = themeId,
                syncWithRealTime = true,
                useLocationForSunTimes = false,
                useCustomLocation = false,
                deviceLocationKind = "coarse",
                customLocationLatitude = 45.5f,
                customLocationLongitude = 9.2f,
                customLocationLabel = "Milano",
                liveWeatherEnabled = false,
                liveWeatherApiKey = "",
                weatherProviderId = "openmeteo",
                weatherApiComApiKey = "",
                openWeatherApiKey = "",
                automaticUpdateCheckEnabled = true,
                updateNotificationsEnabled = true,
                fixedHour = 12f,
                parallaxStrength = 0.5f,
                scrollBackground = true,
                swipeScroll = true,
                scrollSpeed = 1f,
                autoThemeByDate = false,
                seasonalCalendarJson = "",
            ),
            themeCustomizations = mapOf(themeId to defaultCustomizationFor(themeId)),
            customThemeData = com.paperscrape.livewallpaper.engine.CustomThemeData(),
        ).toJsonString()
    }

    private fun validThemeShare(): String {
        val themeId = "beach"
        return ThemeShare.of(
            sourceThemeId = themeId,
            name = "Fuzz fixture",
            customization = defaultCustomizationFor(themeId),
            appVersionName = "5.2",
            nowMillis = 1_700_000_000_000L,
        ).toJsonString()
    }

    /**
     * Parses [raw] and, if the backup is accepted, writes it back the two ways the app does: the
     * import's staging copy of the saved themes, and the whole document as "Export backup" would.
     */
    private fun parseAndWriteBackBackup(raw: String): Any {
        val parsed = parseAppBackup(raw)
        if (parsed is BackupParseResult.Ok) {
            parsed.backup.customThemeData.toJsonString()
            parsed.backup.toJsonString()
        }
        return parsed
    }

    /** The same for a shared theme: what the gallery holds after an accepted import is written back whole. */
    private fun parseAndWriteBackThemeShare(raw: String): Any {
        val parsed = parseThemeShare(raw)
        if (parsed is ThemeParseResult.Ok) parsed.share.toJsonString()
        return parsed
    }

    @Test
    fun `the assessment's 173-byte backup is taken without closing the app`() {
        // Byte for byte the file of assessment v5.7, registri/strumenti/prova_import_A_backup.json.
        val raw = "{\"kind\":\"paperscrape-app-backup\",\"schemaVersion\":1,\"settings\":{}," +
            "\"customThemeSchemaVersion\":1,\"customThemes\":[{\"layout\":{\"staticObjects\":" +
            "[{\"type\":\"HOUSE\",\"scale\":1e300}]}}]}"
        assertEquals("the fixture is not the assessment's file", 173, raw.toByteArray().size)
        // It may be accepted (the one bad number falls back) or refused; what it may not do is throw.
        parseAndWriteBackBackup(raw)
    }

    @Test
    fun `a backup's own numbers fall back when they are not finite`() {
        // The five settings numbers were read raw until v5.8: accepted, stored, and then refused by
        // every export. They take the default now, as every number in the theme half already did.
        //
        // Numbers, not the strings "NaN" and "Infinity". Measured: this classpath's reference
        // org.json answers `optDouble` on the string "NaN" with the default, where Android's reads
        // it as NaN (see BCK-03 in CustomThemeData.kt) -- so a string here would pass on the JVM
        // with the fix reverted, and did (mutation M4). ±1e300 is a number to both, and infinite
        // only once it is a Float.
        val raw = validBackup().let { JSONObject(it) }.apply {
            getJSONObject("settings").apply {
                put("fixedHour", 1e300)
                put("scrollSpeed", -1e300)
                put("customLocationLatitude", 1e300)
                put("customLocationLongitude", -1e300)
                put("parallaxStrength", 1e300)
            }
        }.toString()
        val parsed = parseAppBackup(raw)
        assertTrue("a readable backup was refused for one bad number", parsed is BackupParseResult.Ok)
        val settings = (parsed as BackupParseResult.Ok).backup.settings
        val defaults = WallpaperSettings()
        assertEquals(defaults.fixedHour, settings.fixedHour)
        assertEquals(defaults.scrollSpeed, settings.scrollSpeed)
        assertEquals(defaults.customLocationLatitude, settings.customLocationLatitude)
        assertEquals(defaults.customLocationLongitude, settings.customLocationLongitude)
        assertEquals(defaults.parallaxStrength, settings.parallaxStrength)
        parsed.backup.toJsonString()
    }

    @Test
    fun `a mutated backup is refused, never thrown on`() {
        val valid = validBackup()
        assertTrue("the fixture itself must parse, or the fuzzer starts from nothing", parseAppBackup(valid) is BackupParseResult.Ok)
        val crashes = fuzz("backup", valid) { parseAndWriteBackBackup(it) }
        assertEquals(
            "parseAppBackup let a Throwable escape -- its call site has no catch, so this closes the " +
                "settings screen while the user is restoring a backup. See build/reports/fuzz/fuzz_backup.txt",
            0,
            crashes,
        )
    }

    @Test
    fun `a mutated shared theme is refused, never thrown on`() {
        val valid = validThemeShare()
        assertTrue("the fixture itself must parse, or the fuzzer starts from nothing", parseThemeShare(valid) is ThemeParseResult.Ok)
        val crashes = fuzz("themeshare", valid) { parseAndWriteBackThemeShare(it) }
        assertEquals(
            "parseThemeShare let a Throwable escape -- its call site has no catch, so this closes the " +
                "theme gallery. See build/reports/fuzz/fuzz_themeshare.txt",
            0,
            crashes,
        )
    }

    /**
     * The two tests above are only worth their green if this harness can go red.
     *
     * Same fuzzer, pointed at a deliberately unguarded parser: the raw `JSONObject(...)`
     * constructor plus a `getString` of a field the mutations delete. A zero here means the fuzzer
     * is not reaching its target and the greens above prove nothing.
     */
    @Test
    fun `the fuzzer itself can go red`() {
        val valid = validBackup()
        val crashes = fuzz("mutation_control", valid) { raw ->
            // No runCatching, and `getString` throws where `optString` would not.
            JSONObject(raw).getString("kind")
        }
        assertTrue(
            "MUTATION CONTROL FAILED: the fuzzer found no escapes even against an unguarded parser, " +
                "so every green in this class proves nothing. crashes=$crashes",
            crashes > 0,
        )
    }

    private companion object {
        /** Fixed, so a failing run is replayable character for character. */
        const val SEED = 20260916L

        /**
         * What the numeric pass puts in place of each number: the two non-finite strings Android's
         * `org.json` coerces (this classpath's reference implementation coerces them in `getDouble`
         * only, and reads them as absent in `optDouble`), and the two values that are finite as a
         * Double and infinite as a Float, which are numbers to both.
         */
        val HOSTILE_NUMBERS: List<Any> = listOf("NaN", "Infinity", 1e300, -1e300)
    }
}
