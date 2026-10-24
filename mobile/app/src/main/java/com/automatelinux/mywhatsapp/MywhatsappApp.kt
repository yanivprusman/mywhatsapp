package com.automatelinux.mywhatsapp

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class MywhatsappApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidPlatform.init(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(WantedWatcherService.CHANNEL_POSTS, "Wanted posts", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "A new job post in a group you follow" },
        )
        nm.createNotificationChannel(
            NotificationChannel(WantedWatcherService.CHANNEL_WATCHER, "Watching for posts", NotificationManager.IMPORTANCE_MIN)
                .apply { description = "The quiet notification that keeps the watcher running"; setShowBadge(false) },
        )
    }
}
