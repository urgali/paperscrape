package com.paperscrape.livewallpaper.engine

import com.paperscrape.livewallpaper.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

/**
 * The palms switch: which species the two summer themes' tree slots are drawn as.
 *
 * **Before v5.1 there was no way to have one without the other.** `SceneCustomization.configFor`
 * maps TREE and PALM_TREE to the same TREES category, so turning trees off on the Beach took the
 * palms with it and left the shore bare, and nothing turned the palms off on their own.
 *
 * What is worth pinning is that the switch is resolved **once**, on the way from the layout to the
 * renderer's object list, rather than at the blit. Five separate things ask a slot what type it is
 * -- the drawing, the size, the occlusion box, the falling leaves, the preview -- and a switch read
 * at the blit would have answered the first and left the other four drawing an oak at a palm's
 * height inside a palm's box.
 */
class PalmSpeciesTest {

    private val on = SceneCustomization.DEFAULT
    private val off = SceneCustomization.DEFAULT.copy(palmsEnabled = false)

    private fun palm(x: Float = 0.5f, depth: Float = 0.5f) =
        StaticSceneObject(SceneObjectType.PALM_TREE, depthFraction = depth, tileFractionX = x, scale = 1.3f)

    @Test
    fun `on by default, because a saved Beach theme must not come back planted with oaks`() {
        // `sceneCustomizationFromJson` fills an absent field from DEFAULT rather than from the
        // theme's own defaults, so this value is what every theme saved before v5.1 will be read
        // back with. It is the one switch in its group that starts on, and this is the reason.
        assertTrue(SceneCustomization.DEFAULT.palmsEnabled)
    }

    @Test
    fun `off turns a palm slot into a tree slot and changes nothing else about it`() {
        val slot = palm(x = 0.37f, depth = 0.81f)
        assertEquals(slot, on.palmSpeciesApplied(slot))
        val swapped = off.palmSpeciesApplied(slot)
        assertEquals(SceneObjectType.TREE, swapped.type)
        // Same place, same depth, same size. The slot is not re-dealt, re-thinned or moved: the
        // scene keeps exactly the vegetation it had, drawn as another species.
        assertEquals(slot.tileFractionX, swapped.tileFractionX, 0f)
        assertEquals(slot.depthFraction, swapped.depthFraction, 0f)
        assertEquals(slot.scale, swapped.scale, 0f)
        assertEquals(slot, swapped.copy(type = SceneObjectType.PALM_TREE))
    }

    @Test
    fun `nothing but a palm is touched, either way`() {
        for (type in SceneObjectType.entries) {
            if (type == SceneObjectType.PALM_TREE) continue
            val slot = StaticSceneObject(type, depthFraction = 0.4f, tileFractionX = 0.6f)
            assertEquals("$type moved with the palms switch on", slot, on.palmSpeciesApplied(slot))
            assertEquals("$type moved with the palms switch off", slot, off.palmSpeciesApplied(slot))
        }
    }

    @Test
    fun `with it off the palm themes hold no palm at all, and the same number of trees`() {
        for (id in listOf("beach", "desert")) {
            val objects = SceneObjectCatalog.layoutFor(id, 0xFF000000.toInt()).staticObjects
            val palms = objects.count { it.type == SceneObjectType.PALM_TREE }
            assertTrue("$id should ship palms to begin with", palms > 0)

            val swapped = objects.map { off.palmSpeciesApplied(it) }
            assertFalse("$id still holds a palm with the switch off", swapped.any { it.type == SceneObjectType.PALM_TREE })
            // The count is the point: this is a species change, not a second visibility toggle.
            assertEquals(
                "$id lost tree slots instead of changing their species",
                objects.count { it.type == SceneObjectType.TREE } + palms,
                swapped.count { it.type == SceneObjectType.TREE },
            )
        }
    }

    @Test
    fun `a swapped slot is a tree to the size table and to the occlusion pass`() {
        // The reason the swap is made on the spec rather than at the blit. `variantFor` decides
        // the drawing, and `SceneVariant` decides how tall it stands; asking them after the swap
        // is what stops an oak being drawn at a palm's 90.33 units.
        val swapped = off.palmSpeciesApplied(palm())
        val variant = SceneObjectRenderer.variantFor(swapped)
        assertEquals(SceneSpace.SceneVariant.TREE, variant)
        assertEquals(SceneSpace.SceneVariant.TREE.spriteUnitsTall, variant.spriteUnitsTall, 0f)
        assertEquals(SceneSpace.SceneVariant.PALM_TREE, SceneObjectRenderer.variantFor(on.palmSpeciesApplied(palm())))
    }

    @Test
    fun `the gallery card shows whichever species the wallpaper would draw`() {
        // A preview that went on showing palms after the switch was turned off would be the
        // gallery lying about the theme, which is what `ThemePreviewScene` exists not to do.
        for (id in listOf("beach", "desert")) {
            val theme = ThemeCatalog.byId(id)
            val base = defaultCustomizationFor(id)
            val withPalms = ThemePreviewScenes.forTheme(theme, base)
            assertTrue("$id preview has no palm by default", withPalms.contains(R.drawable.palmtree_fronds))

            val without = ThemePreviewScenes.forTheme(theme, base.copy(palmsEnabled = false))
            assertFalse("$id preview still draws a palm", without.contains(R.drawable.palmtree_fronds))
            assertFalse("$id preview still draws a palm trunk", without.contains(R.drawable.palmtree_trunk))
            assertTrue("$id preview lost its trees entirely", without.contains(R.drawable.tree_canopy))
        }
    }

    @Test
    fun `it is inert on every theme that has no palms`() {
        for (theme in ThemeCatalog.ALL) {
            if (theme.id in setOf("beach", "desert")) continue
            val base = defaultCustomizationFor(theme.id)
            assertEquals(
                "${theme.id} changed when the palms switch moved, and it has no palms to change",
                ThemePreviewScenes.forTheme(theme, base).items,
                ThemePreviewScenes.forTheme(theme, base.copy(palmsEnabled = false)).items,
            )
        }
    }

    @Test
    fun `it survives a theme round trip, and an older payload comes back with palms`() {
        val saved = SceneCustomization.DEFAULT.copy(palmsEnabled = false)
        assertFalse(sceneCustomizationFromJson(saved.toJson()).palmsEnabled)
        assertTrue(sceneCustomizationFromJson(SceneCustomization.DEFAULT.toJson()).palmsEnabled)
        // What a theme saved before v5.1 looks like: the field is simply absent.
        val older = SceneCustomization.DEFAULT.toJson().apply { remove("palmsEnabled") }
        assertFalse("the field should be gone for this to mean anything", older.has("palmsEnabled"))
        assertTrue("a pre-v5.1 theme lost its palms on upgrade", sceneCustomizationFromJson(older).palmsEnabled)
    }

    @Test
    fun `a backup carries it, so it is not lost with the phone`() {
        // The whole customization goes into the backup document, so this needs no field of its
        // own in `AppBackup` -- but "it needs no field" and "it is carried" are different claims
        // and only one of them is worth a test.
        val customizations = mapOf("beach" to SceneCustomization.DEFAULT.copy(palmsEnabled = false))
        val document = JSONObject().apply {
            for ((id, c) in customizations) put(id, c.toJson())
        }
        val read = sceneCustomizationFromJson(document.getJSONObject("beach"))
        assertFalse(read.palmsEnabled)
    }

    /** Whether any item in the scene blits [resId]. */
    private fun ThemePreviewScene.contains(resId: Int): Boolean =
        (backdrop + items + cars + ground).any { item -> item.parts.any { it.resId == resId } }
}
