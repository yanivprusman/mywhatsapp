package com.automatelinux.mywhatsapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.automatelinux.mywhatsapp.data.Chat
import com.automatelinux.mywhatsapp.data.Message
import com.automatelinux.mywhatsapp.data.listStamp
import com.automatelinux.mywhatsapp.data.mediaLabel
import com.automatelinux.mywhatsapp.ui.theme.Wa

@Composable
fun GroupAvatar(size: Dp = 52.dp) {
    val c = Wa.colors
    Box(
        Modifier.size(size).clip(CircleShape).background(if (c.isDark) Color(0xFF6A7175) else Color(0xFFDFE5E7)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Groups, null, tint = if (c.isDark) Color(0xFFCFD4D6) else Color.White, modifier = Modifier.size(size * 0.62f))
    }
}

fun mediaIcon(type: String): ImageVector? = when (type) {
    "image" -> Icons.Filled.Photo
    "video" -> Icons.Filled.Videocam
    "audio" -> Icons.Filled.Headphones
    "document" -> Icons.AutoMirrored.Filled.InsertDriveFile
    else -> null
}

/** First line of the last message, as WhatsApp previews it. */
@Composable
fun MessagePreview(m: Message?) {
    val c = Wa.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (m == null) {
            Text("No messages yet", color = c.textSecondary, fontSize = 15.sp)
            return@Row
        }
        if (m.fromMe) {
            Icon(Icons.Filled.DoneAll, "Read", tint = c.tick, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(3.dp))
        }
        mediaIcon(m.mediaType)?.let {
            Icon(it, null, tint = c.textSecondary, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(3.dp))
        }
        val body = m.text.lineSequence().firstOrNull { it.isNotBlank() } ?: mediaLabel(m)
        val text = if (!m.fromMe && m.senderName != null) "${m.senderName}: $body" else body
        Text(text, color = c.textSecondary, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ChatRow(chat: Chat, onClick: () -> Unit) {
    val c = Wa.colors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("chat-" + chat.jid.substringBefore('@'))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (chat.isGroup) GroupAvatar() else DefaultAvatar()
        Column(Modifier.padding(start = 16.dp).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    chat.name, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                chat.last?.let { Text(listStamp(it.ts), color = c.textSecondary, fontSize = 12.sp) }
            }
            Spacer(Modifier.size(2.dp))
            MessagePreview(chat.last)
        }
    }
}
