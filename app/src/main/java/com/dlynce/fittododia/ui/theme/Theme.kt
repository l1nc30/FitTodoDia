@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.dlynce.fittododia.ui.theme

import android.app.Activity
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val DarkColors = darkColorScheme(
    primary = NeonCyan,
    onPrimary = Bg,

    secondary = NeonPurple,
    onSecondary = Bg,

    tertiary = NeonGreen,
    onTertiary = Bg,

    background = Bg,
    onBackground = TextPrimary,

    surface = Surface,
    onSurface = TextPrimary,

    surfaceVariant = Surface2,
    onSurfaceVariant = TextSecondary,

    outline = Outline,
    error = Danger,
    onError = Bg
)

private val LightColors = lightColorScheme(
    // Mantém “dark-like” mesmo se cair no light por algum motivo
    primary = NeonCyan,
    onPrimary = Bg,
    secondary = NeonPurple,
    onSecondary = Bg,
    tertiary = NeonGreen,
    onTertiary = Bg,
    background = Bg,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextSecondary,
    outline = Outline,
    error = Danger,
    onError = Bg
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun FitTodoDiaTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.background.toArgb()
            window.navigationBarColor = colors.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
        shapes = AppShapes
    ) {
        // ✅ Isso garante fundo preto em toda a app, mesmo se alguma tela esquecer containerColor
        Surface(color = MaterialTheme.colorScheme.background) {
            content()
        }
    }
}
