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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.automatelinux.mywhatsapp.data.HelperRound
import com.automatelinux.mywhatsapp.replyInWhatsApp
import com.automatelinux.mywhatsapp.ui.theme.Wa
import kotlinx.coroutines.launch

/**
 * The bottom of a private chat: the reply helper instead of a message box.
 *
 * The box is for talking to the helper, not to the contact: empty + ✨ asks for
 * drafts; anything typed ("that's not a good idea — I'll cook what she likes") is
 * taken into the next drafts, together with everything said before (the rounds,
 * kept here and sent back each time). Tapping a draft opens real WhatsApp with
 * it in the message box — the owner edits and sends there; nothing is sent from
 * here. "Start over" forgets the rounds.
 */
@Composable
fun ReplyHelper(chat: Chat, onResult: (String) -> Unit) {
    val c = Wa.colors
    val api = LocalApi.current
    val scope = rememberCoroutineScope()
    val rounds = remember(chat.jid) { mutableStateListOf<HelperRound>() }
    var said by remember(chat.jid) { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val phone = chat.jid.substringBefore('@')

    fun ask() {
        if (loading) return
        val now = said.trim()
        loading = true; error = null
        scope.launch {
            try {
                val drafts = api.suggest(chat.jid, now, rounds.toList())
                rounds.add(HelperRound(now, drafts)); said = ""
            } catch (e: Exception) { error = e.message ?: "unknown error" }
            loading = false
        }
    }

    Column(Modifier.fillMaxWidth().background(c.background).navigationBarsPadding()) {
        val last = rounds.lastOrNull()
        if (last != null || loading || error != null) {
            Column(Modifier.fillMaxWidth().heightIn(max = 340.dp).verticalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AutoAwesome, null, tint = c.brand, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Reply helper", color = c.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    if (rounds.isNotEmpty() && !loading) Text(
                        "Start over", color = c.brand, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { rounds.clear(); error = null }
                            .padding(horizontal = 8.dp, vertical = 4.dp).testTag("helper-reset"),
                    )
                }
                last?.said?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        "You: $it", color = c.textSecondary, fontSize = 13.sp,
                        style = TextStyle(textDirection = TextDirection.Content), modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (last != null) Column(
                    Modifier.padding(top = 6.dp).alpha(if (loading) 0.45f else 1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    last.drafts.forEachIndexed { i, d ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.bubbleOut)
                                .clickable(enabled = !loading) { replyInWhatsApp(phone, d.text)?.let(onResult) }
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
                if (loading) Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = c.brand, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (rounds.isEmpty()) "Reading the chat…" else "Redrafting…", color = c.textSecondary, fontSize = 13.sp)
                }
                error?.let { Text("Couldn't draft: $it", color = c.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.Bottom) {
            Box(
                Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).background(c.inputBg)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                if (said.isEmpty()) Text(
                    if (rounds.isEmpty()) "Suggest a reply… (tell it anything first)" else "Tell it what to change…",
                    color = c.textSecondary, fontSize = 16.sp, maxLines = 1,
                )
                BasicTextField(
                    value = said, onValueChange = { said = it }, maxLines = 5,
                    textStyle = TextStyle(color = c.text, fontSize = 16.sp, textDirection = TextDirection.Content),
                    cursorBrush = SolidColor(c.brand),
                    modifier = Modifier.fillMaxWidth().testTag("helper-input"),
                )
            }
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(c.fab).alpha(if (loading) 0.5f else 1f)
                    .clickable(enabled = !loading) { ask() }.testTag("helper-suggest"),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.AutoAwesome, "Suggest replies", tint = c.onFab, modifier = Modifier.size(24.dp)) }
        }
    }
}
