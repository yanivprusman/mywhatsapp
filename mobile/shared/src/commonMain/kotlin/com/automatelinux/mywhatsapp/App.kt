package com.automatelinux.mywhatsapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.AddIcCall
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.DonutLarge
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MergeType
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.WavingHand
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import com.automatelinux.mywhatsapp.data.Api
import com.automatelinux.mywhatsapp.data.Chat
import com.automatelinux.mywhatsapp.ui.ConversationScreen
import com.automatelinux.mywhatsapp.ui.ImageCache
import com.automatelinux.mywhatsapp.ui.LocalApi
import com.automatelinux.mywhatsapp.ui.LocalImages
import kotlinx.coroutines.delay
import androidx.compose.runtime.collectAsState
import com.automatelinux.mywhatsapp.ui.BarIcon
import com.automatelinux.mywhatsapp.ui.CallsTab
import com.automatelinux.mywhatsapp.ui.ChatsTab
import com.automatelinux.mywhatsapp.ui.EmptyState
import com.automatelinux.mywhatsapp.ui.HomeTopBar
import com.automatelinux.mywhatsapp.ui.NavTab
import com.automatelinux.mywhatsapp.ui.OverflowMenu
import com.automatelinux.mywhatsapp.ui.Screen
import com.automatelinux.mywhatsapp.ui.SelectContactScreen
import com.automatelinux.mywhatsapp.ui.SubTopBar
import com.automatelinux.mywhatsapp.ui.ToolsTab
import com.automatelinux.mywhatsapp.ui.UpdatesTab
import com.automatelinux.mywhatsapp.ui.WaBottomNav
import com.automatelinux.mywhatsapp.ui.slug
import com.automatelinux.mywhatsapp.ui.theme.AppTheme
import com.automatelinux.mywhatsapp.ui.theme.Wa

private val tabs = listOf(
    NavTab("Chats", Icons.AutoMirrored.Outlined.Chat, Icons.AutoMirrored.Filled.Chat),
    NavTab("Updates", Icons.Outlined.DonutLarge, Icons.Filled.DonutLarge),
    NavTab("Tools", Icons.Outlined.Storefront, Icons.Filled.Storefront),
    NavTab("Calls", Icons.Outlined.Call, Icons.Filled.Call),
)

private val tabMenus = listOf(
    listOf("Advertise", "Catalogue", "New group", "New broadcast", "Labels", "Linked devices", "Starred", "Read all", "Settings"),
    listOf("Create channel", "Status privacy", "Settings"),
    listOf("Business profile", "Settings"),
    listOf("Clear call log", "Settings"),
)

data class AppConfig(val baseUrl: String, val token: String)

// Shared entry composable — rendered by MainActivity on Android and (on a Mac)
// by ComposeUIViewController on iOS.
@Composable
fun App(config: AppConfig) {
    val api = remember { Api(config.baseUrl, config.token) }
    val images = remember { ImageCache() }
    AppTheme { CompositionLocalProvider(LocalApi provides api, LocalImages provides images) {
        var tab by rememberSaveable { mutableStateOf(0) }
        val stack = remember { mutableStateListOf<Screen>() }
        val open: (Screen) -> Unit = { stack.add(it) }
        val openMenu: (String) -> Unit = { item -> open(Screen.Empty(item, slug(item))) }

        PlatformBackHandler(enabled = stack.isNotEmpty() || tab != 0) {
            if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) else tab = 0
        }

        val top = stack.lastOrNull()
        var chats by remember { mutableStateOf<List<Chat>?>(null) }
        var chatsError by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(top == null) {
            while (top == null) {
                try { chats = api.chats(); chatsError = null } catch (e: Exception) { chatsError = e.message ?: "unknown error" }
                delay(15_000)
            }
        }
        // A tapped notification names a chat: open it on its Wanted posts.
        val openJid by Launch.openChat.collectAsState()
        LaunchedEffect(openJid, chats) {
            val jid = openJid ?: return@LaunchedEffect
            val chat = chats?.firstOrNull { it.jid == jid } ?: return@LaunchedEffect
            stack.clear(); tab = 0
            stack.add(Screen.Conversation(chat, "wanted"))
            Launch.openChat.value = null
        }
        if (top != null) {
            SubScreen(top, onBack = { stack.removeAt(stack.lastIndex) }, open = open)
            return@CompositionLocalProvider
        }

        Column(Modifier.fillMaxSize().background(Wa.colors.background)) {
            HomeTopBar(
                title = if (tab == 0) "MyWhatsApp" else tabs[tab].label,
                brandTitle = tab == 0,
            ) {
                when (tab) {
                    0 -> BarIcon(Icons.Outlined.PhotoCamera, "Camera", "camera") { open(Screen.Empty("Camera", "camera")) }
                    else -> BarIcon(Icons.Filled.Search, "Search", "search-" + slug(tabs[tab].label)) {
                        open(Screen.Empty("Search " + tabs[tab].label.lowercase(), "search"))
                    }
                }
                OverflowMenu(tabMenus[tab], "overflow-" + slug(tabs[tab].label), openMenu)
            }
            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> ChatsTab(chats, chatsError, open)
                    1 -> UpdatesTab(open)
                    2 -> ToolsTab(open)
                    else -> CallsTab(open)
                }
                Fabs(tab, open, Modifier.align(Alignment.BottomEnd).padding(16.dp))
            }
            WaBottomNav(tabs, tab) { tab = it }
        }
    } }
}

