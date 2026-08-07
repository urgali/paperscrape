package com.paperscrape.livewallpaper.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PaperOrange = Color(0xFFF2A65A)
private val PaperOrangeDark = Color(0xFFB5651D)
private val PaperCream = Color(0xFFFFF7EC)
private val PaperNight = Color(0xFF1B1B2F)

private val LightColors = lightColorScheme(
    primary = PaperOrangeDark,
    secondary = PaperOrange,
    background = PaperCream,
    surface = PaperCream,
)

private val DarkColors = darkColorScheme(
    primary = PaperOrange,
    secondary = PaperOrangeDark,
    background = PaperNight,
    surface = PaperNight,
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
