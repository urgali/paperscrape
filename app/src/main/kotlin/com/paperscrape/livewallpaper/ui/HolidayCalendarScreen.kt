package com.paperscrape.livewallpaper.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Egg
import androidx.compose.material.icons.outlined.LocalFlorist
import androidx.compose.material.icons.outlined.Park
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.paperscrape.livewallpaper.engine.CalendarSpan
import com.paperscrape.livewallpaper.engine.CalendarTier
import com.paperscrape.livewallpaper.engine.CalendarWindow
import com.paperscrape.livewallpaper.engine.EasterSpan
import com.paperscrape.livewallpaper.engine.SeasonalCalendar
import com.paperscrape.livewallpaper.engine.SeasonalThemeRules
import com.paperscrape.livewallpaper.engine.conflictsFor
import com.paperscrape.livewallpaper.engine.coverage
import com.paperscrape.livewallpaper.engine.gapsAfter
import com.paperscrape.livewallpaper.engine.runsOf
import com.paperscrape.livewallpaper.prefs.WallpaperPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.MonthDay
import java.time.format.TextStyle
import java.util.Locale

/**
 * The holiday calendar: which dates each automatic-theme window covers.
 *
 * ### What this screen lets a person change, and what it does not
 *
 * **Only dates.** The windows themselves, their names, the theme each one picks and the order they
 * are checked in are [CalendarWindow] — code, not preferences. There is no "add a window", no
 * "pick a different theme for Halloween" and no way to reorder anything, because the precedence is
 * a designed property of the calendar rather than a taste.
 *
 * ### The two tiers are drawn as two tiers
 *
 * Holidays sit **above** seasons and pass over them; the seasons are the floor underneath. That is
 * the model the rules implement, so the screen states it in the same shape: two sections, the
 * holidays first because they win, and each season's row saying what it is the floor for. A user
 * who shortens Halloween should be able to see, without experimenting, that October falls back to
 * autumn rather than to nothing.
 *
 * ### Why an edit is a draft with a Save, and not a live slider
 *
 * Two windows of the same tier must never both claim a day. The cheap implementation is to let the
 * edit land and repair it afterwards, which means the invalid state exists — briefly in memory,
 * and on disk if the process dies in between. So a window opens a **draft**: the sliders move a
 * local copy, the conflict line updates as they move, and **Save is disabled while the draft
 * conflicts**. Nothing invalid is ever written. That is also why the sliders are
 * [PreferenceSlider]s rather than raw ones — a raw `Slider` here would put a disk write inside the
 * thumb's feedback loop, which this project already forbids for exactly that reason.
 */
@Composable
internal fun HolidayCalendarScreen(
    calendar: SeasonalCalendar,
    autoThemeEnabled: Boolean,
    prefs: WallpaperPrefs,
    scope: CoroutineScope,
    onBack: () -> Unit,
) {
    var editing by remember { mutableStateOf<CalendarWindow?>(null) }
    val coverage = calendar.coverage()
    val today = LocalDate.now()
    val activeWindow = SeasonalThemeRules.windowForDate(today, calendar)

    SettingsSubScreen(title = "Holiday calendar", onBack = onBack) {
        if (!autoThemeEnabled) {
            SettingsBanner(
                "\"Automatic theme by date\" is off, so these dates are not picking your theme right " +
                    "now. Your edits are kept, and take effect as soon as you turn it back on.",
            )
        } else {
            SettingsBanner(
                "The calendar picks a theme for each day. Holidays sit on top; the seasons " +
                    "underneath cover the rest of the year.",
            )
        }

        // A gap is legal and recoverable -- the wallpaper falls back to the theme picked by hand --
        // but it is silent, and a silent gap is what makes an app look broken. Named here, with the
        // dates, rather than left for the user to discover on the day.
        if (coverage.hasGaps) {
            SettingsBanner(
                "No season covers " + describeRuns(coverage.uncoveredRuns) + ". On those days the " +
                    "wallpaper keeps the theme you picked by hand, unless a holiday covers them.",
                isError = true,
            )
        }

        SettingsSectionHeader("Holidays")
        SettingsCaption("Checked first, in this order. A holiday covers whatever season is under it.")
        SettingsGroup {
            for (window in CalendarWindow.OCCASIONS) {
                SettingsNavigationRow(
                    title = window.label,
                    supporting = supportingFor(window, calendar, today),
                    icon = iconFor(window),
                    supportingIsAccent = window == activeWindow,
                    onClick = { editing = window },
                )
            }
        }

        SettingsSectionHeader("Seasons")
        SettingsCaption("The floor under the holidays. Every day a holiday does not claim comes from here.")
        SettingsGroup {
            for (window in CalendarWindow.SEASONS) {
                SettingsNavigationRow(
                    title = window.label,
                    supporting = supportingFor(window, calendar, today),
                    icon = iconFor(window),
                    supportingIsAccent = window == activeWindow,
                    onClick = { editing = window },
                )
            }
        }

        OutlinedButton(
            onClick = { scope.launch { prefs.resetSeasonalCalendar() } },
            enabled = !calendar.isFactory,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp),
        ) {
            Text("Reset calendar to defaults")
        }
        SettingsCaption(
            if (calendar.isFactory) {
                "Every window is on its default dates."
            } else {
                "Puts every window back on the dates the app ships with."
            },
        )

        SettingsBottomSpacer()
    }

    editing?.let { window ->
        if (window.isComputed) {
            EasterWindowEditor(
                calendar = calendar,
                onSave = { scope.launch { prefs.setSeasonalCalendar(calendar.withEaster(it)) } },
                onClose = { editing = null },
            )
        } else {
            DatedWindowEditor(
                window = window,
                calendar = calendar,
                onSave = { scope.launch { prefs.setSeasonalCalendar(calendar.withSpan(window, it)) } },
                onClose = { editing = null },
            )
        }
    }
}

