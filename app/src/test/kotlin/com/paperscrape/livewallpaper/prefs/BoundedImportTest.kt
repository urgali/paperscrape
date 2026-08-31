package com.paperscrape.livewallpaper.prefs

import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** BCK-04: an import reads a document, not whatever file was picked. */
class BoundedImportTest {

    @Test
    fun `a document inside the bound comes back whole`() {
        val text = "{\"kind\":\"paperscrape.backup\"}"
        assertEquals(text, BoundedImport.readBounded(StringReader(text), limit = 1000))
    }

    @Test
    fun `a document exactly at the bound is accepted`() {
        // The boundary in the direction that must not reject a legitimate file.
        val text = "x".repeat(64)
        assertEquals(text, BoundedImport.readBounded(StringReader(text), limit = 64))
    }

    @Test
    fun `one character past the bound is refused`() {
        assertNull(BoundedImport.readBounded(StringReader("x".repeat(65)), limit = 64))
    }

    @Test
    fun `a very long file is refused without being held in memory`() {
        // The case the finding is about: the reader stops as soon as the bound is passed, so the
        // builder never grows past it however long the source is. A 50 MB source with a 1 KB bound
        // must cost 1 KB, and the test would time out or die rather than pass if it did not.
        val huge = object : java.io.Reader() {
            private var left = 50_000_000
            override fun read(cbuf: CharArray, off: Int, len: Int): Int {
                if (left <= 0) return -1
                val n = minOf(len, left)
                java.util.Arrays.fill(cbuf, off, off + n, 'x')
                left -= n
                return n
            }
            override fun close() = Unit
        }
        assertNull(BoundedImport.readBounded(huge, limit = 1024))
    }

    @Test
    fun `an empty document reads as empty rather than as a failure`() {
        assertEquals("", BoundedImport.readBounded(StringReader(""), limit = 64))
    }

    @Test
    fun `the shipped bound is far above a real backup and far below a runaway file`() {
        // Stated so the number is a decision rather than a magic constant: hundreds of kilobytes is
        // what this app writes, and four million characters is the refusal point.
        assertEquals(4_000_000, BoundedImport.MAX_IMPORT_CHARS)
    }
}
