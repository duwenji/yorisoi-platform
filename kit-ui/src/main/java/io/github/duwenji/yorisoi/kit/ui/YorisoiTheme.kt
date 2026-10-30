package io.github.duwenji.yorisoi.kit.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** The yorisoi green, as on the launcher icons (drawable/yorisoi_launcher_background). */
private val Green = Color(0xFF2E7D32)
private val GreenContainer = Color(0xFFD7EED5)
private val OnGreenContainer = Color(0xFF0B2E0F)
private val Background = Color(0xFFFBFDF8)

private val GreenDark = Color(0xFF81C784)
private val GreenContainerDark = Color(0xFF1F3A21)
private val BackgroundDark = Color(0xFF111411)

/** The yorisoi colors, light or [dark]; kit-mood's MoodTheme starts from these with no mood. */
fun yorisoiColorScheme(dark: Boolean): ColorScheme = if (dark) {
    darkColorScheme(
        primary = GreenDark, onPrimary = BackgroundDark,
        primaryContainer = GreenContainerDark, onPrimaryContainer = GreenContainer,
        secondary = GreenDark, secondaryContainer = GreenContainerDark, onSecondaryContainer = GreenContainer,
        background = BackgroundDark, surface = BackgroundDark,
    )
} else {
    lightColorScheme(
        primary = Green, onPrimary = Color.White,
        primaryContainer = GreenContainer, onPrimaryContainer = OnGreenContainer,
        secondary = Green, secondaryContainer = GreenContainer, onSecondaryContainer = OnGreenContainer,
        background = Background, surface = Background,
    )
}

/** The theme of a yorisoi app: the green, dark when [dark] (usually isSystemInDarkTheme()). */
@Composable
fun YorisoiTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = yorisoiColorScheme(dark), content = content)
}
