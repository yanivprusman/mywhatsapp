package com.automatelinux.mywhatsapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MarkChatUnread
import androidx.compose.material.icons.outlined.MergeType
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.WavingHand
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.automatelinux.mywhatsapp.ui.theme.Wa

// Every tab is the real WhatsApp Business layout with zero data behind it.
// When real data arrives, the empty states are what gets replaced.

private val chatFilters = listOf("All", "Unread", "Favourites", "Groups")

@Composable
fun ChatsTab(open: (Screen) -> Unit) {
    val c = Wa.colors
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("All") }

    LazyColumn(Modifier.fillMaxSize().background(c.background)) {
        item {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(50)).background(c.searchBg)
                    .padding(horizontal = 16.dp).height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, null, tint = c.textSecondary, modifier = Modifier.size(22.dp))
                Box(Modifier.padding(start = 12.dp).weight(1f)) {
                    if (query.isEmpty()) Text("Search", color = c.textSecondary, fontSize = 16.sp)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = c.text, fontSize = 16.sp),
                        cursorBrush = SolidColor(c.brand),
                        modifier = Modifier.fillMaxWidth().testTag("search-chats"),
                    )
                }
            }
        }
        item {
            LazyRow(
                Modifier.padding(vertical = 8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(chatFilters) { f ->
                    FilterChip(f, filter == f, "filter-" + slug(f)) { filter = f }
                }
                item {
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(c.chipBg)
                            .clickable { open(Screen.Empty("New list", "list")) }
                            .testTag("filter-add-list")
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) { Icon(Icons.Filled.Add, "New list", tint = c.chipText, modifier = Modifier.size(22.dp)) }
                }
            }
        }
        item {
            val (icon, title, body) = when {
                query.isNotBlank() -> Triple(Icons.Filled.Search, "No results", "No chats, contacts or messages found for “$query”.")
                filter == "Unread" -> Triple(Icons.Outlined.MarkChatUnread, "No unread chats", "You're all caught up.")
                filter == "Favourites" -> Triple(Icons.Outlined.StarBorder, "No favourites yet", "Add chats to Favourites so they're always easy to find.")
                filter == "Groups" -> Triple(Icons.Outlined.Groups, "No groups yet", "Groups will appear here.")
                else -> Triple(Icons.AutoMirrored.Outlined.Chat, "No chats yet", "Your conversations will appear here.")
            }
            EmptyState(icon, title, body, Modifier.padding(top = 56.dp))
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 40.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Lock, null, tint = c.textSecondary, modifier = Modifier.size(12.dp))
                Spacer(Modifier.size(4.dp))
                Text(
                    buildAnnotatedString {
                        append("Your personal messages are ")
                        withStyle(SpanStyle(color = c.brand)) { append("end-to-end encrypted") }
                    },
                    color = c.textSecondary, fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
fun UpdatesTab(open: (Screen) -> Unit) {
    val c = Wa.colors
    LazyColumn(Modifier.fillMaxSize().background(c.background)) {
        item { SectionHeader("Status", large = true) }
        item {
            ListRow("My status", "Tap to add status update", "my-status", leading = {
                Box {
                    DefaultAvatar(52.dp)
                    Box(
                        Modifier.align(Alignment.BottomEnd).size(22.dp).clip(CircleShape)
                            .background(c.background).padding(2.dp).clip(CircleShape).background(c.brand),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Add, null, tint = c.onFab, modifier = Modifier.size(16.dp)) }
                }
            }) { open(Screen.Empty("My status", "status")) }
        }
        item { Box(Modifier.fillMaxWidth().padding(top = 12.dp).height(1.dp).background(c.divider)) }
        item {
            Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { SectionHeader("Channels", large = true) }
                Text(
                    "Explore", color = c.brand, fontSize = 15.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50))
                        .clickable { open(Screen.Empty("Channels", "channels")) }
                        .testTag("explore-channels").padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
        item {
            Text(
                "Stay updated on topics that matter to you. Channels you follow will appear here.",
                color = c.textSecondary, fontSize = 15.sp, lineHeight = 21.sp,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        item {
            Box(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    "Find channels", color = c.onFab, fontSize = 15.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(c.brand)
                        .clickable { open(Screen.Empty("Find channels", "channels")) }
                        .testTag("find-channels").padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
fun ToolsTab(open: (Screen) -> Unit) {
    val c = Wa.colors
    LazyColumn(Modifier.fillMaxSize().background(c.background)) {
        item {
            ListRow("MyWhatsApp Business", "Profile not set up", "business-profile", leading = { DefaultAvatar(56.dp) }) {
                open(Screen.Empty("Business profile", "profile"))
            }
        }
        item { Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider)) }
        item { SectionHeader("My tools") }
        item { PlainIconRow(Icons.Outlined.MergeType, "Merged groups", "Combine groups into one conversation", "tool-merged-groups") { open(Screen.Empty("Merged groups", "merge")) } }
        item { PlainIconRow(Icons.Outlined.FilterAlt, "Message filters", "Show only the messages you care about", "tool-message-filters") { open(Screen.Empty("Message filters", "filter")) } }
        item { PlainIconRow(Icons.Outlined.MonitorHeart, "Diagnostics", "Connection and delivery health", "tool-diagnostics") { open(Screen.Empty("Diagnostics", "diagnostics")) } }
        item { SectionHeader("Grow your business") }
        item { PlainIconRow(Icons.Outlined.Inventory2, "Catalogue", "Show products and services", "tool-catalogue") { open(Screen.Empty("Catalogue", "catalogue")) } }
        item { PlainIconRow(Icons.Outlined.Storefront, "Advertise", "Create ads that lead to chats", "tool-advertise") { open(Screen.Empty("Advertise", "advertise")) } }
        item { SectionHeader("Organise your chats") }
        item { PlainIconRow(Icons.AutoMirrored.Outlined.Label, "Labels", "Organise chats and customers", "tool-labels") { open(Screen.Empty("Labels", "labels")) } }
        item { PlainIconRow(Icons.Outlined.WavingHand, "Greeting message", "Welcome new customers automatically", "tool-greeting") { open(Screen.Empty("Greeting message", "greeting")) } }
        item { PlainIconRow(Icons.Outlined.Schedule, "Away message", "Reply automatically when you're away", "tool-away") { open(Screen.Empty("Away message", "away")) } }
        item { PlainIconRow(Icons.Outlined.Bolt, "Quick replies", "Reuse frequent messages", "tool-quick-replies") { open(Screen.Empty("Quick replies", "quick")) } }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun CallsTab(open: (Screen) -> Unit) {
    val c = Wa.colors
    LazyColumn(Modifier.fillMaxSize().background(c.background)) {
        item { SectionHeader("Favourites", large = true) }
        item {
            ListRow("Add favourite", null, "add-favourite", leading = {
                IconCircle(Icons.Filled.Favorite, c.brand, c.onFab, 48.dp)
            }) { open(Screen.Empty("Add favourite", "favourite")) }
        }
        item { SectionHeader("Recent", large = true) }
        item { EmptyState(Icons.Outlined.Call, "No recent calls", "Calls you make and receive will appear here.", Modifier.padding(top = 24.dp)) }
    }
}

@Composable
fun SelectContactScreen(open: (Screen) -> Unit) {
    val c = Wa.colors
    LazyColumn(Modifier.fillMaxSize().background(c.background)) {
        item { ListRow("New group", null, "new-group", leading = { IconCircle(Icons.Outlined.Groups, c.brand, c.onFab) }) { open(Screen.Empty("New group", "group")) } }
        item { ListRow("New contact", null, "new-contact", leading = { IconCircle(Icons.Filled.Add, c.brand, c.onFab) }) { open(Screen.Empty("New contact", "contact")) } }
        item { ListRow("New community", null, "new-community", leading = { IconCircle(Icons.Outlined.Explore, c.brand, c.onFab) }) { open(Screen.Empty("New community", "community")) } }
        item { SectionHeader("Contacts on MyWhatsApp") }
        item { Text("No contacts yet.", color = c.textSecondary, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
    }
}
