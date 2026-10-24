package com.automatelinux.mywhatsapp.ui

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.automatelinux.mywhatsapp.data.Chat
import com.automatelinux.mywhatsapp.data.Message
import com.automatelinux.mywhatsapp.data.clock
import com.automatelinux.mywhatsapp.data.dayLabel
import com.automatelinux.mywhatsapp.data.displayFilename
import com.automatelinux.mywhatsapp.data.localTime
import com.automatelinux.mywhatsapp.decodeImage
import com.automatelinux.mywhatsapp.ui.theme.Wa
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

@Composable
fun ConversationScreen(chat: Chat, onBack: () -> Unit, open: (Screen) -> Unit) {
    val c = Wa.colors
    val api = LocalApi.current
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<Message>() } // newest first
    var hasMore by remember { mutableStateOf(true) }
    var loadingOlder by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var hint by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    val list = rememberLazyListState()

    // Newest page, then poll for what arrives while the chat is open.
    LaunchedEffect(chat.jid) {
        try {
            val page = api.messages(chat.jid)
            messages.clear(); messages.addAll(page.messages); hasMore = page.hasMore; error = null
        } catch (e: Exception) { error = e.message }
        while (true) {
            delay(4000)
            try {
                val newest = messages.firstOrNull { !it.pending && !it.failed }?.ts ?: continue
                val fresh = api.messages(chat.jid, after = newest).messages
                if (fresh.isNotEmpty()) {
                    // The bridge's echo replaces our optimistic copy of the same text.
                    fresh.forEach { f -> messages.removeAll { it.pending && it.text == f.text } }
                    messages.addAll(0, fresh.filter { f -> messages.none { it.id == f.id } })
                    if (list.firstVisibleItemIndex <= 2) list.animateScrollToItem(0)
                }
            } catch (_: Exception) { /* next tick retries; the first-load error is the visible one */ }
        }
    }

    val nearTop by remember { derivedStateOf {
        val last = list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        last >= list.layoutInfo.totalItemsCount - 5
    } }
    LaunchedEffect(nearTop, hasMore, messages.size) {
        if (!nearTop || !hasMore || loadingOlder || messages.isEmpty()) return@LaunchedEffect
        loadingOlder = true
        try {
            val oldest = messages.last { !it.pending && !it.failed }.ts
            val page = api.messages(chat.jid, before = oldest)
            messages.addAll(page.messages.filter { p -> messages.none { it.id == p.id } }); hasMore = page.hasMore
        } catch (e: Exception) { hint = "Couldn't load older messages: ${e.message}" }
        loadingOlder = false
    }
    LaunchedEffect(hint) { if (hint != null) { delay(2500); hint = null } }

    fun send() {
        val text = draft.trim()
        if (text.isEmpty()) return
        draft = ""
        val local = Message(
            id = "local-" + Clock.System.now().toEpochMilliseconds(), text = text,
            ts = Clock.System.now().toString(), fromMe = true, sender = "", mediaType = "",
            filename = "", downloadable = false, pending = true,
        )
        messages.add(0, local)
        scope.launch {
            list.animateScrollToItem(0)
            try { api.send(chat.jid, text) } catch (e: Exception) {
                val i = messages.indexOfFirst { it.id == local.id }
                if (i >= 0) messages[i] = local.copy(pending = false, failed = true)
                hint = "Not sent: ${e.message}"
            }
        }
    }

    Column(Modifier.fillMaxSize().background(c.chatBg).imePadding()) {
        ConversationTopBar(chat, onBack, open)
        Box(Modifier.weight(1f)) {
            when {
                error != null && messages.isEmpty() -> EmptyState(
                    Icons.Filled.ErrorOutline, "Can't load messages", error ?: "", Modifier.align(Alignment.Center),
                )
                messages.isEmpty() -> CircularProgressIndicator(color = c.brand, modifier = Modifier.align(Alignment.Center))
                else -> LazyColumn(
                    state = list, reverseLayout = true, modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
                ) {
                    val n = messages.size
                    for (i in 0 until n) {
                        val m = messages[i]
                        val older = messages.getOrNull(i + 1)
                        val day = localTime(m.ts).date
                        val firstOfRun = older == null || older.fromMe != m.fromMe ||
                            older.sender != m.sender || localTime(older.ts).date != day
                        item(key = m.id) { Bubble(chat, m, firstOfRun) }
                        if (older == null || localTime(older.ts).date != day) {
                            item(key = "day-$day-${m.id}") { DateChip(dayLabel(day)) }
                        }
                    }
                    if (loadingOlder) item(key = "older") {
                        Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = c.brand, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                        }
                    }
                    if (!hasMore) item(key = "notice") { EncryptionNotice() }
                }
            }
            hint?.let {
                Text(
                    it, color = Color.White, fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp)
                        .clip(RoundedCornerShape(8.dp)).background(Color(0xE6323739))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
        InputBar(
            draft, { draft = it }, onSend = ::send,
            onUnavailable = { what -> hint = "$what isn't available yet — text only for now" },
        )
    }
}

