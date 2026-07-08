package com.example.record.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = Mint500,
    onPrimary = Color.Black,
    primaryContainer = Mint100,
    onPrimaryContainer = Mint900,

    secondary = Mint300,
    onSecondary = Color.Black,
    secondaryContainer = Mint100,
    onSecondaryContainer = Mint800,

    tertiary = Gray600,
    onTertiary = Color.White,
    tertiaryContainer = Gray200,
    onTertiaryContainer = Gray900,

    background = Gray50,
    onBackground = Gray900,

    surface = Color.White,
    onSurface = Gray900,

    surfaceVariant = Gray100,
    onSurfaceVariant = Gray700
)

private val DarkColorScheme = darkColorScheme(
    primary = Mint300,
    onPrimary = Gray900,
    primaryContainer = Mint700,
    onPrimaryContainer = Color.White,

    secondary = Mint200,
    onSecondary = Gray900,
    secondaryContainer = Mint800,
    onSecondaryContainer = Color.White,

    tertiary = Gray400,
    onTertiary = Gray900,
    tertiaryContainer = Gray700,
    onTertiaryContainer = Color.White,

    background = Gray950,
    onBackground = Color.White,

    surface = Gray900,
    onSurface = Color.White,

    surfaceVariant = Gray800,
    onSurfaceVariant = Gray300
)

@Composable
fun RecordTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
