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
    val chatBg: Color,         // conversation wallpaper
    val bubbleOut: Color,
    val bubbleIn: Color,
    val bubbleMeta: Color,     // time inside a bubble
    val dateChip: Color,
    val dateChipText: Color,
    val notice: Color,         // the yellow encryption notice
    val noticeText: Color,
    val link: Color,
    val tick: Color,           // read ticks
    val inputBg: Color,
    val fileCard: Color,       // document card inside a bubble
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
    chatBg = Color(0xFFEFEAE2),
    bubbleOut = Color(0xFFD9FDD3),
    bubbleIn = Color(0xFFFFFFFF),
    bubbleMeta = Color(0xFF667781),
    dateChip = Color(0xFFFFFFFF),
    dateChipText = Color(0xFF54656F),
    notice = Color(0xFFFFEECD),
    noticeText = Color(0xFF54656F),
    link = Color(0xFF027EB5),
    tick = Color(0xFF53BDEB),
    inputBg = Color(0xFFFFFFFF),
    fileCard = Color(0x0F0B141A),
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
    chatBg = Color(0xFF0B141A),
    bubbleOut = Color(0xFF144D37),
    bubbleIn = Color(0xFF202C33),
    bubbleMeta = Color(0xFF8BA69C),
    dateChip = Color(0xFF182229),
    dateChipText = Color(0xFF8696A0),
    notice = Color(0xFF182229),
    noticeText = Color(0xFFFFD279),
    link = Color(0xFF53BDEB),
    tick = Color(0xFF53BDEB),
    inputBg = Color(0xFF202C33),
    fileCard = Color(0x26000000),
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
