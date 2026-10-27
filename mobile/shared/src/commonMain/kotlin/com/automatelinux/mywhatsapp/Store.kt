package com.automatelinux.mywhatsapp

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** The platform's key-value store (SharedPreferences on Android). */
expect fun appSettings(): Settings

/** Open WhatsApp on the chat with [phone] (digits, country code first), as its own "Message" does. Returns an error, or null. */
expect fun openWhatsApp(phone: String): String?

/** Open a WhatsApp chat (a group's or a person's [jid]) in the WhatsApp app. Returns an error, or null. */
expect fun openWhatsAppChat(jid: String): String?

/**
 * Open [jid] in WhatsApp and land on the message whose text starts with [text]: the
 * accessibility helper opens WhatsApp's in-chat search and enters it. Returns a note
 * for the user (e.g. the helper is off and only the chat was opened), or null.
 */
expect fun showInWhatsApp(jid: String, text: String): String?

/** Whether this device can place a call (false on the desktop — the sheet then has no Call row). */
expect val canDial: Boolean

/** Open the dialer on [phone]. Returns an error, or null. */
expect fun dial(phone: String): String?

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
