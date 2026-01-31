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
import androidx.compose.material3.lightColorScheme


private val DarkColors = darkColorScheme(
    // ✅ roxo como cor principal do app (botões / seleção nav)
    primary = Purple792BEE,
    onPrimary = TextPrimary,

    // ✅ verde-lima como secundária (XP, sucesso, destaques)
    secondary = LimeA0EE2B,
    onSecondary = Bg,

    // ✅ cyan como terciária (detalhe/neon)
    tertiary = NeonPurple,
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
    // Brand
    primary = Purple792BEE,
    onPrimary = androidx.compose.ui.graphics.Color.White,

    secondary = LimeA0EE2B,
    onSecondary = androidx.compose.ui.graphics.Color(0xFF0B0F16),

    tertiary = NeonPurple,
    onTertiary = androidx.compose.ui.graphics.Color.White,

    // Containers (tint neon “soft”)
    primaryContainer = androidx.compose.ui.graphics.Color(0xFFEDE4FF),   // roxo bem suave
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF1C1033),

    secondaryContainer = androidx.compose.ui.graphics.Color(0xFFE9FFD6), // verde suave
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF0D1A08),

    // Base surfaces (frio, “futurista”, sem sujar)
    background = androidx.compose.ui.graphics.Color(0xFFF7F8FC),         // off-white frio
    onBackground = androidx.compose.ui.graphics.Color(0xFF0B0F16),

    surface = androidx.compose.ui.graphics.Color(0xFFFFFFFF),           // branco único
    onSurface = androidx.compose.ui.graphics.Color(0xFF0B0F16),

    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFF1F3FA),    // cinza frio (chips/cards secundários)
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF2D3445),

    outline = androidx.compose.ui.graphics.Color(0xFFD4D8E6),

    error = androidx.compose.ui.graphics.Color(0xFFB3261E),
    onError = androidx.compose.ui.graphics.Color.White
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

            val controller = WindowCompat.getInsetsController(window, view)
            val useDarkIcons = !darkTheme // tema claro => ícones escuros

            controller.isAppearanceLightStatusBars = useDarkIcons
            controller.isAppearanceLightNavigationBars = useDarkIcons
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
