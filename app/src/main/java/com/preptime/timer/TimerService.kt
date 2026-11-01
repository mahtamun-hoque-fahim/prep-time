package com.preptime.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import com.preptime.MainActivity
import com.preptime.R
import com.preptime.model.Phase
import com.preptime.model.SpeakerClock
import com.preptime.model.formatClock

/**
 * Keeps the process and CPU alive while a speaker is live, so alerts fire with the screen off.
 * Its notification is a Live Update: on Android 16+ it becomes a counting pill in the status bar.
 */
class TimerService : Service() {
    private var lock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?) = null

    // After a process kill there is no timer to serve; don't come back as an orphan.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_NOT_STICKY

    override fun onCreate() {
        super.onCreate()
        running = true
        val n = latest ?: build(this, "Prep Time", null)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(ID, n)
        }
        lock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "preptime:timer")
            .apply { acquire(4 * 60 * 60 * 1000L) } // cap: a forgotten timer can't hold the CPU forever
    }

    override fun onDestroy() {
        running = false
        latest = null
        lock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "timer_live"
        private const val ID = 1
        private var running = false
        private var latest: Notification? = null

        /** Starts the service if needed and shows [clock] (`now` = elapsedRealtime) in the notification and pill. */
        fun show(ctx: Context, title: String, clock: SpeakerClock, now: Long) {
            val n = build(ctx, title, clock to clock.remainingMs(now))
            latest = n
            if (running) {
                ctx.getSystemService(NotificationManager::class.java).notify(ID, n)
            } else {
                ctx.startForegroundService(Intent(ctx, TimerService::class.java))
            }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, TimerService::class.java))
        }

        private fun build(ctx: Context, title: String, state: Pair<SpeakerClock, Long>?): Notification {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.deleteNotificationChannel("timer") // 1.0's low-importance channel, which can't show a pill
            // Default importance so the pill shows; silent because the bell is the only sound.
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Running timer", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    setSound(null, null)
                    enableVibration(false)
                },
            )
            val open = PendingIntent.getActivity(
                ctx, 0,
                Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE,
            )
            val b = Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_STOPWATCH)
            if (state != null) {
                val (clock, remaining) = state
                // The system ticks the chronometer itself, so no per-second reposting is needed.
                val end = System.currentTimeMillis() + remaining
                when (clock.phase) {
                    Phase.RUNNING -> b.setContentText("Speaking").setWhen(end)
                        .setUsesChronometer(true).setChronometerCountDown(true)
                    Phase.OVERTIME -> b.setContentText("Time up · overtime").setWhen(end).setUsesChronometer(true)
                    else -> b.setContentText("Paused · ${formatClock(remaining)}").setShowWhen(false)
                        .setShortCriticalText("Paused")
                }
            }
            // shortcut: raw EXTRA_REQUEST_PROMOTED_ONGOING key, as setRequestPromotedOngoing isn't in the API 36 SDK; use it once compileSdk >= 37
            b.addExtras(android.os.Bundle().apply { putBoolean("android.requestPromotedOngoing", true) })
            return b.build()
        }
    }
}
