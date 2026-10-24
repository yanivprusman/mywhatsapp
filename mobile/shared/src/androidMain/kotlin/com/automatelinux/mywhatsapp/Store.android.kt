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
        // CLEAR_TASK: an already-open WhatsApp conversation otherwise swallows the intent and stays
        // on ITS chat (measured: asked for one group, got the one open from before).
        return Intent("$pkg.Conversation").setPackage(pkg).putExtra("jid", jid)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
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

/** What the accessibility helper should find once WhatsApp shows the chat. */
data class FindJob(val jid: String, val query: String, val at: Long)

object FindMessage {
    @Volatile var job: FindJob? = null
    const val SERVICE = "com.automatelinux.mywhatsapp.FindMessageService"

    fun helperEnabled(): Boolean {
        val ctx = AndroidPlatform.context
        val on = android.provider.Settings.Secure.getString(ctx.contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        return on.split(':').any { it.equals("${ctx.packageName}/$SERVICE", ignoreCase = true) }
    }

    /** WhatsApp's search matches text inside messages; the first line, cut at a word, is distinctive and short. */
    fun queryFor(text: String): String {
        val line = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return ""
        if (line.length <= 40) return line
        val cut = line.take(40)
        return cut.substring(0, cut.lastIndexOf(' ').takeIf { it > 15 } ?: cut.length).trim()
    }
}

actual fun showInWhatsApp(jid: String, text: String): String? {
    val query = FindMessage.queryFor(text)
    if (query.isEmpty() || !FindMessage.helperEnabled()) {
        // Explicit state, not a silent fallback: without the helper only the chat can be opened.
        if (query.isNotEmpty()) {
            val cm = AndroidPlatform.context.getSystemService(android.content.ClipboardManager::class.java)
            cm.setPrimaryClip(android.content.ClipData.newPlainText("message", query))
        }
        return openWhatsAppChat(jid) ?: if (query.isEmpty()) null
        else "Opened the chat. To land on the message, turn on MyWhatsApp in Accessibility (text copied for now)"
    }
    FindMessage.job = FindJob(jid, query, System.currentTimeMillis())
    return openWhatsAppChat(jid)
}

actual fun dial(phone: String): String? {
    AndroidPlatform.context.startActivity(
        Intent(Intent.ACTION_DIAL, Uri.parse("tel:+$phone")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    return null
}
