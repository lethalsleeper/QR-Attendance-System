package com.example.qrattendance.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Indigo600,
    onPrimary = Color.White,
    primaryContainer = Indigo100,
    onPrimaryContainer = Indigo950,
    secondary = Sky600,
    onSecondary = Color.White,
    secondaryContainer = Sky100,
    onSecondaryContainer = Sky900,
    tertiary = Emerald600,
    onTertiary = Color.White,
    tertiaryContainer = Emerald100,
    onTertiaryContainer = Emerald900,
    error = Red600,
    onError = Color.White,
    errorContainer = Red100,
    onErrorContainer = Red900,
    background = Slate50,
    onBackground = Slate900,
    surface = Color.White,
    onSurface = Slate900,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate600,
    surfaceTint = Indigo600,
    outline = Slate300,
    outlineVariant = Slate200,
    inverseSurface = Slate800,
    inverseOnSurface = Slate50,
    inversePrimary = Indigo300,
    scrim = Color.Black,
    surfaceBright = Color.White,
    surfaceDim = Slate100,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Slate50,
    surfaceContainer = Slate100,
    surfaceContainerHigh = Slate100,
    surfaceContainerHighest = Slate200,
)

private val DarkColors = darkColorScheme(
    primary = Indigo300,
    onPrimary = Indigo950,
    primaryContainer = Indigo800,
    onPrimaryContainer = Indigo100,
    secondary = Sky300,
    onSecondary = Sky900,
    secondaryContainer = Sky800,
    onSecondaryContainer = Sky100,
    tertiary = Emerald300,
    onTertiary = Emerald900,
    tertiaryContainer = Emerald800,
    onTertiaryContainer = Emerald100,
    error = Red300,
    onError = Red900,
    errorContainer = Red800,
    onErrorContainer = Red100,
    background = Night900,
    onBackground = Slate100,
    surface = Night800,
    onSurface = Slate100,
    surfaceVariant = Night700,
    onSurfaceVariant = Slate400,
    surfaceTint = Indigo300,
    outline = Slate600,
    outlineVariant = Night600,
    inverseSurface = Slate100,
    inverseOnSurface = Slate900,
    inversePrimary = Indigo600,
    scrim = Color.Black,
    surfaceBright = Night700,
    surfaceDim = Night900,
    surfaceContainerLowest = Night900,
    surfaceContainerLow = Night800,
    surfaceContainer = Night700,
    surfaceContainerHigh = Night700,
    surfaceContainerHighest = Night600,
)

/** Colours outside the Material scheme: success/warning states and brand header gradient. */
@Immutable
data class ExtendedColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val headerStart: Color,
    val headerEnd: Color,
    val onHeader: Color,
    val onHeaderMuted: Color,
)

private val LightExtendedColors = ExtendedColors(
    success = Emerald600,
    onSuccess = Color.White,
    successContainer = Emerald100,
    onSuccessContainer = Emerald900,
    warning = Amber600,
    warningContainer = Amber100,
    onWarningContainer = Amber900,
    headerStart = Indigo600,
    headerEnd = Violet600,
    onHeader = Color.White,
    onHeaderMuted = Indigo100,
)

private val DarkExtendedColors = ExtendedColors(
    success = Emerald300,
    onSuccess = Emerald900,
    successContainer = Emerald800,
    onSuccessContainer = Emerald100,
    warning = Amber300,
    warningContainer = Amber800,
    onWarningContainer = Amber100,
    headerStart = Indigo900,
    headerEnd = Violet900,
    onHeader = Color.White,
    onHeaderMuted = Indigo200,
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }

val MaterialTheme.extendedColors: ExtendedColors
    @Composable
    @ReadOnlyComposable
    get() = LocalExtendedColors.current

@Composable
fun QRAttendanceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors
    CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