@Composable
private fun Fabs(tab: Int, open: (Screen) -> Unit, modifier: Modifier) {
    val c = Wa.colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        when (tab) {
            0 -> Fab(Icons.Filled.AddComment, "New chat", "fab-new-chat") { open(Screen.SelectContact) }
            1 -> {
                SmallFloatingActionButton(
                    onClick = { open(Screen.Empty("Text status", "status")) },
                    containerColor = c.chipBg, contentColor = c.text, shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("fab-text-status"),
                ) { Icon(Icons.Filled.Edit, "Text status", Modifier.size(20.dp)) }
                Box(Modifier.size(16.dp))
                Fab(Icons.Filled.PhotoCamera, "Camera", "fab-camera") { open(Screen.Empty("Camera", "camera")) }
            }
            2 -> Unit
            else -> Fab(Icons.Filled.AddIcCall, "New call", "fab-new-call") { open(Screen.Empty("New call", "call")) }
        }
    }
}

@Composable
private fun Fab(icon: ImageVector, label: String, tag: String, onClick: () -> Unit) {
    val c = Wa.colors
    FloatingActionButton(
        onClick = onClick,
        containerColor = c.fab, contentColor = c.onFab,
        shape = RoundedCornerShape(16.dp),
        elevation = FloatingActionButtonDefaults.elevation(3.dp),
        modifier = Modifier.testTag(tag),
    ) { Icon(icon, label) }
}

@Composable
private fun SubScreen(screen: Screen, onBack: () -> Unit, open: (Screen) -> Unit) {
    val c = Wa.colors
    Column(Modifier.fillMaxSize().background(c.background)) {
        when (screen) {
            is Screen.Conversation -> ConversationScreen(screen.chat, screen.filter, onBack, open)
            Screen.SelectContact -> {
                SubTopBar("Select contact", "0 contacts", onBack) {
                    BarIcon(Icons.Filled.Search, "Search", "search-contacts") { open(Screen.Empty("Search contacts", "search")) }
                }
                Box(Modifier.weight(1f)) { SelectContactScreen(open) }
            }
            is Screen.Empty -> {
                SubTopBar(screen.title, null, onBack)
                Box(Modifier.weight(1f).navigationBarsPadding(), contentAlignment = Alignment.Center) {
                    EmptyState(iconFor(screen.kind), "Nothing here yet", "This screen is empty for now.")
                }
            }
        }
    }
}

private fun iconFor(kind: String): ImageVector = when (kind) {
    "advertise" -> Icons.Outlined.Campaign
    "catalogue" -> Icons.Outlined.Inventory2
    "new-group", "group", "community" -> Icons.Outlined.Groups
    "new-broadcast" -> Icons.Outlined.Campaign
    "labels", "list" -> Icons.AutoMirrored.Outlined.Label
    "linked-devices" -> Icons.Outlined.Devices
    "starred", "favourite" -> Icons.Outlined.StarBorder
    "read-all" -> Icons.Outlined.DoneAll
    "settings", "status-privacy" -> Icons.Outlined.Settings
    "camera" -> Icons.Outlined.PhotoCamera
    "status", "text-status" -> Icons.Outlined.DonutLarge
    "channels", "create-channel" -> Icons.Outlined.Explore
    "call", "clear-call-log" -> Icons.Outlined.Call
    "profile", "business-profile", "contact" -> Icons.Outlined.Person
    "merge" -> Icons.Outlined.MergeType
    "filter" -> Icons.Outlined.FilterAlt
    "diagnostics" -> Icons.Outlined.MonitorHeart
    "greeting" -> Icons.Outlined.WavingHand
    "away" -> Icons.Outlined.Schedule
    "quick" -> Icons.Outlined.Bolt
    "search" -> Icons.Filled.Search
    else -> Icons.AutoMirrored.Outlined.Chat
}
