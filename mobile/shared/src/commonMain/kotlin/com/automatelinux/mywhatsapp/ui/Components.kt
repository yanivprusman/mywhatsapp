package com.automatelinux.mywhatsapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.automatelinux.mywhatsapp.ui.theme.Wa

// ── Top bars ────────────────────────────────────────────────────────────────

@Composable
fun HomeTopBar(
    title: String,
    brandTitle: Boolean,
    actions: @Composable () -> Unit,
) {
    val c = Wa.colors
    Row(
        Modifier.fillMaxWidth().background(c.background).statusBarsPadding()
            .height(64.dp).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            color = if (brandTitle) c.brand else c.text,
            fontSize = if (brandTitle) 24.sp else 22.sp,
            fontWeight = if (brandTitle) FontWeight.Bold else FontWeight.Medium,
            letterSpacing = if (brandTitle) (-0.3).sp else 0.sp,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

@Composable
fun SubTopBar(title: String, subtitle: String?, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    val c = Wa.colors
    Row(
        Modifier.fillMaxWidth().background(c.background).statusBarsPadding()
            .height(64.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", "back", onBack)
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(title, color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(subtitle, color = c.textSecondary, fontSize = 13.sp)
        }
        actions()
    }
}

@Composable
fun BarIcon(icon: ImageVector, label: String, tag: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.testTag(tag)) {
        Icon(icon, contentDescription = label, tint = Wa.colors.iconTint)
    }
}

@Composable
fun OverflowMenu(items: List<String>, tag: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        BarIcon(Icons.Filled.MoreVert, "More options", tag) { open = true }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = if (Wa.colors.isDark) Color(0xFF233138) else Color.White,
            shape = RoundedCornerShape(16.dp),
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item, color = Wa.colors.text, fontSize = 16.sp) },
                    onClick = { open = false; onPick(item) },
                    modifier = Modifier.testTag("menu-" + slug(item)).width(220.dp),
                )
            }
        }
    }
}

// ── Bottom navigation ───────────────────────────────────────────────────────

data class NavTab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

@Composable
fun WaBottomNav(tabs: List<NavTab>, selected: Int, onSelect: (Int) -> Unit) {
    val c = Wa.colors
    Column(Modifier.fillMaxWidth().background(c.background)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().height(80.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { i, tab ->
                val on = i == selected
                Column(
                    Modifier.weight(1f)
                        .clickable { onSelect(i) }
                        .testTag("tab-" + slug(tab.label))
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.size(width = 64.dp, height = 32.dp).clip(CircleShape)
                            .background(if (on) c.navIndicator else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (on) tab.selectedIcon else tab.icon,
                            contentDescription = tab.label,
                            tint = if (on && !c.isDark) Color(0xFF0A3D24) else c.iconTint,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        tab.label,
                        color = c.text,
                        fontSize = 12.sp,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}

// ── Building blocks ─────────────────────────────────────────────────────────

@Composable
fun FilterChip(label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    val c = Wa.colors
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) c.chipSelectedBg else c.chipBg)
            .clickable(onClick = onClick)
            .testTag(tag)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            color = if (selected) c.chipSelectedText else c.chipText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
fun DefaultAvatar(size: Dp = 52.dp) {
    val c = Wa.colors
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(if (c.isDark) Color(0xFF6A7175) else Color(0xFFDFE5E7)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Icon(
            Icons.Filled.Person, null,
            tint = if (c.isDark) Color(0xFFCFD4D6) else Color.White,
            modifier = Modifier.size(size * 0.92f).padding(top = size * 0.12f),
        )
    }
}

@Composable
fun IconCircle(icon: ImageVector, bg: Color, fg: Color, size: Dp = 48.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun SectionHeader(text: String, large: Boolean = false) {
    val c = Wa.colors
    Text(
        text,
        color = if (large) c.text else c.textSecondary,
        fontSize = if (large) 20.sp else 14.sp,
        fontWeight = if (large) FontWeight.Bold else FontWeight.Medium,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = if (large) 16.dp else 20.dp, bottom = 8.dp),
    )
}

@Composable
fun ListRow(
    title: String,
    subtitle: String? = null,
    tag: String,
    leading: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val c = Wa.colors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).testTag(tag)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Column(Modifier.padding(start = 16.dp).weight(1f)) {
            Text(title, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = c.textSecondary, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun PlainIconRow(icon: ImageVector, title: String, subtitle: String? = null, tag: String, onClick: () -> Unit) {
    ListRow(title, subtitle, tag, leading = {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Wa.colors.textSecondary, modifier = Modifier.size(24.dp))
        }
    }, onClick = onClick)
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier) {
    val c = Wa.colors
    Column(
        modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconCircle(icon, c.emptyBadge, c.brand, size = 96.dp)
        Spacer(Modifier.height(20.dp))
        Text(title, color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, color = c.textSecondary, fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 21.sp)
    }
}

fun slug(s: String) = s.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
