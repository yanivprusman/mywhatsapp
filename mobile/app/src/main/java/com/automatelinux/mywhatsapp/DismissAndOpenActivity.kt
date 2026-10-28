package com.automatelinux.mywhatsapp

import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle

/**
 * Target of a post notification's "Message <sender>" action.
 *
 * Android auto-cancels a notification only when its body is tapped, never when an
 * action button is — so replying straight to WhatsApp left the post sitting in the
 * shade, and its launcher badge "1" with it. This invisible activity dismisses the
 * post (and the group summary once it is the last one), then hands off to WhatsApp.
 * An activity, not a receiver: Android 12+ blocks starting activities from a
 * notification's broadcast/service trampoline.
 */
class DismissAndOpenActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val nm = getSystemService(NotificationManager::class.java)
        nm.cancel(intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0))
        WantedWatcherService.dropEmptySummary(nm)
        @Suppress("DEPRECATION")
        intent.getParcelableExtra<Intent>(EXTRA_TARGET)?.let(::startActivity)
        finish()
    }

    companion object {
        const val EXTRA_NOTIFICATION_ID = "notificationId"
        const val EXTRA_TARGET = "target"
    }
}
