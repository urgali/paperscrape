package com.paperscrape.livewallpaper.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// ---------------------------------------------------------------------------------------------
// Structure
//
// One vocabulary for every settings screen: a section header, a rounded group that holds rows,
// and rows that are either navigation, a switch, or read-only status. Grouping is what replaced
// v2.8's single 20 dp-spaced column, and the container colour is what replaced its dividers --
// see `DESIGN_NOTES.md` on the Material 3 scope being the settings UI only.
// ---------------------------------------------------------------------------------------------

/** A Material 3 section label. Sits above a [SettingsGroup], never inside one. */
@Composable
internal fun SettingsSectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 32.dp, end = 32.dp, top = 20.dp, bottom = 8.dp),
    )
}

/**
 * The rounded container a run of related rows sits in.
 *
 * Rows inside it need no divider: the container is what says "these belong together", which is
 * the whole reason the flat list needed one. Nesting these is deliberately not supported.
 */
@Composable
internal fun SettingsGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        content = { Column(content = content) },
    )
}

/**
 * One row of a [SettingsGroup]: optional leading icon, title, optional supporting line, optional
 * trailing control.
 *
 * [supportingIsAccent] paints the supporting line in the primary colour, which is how a row
 * reports that something is on ("Live Weather on", "Driven by Live Weather") without needing a
 * badge or a second control.
 */
@Composable
internal fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    supportingIsAccent: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val titleColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
    val supportingColor = when {
        !enabled -> MaterialTheme.colorScheme.outline
        supportingIsAccent -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(24.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            if (!supporting.isNullOrBlank()) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodyMedium,
                    color = supportingColor,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        trailing?.invoke()
    }
}

/** A row that drills down into another screen. */
@Composable
internal fun SettingsNavigationRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    icon: ImageVector? = null,
    supportingIsAccent: Boolean = false,
) {
    SettingsRow(
        title = title,
        modifier = modifier,
        supporting = supporting,
        icon = icon,
        supportingIsAccent = supportingIsAccent,
        onClick = onClick,
        trailing = {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

/** A row whose whole surface toggles a switch. */
@Composable
internal fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    SettingsRow(
        title = title,
        modifier = modifier,
        supporting = supporting,
        icon = icon,
        enabled = enabled,
        onClick = { onCheckedChange(!checked) },
        trailing = { Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled) },
    )
}

/** Explanatory text under a group. Never inside one -- a caption is not a row. */
@Composable
internal fun SettingsCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 32.dp, end = 32.dp, top = 8.dp),
    )
}

/**
 * A filled note that states a rule the user is currently subject to -- "read-only while Live
 * Weather is on", "these edits belong to this theme". Distinct from [SettingsCaption] in weight,
 * not in kind: use it when ignoring the text would surprise the user.
 */
@Composable
internal fun SettingsBanner(text: String, modifier: Modifier = Modifier, isError: Boolean = false) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(14.dp),
        )
    }
}

/**
 * A single-choice segmented button row.
 *
 * Used for the two places where a set of mutually exclusive booleans is really one choice --
 * location source and seasonal palette. It writes nothing itself; see [SettingsUiModel] for the
 * mapping back to the preferences those choices have always been stored as.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsSegmentedChoice(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, label ->
            SegmentedButton(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                // One line, ellipsised. A segmented button gives its label a fixed share of the
                // width and does not clip it: a label too long for its share wraps and draws
                // outside the control's own outline, over its neighbours' borders. Adding a third
                // weather provider is what found that; bounding it here is what stops the fourth
                // finding it again.
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Trailing space under the last row of a scrolling screen.
 *
 * One spacer, used by every settings screen, sized by [SettingsInsets.bottomSpacing] from the
 * inset the activity measured -- rather than each screen carrying a padding of its own, which is
 * how the last row ended up half under the gesture bar on some of them and not others.
 */
@Composable
internal fun SettingsBottomSpacer() {
    // A constant. The system inset is reserved by the screen's Scaffold, outside the scroll
    // container, so this is only the gap between the last row and that reservation -- see
    // SettingsInsets for why the reservation moved out of here.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(SettingsInsets.BOTTOM_BREATHING_ROOM),
    )
}

// ---------------------------------------------------------------------------------------------
// Screen shells
// ---------------------------------------------------------------------------------------------


