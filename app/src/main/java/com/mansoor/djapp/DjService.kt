package com.mansoor.djapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Keeps the process (and the DJ engine's timers) alive while a mix is running. */
class DjService : Service() {
    companion object {
        private const val CHANNEL = "dj_mix"
        private const val NOTIF_ID = 1
        private const val ACTION_STOP = "com.mansoor.djapp.STOP"
        private const val ACTION_NEXT = "com.mansoor.djapp.NEXT"
        private const val ACTION_TOGGLE = "com.mansoor.djapp.TOGGLE"

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, DjService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, DjService::class.java))
        }
    }

    private val scope = MainScope()
    private var watcher: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                DjEngine.stop()
                return START_NOT_STICKY
            }
            ACTION_NEXT -> DjEngine.next()
            ACTION_TOGGLE -> DjEngine.togglePause()
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "DJ mix", NotificationManager.IMPORTANCE_LOW))
        ServiceCompat.startForeground(
            this, NOTIF_ID, build(DjEngine.state.value),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "djapp:mix")
                .apply { acquire(6 * 60 * 60 * 1000L) }
        }
        if (watcher == null) {
            watcher = scope.launch {
                DjEngine.state
                    .map { Triple(it.trackName, it.artist, it.paused) }
                    .distinctUntilChanged()
                    .collect { nm.notify(NOTIF_ID, build(DjEngine.state.value)) }
            }
        }
        return START_NOT_STICKY
    }

    private fun action(action: String): PendingIntent = PendingIntent.getService(
        this, action.hashCode(), Intent(this, DjService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun build(s: DjEngine.State): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_dj)
            .setContentTitle(s.trackName.ifBlank { "DJ Mix" })
            .setContentText(s.artist.ifBlank { "Mixing your playlists" })
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, if (s.paused) "Play" else "Pause", action(ACTION_TOGGLE))
            .addAction(0, "Mix next", action(ACTION_NEXT))
            .addAction(0, "Stop", action(ACTION_STOP))
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }
}
