package com.paperscrape.livewallpaper.engine

import android.content.ComponentCallbacks2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ARC-10 and ARC-11: one change is one rebuild, and a hint does not cost more than it saves.
 */
class RegistryAndTrimPolicyTest {

    // ------------------------------------------------------------------ ARC-10

    /**
     * Two collectors of the same store flow used to bump the generation twice for one write.
     *
     * The engine collects it and so does the settings screen, and the two coexist whenever a user
     * edits a theme with the picker's preview behind them -- which is the normal case, not a corner
     * one. `SceneObjectRenderer` rebuilds when the generation moves, so it rebuilt twice.
     */
    @Test
    fun `publishing the same data twice is one generation`() {
        val data = CustomThemeData.EMPTY
        CustomThemeRegistry.update(data)
        val after = CustomThemeRegistry.generation()
        CustomThemeRegistry.update(data)
        CustomThemeRegistry.update(data)
        assertEquals("identical data must not bump the generation", after, CustomThemeRegistry.generation())
    }

    @Test
    fun `publishing different data does bump the generation`() {
        val theme = ThemeCatalog.byId("sunset")
        val entry = CustomThemeEntry(
            id = "sunset",
            name = theme.displayName,
            theme = theme,
            layout = SceneObjectCatalog.layoutFor("sunset", theme.accentColor),
            customization = defaultCustomizationFor("sunset"),
        )
        CustomThemeRegistry.update(CustomThemeData.EMPTY)
        val before = CustomThemeRegistry.generation()
        CustomThemeRegistry.update(CustomThemeData(overrides = mapOf("sunset" to entry), customThemes = emptyList()))
        assertTrue("a real change must be seen", CustomThemeRegistry.generation() > before)
        // And the new data must actually be published, not just counted.
        assertEquals(setOf("sunset"), CustomThemeRegistry.overriddenBuiltinIds())
        CustomThemeRegistry.update(CustomThemeData.EMPTY)
    }

    // ------------------------------------------------------------------ ARC-11

    /**
     * A visible engine under a *low* memory hint keeps its atlas.
     *
     * The GPU side is all or nothing -- there is no LRU over the atlas -- so honouring
     * `TRIM_TO_HALF` meant dropping every uploaded texture and re-uploading them on the next frame.
     * That is a spike paid for memory the process gives back immediately, which is not what
     * `RUNNING_LOW` is asking for. `RUNNING_CRITICAL` is, and still drops it.
     */
    @Test
    fun `a low hint on a visible engine does not drop the atlas`() {
        val action = MemoryPressurePolicy.actionFor(
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            anyEngineVisible = true,
        )
        assertEquals(TrimAction.TRIM_TO_HALF, action)
        assertFalse(
            "a half-trim on a visible engine must not clear the GPU atlas",
            MemoryPressurePolicy.dropsGpuTextures(action),
        )
    }

    @Test
    fun `a critical hint still drops the atlas`() {
        val visible = MemoryPressurePolicy.actionFor(
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            anyEngineVisible = true,
        )
        assertEquals(TrimAction.TRIM_TO_QUARTER, visible)
        assertTrue(MemoryPressurePolicy.dropsGpuTextures(visible))
    }

    @Test
    fun `releasing everything drops the atlas and keeping everything does not`() {
        assertTrue(MemoryPressurePolicy.dropsGpuTextures(TrimAction.RELEASE_ALL))
        assertFalse(MemoryPressurePolicy.dropsGpuTextures(TrimAction.KEEP_ALL))
    }

    @Test
    fun `an invisible engine gives everything back at every real pressure level`() {
        for (level in listOf(
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
        )) {
            val action = MemoryPressurePolicy.actionFor(level, anyEngineVisible = false)
            assertEquals("level $level with nothing drawing", TrimAction.RELEASE_ALL, action)
            assertTrue(MemoryPressurePolicy.dropsGpuTextures(action))
        }
    }
}
