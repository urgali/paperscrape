package com.paperscrape.livewallpaper.engine

import androidx.compose.runtime.SnapshotMutationPolicy
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.structuralEqualityPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Why the settings screen may not deduplicate its saved-theme state.**
 *
 * [SceneTheme] declares `equals` as "same id", which is deliberate and load-bearing -- the class
 * has `IntArray` fields, a data-class `equals` would compare them by identity, and the id is what
 * every lookup in the app actually means by "the same theme". `CLAUDE.md` already records the
 * first thing it broke: a round-trip test that asserted whole-object equality passed while every
 * colour was being lost.
 *
 * This is the second thing it broke, and it is what these tests exist to pin.
 * `CustomThemeEntry` is a data class *containing* a `SceneTheme`, and `CustomThemeData` contains
 * those. So a restored backup whose themes have the same ids and completely different contents is
 * `==` to what was there before, `collectAsState`'s default `structuralEqualityPolicy` decides
 * nothing has changed, and the open settings screen never repaints.
 *
 * Reproduced on a Pixel 9 before the fix: restore a backup whose saved theme keeps its id and
 * changes its `displayName`, and the DataStore holds `ZZRenamed` while the screen still says
 * `ZZTest` -- through a navigation to the theme gallery and back, and until the Activity is
 * recreated. The backup had already written both stores correctly; nothing was wrong with it.
 *
 * `SettingsScreen.rememberCustomThemeData` collects with `neverEqualPolicy()` instead. **If these
 * tests ever start failing, that workaround can go** -- they fail exactly when `SceneTheme` gains
 * a content-aware `equals`, which would make the default policy correct again.
 */
class CustomThemeDataEqualityTest {

    private fun theme(id: String, name: String, accent: Int = 0xFF112233.toInt()) = SceneTheme(
        id = id,
        displayName = name,
        skyNight = intArrayOf(0xFF0B0E2E.toInt(), 0xFF1B1B3A.toInt()),
        skyDawn = intArrayOf(0xFFFF9E7D.toInt(), 0xFFFFD59E.toInt()),
        skyDay = intArrayOf(0xFF6EC6FF.toInt(), 0xFFCDEFFF.toInt()),
        skyDusk = intArrayOf(0xFFFF7A59.toInt(), 0xFFFFC98B.toInt()),
        hillColorsDay = intArrayOf(0xFF7FB069.toInt()),
        hillColorsNight = intArrayOf(0xFF243B2E.toInt()),
        sunColor = 0xFFFFD166.toInt(),
        moonColor = 0xFFF2F2F2.toInt(),
        starColor = 0xFFFFFFFF.toInt(),
        accentColor = accent,
    )

    private fun entry(id: String, name: String, themeName: String = name) = CustomThemeEntry(
        id = id,
        name = name,
        theme = theme(id, themeName),
        layout = SceneObjectLayout(staticObjects = emptyList(), cars = emptyList()),
    )

    // ------------------------------------------------------------------ the hazard itself

    /** Two themes that share nothing but an id are equal, and that is the whole problem. */
    @Test
    fun `SceneTheme still compares by id alone`() {
        val a = theme("custom:1", "ZZTest", accent = 0xFF000000.toInt())
        val b = theme("custom:1", "ZZRenamed", accent = 0xFFFFFFFF.toInt())
        assertEquals("SceneTheme has gained a content-aware equals -- see this class's doc", a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals("and two different ids are still different", a, theme("custom:2", "ZZTest"))
    }

    /**
     * Which makes a restored `CustomThemeData` indistinguishable from the one it replaced.
     *
     * The exact shape of the reproduction: same id, same entry name, different `displayName` on the
     * theme inside. Compose is told the value did not change, so nothing recomposes and the screen
     * keeps painting the old name out of a composition that was never invalidated.
     */
    @Test
    fun `a restored theme that keeps its id is equal to the one it replaced`() {
        val before = CustomThemeData(customThemes = listOf(entry("custom:1", "ZZ", themeName = "ZZTest")))
        val after = CustomThemeData(customThemes = listOf(entry("custom:1", "ZZ", themeName = "ZZRenamed")))
        assertEquals(
            "the settings screen cannot tell these apart, which is why it must not try",
            before,
            after,
        )
    }

    /** The same for a built-in override, which is the more common thing to restore. */
    @Test
    fun `a restored built-in override that keeps its id is equal to the one it replaced`() {
        val before = CustomThemeData(overrides = mapOf("winter" to entry("winter", "Winter", "Winter")))
        val after = CustomThemeData(overrides = mapOf("winter" to entry("winter", "Winter", "Midwinter")))
        assertEquals(before, after)
    }

    // ------------------------------------------------------------------ what still is a change

    /**
     * Not everything hides. A different id, a different entry name or a different count all show
     * through -- which is why the defect looked intermittent and why the simple half of the
     * restore always worked.
     */
    @Test
    fun `a change the default policy does notice`() {
        val one = CustomThemeData(customThemes = listOf(entry("custom:1", "ZZ")))
        assertNotEquals(one, CustomThemeData(customThemes = listOf(entry("custom:2", "ZZ"))))
        assertNotEquals(one, CustomThemeData(customThemes = listOf(entry("custom:1", "Renamed"))))
        assertNotEquals(one, CustomThemeData(customThemes = emptyList()))
        assertTrue(CustomThemeData.EMPTY.customThemes.isEmpty())
    }

    // ------------------------------------------------------------------ the fix, as a contract

    /**
     * The two policies, on the exact value the restore produces.
     *
     * `SettingsScreen.rememberCustomThemeData` holds the saved themes in a state created with
     * [neverEqualPolicy]; `collectAsState` would have created it with [structuralEqualityPolicy].
     * This is the difference between them, measured by counting how many times an observer is
     * invalidated -- which is precisely how many times Compose would recompose the settings tree.
     *
     * Under the default policy the restored value is swallowed: **zero** notifications for a write
     * that changed the theme's whole appearance. Under the policy the screen now uses, one.
     */
    @Test
    fun `only the policy the settings screen uses reports a restore that keeps every id`() {
        val before = CustomThemeData(customThemes = listOf(entry("custom:1", "ZZ", themeName = "ZZTest")))
        val after = CustomThemeData(customThemes = listOf(entry("custom:1", "ZZ", themeName = "ZZRenamed")))

        assertEquals(
            "the default policy noticed the restore, so the workaround is no longer needed",
            0,
            notificationsFor(structuralEqualityPolicy(), before, after),
        )
        assertEquals(
            "the settings screen's own policy missed the restore",
            1,
            notificationsFor(neverEqualPolicy(), before, after),
        )
    }

    /**
     * How many times writing [next] over [first] invalidates a reader, under [policy].
     *
     * Uses the snapshot system directly rather than a Compose UI test: what is being checked is a
     * property of the state object, and running it on the JVM keeps it in the suite that runs on
     * every commit.
     */
    private fun notificationsFor(
        policy: SnapshotMutationPolicy<CustomThemeData>,
        first: CustomThemeData,
        next: CustomThemeData,
    ): Int {
        val state = mutableStateOf(first, policy)
        var writes = 0
        val handle = Snapshot.registerApplyObserver { changed, _ ->
            if (changed.contains(state)) writes++
        }
        try {
            Snapshot.withMutableSnapshot { state.value = next }
        } finally {
            handle.dispose()
        }
        return writes
    }
}