/**
 * The shell every settings destination uses: a full-screen dialog with a back arrow and a
 * scrolling body.
 *
 * Still a `Dialog` rather than a navigation graph, deliberately -- the drill-down mechanism the
 * project already had works, handles the system back gesture through `onDismissRequest`, and
 * replacing it would be an architectural refactor with no user-visible result. What changed in
 * v2.9 is that every destination now uses a back arrow (they are all drill-downs from the home
 * screen) instead of a close cross at the top level and an arrow one level down.
 *
 * The body has **no padding**: [SettingsGroup] carries its own margins, so a screen made of
 * groups lines up on its own. Screens made of form controls use [SettingsFormSubScreen].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsSubScreen(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onBack, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            // **Not fillMaxSize.** The dialog's content is measured against the display, its
            // window is only as tall as the space between the system bars, and the difference was
            // laid out off-window and clipped. See SettingsInsets for the measurement and for why
            // this is a height and not a padding.
            modifier = Modifier.fillMaxWidth().height(settingsDialogHeight()),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Scaffold(
                // The dialog window's own insets, which are zero exactly when the window already
                // fits the system bars -- the case the height above is sized for. A device whose
                // dialog window is full-bleed instead reports real values here and they are
                // reserved normally. Neither case needs to know which one it is.
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = {
                    TopAppBar(
                        title = { Text(title) },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                    )
                },
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        // Before the scroll modifier, so the scrollable area itself shrinks when
                        // the keyboard opens: the city search field stays visible with its results
                        // rather than being pushed under it.
                        .imePadding()
                        .verticalScroll(rememberScrollState()),
                ) {
                    content()
                    SettingsBottomSpacer()
                }
            }
        }
    }
}

/**
 * The same shell for the leaf screens that are a form rather than a list -- every Scene Objects
 * category, every decoration's options. Identical to what those screens have always used: 16 dp
 * padding, 12 dp between controls.
 */
@Composable
internal fun SettingsFormSubScreen(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    SettingsSubScreen(title = title, onBack = onBack) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

/** A bold label inside a form screen, above a run of related controls. */
@Composable
internal fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

/**
 * The switch row used inside form screens (Scene Objects categories and decoration options),
 * where controls are laid out directly rather than in a [SettingsGroup].
 */
@Composable
internal fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
            )
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

// ---------------------------------------------------------------------------------------------
// Colour editing -- unchanged behaviour, moved here so every screen can reach it
// ---------------------------------------------------------------------------------------------

internal data class ColorEditTarget(val label: String, val color: Int, val onChange: (Int) -> Unit)

@Composable
internal fun ColorSwatchRow(label: String, color: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(color))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
        )
    }
}

internal fun colorToHex(color: Int): String = String.format("#%06X", color and 0x00FFFFFF)

internal fun parseHexColor(text: String): Int? {
    val cleaned = text.trim().removePrefix("#")
    if (cleaned.length != 6) return null
    return try {
        val rgb = cleaned.toLong(16).toInt()
        (0xFF shl 24) or rgb
    } catch (e: NumberFormatException) {
        null
    }
}

/**
 * Touch-and-drag HSV color editor: a saturation/brightness square you drag your finger across
 * (classic palette-picker UX), a hue strip below it, and an editable hex field kept in sync both
 * ways -- dragging updates the hex text, and typing a valid hex value updates the picker.
 */
