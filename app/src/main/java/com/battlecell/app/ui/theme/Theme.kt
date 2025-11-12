package com.battlecell.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = AccentGold,
    onPrimary = Color(0xFF2B1A0D),
    secondary = DarkSage,
    onSecondary = Color(0xFFECE3CF),
    background = DarkMidnight,
    onBackground = Color(0xFFF1E4D2),
    surface = DarkSurface,
    onSurface = Color(0xFFF1E4D2),
    error = AccentCrimson,
    outline = Color(0xFF67543B)
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBronze,
    onPrimary = Color.White,
    secondary = SecondarySage,
    onSecondary = Color(0xFF1F271C),
    background = LightParchment,
    onBackground = Color(0xFF2D2213),
    surface = LightSurface,
    onSurface = Color(0xFF2D2213),
    surfaceVariant = LightOutline,
    onSurfaceVariant = Color(0xFF4A3B26),
    error = AccentCrimson,
    outline = Color(0xFFB49E82)
)

@Composable
fun BattleCellTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(view.context)
            else dynamicLightColorScheme(view.context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = BattleCellTypography,
        content = content
    )
}
