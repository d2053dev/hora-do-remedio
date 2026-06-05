package com.example.ui.theme

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

private val DarkColorScheme = darkColorScheme(
    primary = MinimalPrimaryLavender,
    secondary = MinimalPrimaryLavender,
    tertiary = ColorSuccess,
    background = MinimalDarkBg,
    surface = MinimalDarkSurface,
    surfaceVariant = MinimalDarkSurface,
    onPrimary = MinimalDeepAmethyst,
    onSecondary = MinimalDeepAmethyst,
    onTertiary = ColorWhite,
    onBackground = Color(0xFFE6E1E5),
    onSurface = Color(0xFFE6E1E5),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = MinimalDarkBorder,
    outlineVariant = MinimalDarkBorder,
    primaryContainer = MinimalAccentPass,
    onPrimaryContainer = MinimalDeepViolet,
    error = ColorDanger
)

private val LightColorScheme = lightColorScheme(
    primary = MinimalLightPrimary,
    secondary = MinimalLightPrimary,
    tertiary = ColorSuccess,
    background = MinimalLightBg,
    surface = MinimalLightSurface,
    surfaceVariant = MinimalLightBorder,
    onPrimary = ColorWhite,
    onSecondary = ColorWhite,
    onTertiary = ColorWhite,
    onBackground = Color(0xFF1D1B20),
    onSurface = Color(0xFF1D1B20),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),
    primaryContainer = MinimalLightPrimaryContainer,
    onPrimaryContainer = MinimalLightOnPrimaryContainer,
    error = ColorDanger
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Disabling dynamic colors by default so our custom theme is highlighted
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
