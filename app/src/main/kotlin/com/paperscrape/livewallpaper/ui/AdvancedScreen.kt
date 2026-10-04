package com.paperscrape.livewallpaper.ui

import androidx.compose.runtime.MutableState
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Refresh
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.paperscrape.livewallpaper.BuildConfig
import com.paperscrape.livewallpaper.R
import com.paperscrape.livewallpaper.engine.CustomThemeData
import com.paperscrape.livewallpaper.engine.ThemeCatalog
import com.paperscrape.livewallpaper.engine.WallpaperEngineCensus
import com.paperscrape.livewallpaper.prefs.AppBackup
import com.paperscrape.livewallpaper.prefs.BackupImportError
import com.paperscrape.livewallpaper.prefs.BackupParseResult
import com.paperscrape.livewallpaper.prefs.BackupRepository
import com.paperscrape.livewallpaper.prefs.CustomThemeStore
import com.paperscrape.livewallpaper.prefs.WallpaperPrefs
import com.paperscrape.livewallpaper.prefs.WallpaperSettings
import com.paperscrape.livewallpaper.prefs.BoundedImport
import com.paperscrape.livewallpaper.update.ApkDownloader
import com.paperscrape.livewallpaper.update.ApkInstaller
import com.paperscrape.livewallpaper.update.ApkSafety
import com.paperscrape.livewallpaper.update.DownloadPhase
import com.paperscrape.livewallpaper.update.InstallVerdict
import com.paperscrape.livewallpaper.update.UpdateCheckResult
import com.paperscrape.livewallpaper.update.UpdateChecker
import com.paperscrape.livewallpaper.update.UpdateDownloadResult
import com.paperscrape.livewallpaper.update.UpdateInfo
import com.paperscrape.livewallpaper.update.UpdateNotificationPolicy
import com.paperscrape.livewallpaper.update.UpdateNotifier
import com.paperscrape.livewallpaper.update.UpdatePrefs
import com.paperscrape.livewallpaper.update.UpdateReplyStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val SOURCE_URL = "https://github.com/urgali/paperscrape"

/**
 * The settings that are about the app rather than about the wallpaper: custom-theme maintenance,
 * updates and update notifications, the whole-app backup, and the version.
 *
 * In v2.8 these were the tail of the home screen, in the same scroll as the parallax slider and
 * the weather toggles. The theme gallery deliberately does *not* live here -- it is reached from
 * the home screen's Theme row, because choosing a theme is the most common thing a user comes to
 * these settings to do.
 */
