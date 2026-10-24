package com.automatelinux.mywhatsapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// WhatsApp (Business) palette, 2024+ redesign. Everything on screen reads from
// these tokens, not from Material's scheme, so light and dark stay faithful.
@Immutable
data class WaColors(
    val background: Color,
    val text: Color,
    val textSecondary: Color,
    val brand: Color,          // wordmark, links, accents
    val fab: Color,
    val onFab: Color,
    val searchBg: Color,
    val chipBg: Color,
    val chipText: Color,
    val chipSelectedBg: Color,
    val chipSelectedText: Color,
    val navIndicator: Color,
    val divider: Color,
    val iconTint: Color,
    val emptyBadge: Color,     // soft circle behind empty-state icons
    val isDark: Boolean,
)

val LightWa = WaColors(
    background = Color(0xFFFFFFFF),
    text = Color(0xFF0A0A0A),
    textSecondary = Color(0xFF667781),
    brand = Color(0xFF1DAA61),
    fab = Color(0xFF1DAA61),
    onFab = Color(0xFFFFFFFF),
    searchBg = Color(0xFFF0F2F5),
    chipBg = Color(0xFFF0F2F5),
    chipText = Color(0xFF5E6B73),
    chipSelectedBg = Color(0xFFD8FDD2),
    chipSelectedText = Color(0xFF15603E),
    navIndicator = Color(0xFFD8FDD2),
    divider = Color(0xFFE9EDEF),
    iconTint = Color(0xFF1F2C34),
    emptyBadge = Color(0xFFE7FCE3),
    isDark = false,
)

val DarkWa = WaColors(
    background = Color(0xFF0B141A),
    text = Color(0xFFE9EDEF),
    textSecondary = Color(0xFF8696A0),
    brand = Color(0xFF21C063),
    fab = Color(0xFF21C063),
    onFab = Color(0xFF0B141A),
    searchBg = Color(0xFF202C33),
    chipBg = Color(0xFF202C33),
    chipText = Color(0xFF8696A0),
    chipSelectedBg = Color(0xFF103529),
    chipSelectedText = Color(0xFFD8FDD2),
    navIndicator = Color(0xFF103529),
    divider = Color(0xFF222D34),
    iconTint = Color(0xFFE9EDEF),
    emptyBadge = Color(0xFF103529),
    isDark = true,
)

val LocalWa = staticCompositionLocalOf { LightWa }

object Wa {
    val colors: WaColors @Composable get() = LocalWa.current
}

// Multiplatform theme (no Android-only dynamic color, so it compiles for iOS too).
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val wa = if (dark) DarkWa else LightWa
    val scheme = if (dark) {
        darkColorScheme(primary = wa.brand, background = wa.background, surface = wa.background, onSurface = wa.text, onBackground = wa.text)
    } else {
        lightColorScheme(primary = wa.brand, background = wa.background, surface = wa.background, onSurface = wa.text, onBackground = wa.text)
    }
    CompositionLocalProvider(LocalWa provides wa) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
