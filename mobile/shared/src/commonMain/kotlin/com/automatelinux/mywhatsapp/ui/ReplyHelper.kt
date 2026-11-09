package com.automatelinux.mywhatsapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.automatelinux.mywhatsapp.data.Chat
import com.automatelinux.mywhatsapp.data.HelperTurn
import com.automatelinux.mywhatsapp.replyInWhatsApp
import com.automatelinux.mywhatsapp.replyInWhatsAppGroup
import com.automatelinux.mywhatsapp.ui.theme.Wa
import kotlinx.coroutines.launch

/**
 * The bottom of a private chat: the reply helper instead of a message box.
 *
 * A conversation with Claude about how to answer this chat, kept on the server
 * (same thread on phone and desktop, still there tomorrow). The box talks to the
 * helper, not to the contact: ✨ with an empty box asks for drafts; anything typed
 * ("תמצא מתכון will lead to a bad outcome…") gets an answer — it may agree, argue
 * or ask — and new drafts that take in everything said so far. Tapping a draft
 * opens real WhatsApp with it in the message box; the owner edits and sends there,
 * nothing is sent from here. "Start over" begins a fresh conversation. The thread
 * folds down to its latest answer so the chat itself keeps the room.
 */
@Composable
fun ReplyHelper(chat: Chat, onResult: (String) -> Unit) {
    val c = Wa.colors
    val api = LocalApi.current
    val scope = rememberCoroutineScope()
    val turns = remember(chat.jid) { mutableStateListOf<HelperTurn>() }
    var said by remember(chat.jid) { mutableStateOf("") }
    var pending by remember { mutableStateOf<String?>(null) } // what he just said, shown while it thinks
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var expanded by remember(chat.jid) { mutableStateOf(false) }
    val phone = chat.jid.substringBefore('@')
    val scroll = rememberScrollState()

    LaunchedEffect(chat.jid) {
        try { turns.clear(); turns.addAll(api.helper(chat.jid)) } catch (e: Exception) { error = e.message }
    }
    LaunchedEffect(turns.size, loading, expanded) { scroll.animateScrollTo(scroll.maxValue) }

    fun ask() {
        if (loading) return
        val now = said.trim()
        loading = true; error = null; pending = now.ifEmpty { null }; said = ""
        scope.launch {
            try { val t = api.helperSay(chat.jid, now); turns.clear(); turns.addAll(t) }
            catch (e: Exception) { error = e.message ?: "unknown error"; said = now }
            pending = null; loading = false
        }
    }

    fun reset() {
        scope.launch {
            try { api.helperReset(chat.jid); turns.clear(); error = null; expanded = false }
            catch (e: Exception) { error = e.message }
        }
    }

    Column(Modifier.fillMaxWidth().background(c.background).navigationBarsPadding()) {
        if (turns.isNotEmpty() || loading || error != null) {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AutoAwesome, null, tint = c.brand, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Reply helper", color = c.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                if (turns.isNotEmpty() && !loading) HeaderButton("Start over", "helper-reset", ::reset)
                if (turns.size > 1) Box(
                    Modifier.clip(CircleShape).clickable { expanded = !expanded }.padding(6.dp).testTag("helper-expand"),
                ) {
                    Icon(
                        if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                        if (expanded) "Show less" else "Show the whole conversation", tint = c.iconTint, modifier = Modifier.size(20.dp),
                    )
                }
            }
            // Folded: the latest answer and its drafts. Unfolded: everything said so far.
            val shown = if (expanded) turns.toList() else turns.toList().takeLast(if (turns.lastOrNull()?.role == "helper" && turns.size > 1 && turns[turns.size - 2].role == "me") 2 else 1)
            Column(
                Modifier.fillMaxWidth().heightIn(max = if (expanded) 520.dp else 360.dp).verticalScroll(scroll)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                shown.forEachIndexed { i, t ->
                    val latest = t === turns.lastOrNull()
                    if (t.role == "me") MeLine(t.text) else HelperAnswer(t, enabled = !loading, dim = !latest) { text ->
                        (if (chat.isGroup) replyInWhatsAppGroup(chat.jid, chat.name, text) else replyInWhatsApp(phone, text))?.let(onResult)
                    }
                }
                pending?.let { MeLine(it) }
                if (loading) Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = c.brand, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Thinking…", color = c.textSecondary, fontSize = 13.sp)
                }
                error?.let { Text("Couldn't answer: $it", color = c.textSecondary, fontSize = 13.sp) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.Bottom) {
            Box(
                Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).background(c.inputBg)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                if (said.isEmpty()) Text(
                    if (turns.isEmpty()) "Ask the helper, or tap ✨" else "Talk to the helper…",
                    color = c.textSecondary, fontSize = 16.sp, maxLines = 1,
                )
                BasicTextField(
                    value = said, onValueChange = { said = it }, maxLines = 6,
                    textStyle = TextStyle(color = c.text, fontSize = 16.sp, textDirection = TextDirection.Content),
                    cursorBrush = SolidColor(c.brand),
                    modifier = Modifier.fillMaxWidth().testTag("helper-input"),
                )
            }
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(c.fab).alpha(if (loading) 0.5f else 1f)
                    .clickable(enabled = !loading) { ask() }.testTag("helper-ask"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (said.isBlank()) Icons.Filled.AutoAwesome else Icons.AutoMirrored.Filled.Send,
                    if (said.isBlank()) "Suggest replies" else "Tell the helper",
                    tint = c.onFab, modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun HeaderButton(label: String, tag: String, onClick: () -> Unit) {
    Text(
        label, color = Wa.colors.brand, fontSize = 13.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp).testTag(tag),
    )
}

/** What the owner told the helper: on the right, like his own messages. */
@Composable
private fun MeLine(text: String) {
    val c = Wa.colors
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Text(
            text, color = c.text, fontSize = 15.sp, style = TextStyle(textDirection = TextDirection.Content),
            modifier = Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(12.dp)).background(c.inputBg)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

/** The helper's answer to him, then its drafts — each a tap away from WhatsApp's message box. */
@Composable
private fun HelperAnswer(t: HelperTurn, enabled: Boolean, dim: Boolean, onUse: (String) -> Unit) {
    val c = Wa.colors
    Column(Modifier.fillMaxWidth().alpha(if (dim) 0.6f else 1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (t.text.isNotBlank()) Text(t.text, color = c.text, fontSize = 15.sp, style = TextStyle(textDirection = TextDirection.Content))
        t.drafts.forEachIndexed { i, d ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.bubbleOut)
                    .clickable(enabled = enabled) { onUse(d.text) }
                    .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp)
                    .testTag("helper-draft-$i"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(d.label, color = c.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text(d.text, color = c.text, fontSize = 16.sp, style = TextStyle(textDirection = TextDirection.Content))
                }
                Icon(Icons.AutoMirrored.Filled.Send, "Use in WhatsApp", tint = c.brand, modifier = Modifier.padding(start = 6.dp).size(20.dp))
            }
        }
    }
}
