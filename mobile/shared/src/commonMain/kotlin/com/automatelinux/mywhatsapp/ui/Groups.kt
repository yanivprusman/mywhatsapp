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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.automatelinux.mywhatsapp.data.GroupToggle
import com.automatelinux.mywhatsapp.ui.theme.Wa
import kotlinx.coroutines.launch

/**
 * Tools → Groups: switch each chat on or off, and each group inside a merged
 * chat. Kept on the server, so the phone and the desktop show the same list; a
 * hidden chat also stops raising Wanted alerts. Nothing changes in WhatsApp.
 */
@Composable
fun GroupsScreen(onBack: () -> Unit) {
    val c = Wa.colors
    val api = LocalApi.current
    val scope = rememberCoroutineScope()
    var groups by remember { mutableStateOf<List<GroupToggle>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try { groups = api.groups() } catch (e: Exception) { error = e.message ?: "unknown error" }
    }

    // Flip at once, then tell the server; a refusal flips it back and says why.
    // [kind]: "chat" (consolidated), "source" (a group in a merged chat) or "conversation" (Chats side).
    fun flip(id: String, kind: String, shown: Boolean) {
        fun apply(v: Boolean) {
            groups = groups?.map { g ->
                when (kind) {
                    "source" -> g.copy(sources = g.sources.map { s -> if (s.jid == id) s.copy(shown = v) else s })
                    "conversation" -> if (g.id == id && g.section == "chats") g.copy(shown = v) else g
                    else -> if (g.id == id && g.section != "chats") g.copy(shown = v) else g
                }
            }
        }
        apply(shown)
        error = null
        scope.launch {
            try { api.setShown(id, kind, shown) } catch (e: Exception) {
                apply(!shown); error = "Couldn't save: " + (e.message ?: "unknown error")
            }
        }
    }

    Column(Modifier.fillMaxSize().background(c.background)) {
        SubTopBar("Groups", "Choose what shows in Chats", onBack)
        val list = groups
        if (list == null) {
            Box(Modifier.weight(1f).navigationBarsPadding(), contentAlignment = Alignment.Center) {
                if (error != null) EmptyState(Icons.Filled.ErrorOutline, "Can't reach MyWhatsApp's server", error!!)
                else EmptyState(Icons.Outlined.Groups, "Loading groups…", "")
            }
            return@Column
        }
        LazyColumn(Modifier.weight(1f)) {
            error?.let { msg ->
                item { Text(msg, color = c.textSecondary, fontSize = 14.sp, modifier = Modifier.padding(16.dp).testTag("groups-error")) }
            }
            item { SectionHeader("Consolidated") }
            items(list.filter { it.section != "chats" }, key = { "c/" + it.id }) { g ->
                val on = g.sources.count { it.shown }
                ToggleRow(
                    title = g.name,
                    subtitle = when {
                        !g.shown -> "Hidden"
                        g.merged && on == 0 -> "Hidden — every group below is off"
                        g.merged -> "Merged from $on of ${g.sources.size} groups"
                        else -> null
                    },
                    checked = g.shown,
                    tag = "group-switch-" + g.id,
                    large = true,
                ) { flip(g.id, "chat", shown = it) }
            }
            // The Chats side lists every chat and group, so only the ones hidden there are here, to bring back.
            val hiddenChats = list.filter { it.section == "chats" }
            if (hiddenChats.isNotEmpty()) {
                item {
                    Column {
                        Box(Modifier.fillMaxWidth().padding(top = 12.dp).height(1.dp).background(c.divider))
                        SectionHeader("Hidden from Chats")
                    }
                }
                items(hiddenChats, key = { "h/" + it.id }) { g ->
                    ToggleRow(
                        title = g.name,
                        subtitle = if (g.shown) null else "Hidden",
                        checked = g.shown,
                        tag = "conversation-switch-" + g.id,
                        large = true,
                    ) { flip(g.id, "conversation", shown = it) }
                }
            }
            list.filter { it.merged && it.section != "chats" }.forEach { g ->
                item(key = "hdr-" + g.id) {
                    Column {
                        Box(Modifier.fillMaxWidth().padding(top = 12.dp).height(1.dp).background(c.divider))
                        SectionHeader("Groups in ${g.name}")
                    }
                }
                items(g.sources, key = { g.id + "/" + it.jid }) { s ->
                    ToggleRow(
                        title = s.name.trim(),
                        subtitle = null,
                        checked = s.shown,
                        enabled = g.shown,
                        tag = "source-switch-" + s.jid,
                        large = false,
                    ) { flip(s.jid, "source", shown = it) }
                }
            }
            item {
                Text(
                    "Consolidated chats switched off also stop sending Wanted alerts. Hiding a chat on one side never hides it on the other. Nothing changes in WhatsApp — you stay in every group.",
                    color = c.textSecondary, fontSize = 13.sp, lineHeight = 18.sp,
                    modifier = Modifier.padding(16.dp),
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    tag: String,
    large: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    val c = Wa.colors
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!checked) }.testTag(tag)
            .padding(horizontal = 16.dp, vertical = if (large) 12.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GroupAvatar(if (large) 48.dp else 40.dp)
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(
                title, color = if (enabled) c.text else c.textSecondary,
                fontSize = if (large) 17.sp else 16.sp,
                fontWeight = if (large) FontWeight.Medium else FontWeight.Normal,
            )
            if (subtitle != null) Text(subtitle, color = c.textSecondary, fontSize = 14.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = c.onFab, checkedTrackColor = c.brand,
                uncheckedThumbColor = c.textSecondary, uncheckedTrackColor = c.chipBg, uncheckedBorderColor = c.textSecondary,
            ),
            modifier = Modifier.testTag("$tag-toggle"),
        )
    }
}
