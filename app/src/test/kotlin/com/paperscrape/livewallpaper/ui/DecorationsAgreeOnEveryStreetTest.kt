package com.paperscrape.livewallpaper.ui

import com.paperscrape.livewallpaper.engine.CustomThemeData
import com.paperscrape.livewallpaper.engine.CustomThemeRegistry
import com.paperscrape.livewallpaper.engine.DecorationExclusion
import com.paperscrape.livewallpaper.engine.DistantHousesConfig
import com.paperscrape.livewallpaper.engine.SceneCustomization
import com.paperscrape.livewallpaper.engine.SceneObjectCatalog
import com.paperscrape.livewallpaper.engine.SceneObjectLayout
import com.paperscrape.livewallpaper.engine.StorefrontStreet
import com.paperscrape.livewallpaper.engine.ThemeCatalog
import com.paperscrape.livewallpaper.engine.defaultCustomizationFor
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * **The settings, the setters and the reading of stored data agree on the Halloween and the Christmas decorations, on
 * every street** (`DESIGN_NOTES.md` §12, decisions 40 and 77): a switch the settings let the user turn on reads on after
 * the tap, and no switch is held off by one that cannot be turned off.
 *
 * The streets are the catalogue's: `random:<n>` built on the JVM with no accent colour (a second car's colour is Android's
 * HSV, so the ones that need it are passed over), and the twelve built-in ones.
 */
class DecorationsAgreeOnEveryStreetTest {

    @Before
    fun clearRegistry() = CustomThemeRegistry.update(CustomThemeData.EMPTY)

    /** The street of `random:<n>` as the catalogue lays it, or null where the JVM cannot build it. */
    private fun randomStreet(n: Int): SceneObjectLayout? = runCatching { SceneObjectCatalog.layoutFor("random:$n", 0) }.getOrNull()

    private fun SceneObjectLayout.hasPlace() = staticObjects.any { StorefrontStreet.isPlace(it) }

    /** Nothing on but what the test turns on. */
    private val none = SceneCustomization.DEFAULT

    /**
     * A decoration switch that takes a tap reads on after it: the tap written as the store's setters write it (a Christmas
     * one with the four switches as they read and then itself, the Halloween one alone) and read back as the store reads
     * it ([DecorationExclusion.resolved]). A switch greyed by the other side leaves that side on and free to go off. And
     * with *Show Buildings* on, *Lights on the buildings* and the Halloween decorations never read on together: the
     * gallery card draws its three shops by that switch, on any street. Every side stored on and off, the houses, the
     * buildings, the trees and the houses on the mountains on and off, on a theme that ships neither side and on the
     * Halloween theme.
     */
    @Test
    fun `on every street a decoration switch that takes a tap reads on after it, and none is held by one that cannot go off`() {
        val streets = (1..80).mapNotNull { n -> randomStreet(n)?.let { "random:$n" to it } } +
            ThemeCatalog.ALL.map { it.id to SceneObjectCatalog.layoutFor(it.id, it.accentColor) }
        val noPlace = streets.count { (_, s) -> !s.hasPlace() }
        assertTrue("too few streets with no place: $noPlace", noPlace >= 5)
        val shipping = listOf(none, defaultCustomizationFor("halloween"))
        for ((id, street) in streets) for (bits in 0 until 256) for (defaults in shipping) {
            val stored = none.copy(
                houses = none.houses.copy(visible = bits and 1 != 0, density = 0.65f),
                buildings = none.buildings.copy(visible = bits and 2 != 0, density = 0.65f),
                trees = none.trees.copy(visible = bits and 4 != 0, density = 0.65f),
                distantHouses = DistantHousesConfig(visible = bits and 8 != 0, density = 0.5f),
                christmasBuildingLightsEnabled = bits and 16 != 0,
                christmasTreeLightsEnabled = bits and 32 != 0,
                christmasFirsEnabled = bits and 64 != 0,
                halloweenDecorationsEnabled = bits and 128 != 0,
            )
            val what = "$id, $bits, ${if (defaults === none) "a theme that ships neither" else "the Halloween theme"}"
            fun christmasRows(c: SceneCustomization) = listOf(
                SettingsUiModel.christmasBuildingDecoration(c.christmasBuildingLightsEnabled, c, street),
                SettingsUiModel.christmasTreeDecoration(c.christmasTreeLightsEnabled, c, street),
                SettingsUiModel.christmasFirs(c.christmasFirsEnabled, c, street),
            )
            val read = DecorationExclusion.resolved(stored, defaults, street)
            val christmas = christmasRows(read)
            val halloween = SettingsUiModel.halloweenDecorations(read, street)
            for ((k, row) in christmas.withIndex()) {
                if (!row.interactive || row.shownOn) continue
                val tapped = when (k) {
                    0 -> read.copy(christmasBuildingLightsEnabled = true)
                    1 -> read.copy(christmasTreeLightsEnabled = true)
                    else -> read.copy(christmasFirsEnabled = true)
                }
                val after = DecorationExclusion.resolved(tapped, defaults, street)
                assertTrue("$what: Christmas switch $k took the tap and reads off", christmasRows(after)[k].shownOn)
            }
            if (halloween.interactive && !halloween.shownOn) {
                val after = DecorationExclusion.resolved(stored.copy(halloweenDecorationsEnabled = true), defaults, street)
                assertTrue("$what: the Halloween decorations took the tap and read off", SettingsUiModel.halloweenDecorations(after, street).shownOn)
            }
            if (christmas.any { it.blockedByTheOther }) {
                assertTrue("$what: held by Halloween decorations that cannot go off", halloween.shownOn && halloween.interactive)
            }
            if (halloween.blockedByTheOther) {
                assertTrue("$what: held by a Christmas switch that cannot go off", christmas.any { it.shownOn && it.interactive })
            }
            if (read.buildings.visible) {
                assertFalse("$what: both on the card's shops", read.halloweenDecorationsEnabled && read.christmasBuildingLightsEnabled)
            }
        }
    }
}
