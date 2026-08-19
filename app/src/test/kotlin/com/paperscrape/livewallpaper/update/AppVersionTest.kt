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
}
