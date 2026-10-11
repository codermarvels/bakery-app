package com.vsjbakery.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Brown700,
    onPrimary = Cream100,
    primaryContainer = Cream300,
    onPrimaryContainer = Brown900,
    secondary = Gold500,
    onSecondary = Brown900,
    secondaryContainer = Gold100,
    onSecondaryContainer = Brown800,
    tertiary = Gold700,
    onTertiary = Cream50,
    background = Cream100,
    onBackground = Brown900,
    surface = Cream50,
    onSurface = Brown900,
    surfaceVariant = Cream200,
    onSurfaceVariant = Brown500,
    surfaceContainerLowest = Cream50,
    surfaceContainerLow = Cream100,
    surfaceContainer = Cream200,
    surfaceContainerHigh = Cream200,
    surfaceContainerHighest = Cream300,
    outline = Brown300,
    outlineVariant = Cream300,
    error = ErrorRed,
)

private val DarkColors = darkColorScheme(
    primary = Gold300,
    onPrimary = Brown900,
    primaryContainer = Brown700,
    onPrimaryContainer = Cream100,
    secondary = Gold500,
    onSecondary = Brown900,
    secondaryContainer = Brown700,
    onSecondaryContainer = Gold100,
    tertiary = Gold300,
    onTertiary = Brown900,
    background = Brown900,
    onBackground = Cream100,
    surface = Brown900,
    onSurface = Cream100,
    surfaceVariant = Brown800,
    onSurfaceVariant = Cream300,
    surfaceContainerLowest = Brown900,
    surfaceContainerLow = Brown900,
    surfaceContainer = Brown800,
    surfaceContainerHigh = Brown800,
    surfaceContainerHighest = Brown700,
    outline = Brown300,
    outlineVariant = Brown700,
)

private val VsjShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/**
 * App-wide Material 3 theme. Dynamic (wallpaper) colours are deliberately not used so the
 * bakery's brand colours stay consistent on every phone.
 */
@Composable
fun VsjBakeryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = VsjTypography,
        shapes = VsjShapes,
        content = content,
    )
}
