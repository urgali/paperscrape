package com.paperscrape.livewallpaper.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The four colours the app has always been built on. Every role below is derived from these by
 * lightening or darkening within the same hue family -- no new identity is introduced.
 *
 * `PaperOrange` and `PaperOrangeDark` are the wallpaper's own sunset hills (`SceneTheme.SUNSET`
 * uses exactly these two values in `hillColorsDay`), `PaperCream` is the paper stock the settings
 * UI is printed on, and `PaperNight` is the night sky the dark scheme sits against.
 */
private val PaperOrange = Color(0xFFF2A65A)
private val PaperOrangeDark = Color(0xFFB5651D)
private val PaperCream = Color(0xFFFFF7EC)
private val PaperNight = Color(0xFF1B1B2F)

/**
 * The full Material 3 light scheme.
 *
 * Only four roles used to be defined (`primary`, `secondary`, `background`, `surface`), so the
 * remaining roles fell back to Material 3's baseline palette -- which is violet. Switch tracks,
 * inactive slider tracks, containers, dialog surfaces and every "variant" role therefore rendered
 * off-brand next to the cream and orange the app actually is. Each value below is a tone of the
 * four constants above:
 *
 * - **primary family** -- `PaperOrangeDark` itself, with a light tint of it as the container.
 * - **secondary family** -- the same hue desaturated, so secondary containers read as paper rather
 *   than as a second accent competing with primary.
 * - **tertiary family** -- the wallpaper's own day sky (`SceneTheme.SUNSET.skyDay`), the one cool
 *   colour that already exists in the product, used sparingly.
 * - **surface family** -- `PaperCream` stepped darker for each container level, which is what
 *   gives a grouped list its container without needing a border or a divider per row.
 * - **error family** -- a warm brick rather than Material's default red, for the same reason.
 */
private val LightColors = lightColorScheme(
    primary = PaperOrangeDark,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDCC0),
    onPrimaryContainer = Color(0xFF3A1700),
    inversePrimary = PaperOrange,

    secondary = Color(0xFF9B6B45),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF7E2CC),
    onSecondaryContainer = Color(0xFF3A2312),

    tertiary = Color(0xFF3F6277),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC7E7FA),
    onTertiaryContainer = Color(0xFF001E2C),

    background = PaperCream,
    onBackground = Color(0xFF211A14),
    surface = PaperCream,
    onSurface = Color(0xFF211A14),
    surfaceVariant = Color(0xFFF2E3D3),
    onSurfaceVariant = Color(0xFF54443A),
    surfaceTint = PaperOrangeDark,
    inverseSurface = Color(0xFF372F28),
    inverseOnSurface = Color(0xFFFDEEE2),

    surfaceBright = Color(0xFFFFF8F2),
    surfaceDim = Color(0xFFE7D7C7),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFDF1E6),
    surfaceContainer = Color(0xFFF8EADC),
    surfaceContainerHigh = Color(0xFFF2E4D5),
    surfaceContainerHighest = Color(0xFFEDDDCB),

    outline = Color(0xFF877567),
    outlineVariant = Color(0xFFD9C7B7),

    error = Color(0xFFA03027),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD4),
    onErrorContainer = Color(0xFF410002),

    scrim = Color(0xFF000000),
)

/** The same families read against [PaperNight] -- lighter accents, darker containers. */
private val DarkColors = darkColorScheme(
    primary = PaperOrange,
    onPrimary = Color(0xFF4A2600),
    primaryContainer = Color(0xFF6B3A05),
    onPrimaryContainer = Color(0xFFFFDCC0),
    inversePrimary = PaperOrangeDark,

    secondary = Color(0xFFE5BE97),
    onSecondary = Color(0xFF422B12),
    secondaryContainer = Color(0xFF5B4128),
    onSecondaryContainer = Color(0xFFFFDDBC),

    tertiary = Color(0xFF9CCEE8),
    onTertiary = Color(0xFF003546),
    tertiaryContainer = Color(0xFF234C5F),
    onTertiaryContainer = Color(0xFFC7E7FA),

    background = PaperNight,
    onBackground = Color(0xFFE9E1DA),
    surface = PaperNight,
    onSurface = Color(0xFFE9E1DA),
    surfaceVariant = Color(0xFF3A3446),
    onSurfaceVariant = Color(0xFFCFC4BC),
    surfaceTint = PaperOrange,
    inverseSurface = Color(0xFFE9E1DA),
    inverseOnSurface = Color(0xFF33313F),

    surfaceBright = Color(0xFF3A3A56),
    surfaceDim = Color(0xFF131322),
    surfaceContainerLowest = Color(0xFF121221),
    surfaceContainerLow = Color(0xFF222239),
    surfaceContainer = Color(0xFF272740),
    surfaceContainerHigh = Color(0xFF31314E),
    surfaceContainerHighest = Color(0xFF3B3B5C),

    outline = Color(0xFF948E9E),
    outlineVariant = Color(0xFF494455),

    error = Color(0xFFFFB4A8),
    onError = Color(0xFF690003),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD4),

    scrim = Color(0xFF000000),
)

@Composable
fun PaperScrapeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MaterialTheme.typography,
        content = content,
    )
}
