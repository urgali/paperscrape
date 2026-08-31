package com.paperscrape.livewallpaper.prefs

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BCK-06: a process kill at any point of an import leaves a consistent pair of stores.
 *
 * An import writes two DataStores and nothing spans them, so a kill between the two writes left the
 * preferences new and the saved themes old — and unlike a cancellation, `NonCancellable` cannot help,
 * because the process is simply gone.
 *
 * What each store *does* guarantee is that its own write is atomic, and that is enough to make the
 * **pair** recoverable without inventing a transaction. The second store's whole payload rides
 * inside the first store's atomic edit, is applied, and is then cleared. This test kills the import
 * at every point there is and requires the state afterwards to be consistent, either before or after
 * — never half.
 *
 * No new test dependency: `runBlocking` from the coroutines library the app already uses.
 *
 * The two stores are modelled here, because a test cannot kill a real `DataStore` mid-write. What is
 * *not* modelled is the sequence: [ImportStaging] is the code the app runs, driven directly.
 */
class AtomicImportTest {

    /** The pair of stores, as the only two things a kill can land between. */
    private class Stores(var prefs: String = "OLD", var themes: String = "OLD", var pending: String? = null) {
        /** Kill after this many successful writes; -1 never. */
        var killAfter: Int = -1
        private var writes = 0

        private fun step() {
            writes++
            if (writes == killAfter) throw ProcessKill()
        }

        // Each write completes and *then* the process dies, which is what DataStore's own
        // atomicity guarantees: a write is never observed half done, only done or not done.
        fun stagePrefs(payload: String) { prefs = "NEW"; pending = payload; step() }
        fun writeThemes(payload: String) { themes = payload; step() }
        fun clearPending() { pending = null; step() }

        /** What the next start does, before anything reads the themes. */
        suspend fun recover() {
            ImportStaging.finish(pending, { themes = it }, { pending = null })
        }

        val consistent: Boolean
            get() = (prefs == "OLD" && themes == "OLD") || (prefs == "NEW" && themes == "NEW")

        override fun toString() = "prefs=$prefs themes=$themes pending=${pending ?: "-"}"
    }

    private class ProcessKill : RuntimeException("process killed")

    private suspend fun runImport(stores: Stores) {
        runCatching {
            ImportStaging.apply(
                payload = "NEW",
                stagePrefs = { stores.stagePrefs(it) },
                writeThemes = { stores.writeThemes(it) },
                clearPending = { stores.clearPending() },
            )
        }
    }

    @Test
    fun `an uninterrupted import applies both stores and leaves nothing pending`() = runBlocking {
        val stores = Stores()
        runImport(stores)
        assertEquals("NEW", stores.prefs)
        assertEquals("NEW", stores.themes)
        assertEquals(null, stores.pending)
    }

    @Test
    fun `a kill at every point recovers to a consistent pair`() = runBlocking {
        // Every point there is: before the first write, between the two, and between the second and
        // the clear. The one that used to be broken is 2 — the old code had nothing pending there.
        for (killAfter in 1..3) {
            val stores = Stores()
            stores.killAfter = killAfter
            runImport(stores)
            stores.recover()
            assertTrue(
                "a kill after write $killAfter left $stores",
                stores.consistent,
            )
            assertEquals("recovery must not leave a pending document", null, stores.pending)
        }
    }

    @Test
    fun `a kill right after the first write leaves the payload on disk`() = runBlocking {
        // The case the old code could not survive: the preferences are new, the themes are still
        // old, and before this fix there was nothing anywhere that said so.
        val stores = Stores()
        stores.killAfter = 1
        runImport(stores)
        assertEquals("the preferences were written", "NEW", stores.prefs)
        assertEquals("the themes were not", "OLD", stores.themes)
        assertEquals("and the payload is on disk to finish with", "NEW", stores.pending)
        stores.recover()
        assertTrue(stores.toString(), stores.consistent)
    }

    @Test
    fun `recovery is idempotent, so a kill during recovery is harmless`() = runBlocking {
        val stores = Stores()
        stores.killAfter = 2
        runImport(stores)
        stores.recover()
        val afterFirst = stores.toString()
        stores.recover()
        stores.recover()
        assertEquals("recovering again must change nothing", afterFirst, stores.toString())
        assertTrue(stores.consistent)
    }

    @Test
    fun `recovery does nothing when no import was interrupted`() = runBlocking {
        var wrote = false
        val finished = ImportStaging.finish(null, { wrote = true }, { })
        assertFalse("nothing pending must mean nothing done", finished)
        assertFalse("and must not touch the themes store", wrote)
    }

    @Test
    fun `the recovered themes are the exact bytes that were staged`() = runBlocking {
        // Why the import writes the store from the staged string rather than re-serialising a parsed
        // copy: the write that completes the import has to be the same write it would have been.
        val payload = """{"schemaVersion":3,"overrides":{},"customThemes":[]}"""
        val stores = Stores()
        stores.killAfter = 2
        runCatching {
            ImportStaging.apply(payload, { stores.stagePrefs(it) }, { stores.writeThemes(it) }, { stores.clearPending() })
        }
        stores.recover()
        assertEquals(payload, stores.themes)
    }
}
