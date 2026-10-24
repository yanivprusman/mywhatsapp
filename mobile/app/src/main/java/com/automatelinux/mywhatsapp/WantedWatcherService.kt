package com.automatelinux.mywhatsapp

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import com.automatelinux.mywhatsapp.data.Api
import com.automatelinux.mywhatsapp.data.WantedPost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Polls the backend for new Wanted posts and raises one notification per post.
 *
 * A foreground service, not WorkManager: WorkManager's floor is 15 minutes and
 * "דרוש גולש לעכשיו" posts are gone by then. The cost is one small request a
 * minute and a silent, minimised "watching" notification Android requires.
 * Where it resumes is persisted ("wantedAfter"), so a restart neither repeats
 * old posts nor skips the ones that arrived while it was down.
 */
class WantedWatcherService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(ID_WATCHER, watcherNotification("Watching for Wanted posts"), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        if (!running) {
            running = true
            scope.launch { loop() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun loop() {
        val api = Api(BuildConfig.API_BASE_URL, BuildConfig.API_TOKEN)
        val settings = appSettings()
        var failingSince: Long? = null
        while (scope.isActive) {
            try {
                val after = settings.getStringOrNull(KEY_AFTER)
                val res = api.wanted(after)
                if (after != null) res.posts.forEach(::notifyPost)
                settings.putString(KEY_AFTER, res.latest)
                if (failingSince != null) {
                    failingSince = null
                    update(watcherNotification("Watching for Wanted posts"))
                }
            } catch (e: Exception) {
                if (failingSince == null) {
                    failingSince = System.currentTimeMillis()
                    val at = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(failingSince))
                    update(watcherNotification("Can't reach the server since $at — retrying"))
                }
            }
            delay(POLL_MS)
        }
    }

    private fun notifyPost(p: WantedPost) {
        val m = p.message
        val sender = (m.senderName ?: "+${m.sender}").removePrefix("~ ")
        val id = m.id.hashCode()
        val open = PendingIntent.getActivity(
            this, id,
            Intent(this, MainActivity::class.java).putExtra(EXTRA_JID, p.chatJid)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = Notification.Builder(this, CHANNEL_POSTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$sender · ${p.chatName}")
            .setContentText(m.text.lineSequence().firstOrNull { it.isNotBlank() } ?: "New job post")
            .setStyle(Notification.BigTextStyle().bigText(m.text))
            .setColor(0xFF1DAA61.toInt())
            .setWhen(runCatching { java.time.OffsetDateTime.parse(m.ts).toInstant().toEpochMilli() }.getOrDefault(System.currentTimeMillis()))
            .setShowWhen(true)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setGroup(GROUP_POSTS)
        if (Regex("^\\d{6,15}$").matches(m.sender)) {
            AndroidPlatform.whatsAppIntent(m.sender)?.let { wa ->
                val pi = PendingIntent.getActivity(this, id + 1, wa, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                b.addAction(Notification.Action.Builder(null, "Message $sender", pi).build())
            }
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(id, b.build())
        nm.notify(ID_GROUP, Notification.Builder(this, CHANNEL_POSTS).setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF1DAA61.toInt()).setGroup(GROUP_POSTS).setGroupSummary(true).setAutoCancel(true).build())
    }

    private fun watcherNotification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL_WATCHER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("MyWhatsApp")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    private fun update(n: Notification) = getSystemService(NotificationManager::class.java).notify(ID_WATCHER, n)

    companion object {
        const val CHANNEL_POSTS = "wanted_posts"
        const val CHANNEL_WATCHER = "watcher"
        const val EXTRA_JID = "jid"
        private const val GROUP_POSTS = "wanted"
        private const val KEY_AFTER = "wantedAfter"
        private const val ID_WATCHER = 1
        private const val ID_GROUP = 2
        private const val POLL_MS = 60_000L
        @Volatile private var running = false

        fun sync(context: Context) {
            val intent = Intent(context, WantedWatcherService::class.java)
            if (Alerts.enabled) context.startForegroundService(intent) else context.stopService(intent)
        }
    }
}
