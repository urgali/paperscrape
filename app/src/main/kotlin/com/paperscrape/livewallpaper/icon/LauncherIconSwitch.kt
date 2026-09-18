package com.paperscrape.livewallpaper.icon

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.paperscrape.livewallpaper.engine.SeasonalCalendar
import java.time.LocalDate

/**
 * Turns [SeasonalIconRules]' answer into the one launcher entry the system draws.
 *
 * ### The two rules that make this safe
 *
 * **`DONT_KILL_APP` on every call.** Without it the platform kills the process, and this process
 * is the live wallpaper: measured on a BV6600, about seven and a half seconds of black screen
 * before the system re-binds the service. With it, the engine object is the same one before and
 * after and the scene does not drop a frame. There is no reason ever to omit it here, which is
 * why the flag is not a parameter.
 *
 * **Enable before disable.** For the moment between the two calls the package has two enabled
 * launcher entries, which a launcher shows as one icon that is already the new one; the other
 * order would leave it with none, and a launcher that finds no launcher entry is entitled to take
 * the icon off the home screen. That is the "icon disappears" failure this approach is supposed
 * not to have, and the ordering is the whole of the reason it does not.
 *
 * ### It writes only when the answer changed
 *
 * [apply] reads the current state first and returns without writing when it already agrees, so
 * the common case -- every wallpaper start, every date change that stays inside the same window,
 * every settings change -- costs [SeasonalIcon.entries] cheap reads and no writes at all. The
 * reads are a binder call each and were measured at 3 ms for a pair on the test device.
 *
 * `COMPONENT_ENABLED_STATE_DEFAULT` is the trap in that comparison: it is what the system answers
 * for a component nobody has ever set, and it means "as the manifest declared it", not "enabled".
 * Reading it as enabled would make a fresh install believe five disabled aliases were on and
 * write all five; reading it as disabled would make it believe the one enabled alias was off.
 * [SeasonalIcon.enabledInManifest] is what resolves it.
 */
object LauncherIconSwitch {

    fun componentFor(context: Context, icon: SeasonalIcon): ComponentName =
        ComponentName(context.packageName, icon.aliasClassName)

    /** The effective enabled state of [icon], with `DEFAULT` resolved through the manifest. */
    fun isEnabled(context: Context, icon: SeasonalIcon): Boolean =
        when (context.packageManager.getComponentEnabledSetting(componentFor(context, icon))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
            else -> icon.enabledInManifest
        }

    /**
     * Makes [target] the icon, and returns whether anything had to be written.
     *
     * Failures are swallowed deliberately. Nothing else in this app depends on the icon being
     * right, so a ROM that refuses to let a package enable its own alias costs the user a wrong
     * icon; it must not cost them their wallpaper.
     */
    fun apply(context: Context, target: SeasonalIcon): Boolean {
        val pm = context.packageManager
        var wrote = false
        runCatching {
            if (!isEnabled(context, target)) {
                pm.setComponentEnabledSetting(
                    componentFor(context, target),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP,
                )
                wrote = true
            }
            // Only now the others, and only the ones actually on: see "enable before disable".
            for (other in SeasonalIcon.entries) {
                if (other == target || !isEnabled(context, other)) continue
                pm.setComponentEnabledSetting(
                    componentFor(context, other),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
                wrote = true
            }
        }
        return wrote
    }

    /**
     * The whole job in one call: work out today's icon and put it on.
     *
     * [date] and [calendar] are parameters so a test can ask for a day that is not today and a
     * calendar that is not this device's, and for no other reason: every caller in the app passes
     * the device's date and the user's stored calendar.
     */
    fun applyForDate(
        context: Context,
        date: LocalDate = LocalDate.now(),
        calendar: SeasonalCalendar = SeasonalCalendar.DEFAULT,
    ): Boolean = apply(context, SeasonalIconRules.iconForDate(date, calendar))
}
