package com.automatelinux.mywhatsapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.automatelinux.mywhatsapp.data.Chat
import com.automatelinux.mywhatsapp.data.Message
import com.automatelinux.mywhatsapp.dial
import com.automatelinux.mywhatsapp.openWhatsApp
import com.automatelinux.mywhatsapp.ui.theme.Wa

/** What to type when answering a post privately: which post, so the poster knows what you're replying to. */
fun privateReply(chat: Chat, m: Message): String {
    val gist = m.text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.take(2).joinToString(" ")
    val quote = if (gist.length > 140) gist.take(140) + "…" else gist
    // Name the real group it was posted in — a merged chat's name means nothing to the poster.
    return "היי, לגבי ההודעה שלך בקבוצה ${m.chatName ?: chat.name}:\n\"$quote\"\n\n"
}

/**
 * Tapping a post: act on it. Messaging happens in the real WhatsApp, with the
 * post quoted, because that is where the conversation with the poster lives.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SenderSheet(chat: Chat, m: Message, onDismiss: () -> Unit, onResult: (String) -> Unit) {
    val c = Wa.colors
    val clipboard = LocalClipboardManager.current
    val phone = m.sender.takeIf { !m.fromMe && Regex("^\\d{6,15}$").matches(it) }
    val name = when {
        m.fromMe -> "You"
        else -> m.senderName ?: phone?.let { "+$it" } ?: "Unknown"
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.background) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                DefaultAvatar(52.dp)
                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                    Text(name.removePrefix("~ "), color = c.text, fontSize = 19.sp, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        when {
                            phone != null -> "+$phone · ${m.chatName ?: chat.name}"
                            m.fromMe -> m.chatName ?: chat.name
                            else -> "The bridge didn't record who sent this"
                        },
                        color = c.textSecondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (m.text.isNotBlank()) {
                Text(
                    m.text, color = c.text, fontSize = 15.sp, lineHeight = 20.sp, maxLines = 4, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(textDirection = TextDirection.Content, textAlign = TextAlign.Start),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp)).background(c.chatBg).padding(12.dp),
                )
            } else Spacer(Modifier.height(12.dp))
            if (phone != null) {
                SheetAction(Icons.AutoMirrored.Filled.Chat, "Message ${name.removePrefix("~ ")}", "Opens WhatsApp with this post quoted", "sheet-message", c.brand) {
                    val err = openWhatsApp(phone, privateReply(chat, m))
                    if (err != null) onResult(err)
                    onDismiss()
                }
                SheetAction(Icons.Filled.Call, "Call", "+$phone", "sheet-call") {
                    dial(phone)?.let(onResult); onDismiss()
                }
                SheetAction(Icons.Filled.Numbers, "Copy number", null, "sheet-copy-number") {
                    clipboard.setText(AnnotatedString("+$phone")); onResult("Number copied"); onDismiss()
                }
            }
            if (m.text.isNotBlank()) {
                SheetAction(Icons.Filled.ContentCopy, "Copy message", null, "sheet-copy-message") {
                    clipboard.setText(AnnotatedString(m.text)); onResult("Message copied"); onDismiss()
                }
            }
        }
    }
}

@Composable
private fun SheetAction(icon: ImageVector, title: String, subtitle: String?, tag: String, tint: Color? = null, onClick: () -> Unit) {
    val c = Wa.colors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).testTag(tag).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint ?: c.textSecondary, modifier = Modifier.size(24.dp))
        Column(Modifier.padding(start = 22.dp)) {
            Text(title, color = tint ?: c.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(subtitle, color = c.textSecondary, fontSize = 13.sp)
        }
    }
}
