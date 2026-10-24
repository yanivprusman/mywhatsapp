package com.automatelinux.mywhatsapp

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** The platform's key-value store (SharedPreferences on Android). */
expect fun appSettings(): Settings

/** Open WhatsApp on the chat with [phone] (digits, country code first), as its own "Message" does. Returns an error, or null. */
expect fun openWhatsApp(phone: String): String?

/** Open the dialer on [phone]. Returns an error, or null. */
expect fun dial(phone: String): String?

/** The newest message the user has seen, per chat — drives unread badges. */
object Seen {
    private const val KEY = "seen"
    fun all(): Map<String, String> =
        appSettings().getStringOrNull(KEY)?.let { Json.decodeFromString<Map<String, String>>(it) } ?: emptyMap()

    fun mark(jid: String, ts: String) {
        val cur = all()
        if ((cur[jid] ?: "") >= ts) return
        appSettings().putString(KEY, Json.encodeToString<Map<String, String>>(cur + (jid to ts)))
    }
}

/** The filter chip last chosen in each chat — a chat reopens on it (after leaving it, or a restart). */
object ChatFilter {
    fun get(jid: String): String = appSettings().getString("filter:$jid", "all")
    fun set(jid: String, filter: String) = appSettings().putString("filter:$jid", filter)
}

/** Notifications for new Wanted posts. On by default; the Android side starts/stops the watcher. */
object Alerts {
    private const val KEY = "wantedAlerts"
    var onChange: ((Boolean) -> Unit)? = null
    var enabled: Boolean
        get() = appSettings().getBoolean(KEY, true)
        set(v) { appSettings().putBoolean(KEY, v); onChange?.invoke(v) }
}

/** A chat a notification asked to open (set by the Android activity, consumed by App()). */
object Launch {
    val openChat = MutableStateFlow<String?>(null)
}