@Composable
internal fun ColorPickerDialog(
    title: String,
    initialColor: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialHsv = remember(initialColor) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(initialColor, hsv)
        hsv
    }
    var hue by remember { mutableStateOf(initialHsv[0]) }
    var saturation by remember { mutableStateOf(initialHsv[1]) }
    var brightness by remember { mutableStateOf(initialHsv[2]) }
    var hexInput by remember { mutableStateOf(colorToHex(initialColor)) }

    fun updateFromHsv(h: Float, s: Float, v: Float) {
        hue = h
        saturation = s
        brightness = v
        hexInput = colorToHex(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)))
    }

    val currentColor = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(currentColor))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                )
                SaturationBrightnessSquare(
                    hue = hue,
                    saturation = saturation,
                    brightness = brightness,
                    onChange = { s, v -> updateFromHsv(hue, s, v) },
                    modifier = Modifier.fillMaxWidth(),
                )
                HueStrip(
                    hue = hue,
                    onChange = { h -> updateFromHsv(h, saturation, brightness) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { text ->
                        hexInput = text
                        parseHexColor(text)?.let { parsed ->
                            val hsv = FloatArray(3)
                            android.graphics.Color.colorToHSV(parsed, hsv)
                            hue = hsv[0]; saturation = hsv[1]; brightness = hsv[2]
                        }
                    },
                    label = { Text("Hex") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(currentColor) }) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/**
 * The classic "drag your finger across the palette" square: horizontal axis is saturation
 * (white -> full hue color), vertical axis is brightness (bright at top, black at bottom).
 * Responds to both a direct tap (jump straight to that color) and dragging.
 */
@Composable
private fun SaturationBrightnessSquare(
    hue: Float,
    saturation: Float,
    brightness: Float,
    onChange: (saturation: Float, brightness: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hueColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(1.4f)
            .clip(RoundedCornerShape(12.dp)),
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }

        fun reportFromOffset(offset: Offset) {
            val s = (offset.x / widthPx).coerceIn(0f, 1f)
            val v = 1f - (offset.y / heightPx).coerceIn(0f, 1f)
            onChange(s, v)
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(hue) {
                    detectDragGestures(
                        onDragStart = { offset -> reportFromOffset(offset) },
                        onDrag = { change, _ -> change.consume(); reportFromOffset(change.position) },
                    )
                }
                .pointerInput(hue) {
                    detectTapGestures { offset -> reportFromOffset(offset) }
                },
        ) {
            drawRect(brush = Brush.horizontalGradient(listOf(Color.White, hueColor)))
            drawRect(brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        }

        val indicatorSize = 22.dp
        val indicatorX = with(density) { (saturation * widthPx).toDp() } - indicatorSize / 2
        val indicatorY = with(density) { ((1f - brightness) * heightPx).toDp() } - indicatorSize / 2
        Box(
            modifier = Modifier
                .offset(x = indicatorX, y = indicatorY)
                .size(indicatorSize)
                .clip(CircleShape)
                .border(3.dp, Color.White, CircleShape)
                .border(1.dp, Color.Black.copy(alpha = 0.25f), CircleShape),
        )
    }
}

/** A draggable rainbow strip for picking the hue (0-360 degrees). */
@Composable
private fun HueStrip(hue: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val hueColors = remember {
        (0..360 step 30).map { Color(android.graphics.Color.HSVToColor(floatArrayOf(it.toFloat(), 1f, 1f))) }
    }
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp)),
    ) {
        val widthPx = with(density) { maxWidth.toPx() }

        fun reportFromX(x: Float) {
            onChange((x / widthPx).coerceIn(0f, 1f) * 360f)
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset -> reportFromX(offset.x) },
                        onDrag = { change, _ -> change.consume(); reportFromX(change.position.x) },
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset -> reportFromX(offset.x) }
                },
        ) {
            drawRect(brush = Brush.horizontalGradient(hueColors))
        }

        val thumbWidth = 4.dp
        val thumbX = with(density) { (hue / 360f * widthPx).toDp() } - thumbWidth / 2
        Box(
            modifier = Modifier
                .offset(x = thumbX)
                .width(thumbWidth)
                .fillMaxHeight()
                .background(Color.White)
                .border(1.dp, Color.Black.copy(alpha = 0.3f)),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Sliders
// ---------------------------------------------------------------------------------------------

/**
 * A [Slider] that persists its value **once, when the drag ends**, instead of on every
 * intermediate position.
 *
 * Every slider in this screen used to write straight to DataStore from `onValueChange`. One drag
 * therefore produced dozens of disk writes, dozens of preference-flow emissions, dozens of
 * recompositions, and -- because any configuration difference used to reconstruct the whole
 * scene renderer -- dozens of full scene rebuilds, which restarted every car from its start
 * delay. The thumb also had a disk round trip inside its own feedback loop, which is what made
 * the sliders feel like they stuck near the ends of the track rather than following the finger.
 *
 * The in-flight value lives in local state for the duration of the drag only, so there is no
 * lasting duplicate of the preference. [label] receives the value actually being displayed, so a
 * caption like "Density: 42%" keeps updating live while dragging even though nothing is written
 * until the finger lifts.
 *
 * See [SliderDragState] for the handover rules and why the local value is not dropped the instant
 * the drag ends.
 */
@Composable
internal fun PreferenceSlider(
    value: Float,
    onCommit: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true,
    label: (@Composable (Float) -> Unit)? = null,
) {
    var inFlight by remember { mutableStateOf<Float?>(null) }
    var awaitingCommit by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(value, awaitingCommit) {
        if (SliderDragState.shouldReleaseLocalValue(value, awaitingCommit)) {
            awaitingCommit = null
        }
    }

    val displayed = SliderDragState.displayValue(value, inFlight, awaitingCommit)
    label?.invoke(displayed)
    Slider(
        value = displayed,
        onValueChange = { inFlight = it },
        onValueChangeFinished = {
            val settled = inFlight
            if (SliderDragState.shouldCommit(value, settled) && settled != null) {
                awaitingCommit = settled
                onCommit(settled)
            }
            inFlight = null
        },
        valueRange = valueRange,
        steps = steps,
        enabled = enabled,
        modifier = modifier,
    )
}

/**
 * A [PreferenceSlider] wrapped in the label/description layout the settings screens use, inside a
 * [SettingsGroup]. Keeps the slider's own commit-on-release behaviour untouched.
 */
@Composable
internal fun SettingsSliderRow(
    title: String,
    valueLabel: (Float) -> String,
    value: Float,
    onCommit: (Float) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
        PreferenceSlider(
            value = value,
            onCommit = onCommit,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            label = { shown ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                    )
                    Text(
                        valueLabel(shown),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    )
                }
                if (!supporting.isNullOrBlank()) {
                    Text(
                        supporting,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }
}
