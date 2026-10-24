package com.automatelinux.mywhatsapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts the Wanted watcher after a reboot or an app update, if alerts are on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            WantedWatcherService.sync(context)
        }
    }
}
