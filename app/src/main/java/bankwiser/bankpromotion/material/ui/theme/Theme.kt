package bankwiser.bankpromotion.material.ui.theme

import android.app.Activity
// import androidx.compose.foundation.isSystemInDarkTheme // Comment out for now
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme( // Keep for future, but won't be used
    primary = PrimaryIndigo,
    secondary = GradientEndPurple,
    tertiary = PrimaryIndigoDark,
    background = Color(0xFF1A202C),
    surface = TextPrimary,
    onPrimary = TextOnPrimary,
    onSecondary = TextOnPrimary,
    onTertiary = TextOnPrimary,
    onBackground = BackgroundLight,
    onSurface = BackgroundLight,
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryIndigo,
    secondary = GradientEndPurple,
    tertiary = PrimaryIndigoDark,
    background = BackgroundLight,
    surface = SurfaceLight,
    onPrimary = TextOnPrimary,
    onSecondary = TextOnPrimary,
    onTertiary = TextOnPrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
)

@Composable
fun BankWiserProTheme(
    // darkTheme: Boolean = isSystemInDarkTheme(), // Force light theme
    darkTheme: Boolean = false, // Force light theme
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme // Logic remains but darkTheme is false
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // For light theme, status bar icons should be dark.
            // For dark theme, status bar icons should be light.
            // isAppearanceLightStatusBars = true means dark icons.
            window.statusBarColor = colorScheme.primary.toArgb() // Or a specific status bar color from mockup
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography, // Ensure your Type.kt defines appropriate typography
        content = content
    )
}
