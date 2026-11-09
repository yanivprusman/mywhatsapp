package com.automatelinux.mywhatsapp

import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import java.util.prefs.Preferences

private val settings by lazy { PreferencesSettings(Preferences.userRoot().node("com/automatelinux/mywhatsapp")) }

actual fun appSettings(): Settings = settings

// Linux has no WhatsApp app: these drive the WhatsApp Web tab, off the UI thread. They return null
// straight away; a failure arrives as a notification, since by then Chrome is in front of this window.

actual fun openWhatsApp(phone: String): String? { WhatsAppWeb.message(phone); return null }

actual fun replyInWhatsApp(phone: String, text: String): String? {
    java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(text), null)
    WhatsAppWeb.message(phone)
    return "Reply copied — paste it into WhatsApp"
}

actual fun openWhatsAppChat(jid: String): String? { WhatsAppWeb.focus(); return null }

actual fun showInWhatsApp(jid: String, chatName: String, text: String): String? { WhatsAppWeb.show(chatName, text); return null }

actual val canDial: Boolean = false

actual fun dial(phone: String): String? = "Calling isn't available on the desktop"