@Composable
internal fun AdvancedScreen(
    /**
     * Owned by the caller, because the caller owns [scope] (ARC-08).
     *
     * A download launched into `scope` outlives this screen, so its state has to as well or the two
     * disagree the moment the user navigates away mid-transfer.
     */
    updateState: MutableState<UpdateUiState>,
    settings: WallpaperSettings,
    customThemeData: CustomThemeData,
    effectiveThemeId: String,
    prefs: WallpaperPrefs,
    customThemeStore: CustomThemeStore,
    scope: CoroutineScope,
    onUpdateFound: (UpdateInfo) -> Unit,
    /**
     * Puts the system's notification-permission dialog in front of the user, on API 33+ (A1, v5.7D).
     *
     * Passed in from the Activity, which registers it beside the location launcher behind
     * `onRequestLocationPermission` (`SettingsActivity`). It is only ever called when
     * [com.paperscrape.livewallpaper.update.UpdateNotificationPolicy.mustAsk] says there is
     * something to ask for -- below API 33 there is no such permission, and asking would return a
     * result with no dialog, which reads exactly like a refusal.
     *
     * The result carries a second fact since v5.10C: whether the system would show the dialog again
     * (`shouldShowRequestPermissionRationale` after a refusal). Where it would not, the only place the
     * permission can still be given is the phone's page, and the row's tap goes there.
     */
    onRequestNotificationPermission: (onResult: (granted: Boolean, canAskAgain: Boolean) -> Unit) -> Unit =
        { it(true, true) },
    /** The system's preview, where PaperScrape is set as the wallpaper: the tap on *Notify me* when it is not (v5.10C). */
    onApplyWallpaper: () -> Unit = {},
    startInstallFor: UpdateInfo? = null,
    onInstallStarted: () -> Unit = {},
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    /** Where the button's check keeps GitHub's last reply, the store the engine and the home screen use (v5.10D). */
    val updateReplyStore: UpdateReplyStore = remember(context) { UpdatePrefs(context.applicationContext) }
    /**
     * Whether the last attempt to switch notifications on was refused (A1, v5.7D).
     *
     * Screen-local and deliberately not persisted: it is about what just happened, not about what
     * the app should do, and the permission itself is the durable record. On API 29 -- this
     * project's only test device -- it can never become true, because there is no permission to
     * refuse. See `UpdateNotificationPolicy`.
     */
    var permissionRefused by remember { mutableStateOf(false) }
    /**
     * What the phone's settings say about PaperScrape's notifications (`UpdateNotifier.PhoneState`,
     * v5.8B): read when the screen is composed and again every time it comes back to the front --
     * the way to change it is to leave for the phone's settings and return, so a value read once
     * would be stale exactly when it matters. Whether that blocks is decided below with the switch
     * as saved (`UpdateNotificationPolicy.blockedInPhoneSettings`, v5.9F), so turning the switch off
     * or on here is reflected at once, without waiting for the next return to the front.
     */
    var phoneNotifications by remember { mutableStateOf(UpdateNotifier.phoneState(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                phoneNotifications = UpdateNotifier.phoneState(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var showSaveDialog by remember { mutableStateOf(false) }
    var confirmResetSaved by remember { mutableStateOf(false) }
    var confirmResetEdits by remember { mutableStateOf(false) }
    var updateState by updateState
    var backupMessage by remember { mutableStateOf<String?>(null) }
    /** The text the preview read, and what it parsed to: Restore imports exactly this text. */
    var pendingImport by remember { mutableStateOf<Pair<String, AppBackup>?>(null) }
    var confirmExport by remember { mutableStateOf(false) }

    // Recomputed on every composition: both inputs are state the screen already collects, and it
    // is a pass over twelve themes.
    val resetState = SettingsUiModel.themeResetState(
        builtIns = ThemeCatalog.ALL,
        overrides = customThemeData.overrides,
        themeCustomizations = settings.themeCustomizations,
    )

    val backupRepository = remember(prefs, customThemeStore) {
        BackupRepository(prefs, customThemeStore, BuildConfig.VERSION_NAME)
    }

    // Storage Access Framework, so the app needs no filesystem permission at all: the user picks
    // the file and the system hands back a Uri already scoped to it.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            backupMessage = runCatching {
                val text = backupRepository.export()
                context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                    ?: error("could not open the chosen file for writing")
                "Backup saved."
            }.getOrElse { "Could not save the backup: ${it.message}" }
        }
    }

    /**
     * Import is two steps on purpose: this launcher only *reads and validates*, and hands the
     * result to a confirmation dialog. Nothing is written until the user has seen what the file
     * contains and said yes; Restore then imports **the text this read**, not a second read of the
     * file. Until v5.8C it re-opened the file and read it again with an unbounded `readText()`, so
     * the `BoundedImport` cap held only for the preview, and a provider whose content changed
     * between the two reads restored something the user never saw (v5.8B comment audit).
     */
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            // Bounded: see BoundedImport for why an unbounded readText was a defect (BCK-04).
            val raw = BoundedImport.readText(context, uri)
            when (val parsed = backupRepository.preview(raw)) {
                // `Ok` only ever comes from a text that was read: a null reads as `Failed`.
                is BackupParseResult.Ok -> pendingImport = (raw ?: return@launch) to parsed.backup
                is BackupParseResult.Failed -> backupMessage = describe(parsed.error)
            }
        }
    }

    /** Downloads, verifies, and moves to whatever the verdict allows. */
    suspend fun runDownload(info: UpdateInfo) {
        updateState = UpdateUiState.Downloading(-1)
        try {
            val result = ApkDownloader.downloadAndVerify(context, info) { phase ->
                // The download runs on an IO dispatcher; the state it reports is read on the UI
                // thread, and Compose's snapshot state is safe to write from either.
                updateState = when (phase) {
                    is DownloadPhase.Downloading -> UpdateUiState.Downloading(phase.percent)
                    DownloadPhase.Verifying -> UpdateUiState.Verifying
                }
            }
            // The package parse inside `verifiedOrError` reads a whole APK through PackageManager,
            // so it belongs under "Verifying" rather than under a progress bar that has stopped.
            updateState = UpdateUiState.Verifying
            updateState = verifiedOrError(context, result)
        } catch (cancellation: CancellationException) {
            // **ARC-08.** Rotation, light/dark and font-scale changes no longer kill the transfer:
            // `SettingsActivity` handles those configuration changes itself (see
            // `UpdateDownloadLifetimeTest`), so they do not cancel `scope`. Leaving the settings
            // screen for real still cancels it, deliberately -- a user who backs out of an update
            // has abandoned it.
            //
            // **The screen must never be left saying "Downloading..." with nothing running.**
            // `Downloading` and `Verifying` both disable the check row, so a state left behind by
            // a cancelled coroutine is not a cosmetic lie -- it is a dead end with no way out of
            // it. Whatever cancels this -- now only the settings Activity going away -- the state
            // goes back to something the user can act on.
            updateState = UpdateUiState.Available(info)
            throw cancellation
        }
    }

    /**
     * Sends the user to **PaperScrape's own** "install unknown apps" page -- the per-app screen
     * `ACTION_MANAGE_UNKNOWN_APP_SOURCES` opens when it is given a package URI, not the general
     * settings list they would then have to search.
     *
     * Launched for a result purely so there is a callback when they come back: the result code is
     * meaningless for this action (the screen reports nothing), so the permission is re-read
     * instead. Granting it resumes the install that was interrupted; declining says what is
     * missing rather than silently doing nothing.
     */
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val pending = updateState as? UpdateUiState.NeedsPermission ?: return@rememberLauncherForActivityResult
        if (ApkInstaller.canRequestInstalls(context)) {
            updateState = UpdateUiState.ReadyToInstall(pending.apk)
            ApkInstaller.launchInstall(context, pending.apk)
        } else {
            updateState = UpdateUiState.Error(
                "PaperScrape still isn't allowed to install app packages, so the update is ready " +
                    "but can't be handed to Android. Turn on \"Allow from this source\" and tap " +
                    "Install again.",
            )
        }
    }

    // Arriving here from the update dialog's "Install update": start the download immediately, so
    // the user's next tap is "Install", once the file is verified.
    //
    // **This used to hang every time, and the download was never the reason.** The effect was
    // keyed on `startInstallFor` and its own body called `onInstallStarted()`, which sets the
    // caller's `pendingInstall` to null -- so ~30 ms later the key changed from the release to
    // `null`, Compose cancelled the effect it had just started, and the download died with
    // `LeftCompositionCancellationException` before its first progress callback. Nothing ever
    // overwrote `Downloading(-1)`, and because `Downloading` disables the check row the screen had
    // no way forward. Two things keep that from coming back:
    //
    //  1. the key is the tag, not the object. Clearing `pendingInstall` still changes it, to null,
    //     and restarts the effect, harmlessly: the restarted effect finds nothing to start, and the
    //     guard below -- not the key -- is what stops a second run for the same tag;
    //  2. the download itself runs in `scope`, which belongs to the settings screen and outlives
    //     this effect, so even a genuine key change cannot cut a transfer in half.
    //
    // The guard blocks a second run only while the first is still under way or done
    // ([mayStartDownload]): until v5.8C it blocked any second run for the same tag, so after a
    // failed download "Try again" found the release, re-opened the update dialog, and its "Install
    // update" only closed it -- nothing started (v5.8B comment audit).
    var startedTag by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(startInstallFor?.tagName) {
        val info = startInstallFor ?: return@LaunchedEffect
        if (mayStartDownload(updateState, startedTag, info.tagName)) {
            startedTag = info.tagName
            updateState = UpdateUiState.Available(info)
            scope.launch { runDownload(info) }
        }
        onInstallStarted()
    }

    SettingsSubScreen(title = "Advanced & about", onBack = onBack) {
        SettingsSectionHeader("Custom themes")
        SettingsGroup {
            SettingsRow(
                title = "Save the current look as a new theme",
                supporting = "Keeps every edit you have made to the theme showing now",
                icon = Icons.Filled.Save,
                onClick = { showSaveDialog = true },
            )
            // **Two rows, because there are two things to reset** (v5.8C, the maintainer's decision
            // of 2026-09-25). One row used to reset saved versions while its line counted only
            // them, so a theme edited from the menus read "No built-in theme has your edits" beside
            // a disabled button. Each row now names what it resets, lists the themes it would
            // change, is enabled only when there is something, and confirms by naming it -- the
            // form v5.7B gave World & scene's reset. See [ThemeResetUiState].
            SettingsRow(
                title = "Reset saved versions of built-in themes",
                supporting = if (resetState.savedVersions.isEmpty()) {
                    "No built-in theme has a saved version"
                } else {
                    "Saved with Replace with current: ${SettingsUiModel.namesInProse(resetState.savedVersions)}"
                },
                icon = Icons.Outlined.Restore,
                enabled = resetState.savedVersions.isNotEmpty(),
                onClick = { confirmResetSaved = true },
            )
            SettingsRow(
                title = "Reset current edits to built-in themes",
                supporting = if (resetState.currentEdits.isEmpty()) {
                    "No built-in theme has edits made in these settings"
                } else {
                    "Edited in these settings: ${SettingsUiModel.namesInProse(resetState.currentEdits)}"
                },
                icon = Icons.Outlined.Restore,
                enabled = resetState.currentEdits.isNotEmpty(),
                onClick = { confirmResetEdits = true },
            )
        }
        SettingsCaption(
            "A saved version keeps the look it had when you saved it. Edits made in World & scene or " +
                "Seasons & decorations are kept per theme and show on top of it. Themes you saved as new " +
                "themes are not touched by either reset.",
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
            // **A row of its own, under the switch it depends on, and greyed while that one is
            // off** (A1, v5.7D). Not a new meaning for the switch above: somebody turned that on to
            // get a prompt inside the app, and making it also post notifications would change what
            // a control they already set does -- which is N1's defect in different clothes, and N1
            // was repaired one round ago.
            //
            // **On only if a notification can really arrive** (v5.10C, the maintainer's decision of
            // 2026-09-30, inventory I-201): the phone must let PaperScrape post, and PaperScrape must
            // be the wallpaper, because the check runs inside it. Otherwise the switch is off, the line
            // says what is missing, and the tap goes where it is put right -- the phone's notification
            // page, the permission dialog, or the system's wallpaper preview. The saved choice is not
            // written to make the switch look right: it comes back by itself. The whole rule is
            // `UpdateNotificationPolicy.notifyRow`.
            val isTheWallpaper by WallpaperEngineCensus.isTheWallpaper.collectAsState()
            val notifyRow = UpdateNotificationPolicy.notifyRow(
                automaticCheckEnabled = settings.automaticUpdateCheckEnabled,
                notifySwitchOn = settings.updateNotificationsEnabled,
                permission = phoneNotifications.permission,
                appNotificationsEnabled = phoneNotifications.appNotificationsEnabled,
                channelTurnedOff = phoneNotifications.channelTurnedOff,
                isTheWallpaper = isTheWallpaper,
                requestRefused = permissionRefused,
            )
            fun openPhonePage(intent: Intent) {
                // The per-app page, or, on a phone that has none, the app's details, which link to it.
                runCatching { context.startActivity(intent) }.onFailure {
                    runCatching { context.startActivity(UpdateNotifier.appDetailsIntent(context)) }
                }
            }
            SettingsSwitchRow(
                title = stringResource(R.string.settings_update_notify_title),
                supporting = when (notifyRow.line) {
                    UpdateNotificationPolicy.NotifyRowLine.NEEDS_AUTOMATIC_CHECK ->
                        stringResource(R.string.settings_update_notify_needs_check)
                    UpdateNotificationPolicy.NotifyRowLine.BLOCKED ->
                        stringResource(R.string.settings_update_notify_blocked)
                    UpdateNotificationPolicy.NotifyRowLine.BLOCKED_ASK ->
                        stringResource(R.string.settings_update_notify_blocked_ask)
                    UpdateNotificationPolicy.NotifyRowLine.REFUSED ->
                        stringResource(R.string.settings_update_notify_refused)
                    UpdateNotificationPolicy.NotifyRowLine.NOT_THE_WALLPAPER ->
                        stringResource(R.string.settings_update_notify_not_wallpaper)
                    UpdateNotificationPolicy.NotifyRowLine.DESCRIPTION ->
                        stringResource(R.string.settings_update_notify_subtitle)
                },
                icon = Icons.Outlined.Notifications,
                enabled = notifyRow.enabled,
                checked = notifyRow.shownOn,
                onCheckedChange = {
                    when (notifyRow.tap) {
                        UpdateNotificationPolicy.NotifyTap.NOTHING -> Unit
                        UpdateNotificationPolicy.NotifyTap.TURN_OFF ->
                            scope.launch { prefs.setUpdateNotificationsEnabled(false) }
                        UpdateNotificationPolicy.NotifyTap.TURN_ON -> {
                            permissionRefused = false
                            scope.launch { prefs.setUpdateNotificationsEnabled(true) }
                        }
                        // Ask *before* storing, so the stored value and what the user will actually
                        // see agree: a saved "on" with the permission refused reads as blocked.
                        UpdateNotificationPolicy.NotifyTap.ASK_PERMISSION -> {
                            // Whether a refusal now means "go to the phone's page": the system says it
                            // will not ask again -- but it says the same of a dialog closed with BACK,
                            // so only after a refusal already seen on this screen, or with the switch
                            // saved on, which is the phone having taken the permission away (Android
                            // 13+, notifications switched off in its settings: no dialog comes then).
                            val pageAfterRefusal = permissionRefused || settings.updateNotificationsEnabled
                            onRequestNotificationPermission { granted, canAskAgain ->
                                permissionRefused = !granted
                                phoneNotifications = UpdateNotifier.phoneState(context)
                                when {
                                    granted -> {
                                        scope.launch { prefs.setUpdateNotificationsEnabled(true) }
                                        if (!isTheWallpaper) onApplyWallpaper()
                                    }
                                    // The system will not ask again: the phone's page is the one place
                                    // left, and the choice is saved so it shows on once allowed there.
                                    !canAskAgain && pageAfterRefusal -> {
                                        scope.launch { prefs.setUpdateNotificationsEnabled(true) }
                                        openPhonePage(UpdateNotifier.appNotificationSettingsIntent(context))
                                    }
                                }
                            }
                        }
                        UpdateNotificationPolicy.NotifyTap.OPEN_APP_NOTIFICATIONS -> {
                            scope.launch { prefs.setUpdateNotificationsEnabled(true) }
                            openPhonePage(UpdateNotifier.appNotificationSettingsIntent(context))
                        }
                        UpdateNotificationPolicy.NotifyTap.OPEN_CHANNEL -> {
                            scope.launch { prefs.setUpdateNotificationsEnabled(true) }
                            openPhonePage(UpdateNotifier.channelSettingsIntent(context))
                        }
                        UpdateNotificationPolicy.NotifyTap.SET_AS_WALLPAPER -> {
                            scope.launch { prefs.setUpdateNotificationsEnabled(true) }
                            onApplyWallpaper()
                        }
                    }
                },
            )
            SettingsRow(
                title = when (updateState) {
                    is UpdateUiState.Checking -> "Checking..."
                    else -> "Check for updates"
                },
                supporting = when (val state = updateState) {
                    is UpdateUiState.UpToDate -> "You're up to date (v${BuildConfig.VERSION_NAME})"
                    is UpdateUiState.CheckFailed -> UpdateCheckResult.Unreachable(state.reason).message
                    is UpdateUiState.Available -> "Update available - PaperScrape ${state.info.tagName}"
                    is UpdateUiState.Downloading -> "Downloading..."
                    is UpdateUiState.Verifying -> "Verifying..."
                    is UpdateUiState.ReadyToInstall -> "Ready to install"
                    else -> null
                },
                icon = Icons.Outlined.Refresh,
                enabled = updateState.allowsChecking,
                supportingIsAccent = updateState is UpdateUiState.Available ||
                    updateState is UpdateUiState.ReadyToInstall,
                onClick = { scope.launch { updateState = checkForUpdate(updateReplyStore, onUpdateFound) } },
            )
        }

        UpdateProgressSection(
            state = updateState,
            onDownload = { info -> scope.launch { runDownload(info) } },
            onInstall = { apk ->
                if (!ApkInstaller.canRequestInstalls(context)) {
                    updateState = UpdateUiState.NeedsPermission(apk)
                } else {
                    ApkInstaller.launchInstall(context, apk)
                }
            },
            onGrantPermission = { permissionLauncher.launch(ApkInstaller.installPermissionIntent(context)) },
            onOpenReleasePage = { url -> context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) },
            onRetry = { scope.launch { updateState = checkForUpdate(updateReplyStore, onUpdateFound) } },
            onDismiss = { updateState = UpdateUiState.Idle },
        )

        SettingsSectionHeader("Backup")
        SettingsGroup {
            SettingsRow(
                title = "Export app backup",
                supporting = "Everything: your settings, every theme you have customised, and every " +
                    "theme you have saved. Keep the file safe -- it contains your weather API keys " +
                    "and your custom location.",
                icon = Icons.Outlined.Save,
                onClick = { confirmExport = true },
            )
            SettingsRow(
                title = "Import app backup",
                supporting = "Replaces your current settings and themes with the ones in the file. " +
                    "You will see what it contains before anything changes.",
                icon = Icons.Outlined.Restore,
                onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
            )
        }
        SettingsCaption(
            "A backup is for moving your app to a new phone. To share one theme with somebody " +
                "else, use \"Export theme\" on that theme in the Themes gallery -- a theme file " +
                "contains no settings, no location and no API keys.",
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
                            settings.themeCustomizations,
                        ),
                    )
                }
                showSaveDialog = false
            },
            onDismiss = { showSaveDialog = false },
        )
    }

    if (confirmExport) {
        AlertDialog(
            onDismissRequest = { confirmExport = false },
            title = { Text("Export app backup?") },
            text = {
                Text(
                    "The file will contain every setting and every theme you have customised or " +
                        "saved -- including your weather API keys and, if you set one, your custom " +
                        "location. Treat it like a password: anyone you send it to gets those too.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmExport = false
                    exportLauncher.launch(backupRepository.suggestedFileName())
                }) { Text("Export") }
            },
            dismissButton = { TextButton(onClick = { confirmExport = false }) { Text("Cancel") } },
        )
    }

    pendingImport?.let { (_, backup) ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Restore this backup?") },
            text = {
                Text(
                    "From PaperScrape ${backup.appVersionName.ifBlank { "an earlier version" }}.\n\n" +
                        backup.summary() +
                        "\n\nThis replaces your current settings and themes. It cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val raw = pendingImport?.first
                    pendingImport = null
                    scope.launch {
                        backupMessage = when (val result = backupRepository.import(raw)) {
                            is BackupRepository.ImportResult.Applied -> "Backup restored."
                            is BackupRepository.ImportResult.Refused -> describe(result.error)
                            is BackupRepository.ImportResult.RolledBack ->
                                "The restore failed and your previous settings were put back. Nothing changed."
                            is BackupRepository.ImportResult.Broken ->
                                "The restore failed and could not be undone. Import your backup file again."
                        }
                    }
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancel") } },
        )
    }

    backupMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { backupMessage = null },
            title = { Text("Backup") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { backupMessage = null }) { Text("OK") } },
        )
    }

    if (confirmResetSaved) {
        val names = SettingsUiModel.namesInProse(resetState.savedVersions)
        AlertDialog(
            onDismissRequest = { confirmResetSaved = false },
            title = { Text("Reset saved versions?") },
            // The sentence that matters names the themes and what stays: a saved version goes,
            // edits made in the settings stay (the other row resets those), saved new themes stay.
            text = {
                Text(
                    "$names ${if (resetState.savedVersions.size == 1) "loses the version" else "lose the versions"} " +
                        "you saved with Replace with current and ${if (resetState.savedVersions.size == 1) "goes" else "go"} " +
                        "back to how the app draws ${if (resetState.savedVersions.size == 1) "it" else "them"}. " +
                        "Edits made in World & scene or Seasons & decorations stay; the other reset removes " +
                        "those. Themes you saved as new themes are not affected.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { customThemeStore.clearAllOverrides() }
                    confirmResetSaved = false
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmResetSaved = false }) { Text("Cancel") } },
        )
    }

    if (confirmResetEdits) {
        val names = SettingsUiModel.namesInProse(resetState.currentEdits)
        AlertDialog(
            onDismissRequest = { confirmResetEdits = false },
            title = { Text("Reset current edits?") },
            // Named by screen, like World & scene's own reset: the edits are everything those two
            // screens change, and a list of objects would be wrong one decoration later.
            text = {
                Text(
                    "Everything changed in World & scene and in Seasons & decorations for $names goes: " +
                        "colours, densities, sky, weather effects and decorations. " +
                        "${if (resetState.currentEdits.size == 1) "The theme goes" else "Each theme goes"} back to " +
                        "its saved version if it has one, otherwise to how the app draws it. " +
                        "Themes you saved as new themes are not affected.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val ids = resetState.currentEditIds.toSet()
                    scope.launch { prefs.resetCustomizations(ids) }
                    confirmResetEdits = false
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmResetEdits = false }) { Text("Cancel") } },
        )
    }
}


