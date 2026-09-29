package com.paperscrape.livewallpaper.prefs

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.paperscrape.livewallpaper.engine.CUSTOM_THEME_SCHEMA_VERSION
import com.paperscrape.livewallpaper.engine.CustomThemeData
import com.paperscrape.livewallpaper.engine.CustomThemeEntry
import com.paperscrape.livewallpaper.engine.CustomThemeRegistry
import com.paperscrape.livewallpaper.engine.SceneObjectLayout
import com.paperscrape.livewallpaper.engine.SceneObjectType
import com.paperscrape.livewallpaper.engine.StaticSceneObject
import com.paperscrape.livewallpaper.engine.ThemeCatalog
import com.paperscrape.livewallpaper.engine.defaultCustomizationFor
import com.paperscrape.livewallpaper.engine.toJson
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * "Rename" against the real store (v5.9B, inventory row I-01): the name the user types is the name
 * every screen reads back, and it is the name in the file.
 *
 * `SavedThemeNameTest` holds the reading rule on the JVM. This drives the real
 * `CustomThemeStore.renameCustomTheme` through DataStore and Android's own `org.json`, then reads
 * the answer the two ways a screen does -- the entry's theme (the gallery card) and
 * `ThemeCatalog.byId` (the settings preview, the Theme row, Seasons, World & scene) -- and off the
 * file itself. Red on v5.9A, where the rename wrote `name` alone.
 */
@RunWith(AndroidJUnit4::class)
class SavedThemeRenameStoreTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val store get() = CustomThemeStore(context)
    private val file get() = File(context.filesDir, "datastore/$CUSTOM_THEME_STORE_NAME.preferences_pb")

    @Before fun clean() = runBlocking { wipe() }
    @After fun tidy() = runBlocking { wipe() }

    private suspend fun wipe() {
        store.replaceAll(CustomThemeData.EMPTY)
        CustomThemeRegistry.update(store.dataFlow.first())
    }

    @Test
    fun aRenameIsTheNameEveryScreenReadsAndTheNameOnDisk() = runBlocking {
        val theme = entry("custom:1002", "Night walk")
        store.replaceAllJson(document(theme.toJson().toString()))

        store.renameCustomTheme(theme.id, "Night walk 2")

        val read = store.dataFlow.first()
        CustomThemeRegistry.update(read)
        val entry = read.customThemes.single()
        assertEquals("Night walk 2", entry.name)
        assertEquals("the gallery card reads the entry's theme", "Night walk 2", entry.theme.displayName)
        assertEquals("the settings screens read ThemeCatalog.byId", "Night walk 2", ThemeCatalog.byId(theme.id).displayName)
        val onDisk = String(file.readBytes(), Charsets.UTF_8)
        assertTrue("the new name is not in the theme on disk", onDisk.contains("\"displayName\":\"Night walk 2\""))
        assertFalse("the old name is still in the theme on disk", onDisk.contains("\"displayName\":\"Night walk\""))
    }

    /** A theme renamed by an earlier build, as it sits on the phone today: read with its new name. */
    @Test
    fun aThemeRenamedByAnEarlierBuildIsShownWithTheNameTheUserWrote() = runBlocking {
        val renamedBefore = entry("custom:1002", "Night walk").toJson().apply { put("name", "Night walk 2") }
        assertEquals("Night walk", renamedBefore.getJSONObject("theme").getString("displayName"))
        store.replaceAllJson(document(renamedBefore.toString()))

        val read = store.dataFlow.first()
        CustomThemeRegistry.update(read)
        assertEquals("Night walk 2", read.customThemes.single().theme.displayName)
        assertEquals("Night walk 2", ThemeCatalog.byId("custom:1002").displayName)
    }

    private fun document(vararg themes: String) =
        "{\"schemaVersion\":$CUSTOM_THEME_SCHEMA_VERSION,\"overrides\":{},\"customThemes\":[${themes.joinToString(",")}]}"

    private fun entry(id: String, name: String) = CustomThemeEntry(
        id = id,
        name = name,
        theme = ThemeCatalog.ALL.first { it.id == "autumn" }.copy(id = id, displayName = name),
        layout = SceneObjectLayout(
            staticObjects = listOf(StaticSceneObject(SceneObjectType.HOUSE, depthFraction = 0.12f, tileFractionX = 0.2f)),
            cars = emptyList(),
        ),
        customization = defaultCustomizationFor("autumn"),
    )
}
