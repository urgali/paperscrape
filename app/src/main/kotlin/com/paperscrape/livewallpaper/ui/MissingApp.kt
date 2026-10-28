package com.paperscrape.livewallpaper.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher

/**
 * **What a tap that needs another app says where the phone has none.** *Open release page* and *Source code on GitHub*
 * need a browser, the imports a file picker and the exports a place to save, the update's permission the phone's page
 * for it and *Install* the system's installer, *Set as wallpaper* the system's wallpaper picker; on a phone without one
 * -- a work profile, a kiosk, a browser turned off -- the bare start throws `ActivityNotFoundException` out of the tap
 * and the settings close. Through these the tap says which app is missing, and nothing else happens.
 */
internal enum class MissingApp(val line: String) {
    BROWSER("There is no app on this phone to open web pages with."),
    FILE_PICKER("There is no app on this phone to choose a file with. Nothing changed."),
    FILE_SAVER("There is no app on this phone to save a file with. Nothing changed."),
    INSTALL_PERMISSION_PAGE("This phone has no page to allow PaperScrape to install apps, so the update cannot be installed from here."),
    INSTALLER("This phone has no app to install the update with, so it cannot be installed from here."),
    WALLPAPER_PICKER("There is no wallpaper picker on this phone to set PaperScrape with."),
}

/** Starts [intent], or -- no app on the phone takes it -- says so ([missing]); whether it started. */
internal fun Context.startActivityOrSay(intent: Intent, missing: MissingApp): Boolean =
    try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, missing.line, Toast.LENGTH_LONG).show()
        false
    }

/** Launches [input], or -- no app on the phone takes it -- says so ([missing]) with [context]; whether it launched. */
internal fun <I> ActivityResultLauncher<I>.launchOrSay(input: I, context: Context, missing: MissingApp): Boolean =
    try {
        launch(input)
        true
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, missing.line, Toast.LENGTH_LONG).show()
        false
    }
