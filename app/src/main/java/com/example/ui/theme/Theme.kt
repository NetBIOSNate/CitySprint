package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val CityColorScheme = darkColorScheme(
    primary = CityCyan,
    onPrimary = Slate950,
    primaryContainer = CityCyanDark,
    secondary = CityIndigo,
    onSecondary = Slate50,
    background = Slate950,
    onBackground = Slate50,
    surface = Slate900,
    onSurface = Slate50,
    surfaceVariant = Slate800,
    onSurfaceVariant = Slate400,
    error = Color(0xFFEF4444)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force dark theme for city vibe
    dynamicColor: Boolean = false, // Disable dynamic to ensure our premium theme is used
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = CityColorScheme, typography = Typography, content = content)
}
