package com.automatelinux.mywhatsapp

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings

/** Holds the application context the platform actuals need; set once in Application.onCreate. */
@SuppressLint("StaticFieldLeak")
object AndroidPlatform {
    lateinit var context: Context
        private set
    fun init(app: Context) { context = app.applicationContext }

    /**
     * WhatsApp Business is preferred because that is where the owner reads these
     * groups; plain WhatsApp only when Business isn't installed.
     */
    private val whatsAppPackages = listOf("com.whatsapp.w4b", "com.whatsapp")

    /** The chat with [phone], empty — nothing typed in, exactly like WhatsApp's own "Message <name>". */
    private fun installedWhatsApp(): String? = whatsAppPackages.firstOrNull { p ->
        runCatching { context.packageManager.getPackageInfo(p, 0) }.isSuccess
    }

    /**
     * A chat — group or person — opened by jid. WhatsApp's Conversation activity is
     * exported under the action "<package>.Conversation" and takes the jid as an extra;
     * there is no public way to open it AT a given message.
     */
    fun chatIntent(jid: String): Intent? {
        val pkg = installedWhatsApp() ?: return null
        return Intent("$pkg.Conversation").setPackage(pkg).putExtra("jid", jid).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun whatsAppIntent(phone: String): Intent? {
        val pkg = installedWhatsApp() ?: return null
        val uri = Uri.parse("https://api.whatsapp.com/send?phone=$phone")
        return Intent(Intent.ACTION_VIEW, uri).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

private val settings by lazy {
    SharedPreferencesSettings(AndroidPlatform.context.getSharedPreferences("mywhatsapp", Context.MODE_PRIVATE))
}

actual fun appSettings(): Settings = settings

actual fun openWhatsApp(phone: String): String? {
    val intent = AndroidPlatform.whatsAppIntent(phone) ?: return "WhatsApp isn't installed on this phone"
    AndroidPlatform.context.startActivity(intent)
    return null
}

actual fun openWhatsAppChat(jid: String): String? {
    val intent = AndroidPlatform.chatIntent(jid) ?: return "WhatsApp isn't installed on this phone"
    return runCatching { AndroidPlatform.context.startActivity(intent) }.exceptionOrNull()
        ?.let { "WhatsApp wouldn't open the chat: ${it.message}" }
}

actual fun dial(phone: String): String? {
    AndroidPlatform.context.startActivity(
        Intent(Intent.ACTION_DIAL, Uri.parse("tel:+$phone")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    return null
}
