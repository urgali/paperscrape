package com.paperscrape.livewallpaper.ui

import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SystemUpdate
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import com.paperscrape.livewallpaper.update.ApkDownloader
import com.paperscrape.livewallpaper.update.ApkInstaller
import com.paperscrape.livewallpaper.update.ApkSafety
import com.paperscrape.livewallpaper.update.InstallVerdict
import com.paperscrape.livewallpaper.update.UpdateChecker
import com.paperscrape.livewallpaper.update.UpdateDownloadResult
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
    var updateState by remember { mutableStateOf<UpdateUiState>(UpdateUiState.Idle) }

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
                title = when (updateState) {
                    is UpdateUiState.Checking -> "Checking..."
                    else -> "Check for updates"
                },
                supporting = when (val state = updateState) {
                    is UpdateUiState.UpToDate -> "You're up to date (v${BuildConfig.VERSION_NAME})"
                    is UpdateUiState.Available -> "Update available - PaperScrape ${state.info.tagName}"
                    is UpdateUiState.Downloading -> "Downloading..."
                    is UpdateUiState.ReadyToInstall -> "Ready to install"
                    else -> null
                },
                icon = Icons.Outlined.Refresh,
                enabled = updateState.allowsChecking,
                supportingIsAccent = updateState is UpdateUiState.Available ||
                    updateState is UpdateUiState.ReadyToInstall,
                onClick = { scope.launch { updateState = checkForUpdate(onUpdateFound) } },
            )
        }

        UpdateProgressSection(
            state = updateState,
            onDownload = { info ->
                scope.launch {
                    updateState = UpdateUiState.Downloading(-1)
                    val result = ApkDownloader.downloadAndVerify(context, info) { percent ->
                        // The download runs on an IO dispatcher; the state it reports is read on
                        // the UI thread, and Compose's snapshot state is safe to write from either.
                        updateState = UpdateUiState.Downloading(percent)
                    }
                    updateState = verifiedOrError(context, result)
                }
            },
            onInstall = { apk ->
                if (!ApkInstaller.canRequestInstalls(context)) {
                    updateState = UpdateUiState.NeedsPermission(apk)
                } else {
                    ApkInstaller.launchInstall(context, apk)
                }
            },
            onGrantPermission = { context.startActivity(ApkInstaller.installPermissionIntent(context)) },
            onOpenReleasePage = { url -> context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) },
            onRetry = { scope.launch { updateState = checkForUpdate(onUpdateFound) } },
            onDismiss = { updateState = UpdateUiState.Idle },
        )

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


/**
 * Where the update flow has got to.
 *
 * CHECK -> DOWNLOAD -> VERIFY -> INSTALL, with every failure a state of its own rather than a
 * generic error: "no APK in that release", "checksum missing" and "checksum did not match" are
 * different problems with different answers, and flattening them into "update failed" is how a
 * user ends up retrying something that will never work.
 *
 * Nothing here advances on its own. Each step is a tap, including the last one, which hands the
 * file to Android's installer and its own confirmation.
 */
private sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object UpToDate : UpdateUiState
    data class Available(val info: UpdateInfo) : UpdateUiState

    /** [percent] is -1 while the size is unknown. */
    data class Downloading(val percent: Int) : UpdateUiState
    data class ReadyToInstall(val apk: java.io.File) : UpdateUiState
    data class NeedsPermission(val apk: java.io.File) : UpdateUiState
    data class Error(val message: String, val releasePageUrl: String? = null) : UpdateUiState

    /** A check may be started from any state except one already in flight. */
    val allowsChecking: Boolean
        get() = this !is Checking && this !is Downloading
}

private suspend fun checkForUpdate(onUpdateFound: (UpdateInfo) -> Unit): UpdateUiState {
    val update = UpdateChecker.checkForUpdate(BuildConfig.VERSION_NAME) ?: return UpdateUiState.UpToDate
    onUpdateFound(update)
    return UpdateUiState.Available(update)
}

/**
 * Turns a finished download into the next state, including the last safety check.
 *
 * The version comparison that decided to *offer* this update was made against release tags. This
 * one is made against the bytes on disk, which is a different claim: a mis-tagged release, an asset
 * attached to the wrong tag, or a redirect to another project's file all stop here rather than at
 * the system prompt.
 */