/**
 * Start and end for one dated window, as two day-of-year sliders.
 *
 * A slider over the 366 month-days rather than a date picker, because a window has no year: it is
 * "the whole of October", not "October 2026". A `DatePicker` would have to invent a year to show,
 * and the first thing a user would do is wonder what happens the year after.
 */
@Composable
private fun DatedWindowEditor(
    window: CalendarWindow,
    calendar: SeasonalCalendar,
    onSave: (CalendarSpan) -> Unit,
    onClose: () -> Unit,
) {
    val current = calendar.spanFor(window)
    var startText by remember(window) { mutableStateOf(formatDateEntry(current.start)) }
    var endText by remember(window) { mutableStateOf(formatDateEntry(current.end)) }

    val startDay = parseDateEntry(startText)
    val endDay = parseDateEntry(endText)
    // No draft until both ends name a date. Everything downstream -- conflicts, gaps, Save -- is
    // then asking about a span that exists, rather than about a half-typed one.
    val draft = if (startDay != null && endDay != null) CalendarSpan(startDay, endDay) else null

    val conflicts = draft?.let { calendar.conflictsFor(window, it) }.orEmpty()
    val gapsAfter = draft?.let { calendar.gapsAfter(window, it) }.orEmpty()
    val changed = draft != null && draft != current
    // "Beyond saving" is an error; "not finished yet" is a field still being typed. The two
    // disable Save alike and look nothing like each other -- see isDateEntryRejected for why the
    // line between them is not "is it shaped like a date".
    val badEntries = listOf(startText, endText).filter { isDateEntryRejected(it) }

    SettingsSubScreen(title = window.label, onBack = onClose) {
        SettingsBanner(
            when (window.tier) {
                CalendarTier.OCCASION ->
                    "${window.label} covers these dates every year, on top of whatever season they fall in."
                CalendarTier.SEASON ->
                    "${window.label} is the floor for these dates. A holiday can still cover part of it."
            },
        )

        SettingsGroup {
            // Stacked, each at full width, rather than side by side. Two half-width fields fit the
            // screen and not the label: "Starts (day/month)" wrapped onto a second line and ran
            // through the field's own outline, which only showed up on the device. The order is
            // part of the label and cannot be shortened away, so the field gets the width instead.
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DateEntryField(
                    label = "Starts (day/month)",
                    text = startText,
                    onTextChange = { startText = it },
                    imeAction = ImeAction.Next,
                    modifier = Modifier.fillMaxWidth(),
                )
                DateEntryField(
                    label = "Ends (day/month)",
                    text = endText,
                    onTextChange = { endText = it },
                    imeAction = ImeAction.Done,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            // The line that makes "day first" safe: whatever the reader assumed the digits meant,
            // this says in words what the app read them as. It stays put whether or not both ends
            // have been typed, so it never disappears at the moment it is most needed.
            SettingsRow(
                title = "${startDay?.let(::formatDay) ?: "—"} to ${endDay?.let(::formatDay) ?: "—"}",
                supporting = when {
                    draft != null -> "${draft.lengthInDays} days" +
                        if (draft.wraps) ", running through the end of the year" else ""
                    else -> "Type both dates as two numbers, the day first: 03/05 is 3 May."
                },
            )
        }

        if (badEntries.isNotEmpty()) {
            SettingsBanner(
                badEntries.joinToString(" and ") { "\"$it\"" } +
                    (if (badEntries.size > 1) " are not dates. " else " is not a date. ") +
                    "The day comes first, then the month, so 03/05 is 3 May. This cannot be saved.",
                isError = true,
            )
        } else if (conflicts.isNotEmpty()) {
            SettingsBanner(
                "These dates overlap " + conflicts.joinToString(" and ") { it.label } +
                    ". Two ${tierNoun(window.tier)} cannot cover the same day, so this cannot be saved.",
                isError = true,
            )
        } else if (gapsAfter.isNotEmpty()) {
            SettingsBanner(
                "Saving this leaves no season on " + describeRuns(runsOf(gapsAfter)) +
                    ". Those days fall back to the theme you picked by hand.",
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(
                onClick = {
                    startText = formatDateEntry(window.factorySpan.start)
                    endText = formatDateEntry(window.factorySpan.end)
                },
                enabled = draft != window.factorySpan,
            ) { Text("Default dates") }
            Button(
                onClick = { draft?.let { onSave(it); onClose() } },
                // The one gate that matters: neither an overlapping draft nor a draft that is not
                // yet two dates is ever written, so the stored calendar cannot be in a state the
                // rules would have to break a tie in.
                enabled = draft != null && conflicts.isEmpty() && changed,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
        }

        SettingsBottomSpacer()
    }
}

/**
 * Easter's length, as the two things it actually is.
 *
 * There is no date to show: Easter Sunday is computed, and it moves by up to five weeks between
 * years. Showing a date picker here would be showing one year's answer as though it were the
 * setting. So the two controls are lengths — how many days before the Sunday, how many after — and
 * the line underneath says where that lands in the year the user is standing in.
 */
@Composable
private fun EasterWindowEditor(
    calendar: SeasonalCalendar,
    onSave: (EasterSpan) -> Unit,
    onClose: () -> Unit,
) {
    val current = calendar.easter
    var before by remember { mutableIntStateOf(current.daysBefore) }
    var after by remember { mutableIntStateOf(current.daysAfter) }

    val draft = EasterSpan(before, after)
    val year = LocalDate.now().year
    val landing = SeasonalThemeRules.easterWindowIn(year, calendar.withEaster(draft))

    SettingsSubScreen(title = "Easter", onBack = onClose) {
        SettingsBanner(
            "Easter Sunday is calculated, not stored -- it moves by up to five weeks between years. " +
                "What you set here is how long the window is either side of it.",
        )

        SettingsGroup {
            SettingsSliderRow(
                title = "Days before Easter Sunday",
                value = before.toFloat(),
                valueRange = 0f..EasterSpan.MAX_BEFORE.toFloat(),
                steps = EasterSpan.MAX_BEFORE - 1,
                valueLabel = { describeBefore(it.toInt()) },
                onCommit = { before = it.toInt() },
            )
            SettingsSliderRow(
                title = "Days after Easter Sunday",
                value = after.toFloat(),
                valueRange = 0f..EasterSpan.MAX_AFTER.toFloat(),
                steps = EasterSpan.MAX_AFTER - 1,
                valueLabel = { describeAfter(it.toInt()) },
                onCommit = { after = it.toInt() },
            )
            SettingsRow(
                title = "In $year: ${formatFullDate(landing.start)} to ${formatFullDate(landing.endInclusive)}",
                supporting = "${draft.daysBefore + draft.daysAfter + 1} days. " +
                    "Easter is checked first, so it covers whatever else those dates belong to.",
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(
                onClick = { before = EasterSpan.FACTORY.daysBefore; after = EasterSpan.FACTORY.daysAfter },
                enabled = draft != EasterSpan.FACTORY,
            ) { Text("Default") }
            Button(
                onClick = { onSave(draft); onClose() },
                enabled = draft != current,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
        }

        SettingsBottomSpacer()
    }
}

/**
 * One end of a window, typed.
 *
 * `KeyboardType.Number` so the numeric pad opens rather than the alphabet — this field can only
 * ever hold digits and a separator, and offering letters would be offering a mistake. On this
 * device that keypad has **no `/` key at all**, which is what makes the filter's automatic
 * separator load-bearing rather than a convenience.
 *
 * The field turns red only for an entry that cannot become a date; while it is being typed it
 * stays neutral, because a half-written date is not a wrong one.
 *
 * ### The cursor is pinned to the end, and that is not cosmetic
 *
 * The filter inserts the separator, which makes the text longer than what was typed, and Compose
 * keeps a plain `String` field's cursor at its old index. Typing `3 0 0 2` therefore produced
 * **`30/20`**: after `30/0` the cursor sat between the slash and the zero, and the next digit
 * landed there. Found on the device, not in a test — a unit test of the filter cannot see a
 * cursor. Rebuilding the value with the selection at the end on every recomposition fixes it, and
 * costs nothing a five-character numeric field would miss: there is no mid-string editing to
 * preserve here, only typing and backspacing.
 */
@Composable
private fun DateEntryField(
    label: String,
    text: String,
    onTextChange: (String) -> Unit,
    imeAction: ImeAction,
    modifier: Modifier = Modifier,
) {
    val invalid = isDateEntryRejected(text)
    OutlinedTextField(
        value = TextFieldValue(text, TextRange(text.length)),
        onValueChange = { onTextChange(sanitiseDateEntry(it.text)) },
        label = { Text(label) },
        placeholder = { Text("03/05") },
        singleLine = true,
        isError = invalid,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = imeAction),
        modifier = modifier,
    )
}

// --- Wording -------------------------------------------------------------------------------

private fun formatDay(day: MonthDay): String =
    "${day.dayOfMonth} ${day.month.getDisplayName(TextStyle.FULL, Locale.getDefault())}"

private fun formatFullDate(date: LocalDate): String =
    "${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.FULL, Locale.getDefault())}"

private fun tierNoun(tier: CalendarTier): String = when (tier) {
    CalendarTier.OCCASION -> "holidays"
    CalendarTier.SEASON -> "seasons"
}

private fun describeBefore(days: Int): String = when (days) {
    0 -> "None"
    2 -> "2 (Good Friday)"
    else -> days.toString()
}

private fun describeAfter(days: Int): String = when (days) {
    0 -> "None"
    1 -> "1 (Easter Monday)"
    else -> days.toString()
}

private fun supportingFor(window: CalendarWindow, calendar: SeasonalCalendar, today: LocalDate): String {
    val moved = if (window.isComputed) {
        calendar.easter != EasterSpan.FACTORY
    } else {
        calendar.spans.containsKey(window)
    }
    val dates = if (window.isComputed) {
        val range = SeasonalThemeRules.easterWindowIn(today.year, calendar)
        "${formatFullDate(range.start)} to ${formatFullDate(range.endInclusive)} in ${today.year}"
    } else {
        val span = calendar.spanFor(window)
        "${formatDay(span.start)} to ${formatDay(span.end)}"
    }
    return if (moved) "$dates · changed" else dates
}

private fun iconFor(window: CalendarWindow): ImageVector = when (window) {
    CalendarWindow.EASTER -> Icons.Outlined.Egg
    CalendarWindow.HALLOWEEN -> Icons.Outlined.DarkMode
    CalendarWindow.CHRISTMAS -> Icons.Outlined.CardGiftcard
    CalendarWindow.NEW_YEAR -> Icons.Outlined.Celebration
    CalendarWindow.WINTER -> Icons.Outlined.AcUnit
    CalendarWindow.SPRING -> Icons.Outlined.LocalFlorist
    CalendarWindow.SUMMER -> Icons.Outlined.WbSunny
    CalendarWindow.AUTUMN -> Icons.Outlined.Park
}

/** The icon the settings row for this whole screen uses. */
internal val holidayCalendarIcon: ImageVector = Icons.Filled.Event

private fun describeRuns(runs: List<ClosedRange<MonthDay>>): String {
    if (runs.isEmpty()) return "no days"
    return runs.joinToString(", ") { run ->
        if (run.start == run.endInclusive) formatDay(run.start)
        else "${formatDay(run.start)}–${formatDay(run.endInclusive)}"
    }
}
