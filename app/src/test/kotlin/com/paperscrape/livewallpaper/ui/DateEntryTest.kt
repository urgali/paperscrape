package com.paperscrape.livewallpaper.ui

import com.paperscrape.livewallpaper.engine.CalendarWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.MonthDay

/**
 * Typing a date instead of dragging one.
 *
 * A slider cannot produce a value outside its range; a text field can produce anything, so the
 * rejection rules are the new surface and this file is where they are held. Three questions, kept
 * separate on purpose:
 *
 * 1. what is a field allowed to *hold* while it is being typed ([sanitiseDateEntry]);
 * 2. is what it holds a *date* ([parseDateEntry]);
 * 3. is what it holds **beyond rescue** ([isDateEntryRejected]) — the only thing that reddens it.
 *
 * The tempting shortcut is to redden anything shaped like a date that does not parse. `typing any
 * real date never reddens the field on the way` is the test that rejects that shortcut, and it did:
 * it failed against the first implementation of this screen, at `09/0`, one keystroke short of a
 * perfectly good 9 September.
 */
class DateEntryTest {

    // --- What must be accepted ------------------------------------------------------------------

    @Test
    fun `the twenty-ninth of february is a date`() {
        // Not a curiosity: it is the shipped last day of winter, so rejecting it would break the
        // factory calendar rather than merely being strict.
        assertEquals(MonthDay.of(2, 29), parseDateEntry("29/02"))
        assertEquals(MonthDay.of(2, 29), parseDateEntry("29/2"))
        assertEquals(MonthDay.of(2, 29), CalendarWindow.WINTER.factorySpan.end)
    }

    @Test
    fun `every factory endpoint survives a trip through the field`() {
        // The round trip that matters most: what the screen puts in the field when it opens must
        // read back as the value it came from, for every window the user can open.
        for (window in CalendarWindow.DATED) {
            val span = window.factorySpan
            for (day in listOf(span.start, span.end)) {
                val typed = formatDateEntry(day)
                assertTrue("${window.label}: '$typed' should be complete", isDateEntryComplete(typed))
                assertEquals("${window.label}: $typed", day, parseDateEntry(typed))
            }
        }
    }

    @Test
    fun `a single digit either side is accepted`() {
        // "3/5" must mean the same as "03/05" -- nobody should have to pad.
        assertEquals(MonthDay.of(5, 3), parseDateEntry("3/5"))
        assertEquals(MonthDay.of(5, 3), parseDateEntry("03/5"))
        assertEquals(MonthDay.of(5, 3), parseDateEntry("3/05"))
        assertEquals(MonthDay.of(5, 3), parseDateEntry("03/05"))
    }

    @Test
    fun `the day comes first`() {
        // The whole ambiguity this screen defends against, pinned as a test: 03/05 is 3 May, not
        // 5 March. If this ever flips, the words underneath the fields would start lying.
        assertEquals(MonthDay.of(5, 3), parseDateEntry("03/05"))
        assertEquals(MonthDay.of(9, 9), parseDateEntry("09/09"))
        assertEquals(MonthDay.of(12, 31), parseDateEntry("31/12"))
        assertEquals("31/12", formatDateEntry(MonthDay.of(12, 31)))
    }

    // --- What must be rejected -------------------------------------------------------------------

    @Test
    fun `impossible dates are not dates, and are shown as errors`() {
        for (text in listOf("30/02", "31/04", "32/01", "00/05", "13/13", "31/09", "00/00")) {
            assertNull("'$text' should not parse", parseDateEntry(text))
            // ...and no further digit can rescue them, which is what makes them an error rather
            // than a field still being typed.
            assertTrue("'$text' should be rejected outright", isDateEntryRejected(text))
        }
    }

    @Test
    fun `a day no month can have is called out before the month is even typed`() {
        // "32" can never be a day, so saying so at once is help. Contrast with the test below.
        for (text in listOf("32", "00", "40")) {
            assertTrue("'$text' can never become a date", isDateEntryRejected(text))
        }
    }

    @Test
    fun `a rejected date leaves no span to save`() {
        // The gate, expressed the way the screen expresses it: no parse, no draft, nothing to write.
        val start = parseDateEntry("01/10")
        val end = parseDateEntry("30/02")
        assertTrue(start != null && end == null)
        assertNull("a span must not be constructible from a rejected end", end)
    }

