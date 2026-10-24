package com.automatelinux.mywhatsapp

import com.russhwolf.settings.Settings

actual fun appSettings(): Settings = TODO("iOS settings: NSUserDefaultsSettings when the iOS target is enabled")
actual fun openWhatsApp(phone: String): String? = "Not available on iOS yet"
actual fun dial(phone: String): String? = "Not available on iOS yet"
actual fun openWhatsAppChat(jid: String): String? = "Not available on iOS yet"
actual fun showInWhatsApp(jid: String, text: String): String? = "Not available on iOS yet"
