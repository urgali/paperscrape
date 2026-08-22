package com.paperscrape.livewallpaper.prefs

import com.paperscrape.livewallpaper.prefs.PrefsRecovery.recoveringFromReadErrors
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException

/**
 * The read-error half of [PrefsRecovery], where the rule is *not* the same as the corruption half.
 *
 * A corrupt file is unrecoverable and gets overwritten once; an I/O failure may be a device that
 * was busy for a moment, so it is answered with defaults for that emission and nothing on disk is
 * touched. The third case -- an exception nobody predicted -- has to keep propagating, because
 * silently running on defaults after an unknown failure is how a bug becomes invisible.
 */
class PrefsRecoveryTest {

    private val key = booleanPreferencesKey("flag")

    @Test
    fun healthyReadsPassThroughUnchanged() {
        val prefs = mutablePreferencesOf(key to true)
        val seen = runBlocking { flowOf(prefs).recoveringFromReadErrors().toList() }

        assertEquals(listOf(prefs), seen)
    }

    @Test
    fun ioFailureIsAnsweredWithDefaultsRatherThanACrash() {
        val failing = flow<Preferences> {
            throw IOException("storage briefly unavailable")
        }

        val seen = runBlocking { failing.recoveringFromReadErrors().toList() }

        assertEquals(listOf(emptyPreferences()), seen)
        assertNull(seen.single()[key])
    }

    /** Subclasses too -- the failures DataStore actually reports are rarely plain IOException. */
    @Test
    fun ioSubclassesAreAnsweredTheSameWay() {
        val failing = flow<Preferences> {
            throw FileNotFoundException("no such file")
        }

        assertEquals(
            listOf(emptyPreferences()),
            runBlocking { failing.recoveringFromReadErrors().toList() },
        )
    }

    @Test
    fun valuesAlreadyEmittedBeforeTheFailureAreKept() {
        val prefs = mutablePreferencesOf(key to true)
        val failing = flow {
            emit(prefs)
            throw IOException("gone mid-stream")
        }

        val seen = runBlocking { failing.recoveringFromReadErrors().toList() }

        assertEquals(listOf(prefs, emptyPreferences()), seen)
    }

    @Test
    fun anythingThatIsNotAnIoFailureKeepsPropagating() {
        val failing = flow<Preferences> {
            throw IllegalStateException("something nobody predicted")
        }

        val thrown = try {
            runBlocking { failing.recoveringFromReadErrors().toList() }
            null
        } catch (e: Throwable) {
            e
        }

        assertTrue("expected the unknown failure to propagate, got $thrown", thrown is IllegalStateException)
    }
}
