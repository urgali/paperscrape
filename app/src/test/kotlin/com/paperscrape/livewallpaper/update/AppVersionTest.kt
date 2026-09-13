package com.paperscrape.livewallpaper.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Version comparison for the update check.
 *
 * `UpdateChecker` read a release tag with `tagName.removePrefix("v").toIntOrNull()`, which was
 * correct while a tag was a bare integer and returns `null` for every tag the semver scheme
 * produces — so the updater would have silently reported "no update" forever. These are the cases
 * that had to start working, plus the ones that have to keep failing.
 */
class AppVersionTest {

    private fun v(raw: String) = requireNotNull(AppVersion.parse(raw)) { "expected $raw to parse" }

    @Test
    fun `a newer minor is an update`() {
        assertTrue(v("1.1") > v("1.0"))
    }

    @Test
    fun `the same version is not an update`() {
        assertEquals(v("1.1"), v("1.1"))
        assertTrue(v("1.1") <= v("1.1"))
    }

    @Test
    fun `a newer major is an update`() {
        assertTrue(v("2.0") > v("1.1"))
    }

    @Test
    fun `major beats minor, so 2 dot 0 is newer than 1 dot 9`() {
        // The case a string comparison or a naive parse gets wrong: "2.0" sorts before "1.9" as
        // text, and 2 + 0 is less than 1 + 9 as a sum. Only comparing the fields in order works.
        assertTrue(v("2.0") > v("1.9"))
        assertTrue(v("1.9") < v("2.0"))
    }

    @Test
    fun `an older release is not an update`() {
        assertTrue(v("1.9") <= v("2.0"))
    }

    @Test
    fun `a tag that is not MAJOR dot MINOR is ignored`() {
        // Bare integers matter most here. This repository's pre-release history used them --
        // `v73`, `v74` -- and reading one as major 73 would offer every user an "update" to a
        // build that predates the release scheme entirely.
        assertNull(AppVersion.parse("v73"))
        assertNull(AppVersion.parse("v1"))
        assertNull(AppVersion.parse("v1.1.2"))
        assertNull(AppVersion.parse("v1.1-beta.1"))
        assertNull(AppVersion.parse("nightly"))
        assertNull(AppVersion.parse(""))
        assertNull(AppVersion.parse("v1.x"))
    }

    @Test
    fun `the same parser reads a tag and a versionName`() {
        // One parser for both sides of the comparison: the tag arrives from GitHub with a `v` and
        // the installed version from BuildConfig without one, and the two must agree.
        assertEquals(AppVersion.parse("v1.0"), AppVersion.parse("1.0"))
        assertEquals("1.0", v("v1.0").toString())
    }

    /**
     * The major bump out of a two-digit minor: **v4.31 -> v5.0**, the renumber v5.0 made for the
     * neighbourhood redraw.
     *
     * This is the shape no earlier case covered. `2.0 > 1.9` already pins "major beats minor", but
     * both sides there are single digits and the *sum* of the fields still happens to order them
     * correctly. Here it does not: 5 + 0 is five and 4 + 31 is thirty-five, so a parser that added
     * its fields, or weighted the minor at all, would call 5.0 **older** than 4.31 and every
     * installed user would be told there was nothing to update to.
     *
     * `versionCode` is deliberately not in this test. It stayed at 63 across the rename, which is
     * correct and is none of this parser's business -- see `AppVersion`'s own KDoc.
     */
    @Test
    fun `five dot zero is newer than four dot thirty-one`() {
        assertTrue(v("5.0") > v("4.31"))
        assertTrue(v("4.31") < v("5.0"))
        assertEquals(v("5.0"), AppVersion.parse("v5.0"))
        assertEquals("5.0", v("v5.0").toString())
        assertEquals(5, v("5.0").major)
        assertEquals(0, v("5.0").minor)
    }

    /**
     * The first release whose minor number is two digits. `AppVersion` compares parsed integers
     * rather than strings, so 2.10 is correctly newer than 2.9 -- a string comparison would have
     * read "2.10" as older than "2.9" and silently stopped offering updates.
     */
    @Test
    fun `a two-digit minor is newer than a single-digit one`() {
        val v29 = AppVersion.parse("2.9")!!
        val v210 = AppVersion.parse("v2.10")!!
        assertTrue(v210 > v29)
        assertTrue(v29 < v210)
        assertEquals(10, v210.minor)
    }
}