/**
 * Where the update flow currently is.
 *
 * CHECK -> DOWNLOAD -> VERIFY -> INSTALL, with every failure given its own message rather than a
 * generic one: "no APK in that release", "checksum missing" and "checksum did not match" are
 * different problems with different answers, and flattening them into "update failed" is how a
 * user ends up retrying something that will never work.
 *
 * Verification follows the download on its own. Installing always starts from a tap on "Install",
 * which hands the file to Android's installer and its own confirmation; when the install
 * permission has to be granted first, the installer opens on the way back from granting it.
 *
 * `internal` rather than file-private since ARC-08: the state is owned by `SettingsScreen`, which
 * owns the coroutine scope the download runs in, so the type has to be visible there too.
 */
internal sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object UpToDate : UpdateUiState

    /**
     * The check could not be completed, so nothing is known.
     *
     * A state of its own rather than an [Error]: nothing broke, nothing was downloaded and there is
     * nothing to clean up -- the question simply was not answered. It is also not [UpToDate], which
     * is the whole point (see [checkForUpdate]).
     */
    data class CheckFailed(val reason: UpdateCheckResult.Unreachable.Reason) : UpdateUiState

    data class Available(val info: UpdateInfo) : UpdateUiState

    /** [percent] is -1 while the size is unknown. */
    data class Downloading(val percent: Int) : UpdateUiState

    /** Every byte has arrived; the checksum and the package are being checked. */
    data object Verifying : UpdateUiState
    data class ReadyToInstall(val apk: java.io.File) : UpdateUiState
    data class NeedsPermission(val apk: java.io.File) : UpdateUiState
    data class Error(val message: String, val releasePageUrl: String? = null) : UpdateUiState

    /** A check may be started from any state except one already in flight. */
    val allowsChecking: Boolean
        get() = this !is Checking && this !is Downloading && this !is Verifying
}

