package com.automatelinux.mywhatsapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.automatelinux.mywhatsapp.data.Chat
import com.automatelinux.mywhatsapp.ui.theme.Wa

private val filterHelp = mapOf(
    "all" to "Every message",
    "wanted" to "Job posts — someone looking for a worker or a quote",
    "available" to "Workers offering themselves",
    "media" to "Photos and videos",
    "links" to "Messages with a link",
)

/**
 * A chat's settings, from its ⋮ menu: which messages to show (kept per chat),
 * and for a merged chat, the groups it is made of.
 */
@Composable
fun GroupSettings(chat: Chat, filter: String, counts: Map<String, Int>, onFilter: (String) -> Unit, onBack: () -> Unit) {
    val c = Wa.colors
    Column(Modifier.fillMaxSize().background(c.background)) {
        SubTopBar("Group settings", chat.name, onBack)
        LazyColumn(Modifier.weight(1f)) {
            item { SectionHeader("Show messages") }
            messageFilters.forEach { f ->
                item {
                    Row(
                        Modifier.fillMaxWidth().clickable { onFilter(f) }.testTag("settings-filter-$f")
                            .padding(start = 8.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = filter == f, onClick = { onFilter(f) },
                            colors = RadioButtonDefaults.colors(selectedColor = c.brand, unselectedColor = c.textSecondary),
                        )
                        Column(Modifier.padding(start = 8.dp).weight(1f)) {
                            Text(filterLabel(f), color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                            Text(filterHelp[f] ?: "", color = c.textSecondary, fontSize = 14.sp)
                        }
                        counts[f]?.let { Text("$it", color = c.textSecondary, fontSize = 14.sp) }
                    }
                }
            }
            if (chat.merged) {
                item { Box(Modifier.fillMaxWidth().padding(top = 12.dp).height(1.dp).background(c.divider)) }
                item { SectionHeader("Merged from ${chat.sources.size} groups") }
                chat.sources.forEach { src ->
                    item {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            GroupAvatar(40.dp)
                            Text(src.name, color = c.text, fontSize = 16.sp, modifier = Modifier.padding(start = 14.dp))
                        }
                    }
                }
                item {
                    Text(
                        "A person's repeated post shows once, with how many times and where it was posted. Read-only — tap a post to message its sender.",
                        color = c.textSecondary, fontSize = 13.sp, lineHeight = 18.sp,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