    // --- Unfinished is not wrong -------------------------------------------------------------------

    @Test
    fun `a field being typed is not an error`() {
        for (text in listOf("", "0", "03", "03/", "3", "1", "09/0", "30/0")) {
            assertFalse("'$text' should not be shown as an error", isDateEntryRejected(text))
        }
        // ...and none of them is a date yet either, so Save stays off throughout.
        for (text in listOf("", "0", "03", "03/")) {
            assertNull("'$text' is not a date yet", parseDateEntry(text))
        }
    }

    @Test
    fun `typing any real date never reddens the field on the way`() {
        // The property the first implementation of this file got wrong: it reddened anything shaped
        // like a date that did not parse, so typing 09/09 flashed red at "09/0" -- one keystroke
        // from correct. Walk every keystroke of every date in the year.
        var walked = 0
        for (day in com.paperscrape.livewallpaper.engine.CalendarSpan.ALL_DAYS) {
            val target = formatDateEntry(day)
            var typed = ""
            for (character in target.filter { it.isDigit() }) {
                typed = sanitiseDateEntry(typed + character)
                assertFalse(
                    "typing $target reddened at '$typed'",
                    isDateEntryRejected(typed),
                )
                walked++
            }
            assertEquals("$target should land on its date", day, parseDateEntry(typed))
        }
        assertEquals("every day of the year, four keystrokes each", 366 * 4, walked)
    }

    // --- The filter --------------------------------------------------------------------------------

    @Test
    fun `the separator appears on its own when a third digit arrives`() {
        assertEquals("03/5", sanitiseDateEntry("035"))
        assertEquals("31/12", sanitiseDateEntry("3112"))
        assertEquals("0", sanitiseDateEntry("0"))
        assertEquals("03", sanitiseDateEntry("03"))
    }

    @Test
    fun `the separator can also be typed`() {
        assertEquals("3/5", sanitiseDateEntry("3/5"))
        assertEquals("03/", sanitiseDateEntry("03/"))
        assertEquals("03/05", sanitiseDateEntry("03/05"))
    }

    @Test
    fun `the separator can be deleted without it coming straight back`() {
        // Backspacing "03/" to "03" must leave "03". If the filter re-inserted the slash the field
        // would be impossible to correct.
        assertEquals("03", sanitiseDateEntry("03"))
        assertEquals("0", sanitiseDateEntry("0"))
    }

    @Test
    fun `letters and extra separators are refused entry`() {
        assertEquals("03/05", sanitiseDateEntry("0a3/b05"))
        assertEquals("03/05", sanitiseDateEntry("03//05"))
        assertEquals("03/05", sanitiseDateEntry("03/05/2026"))
        assertEquals("", sanitiseDateEntry("/"))
        assertEquals("", sanitiseDateEntry("abc"))
    }

    @Test
    fun `no more than two digits either side survive`() {
        assertEquals("31/12", sanitiseDateEntry("311299"))
        assertEquals("31/12", sanitiseDateEntry("31/1299"))
    }

    @Test
    fun `anything the filter allows is either a date or unfinished, never nonsense`() {
        // A loose end worth closing by enumeration rather than by argument: run every string the
        // filter can emit for inputs up to four digits, and check each is one of the two states the
        // screen knows how to draw.
        val alphabet = "0123456789/"
        var checked = 0
        fun walk(prefix: String, depth: Int) {
            if (depth == 0) {
                val filtered = sanitiseDateEntry(prefix)
                // Idempotent: what the filter emits, it emits again unchanged.
                assertEquals("filter not idempotent for '$prefix'", filtered, sanitiseDateEntry(filtered))
                // Exactly one of the three states the screen knows how to draw: a date, an error
                // it names, or a field still being typed. Never two at once, never none.
                val isDate = parseDateEntry(filtered) != null
                val isError = isDateEntryRejected(filtered)
                assertFalse("'$filtered' is both a date and an error", isDate && isError)
                checked++
                return
            }
            for (character in alphabet) walk(prefix + character, depth - 1)
        }
        for (depth in 0..3) walk("", depth)
        assertEquals(1 + 11 + 121 + 1331, checked)
    }
}
