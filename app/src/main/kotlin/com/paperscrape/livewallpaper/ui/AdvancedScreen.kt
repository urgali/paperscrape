package com.paperscrape.livewallpaper.ui

import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import com.paperscrape.livewallpaper.BuildConfig
import com.paperscrape.livewallpaper.R
import com.paperscrape.livewallpaper.engine.CustomThemeData
import com.paperscrape.livewallpaper.prefs.CustomThemeStore
import com.paperscrape.livewallpaper.prefs.WallpaperPrefs
import com.paperscrape.livewallpaper.prefs.WallpaperSettings
import com.paperscrape.livewallpaper.update.UpdateChecker
import com.paperscrape.livewallpaper.update.UpdateInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val SOURCE_URL = "https://github.com/urgali/paperscrape"

/**
 * The settings that are about the app rather than about the wallpaper: custom-theme maintenance,
 * the update checker, and the version.
 *
 * In v2.8 these were the tail of the home screen, in the same scroll as the parallax slider and
 * the weather toggles. The theme gallery deliberately does *not* live here -- it is reached from
 * the home screen's Theme row, because choosing a theme is the most common thing a user comes to
 * these settings to do.
 */
@Composable
internal fun AdvancedScreen(
    settings: WallpaperSettings,
    customThemeData: CustomThemeData,
    effectiveThemeId: String,
    prefs: WallpaperPrefs,
    customThemeStore: CustomThemeStore,
    scope: CoroutineScope,
    onUpdateFound: (UpdateInfo) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var showSaveDialog by remember { mutableStateOf(false) }
    var confirmResetAll by remember { mutableStateOf(false) }
    var manualCheckInProgress by remember { mutableStateOf(false) }
    var manualCheckMessage by remember { mutableStateOf<String?>(null) }

    SettingsSubScreen(title = "Advanced & about", onBack = onBack) {
        SettingsSectionHeader("Custom themes")
        SettingsGroup {
            SettingsRow(
                title = "Save the current look as a new theme",
                supporting = "Keeps every edit you have made to the theme showing now",
                icon = Icons.Filled.Save,
                onClick = { showSaveDialog = true },
            )
            SettingsRow(
                title = "Reset all customised themes",
                supporting = if (customThemeData.overrides.isEmpty()) {
                    "No built-in theme has your edits"
                } else {
                    "${customThemeData.overrides.size} built-in themes have your edits"
                },
                icon = Icons.Outlined.Restore,
                enabled = customThemeData.overrides.isNotEmpty(),
                onClick = { confirmResetAll = true },
            )
        }
        SettingsCaption(
            "A customised theme keeps whatever it looked like when you saved it, even after app updates add " +
                "new objects to that theme. If a theme seems to be missing things it should have, this fixes it.",
        )

        SettingsSectionHeader("Updates")
        SettingsGroup {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_auto_update_check_title),
                supporting = stringResource(R.string.settings_auto_update_check_subtitle),
                icon = Icons.Outlined.SystemUpdate,
                checked = settings.automaticUpdateCheckEnabled,
                onCheckedChange = { scope.launch { prefs.setAutomaticUpdateCheckEnabled(it) } },
            )
            SettingsRow(
                title = if (manualCheckInProgress) "Checking..." else "Check now",
                supporting = manualCheckMessage,
                icon = Icons.Outlined.Refresh,
                enabled = !manualCheckInProgress,
                onClick = {
                    manualCheckMessage = null
                    manualCheckInProgress = true
                    scope.launch {
                        val update = UpdateChecker.checkForUpdate(BuildConfig.VERSION_NAME)
                        manualCheckInProgress = false
                        if (update != null) {
                            onUpdateFound(update)
                        } else {
                            manualCheckMessage = "You're up to date (v${BuildConfig.VERSION_NAME})"
                        }
                    }
                },
            )
        }

        SettingsSectionHeader("About")
        SettingsGroup {
            SettingsRow(
                title = "Version",
                // The release, then the install counter in brackets. It read "v1 (1.0)" under the
                // semver scheme, which names the release with the wrong number.
                supporting = "${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                icon = Icons.Outlined.Info,
            )
            SettingsRow(
                title = "Source code on GitHub",
                supporting = "An open-source live wallpaper inspired by classic \"paper cutout\" animated backgrounds",
                icon = Icons.Filled.Code,
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, SOURCE_URL.toUri())) },
            )
        }
    }

    if (showSaveDialog) {
        NameInputDialog(
            title = "Save current look as...",
            onConfirm = { name ->
                scope.launch {
                    val id = CustomThemeStore.newCustomThemeId()
                    customThemeStore.upsertCustomTheme(
                        snapshotEntry(
                            id,
                            name,
                            effectiveThemeId,
                            settings.pendingCustomization,
                            settings.pendingCustomizationThemeId,
                        ),
                    )
                }
                showSaveDialog = false
            },
            onDismiss = { showSaveDialog = false },
        )
    }

    if (confirmResetAll) {
        AlertDialog(
            onDismissRequest = { confirmResetAll = false },
            title = { Text("Reset all customised themes?") },
            text = {
                Text(
                    "This removes your custom version of every overridden built-in theme " +
                        "(${customThemeData.overrides.size}) and restores each one's current default look. " +
                        "Your independent custom themes are not affected.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { customThemeStore.clearAllOverrides() }
                    confirmResetAll = false
                }) { Text("Reset all") }
            },
            dismissButton = { TextButton(onClick = { confirmResetAll = false }) { Text("Cancel") } },
        )
    }
}
