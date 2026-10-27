package com.automatelinux.mywhatsapp

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.loadSvgPainter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.automatelinux.mywhatsapp.data.Api
import com.automatelinux.mywhatsapp.data.WantedPost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val KEY_AFTER = "wantedAfter"
private const val POLL_MS = 60_000L

fun main() {
    // Alerts are for posts that arrive while they are on: switching them off forgets the
    // resume point, so switching back on doesn't dump everything posted in between.
    Alerts.onChange = { on -> if (!on) appSettings().remove(KEY_AFTER) }
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { watchWanted() }

    application {
        val icon = remember { Thread.currentThread().contextClassLoader.getResourceAsStream("mywhatsapp.svg")!!.use { loadSvgPainter(it, Density(1f)) } }
        Window(
            onCloseRequest = ::exitApplication,
            title = "MyWhatsApp",
            icon = icon,
            state = rememberWindowState(size = DpSize(460.dp, 860.dp)),
            // Escape is the desktop's Back: closes the open chat / sheet, like the phone's back gesture.
            onPreviewKeyEvent = { e -> e.type == KeyEventType.KeyDown && e.key == Key.Escape && DesktopBack.back() },
        ) {
            val openJid by Launch.openChat.collectAsState()
            LaunchedEffect(openJid) { if (openJid != null) window.toFront() }
            App(AppConfig(DesktopConfig.API_BASE_URL, DesktopConfig.API_TOKEN))
        }
    }
}

/** The desktop twin of the phone's WantedWatcherService: polls /api/wanted, one notification per post. */
private suspend fun watchWanted() {
    val api = Api(DesktopConfig.API_BASE_URL, DesktopConfig.API_TOKEN)
    val settings = appSettings()
    var failing = false
    while (true) {
        if (Alerts.enabled) {
            try {
                val after = settings.getStringOrNull(KEY_AFTER)
                val res = api.wanted(after)
                if (after != null) res.posts.forEach(::notifyPost)
                settings.putString(KEY_AFTER, res.latest)
                failing = false
            } catch (e: Exception) {
                // Said once per outage, not once a minute.
                if (!failing) System.err.println("MyWhatsApp: can't reach ${DesktopConfig.API_BASE_URL} for Wanted alerts — ${e.message}")
                failing = true
            }
        }
        delay(POLL_MS)
    }
}

/**
 * A GNOME notification with the phone's two actions. notify-send waits for the click and
 * prints the chosen action, so each one waits on its own thread.
 */
private fun notifyPost(p: WantedPost) {
    val m = p.message
    val sender = (m.senderName ?: "+${m.sender}").removePrefix("~ ")
    val hasPhone = Regex("^\\d{6,15}$").matches(m.sender)
    val cmd = buildList {
        addAll(listOf("notify-send", "--app-name=MyWhatsApp", "--icon=mywhatsapp", "--action=open=Open chat"))
        if (hasPhone) add("--action=message=Message $sender")
        add("$sender · ${p.chatName}")
        add(m.text.ifBlank { "New job post" })
    }
    Thread {
        val proc = runCatching { ProcessBuilder(cmd).redirectErrorStream(false).start() }.getOrElse {
            System.err.println("MyWhatsApp: notify-send failed — ${it.message}"); return@Thread
        }
        when (proc.inputStream.bufferedReader().readText().trim()) {
            "open" -> Launch.openChat.value = p.chatJid
            "message" -> openWhatsApp(m.sender)?.let { System.err.println("MyWhatsApp: $it") }
        }
    }.apply { isDaemon = true }.start()
}
