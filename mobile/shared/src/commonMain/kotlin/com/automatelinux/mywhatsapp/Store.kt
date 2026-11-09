package com.automatelinux.mywhatsapp

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** The platform's key-value store (SharedPreferences on Android). */
expect fun appSettings(): Settings

/** Open WhatsApp on the chat with [phone] (digits, country code first), as its own "Message" does. Returns an error, or null. */
expect fun openWhatsApp(phone: String): String?

/**
 * Open WhatsApp on the chat with [phone] with [text] already in its message box, for the
 * owner to edit and send himself — the reply helper never sends. Desktop: the WhatsApp
 * Web tab can't be handed text, so it is copied and the chat opened. Returns a note, or null.
 */
expect fun replyInWhatsApp(phone: String, text: String): String?

/** Open a WhatsApp chat (a group's or a person's [jid]) in the WhatsApp app. Returns an error, or null. */
expect fun openWhatsAppChat(jid: String): String?

/**
 * Open the chat [jid], named [chatName], in WhatsApp and land on the message whose text
 * starts with [text], through WhatsApp's in-chat search (Android: an accessibility helper,
 * by jid; desktop: the WhatsApp Web tab, which can only reach a group by its name). Returns
 * a note for the user (e.g. the helper is off and only the chat was opened), or null.
 */
expect fun showInWhatsApp(jid: String, chatName: String, text: String): String?

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
