package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = ParadiseTealAccent,
    secondary = ParadiseSand,
    tertiary = ParadiseGreen,
    background = ParadiseDeepTeal,
    surface = ParadiseCoal,
    onPrimary = ParadiseIvory,
    onSecondary = ParadiseCoal,
    onTertiary = ParadiseCoal,
    onBackground = ParadiseIvory,
    onSurface = ParadiseIvory,
    error = ParadiseRed
)

private val LightColorScheme = lightColorScheme(
    primary = ParadiseMediumTeal,
    secondary = ParadiseTealAccent,
    tertiary = ParadiseSand,
    background = ParadiseIvory,
    surface = ParadiseGrayLight,
    onPrimary = ParadiseIvory,
    onSecondary = ParadiseIvory,
    onTertiary = ParadiseCoal,
    onBackground = ParadiseCoal,
    onSurface = ParadiseCoal,
    error = ParadiseRed
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Use our highly artistic theme colors instead of generic dynamic light
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
