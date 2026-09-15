package com.paperscrape.livewallpaper.ui

import java.time.MonthDay

/**
 * Typing a day-and-month as digits: `03/05`, `9/9`, `31/12`.
 *
 * ### Why this exists rather than a slider
 *
 * v5.1's first round put the two ends of a calendar window on sliders over all 366 month-days.
 * Measured on the BV6600, that is **366 positions across a 632-pixel control — 1.7 pixels per day**.
 * A fingertip does not select a date on that; it selects a week. The control was not badly tuned,
 * it was the wrong control: a date is a value you know, and a value you know is typed.
 *
 * Easter's two sliders stay, and deliberately: those are **lengths of 0 to 7 days**, eight
 * positions across the same track, where a slider is hit first time.
 *
 * ### Day first, and said twice
 *
 * The app's interface is English and `03/05` alone is ambiguous — three days into May to a British
 * reader, the fifth of March to an American one. Two defences, together: the field's own label
 * names the order, and the line underneath spells the dates out in words, so whatever the reader
 * assumed, they see what the app understood.
 *
 * ### Validity is [MonthDay]'s answer, not a second opinion
 *
 * `MonthDay.of` already rejects 30 February, 31 April, day 0 and month 13, and already accepts
 * **29 February**, which is not a curiosity here: it is the shipped last day of winter. Anything
 * that re-derived those rules would be a second implementation to keep in step with the first.
 */

/**
 * Filters what a text field is allowed to hold while it is being typed, and inserts the separator.
 *
 * Digits and one `/`, at most two digits each side. The slash appears **on its own** when a third
 * digit arrives without one — `035` becomes `03/5` — and can equally be typed, because a numeric
 * keyboard may or may not offer it and the user should not have to find out which. It can also be
 * deleted: backspacing `03/` to `03` leaves `03`, and does not immediately put the slash back.
 *
 * This is a filter and not a validator. It says nothing about whether the result is a date.
 */
internal fun sanitiseDateEntry(raw: String): String {
    val beforeSlash = StringBuilder()
    val afterSlash = StringBuilder()
    var slashed = false
    for (character in raw) {
        if (character == '/') {
            // A leading slash is meaningless, and a second one is a typo; both are dropped.
            if (!slashed && beforeSlash.isNotEmpty()) slashed = true
            continue
        }
        if (!character.isDigit()) continue
        when {
            !slashed && beforeSlash.length < 2 -> beforeSlash.append(character)
            // The third digit is what tells us the day is finished, so it carries the separator.
            !slashed -> { slashed = true; afterSlash.append(character) }
            afterSlash.length < 2 -> afterSlash.append(character)
        }
    }
    return if (slashed) "$beforeSlash/$afterSlash" else beforeSlash.toString()
}

/** Shaped like a date: two numbers with a separator between them. Says nothing about validity. */
internal fun isDateEntryComplete(text: String): Boolean =
    COMPLETE_ENTRY.matches(text)

private val COMPLETE_ENTRY = Regex("""\d{1,2}/\d{1,2}""")

/**
 * True when [text] is not a date **and cannot become one** by typing more digits. This is the only
 * condition that turns the field red.
 *
 * ### Why "complete" was the wrong test, and how it was caught
 *
 * The first version of this file reddened any entry that was *shaped* like a date and did not
 * parse. That looks right and is not: typing `09/09` passes through `09/0`, which is shaped like a
 * date, is not one — there is no month 0 — and is one keystroke from being perfectly valid. The
 * field would have flashed red in the middle of typing a correct date. `DateEntryTest`'s
 * every-prefix walk found it before the device did.
 *
 * So the question is not "is this finished" but **"is this beyond saving"**. `30/02` is: both parts
 * are full and no further digit can help. `09/0` is not. `32` is beyond saving on its own, because
 * no month makes a 32nd day, and saying so immediately is help rather than noise.
 *
 * Answered by enumeration over what the filter can actually produce, rather than by reasoning about
 * which partial values are extendable — the reasoning is what got it wrong the first time.
 */
internal fun isDateEntryRejected(text: String): Boolean {
    if (text.isEmpty()) return false
    if (parseDateEntry(text) != null) return false
    return reachableEntries(text).none { parseDateEntry(it) != null }
}

/**
 * Every entry reachable from [text] by typing more digits, [text] included.
 *
 * The filter caps an entry at four digits, so four more keystrokes reach everything, and the
 * visited set collapses the walk to a few dozen distinct strings.
 */
private fun reachableEntries(text: String): Set<String> {
    val seen = LinkedHashSet<String>()
    fun walk(current: String, remaining: Int) {
        if (!seen.add(current) || remaining == 0) return
        for (digit in '0'..'9') walk(sanitiseDateEntry(current + digit), remaining - 1)
    }
    walk(text, 4)
    return seen
}

/**
 * The [MonthDay] [text] names, or `null` if it does not name one.
 *
 * Day first, month second. `null` covers both "not finished" and "finished and not a date"; the
 * caller separates them with [isDateEntryComplete], because they deserve different treatment on
 * screen and the same treatment for Save.
 */
internal fun parseDateEntry(text: String): MonthDay? {
    if (!isDateEntryComplete(text)) return null
    val parts = text.split('/')
    val day = parts[0].toIntOrNull() ?: return null
    val month = parts[1].toIntOrNull() ?: return null
    // MonthDay.of is the whole validator: it knows April has 30 days and that 29 February exists.
    return runCatching { MonthDay.of(month, day) }.getOrNull()
}

/** A [MonthDay] as the field shows it when the screen opens: zero-padded, day first. */
internal fun formatDateEntry(day: MonthDay): String =
    "%02d/%02d".format(day.dayOfMonth, day.monthValue)
