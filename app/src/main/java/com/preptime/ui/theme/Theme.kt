package com.preptime.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.preptime.data.Settings
import com.preptime.data.ThemeMode

val Swatches = listOf(
    Color(0xFF0052FF), // blue, default
    Color(0xFF00897B),
    Color(0xFF2E7D32),
    Color(0xFF6A1B9A),
    Color(0xFFEF6C00),
    Color(0xFFC2185B),
)

// Fully rounded: pills for small parts (buttons, fields, chips), large radii for surfaces.
private val RoundShapes = Shapes(
    extraSmall = RoundedCornerShape(50),
    small = RoundedCornerShape(50),
    medium = RoundedCornerShape(28.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun PrepTheme(settings: Settings, content: @Composable () -> Unit) {
    val dark = when (settings.mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val p = Swatches.getOrElse(settings.swatch) { Swatches[0] }
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val soft = lerp(p, base.surface, 0.75f)
    val scheme = base.copy(
        primary = p,
        onPrimary = Color.White,
        primaryContainer = p,
        onPrimaryContainer = Color.White,
        secondaryContainer = soft,
        onSecondaryContainer = base.onSurface,
    )
    MaterialTheme(colorScheme = scheme, shapes = RoundShapes, content = content)
}