/**
 * The explicit check -- the one behind the button the user just pressed.
 *
 * All three of [UpdateCheckResult]'s answers are reported, and that is the v3.1 change: this used
 * to read a nullable and call every null "You're up to date", so in aeroplane mode the app
 * confidently told the user their version was current without having asked anybody. The automatic
 * check at launch (`SettingsScreen`) still ignores the failure -- silence is right for a question
 * nobody asked -- which is the whole reason the two outcomes had to become distinguishable rather
 * than the failure simply being reported everywhere.
 */
private suspend fun checkForUpdate(store: UpdateReplyStore, onUpdateFound: (UpdateInfo) -> Unit): UpdateUiState {
    // The reply kept from the last check answers for a list GitHub says has not changed (v5.10D).
    val result = UpdateChecker.checkForUpdate(BuildConfig.VERSION_NAME, store)
    if (result is UpdateCheckResult.Available) onUpdateFound(result.info)
    return updateStateFor(result)
}

/** What *Advanced & about* shows for a finished check: shared by its button and a tapped notification. */
internal fun updateStateFor(result: UpdateCheckResult): UpdateUiState = when (result) {
    is UpdateCheckResult.Available -> UpdateUiState.Available(result.info)
    UpdateCheckResult.UpToDate -> UpdateUiState.UpToDate
    is UpdateCheckResult.Unreachable -> UpdateUiState.CheckFailed(result.reason)
}

