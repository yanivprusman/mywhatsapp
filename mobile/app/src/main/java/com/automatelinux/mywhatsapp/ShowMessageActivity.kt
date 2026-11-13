package com.automatelinux.mywhatsapp

import android.app.Activity
import android.os.Bundle
import android.widget.Toast

/**
 * "Show this message in WhatsApp" for OTHER apps on the phone (first: IoT Tracker's
 * salvage finds). The accessibility helper that lands on a message lives in this
 * app, and Android lets a user enable it once — so other apps hand the job here
 * instead of each shipping its own helper.
 *
 * Exported on purpose: the worst any caller can do is open a WhatsApp chat and fill
 * WhatsApp's search box. Nothing is sent or edited.
 *
 * Intent: action [ACTION], extras [EXTRA_JID] (chat jid, e.g. …@g.us) and [EXTRA_TEXT]
 * (the message text; its first line is what gets searched).
 */
class ShowMessageActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val jid = intent.getStringExtra(EXTRA_JID)
        val error = if (jid.isNullOrBlank()) "No chat given to show"
        else showInWhatsApp(jid, "", intent.getStringExtra(EXTRA_TEXT) ?: "")
        if (error != null) Toast.makeText(this, error, Toast.LENGTH_LONG).show()
        finish()
    }

    companion object {
        const val ACTION = "com.automatelinux.mywhatsapp.SHOW_MESSAGE"
        const val EXTRA_JID = "jid"
        const val EXTRA_TEXT = "text"
    }
}
