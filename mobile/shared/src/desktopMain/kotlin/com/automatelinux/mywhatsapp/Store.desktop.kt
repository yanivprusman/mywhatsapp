package com.automatelinux.mywhatsapp

import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.util.prefs.Preferences

private val settings by lazy { PreferencesSettings(Preferences.userRoot().node("com/automatelinux/mywhatsapp")) }

actual fun appSettings(): Settings = settings

/** Linux has no WhatsApp app: links go to WhatsApp Web in the default browser. */
private fun openUrl(url: String): String? =
    runCatching { ProcessBuilder("xdg-open", url).inheritIO().start() }.exceptionOrNull()
        ?.let { "Couldn't open the browser: ${it.message}" }

actual fun openWhatsApp(phone: String): String? = openUrl("https://web.whatsapp.com/send?phone=$phone")

/** WhatsApp Web has no link that opens a group, so this opens WhatsApp Web and says so. */
actual fun openWhatsAppChat(jid: String): String? =
    openUrl("https://web.whatsapp.com/") ?: "Opened WhatsApp Web — WhatsApp Web can't be opened on a group, pick it there"

/** No way to land on a message in WhatsApp Web: its first line goes to the clipboard for WhatsApp's in-chat search. */
actual fun showInWhatsApp(jid: String, text: String): String? {
    val query = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }?.take(40)
    openUrl("https://web.whatsapp.com/")?.let { return it }
    if (query == null) return "Opened WhatsApp Web — pick the group there"
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(query), null)
    return "Opened WhatsApp Web — pick the group and paste into its search (first line copied)"
}

actual val canDial: Boolean = false

actual fun dial(phone: String): String? = "Calling isn't available on the desktop"
