package com.oscaribarra.neoplanner.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

// 1) Put YOUR colors here (centralized)
private val LightColors = lightColorScheme(
    primary = NeoPrimary,
    onPrimary = NeoOnPrimary,
    secondary = NeoSecondary,
    onSecondary = NeoOnSecondary,
    background = NeoBackground,
    onBackground = NeoOnBackground,
    surface = NeoSurface,
    onSurface = NeoOnSurface,
    error = NeoError,
    onError = NeoOnError
)

private val DarkColors = darkColorScheme(
    primary = NeoPrimaryDark,
    onPrimary = NeoOnPrimaryDark,
    secondary = NeoSecondaryDark,
    onSecondary = NeoOnSecondaryDark,
    background = NeoBackgroundDark,
    onBackground = NeoOnBackgroundDark,
    surface = NeoSurfaceDark,
    onSurface = NeoOnSurfaceDark,
    error = NeoError,
    onError = NeoOnError
)

@Composable
fun NeoPlannerTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    useDynamicColor: Boolean = true, // Android 12+
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val colorScheme =
        if (useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (useDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            if (useDarkTheme) DarkColors else LightColors
        }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