/**
 * Whether "Install update" for [tag] may start a download now: always for a release not started
 * yet, and for the one [startedTag] names only if its download is no longer under way or done --
 * after a failure (an error, or a fresh check that found it again) it starts again.
 */
internal fun mayStartDownload(state: UpdateUiState, startedTag: String?, tag: String): Boolean =
    startedTag != tag || when (state) {
        is UpdateUiState.Downloading, UpdateUiState.Verifying,
        is UpdateUiState.ReadyToInstall, is UpdateUiState.NeedsPermission -> false
        else -> true
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
            signedByThisApp = ApkInstaller.signedByThisApp(context, result.apk),
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
            InstallVerdict.WrongSignature -> {
                ApkDownloader.clearCache(context)
                UpdateUiState.Error(
                    "That download isn't signed by whoever built the copy on this phone. " +
                        "Nothing was installed.",
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
        // Nothing to offer, nothing to clean up. A failed check belongs here rather than with the
        // errors below: the row's own supporting line already says what happened, and there is no
        // action to put in front of the user beyond pressing the same button again.
        UpdateUiState.Idle, UpdateUiState.Checking, UpdateUiState.UpToDate -> Unit
        is UpdateUiState.CheckFailed -> Unit

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

        UpdateUiState.Verifying -> Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("Verifying...", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
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

/** Each refusal, as a sentence a non-technical user can act on. */
internal fun describe(error: BackupImportError): String = when (error) {
    BackupImportError.NotJson ->
        "That file is not a PaperScrape backup -- it could not be read at all. Nothing changed."
    is BackupImportError.WrongKind ->
        if (error.found == "paperscrape-theme") {
            "That is a shared theme file, not a whole-app backup. Import it from the Themes " +
                "gallery instead. Nothing changed."
        } else {
            "That file is not a PaperScrape backup. Nothing changed."
        }
    is BackupImportError.TooNew ->
        "That backup was made by a newer version of PaperScrape (format ${error.fileVersion}, " +
            "this build reads ${error.supported}). Update the app and try again. Nothing changed."
    is BackupImportError.Malformed ->
        "That backup is incomplete or damaged (${error.what}). Nothing changed."
}