@Composable
private fun ConversationTopBar(chat: Chat, onBack: () -> Unit, open: (Screen) -> Unit) {
    val c = Wa.colors
    Row(
        Modifier.fillMaxWidth().background(c.background).statusBarsPadding().height(64.dp).padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onBack).testTag("back")
                .padding(start = 6.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = c.iconTint)
            Spacer(Modifier.width(2.dp))
            GroupAvatar(40.dp)
        }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(chat.name, color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("You", color = c.textSecondary, fontSize = 13.sp, maxLines = 1)
        }
        BarIcon(Icons.Outlined.Videocam, "Video call", "video-call") { open(Screen.Empty("Video call", "call")) }
        BarIcon(Icons.Filled.Call, "Voice call", "voice-call") { open(Screen.Empty("Voice call", "call")) }
        OverflowMenu(listOf("Group info", "Media, links and docs", "Search", "Mute notifications", "Wallpaper"), "overflow-chat") {
            open(Screen.Empty(it, slug(it)))
        }
    }
}

@Composable
private fun DateChip(label: String) {
    val c = Wa.colors
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(
            label, color = c.dateChipText, fontSize = 12.5.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(c.dateChip).padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun EncryptionNotice() {
    val c = Wa.colors
    Box(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        Text(
            buildAnnotatedString {
                append("🔒 Messages and calls are end-to-end encrypted. Only people in this chat can read, listen to, or share them.")
            },
            color = c.noticeText, fontSize = 12.5.sp, textAlign = TextAlign.Center, lineHeight = 17.sp,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(c.notice).padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

/** A rounded bubble whose first message in a run carries WhatsApp's little corner tail. */
private fun bubbleShape(out: Boolean, tail: Boolean, r: Float, t: Float) = GenericShape { size: Size, _ ->
    val w = size.width; val h = size.height
    if (out) {
        moveTo(r, 0f)
        if (tail) { lineTo(w + t, 0f); lineTo(w, t * 1.2f) } else { lineTo(w - r, 0f); quadraticTo(w, 0f, w, r) }
        lineTo(w, h - r); quadraticTo(w, h, w - r, h)
        lineTo(r, h); quadraticTo(0f, h, 0f, h - r)
        lineTo(0f, r); quadraticTo(0f, 0f, r, 0f)
    } else {
        moveTo(w - r, 0f); quadraticTo(w, 0f, w, r)
        lineTo(w, h - r); quadraticTo(w, h, w - r, h)
        lineTo(r, h); quadraticTo(0f, h, 0f, h - r)
        if (tail) { lineTo(0f, t * 1.2f); lineTo(-t, 0f) } else { lineTo(0f, r); quadraticTo(0f, 0f, r, 0f) }
    }
    close()
}

private val urlRegex = Regex("""https?://[^\s]+""")

@Composable
private fun linkified(text: String): AnnotatedString {
    val c = Wa.colors
    val style = TextLinkStyles(SpanStyle(color = c.link))
    return buildAnnotatedString {
        var at = 0
        for (m in urlRegex.findAll(text)) {
            append(text.substring(at, m.range.first))
            withLink(LinkAnnotation.Url(m.value, style)) { append(m.value) }
            at = m.range.last + 1
        }
        append(text.substring(at))
    }
}

@Composable
private fun Bubble(chat: Chat, m: Message, firstOfRun: Boolean) {
    val c = Wa.colors
    val shape = bubbleShape(m.fromMe, firstOfRun, r = 22f, t = 22f)
    Row(
        Modifier.fillMaxWidth().padding(
            start = if (m.fromMe) 56.dp else 14.dp, end = if (m.fromMe) 14.dp else 56.dp,
            top = if (firstOfRun) 6.dp else 1.5.dp, bottom = 1.5.dp,
        ),
        horizontalArrangement = if (m.fromMe) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            Modifier.clip(shape).background(if (m.fromMe) c.bubbleOut else c.bubbleIn).padding(4.dp),
        ) {
            if (!m.fromMe && chat.isGroup && firstOfRun) {
                Text("+" + m.sender, color = Color(0xFFE26AB6), fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 6.dp, top = 2.dp))
            }
            when (m.mediaType) {
                "image" -> ImageContent(chat, m)
                "video" -> MediaTile(Icons.Filled.PlayArrow, "Video")
                "audio" -> AudioContent()
                "document" -> DocumentContent(m)
            }
            if (m.text.isNotBlank()) {
                Text(
                    linkified(m.text), color = c.text, fontSize = 16.sp, lineHeight = 21.sp,
                    modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 3.dp),
                )
            }
            Row(
                Modifier.align(Alignment.End).padding(start = 12.dp, end = 4.dp, top = 1.dp, bottom = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(clock(m.ts), color = c.bubbleMeta, fontSize = 11.sp)
                if (m.fromMe) {
                    Spacer(Modifier.width(3.dp))
                    when {
                        m.failed -> Icon(Icons.Filled.ErrorOutline, "Not sent", tint = Color(0xFFEA0038), modifier = Modifier.size(15.dp))
                        m.pending -> Icon(Icons.Filled.Schedule, "Sending", tint = c.bubbleMeta, modifier = Modifier.size(14.dp))
                        else -> Icon(Icons.Filled.DoneAll, "Read", tint = c.tick, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageContent(chat: Chat, m: Message) {
    val c = Wa.colors
    val api = LocalApi.current
    val cache = LocalImages.current
    var img by remember(m.id) { mutableStateOf<ImageBitmap?>(cache[m.id]) }
    var failed by remember(m.id) { mutableStateOf(!m.downloadable) }
    LaunchedEffect(m.id) {
        if (img != null || failed) return@LaunchedEffect
        try {
            val decoded = decodeImage(api.media(chat.jid, m.id), 900)
            if (decoded != null) { cache[m.id] = decoded; img = decoded } else failed = true
        } catch (_: Exception) { failed = true }
    }
    val bm = img
    Box(Modifier.clip(RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
        when {
            bm != null -> {
                val ratio = (bm.width.toFloat() / bm.height).coerceIn(0.6f, 1.9f)
                Image(
                    bm, "Photo", contentScale = ContentScale.Crop,
                    modifier = Modifier.width(260.dp).height(260.dp / ratio),
                )
            }
            failed -> Column(
                Modifier.size(width = 260.dp, height = 180.dp).background(c.fileCard),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Outlined.BrokenImage, null, tint = c.bubbleMeta, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(6.dp))
                Text("Photo not available", color = c.bubbleMeta, fontSize = 13.sp)
            }
            else -> Box(Modifier.size(260.dp).background(c.fileCard), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = c.brand, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
            }
        }
    }
}

@Composable
private fun MediaTile(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Box(
        Modifier.size(width = 260.dp, height = 170.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF1F2A30)),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(Color(0x99000000)), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(32.dp))
        }
        Text(label, color = Color.White, fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
    }
}

@Composable
private fun AudioContent() {
    val c = Wa.colors
    Row(Modifier.widthIn(min = 240.dp).padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.PlayArrow, "Play", tint = c.textSecondary, modifier = Modifier.size(34.dp))
        Row(Modifier.weight(1f).padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(6, 12, 18, 10, 22, 14, 8, 16, 24, 12, 6, 14, 20, 10, 8, 16, 12, 6, 10, 14).forEach { h ->
                Box(Modifier.width(3.dp).height(h.dp).clip(RoundedCornerShape(2.dp)).background(c.bubbleMeta))
            }
        }
        Box(Modifier.size(40.dp).clip(CircleShape).background(c.brand), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Mic, null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun DocumentContent(m: Message) {
    val c = Wa.colors
    val name = displayFilename(m.filename).ifBlank { "Document" }
    val ext = name.substringAfterLast('.', "").uppercase().take(4)
    Row(
        Modifier.widthIn(min = 240.dp, max = 280.dp).clip(RoundedCornerShape(6.dp)).background(c.fileCard).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(width = 32.dp, height = 40.dp).clip(RoundedCornerShape(4.dp))
                .background(if (ext == "PDF") Color(0xFFE5252A) else Color(0xFF5B8DEF)),
            contentAlignment = Alignment.Center,
        ) {
            if (ext.isNotEmpty()) Text(ext, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            else Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.padding(start = 10.dp).weight(1f, fill = false)) {
            Text(name, color = c.text, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (ext.isNotEmpty()) Text(ext, color = c.bubbleMeta, fontSize = 12.sp)
        }
    }
}

@Composable
private fun InputBar(draft: String, onDraft: (String) -> Unit, onSend: () -> Unit, onUnavailable: (String) -> Unit) {
    val c = Wa.colors
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Row(
            Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).background(c.inputBg).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarIcon(Icons.Outlined.EmojiEmotions, "Emoji", "emoji") { onUnavailable("Emoji") }
            Box(Modifier.weight(1f).padding(vertical = 12.dp)) {
                if (draft.isEmpty()) Text("Message", color = c.textSecondary, fontSize = 17.sp)
                BasicTextField(
                    value = draft, onValueChange = onDraft, maxLines = 6,
                    textStyle = TextStyle(color = c.text, fontSize = 17.sp),
                    cursorBrush = SolidColor(c.brand),
                    modifier = Modifier.fillMaxWidth().testTag("message-input"),
                )
            }
            Box(Modifier.rotate(-45f)) {
                BarIcon(Icons.Filled.AttachFile, "Attach", "attach") { onUnavailable("Attachments") }
            }
            if (draft.isEmpty()) BarIcon(Icons.Filled.PhotoCamera, "Camera", "chat-camera") { onUnavailable("Camera") }
        }
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(c.fab)
                .clickable { if (draft.isBlank()) onUnavailable("Voice messages") else onSend() }
                .testTag(if (draft.isBlank()) "mic" else "send"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (draft.isBlank()) Icons.Filled.Mic else Icons.AutoMirrored.Filled.Send,
                if (draft.isBlank()) "Voice message" else "Send",
                tint = c.onFab, modifier = Modifier.size(24.dp),
            )
        }
    }
}

