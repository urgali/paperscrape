package com.paperscrape.livewallpaper.icon

import com.paperscrape.livewallpaper.engine.CalendarWindow
import com.paperscrape.livewallpaper.engine.SeasonalCalendar
import com.paperscrape.livewallpaper.engine.SeasonalThemeRules
import java.time.LocalDate

/**
 * The launcher icons this app can wear, one `activity-alias` each.
 *
 * ### Why an alias and not a drawable swap
 *
 * An app cannot change the icon it declares. What it can change is *which of its launcher
 * entries is enabled*, and Android draws whichever one is. So each icon is an
 * `<activity-alias>` in the manifest pointing at the one real launcher activity
 * (`ui.SettingsActivity`, which no longer carries a LAUNCHER filter of its own), and switching
 * icons means enabling one alias and disabling the one that was on. Measured on a BV6600
 * (Android 10) in the v5.4 feasibility round: the icon stays in its home-screen cell, keeps its
 * label, becomes the new one in ~1.2 s, and the launcher is not restarted.
 *
 * **[LauncherIconSwitch] must pass `DONT_KILL_APP`**, and that is the whole reason this is done
 * from inside the app rather than from a script: without the flag the platform kills the process,
 * which for a live wallpaper means about seven and a half seconds of a black screen before the
 * system re-binds the service. The flag is not something `adb shell pm disable` can pass.
 *
 * ### Why six colours and one silhouette
 *
 * Each alias carries a whole `<adaptive-icon>`, not a patch on another one, so every alias
 * repeats all three layers -- including `<monochrome>`. All six point at the same shipped
 * monochrome drawable on purpose: an Android 13+ themed icon discards the colour and keeps the
 * silhouette, and five seasonal silhouettes of the same five buildings would be five identical
 * shapes. The season is a colour statement; the themed icon does not change with it.
 */
enum class SeasonalIcon(
    /**
     * The alias's class name, which is a **stable identifier**, not a description.
     *
     * A home-screen shortcut is a pin on this exact name: renaming one of these in a later
     * release leaves every user who had the app on their home screen with a dead cell. Written
     * out in full rather than assembled from the package, because the string that must never
     * change should be the string you can read.
     */
    val aliasClassName: String,
    /**
     * The calendar window this icon belongs to, or `null` for [DEFAULT], which belongs to none.
     *
     * This is the *only* place a window and an icon are tied together. The three windows with no
     * icon of their own -- Halloween, Easter, New Year -- are not listed anywhere: they resolve
     * through the season they fall in, which is arithmetic on the user's own calendar rather than
     * a table somebody has to remember to update. See [SeasonalIconRules.iconForDate].
     */
    val window: CalendarWindow?,
    /**
     * Whether `AndroidManifest.xml` declares this alias enabled.
     *
     * `PackageManager.getComponentEnabledSetting` answers `COMPONENT_ENABLED_STATE_DEFAULT` for a
     * component nobody has set yet, which means "whatever the manifest said" and not "enabled".
     * Without this flag [LauncherIconSwitch] could not tell the two apart, and every fresh install
     * would write all six components to find out. Keep it in step with the manifest;
     * `SeasonalIconManifestTest` fails if it drifts.
     */
    val enabledInManifest: Boolean,
) {
    /**
     * The icon the app ships with: the sunset town, no season.
     *
     * It exists so that **no install ever shows a season that is not the season**. The manifest
     * has to enable exactly one alias, and a fresh install draws it in the app drawer from the
     * moment it is installed -- possibly for days, if the user installs the app and does not open
     * it. Any of the five would be a coin flip that is wrong four times out of five; this one is
     * simply the icon the app had before this feature existed.
     *
     * It is also the answer when the calendar cannot name a season at all, which a user can
     * arrange by dragging a season off a stretch of the year (see [SeasonalIconRules.iconForDate]).
     */
    DEFAULT("com.paperscrape.livewallpaper.ui.IconDefault", null, enabledInManifest = true),

    WINTER("com.paperscrape.livewallpaper.ui.IconWinter", CalendarWindow.WINTER, enabledInManifest = false),
    SPRING("com.paperscrape.livewallpaper.ui.IconSpring", CalendarWindow.SPRING, enabledInManifest = false),
    SUMMER("com.paperscrape.livewallpaper.ui.IconSummer", CalendarWindow.SUMMER, enabledInManifest = false),
    AUTUMN("com.paperscrape.livewallpaper.ui.IconAutumn", CalendarWindow.AUTUMN, enabledInManifest = false),
    CHRISTMAS("com.paperscrape.livewallpaper.ui.IconChristmas", CalendarWindow.CHRISTMAS, enabledInManifest = false),
    ;

    companion object {
        /** The icon tied to [window], or `null` if that window has none of its own. */
        fun forWindow(window: CalendarWindow): SeasonalIcon? = entries.firstOrNull { it.window == window }
    }
}

/**
 * Which icon the date asks for.
 *
 * ### The icon follows the date, and the date only
 *
 * Not the theme on screen. A user who picked the beach by hand in December sees a Christmas icon
 * over a summer scene, and that is the decision, not an oversight: the icon is a calendar
 * indicator. (The scene itself follows `autoThemeByDate`, which is opt-in and off by default, so
 * making the icon follow the scene would have meant an icon that never changed for most users.)
 *
 * ### The date is read through the user's own calendar
 *
 * Through [SeasonalCalendar] -- the same eight windows the user drags on the Holiday calendar
 * screen -- and not through a civil calendar of its own. Two ideas of "winter" inside one app is a
 * duplicate that gets paid for later, when they disagree. The visible consequence is intended:
 * **move the start of winter and you move the icon with it.**
 *
 * ### Eight windows, six icons
 *
 * Five of the six are seasonal drawings; [SeasonalIcon.DEFAULT] belongs to no window.
 * Halloween, Easter and New Year have no icon of their own and take the icon of the season they
 * fall in -- *derived*, by asking the calendar which season covers that same date, rather than
 * written down. With the factory calendar that yields New Year to Winter, Easter to Spring and
 * Halloween to Autumn; with a calendar the user has moved it can yield something else, and it
 * should.
 */
object SeasonalIconRules {

    /**
     * The icon for [date] under [calendar].
     *
     * Never `null`: a date with no window and no season underneath it -- which the factory
     * calendar cannot produce, since the seasons partition the year, but a user who drags a season
     * boundary can -- resolves to [SeasonalIcon.DEFAULT] rather than leaving whatever icon
     * happened to be showing. "Nothing is in season" is a state with an answer, and the answer is
     * the icon that belongs to no season.
     */
    fun iconForDate(
        date: LocalDate = LocalDate.now(),
        calendar: SeasonalCalendar = SeasonalCalendar.DEFAULT,
    ): SeasonalIcon {
        val window = SeasonalThemeRules.windowForDate(date, calendar)
        if (window != null) {
            SeasonalIcon.forWindow(window)?.let { return it }
            // An occasion with no icon of its own: fall through to the season it lands on. Asked
            // of the calendar rather than looked up, so a user who moves a season moves this too.
        }
        val season = SeasonalThemeRules.seasonForDate(date, calendar) ?: return SeasonalIcon.DEFAULT
        return SeasonalIcon.forWindow(season) ?: SeasonalIcon.DEFAULT
    }
}