private fun verifiedOrError(context: Context, result: UpdateDownloadResult): UpdateUiState = when (result) {
    is UpdateDownloadResult.Verified -> {
        val identity = ApkInstaller.identify(context, result.apk)
        when (val verdict = ApkSafety.verdict(
            expectedPackage = context.packageName,
            installedVersionCode = ApkInstaller.installedVersionCode(context),
            downloaded = identity,
        )) {
            InstallVerdict.Allowed -> UpdateUiState.ReadyToInstall(result.apk)
            InstallVerdict.Unreadable -> {
                ApkDownloader.clearCache(context)
                UpdateUiState.Error("The downloaded file isn't a readable app package. Nothing was installed.")
            }
            is InstallVerdict.WrongPackage -> {
                ApkDownloader.clearCache(context)
                UpdateUiState.Error(
                    "That download is ${verdict.found}, not ${verdict.expected}. Nothing was installed.",
                )
            }
            is InstallVerdict.NotNewer -> {
                ApkDownloader.clearCache(context)
                UpdateUiState.Error(
                    "That release (build ${verdict.found}) isn't newer than what you have " +
                        "(build ${verdict.installed}). Nothing was installed.",
                )
            }
        }
    }

    UpdateDownloadResult.NoApkAsset -> UpdateUiState.Error(
        "That release has no installable APK attached. You can still download it from the release page.",
    )

    UpdateDownloadResult.NoChecksumAsset -> UpdateUiState.Error(
        "That release has no SHA-256 checksum, so the download can't be verified - and an " +
            "unverified app package won't be installed. You can download it manually from the " +
            "release page instead.",
    )

    is UpdateDownloadResult.ChecksumMismatch -> UpdateUiState.Error(
        "The download didn't match the release's checksum, so it was deleted rather than " +
            "installed. Try again on a different connection.",
    )

    UpdateDownloadResult.Failed -> UpdateUiState.Error(
        "The download didn't finish - check your connection and try again. Nothing was changed.",
    )
}

/** The part of the Updates section that only exists once something is happening. */
@Composable
private fun UpdateProgressSection(
    state: UpdateUiState,
    onDownload: (UpdateInfo) -> Unit,
    onInstall: (java.io.File) -> Unit,
    onGrantPermission: () -> Unit,
    onOpenReleasePage: (String) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        UpdateUiState.Idle, UpdateUiState.Checking, UpdateUiState.UpToDate -> Unit

        is UpdateUiState.Available -> Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                "PaperScrape ${state.info.tagName} is available. You have v${BuildConfig.VERSION_NAME}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(10.dp))
            if (state.info.isInstallable) {
                Button(onClick = { onDownload(state.info) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Download and install")
                }
            } else {
                // No APK or no checksum attached: the app will not install something it cannot
                // verify, so this release stays a manual download.
                Text(
                    "This release has no verifiable APK attached, so it has to be downloaded from " +
                        "the release page.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onOpenReleasePage(state.info.releasePageUrl) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Open release page") }
            }
        }

        is UpdateUiState.Downloading -> Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                if (state.percent >= 0) "Downloading... ${state.percent}%" else "Downloading...",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (state.percent >= 0) {
                LinearProgressIndicator(
                    progress = { state.percent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        is UpdateUiState.ReadyToInstall -> Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                "Verified against the release's SHA-256. Android will ask you to confirm the install.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(onClick = { onInstall(state.apk) }, modifier = Modifier.fillMaxWidth()) {
                Text("Install")
            }
        }

        is UpdateUiState.NeedsPermission -> Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                "Android needs your permission to let PaperScrape install app packages. Turn on " +
                    "\"Allow from this source\" on the next screen, then come back and tap Install.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onGrantPermission, modifier = Modifier.weight(1f)) {
                    Text("Open settings")
                }
                OutlinedButton(onClick = { onInstall(state.apk) }, modifier = Modifier.weight(1f)) {
                    Text("Install")
                }
            }
        }

        is UpdateUiState.Error -> Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            SettingsBanner(text = state.message, isError = true, modifier = Modifier.padding(0.dp))
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRetry, modifier = Modifier.weight(1f)) { Text("Try again") }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Dismiss") }
            }
        }
    }
}
