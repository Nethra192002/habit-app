package com.nethra.habitapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

// Runs in the background and checks every 30 seconds whether a nudge is due
class WatcherService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var running = false
    private var model: RiskModel? = null
    private var modelLoadedAt = 0L
    private var decidedEpisode = -1L                 // start time of the episode already judged
    private val recentNudges = ArrayDeque<Long>()    // for the 3-per-hour cap

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Android requires a visible notification while a service runs in the background
        val channel = NotificationChannel("watcher", "Watcher", NotificationManager.IMPORTANCE_MIN)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val notification = NotificationCompat.Builder(this, "watcher")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Robot is watching quietly")
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(
            this, 2, notification,
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!running) {
            running = true
            scope.launch {
                while (isActive) {
                    runCatching { tick() }   // one bad check never kills the watcher
                    delay(30_000)
                }
            }
        }
        return START_STICKY   // if Android stops it, start it again when possible
    }

    private suspend fun tick() {
        val now = System.currentTimeMillis()

        // Heartbeat: remember when the watcher last ran, so the app can show it
        getSharedPreferences("watcher", MODE_PRIVATE).edit().putLong("lastTick", now).apply()

        // Send any decisions still waiting (does nothing if the queue is empty)
        runCatching { flushDecisions(this) }

        // Refresh the model every 6 hours, so a retrain in Python reaches the phone
        if (model == null || now - modelLoadedAt > 6 * 3_600_000L) {
            loadRiskModel(this)?.let { model = it; modelLoadedAt = now }
        }
        val m = model ?: return
        if (!hasUsageAccess(this)) return

        val snap = liveSnapshot(this, m, now)
        val ep = snap.current ?: return                 // not in an episode
        if (ep.startMs == decidedEpisode) return        // already judged this one
        if (ep.activeMs < 5 * 60_000L) return           // not at minute 5 yet

        decidedEpisode = ep.startMs                     // judge each episode once, at minute 5
        val risk = m.score(m.featuresFor(ep.startMs, snap.gapMin,
            snap.minsTodayBefore, snap.episodesTodayBefore, ep.firstCategory))
        val minutes = ep.activeMs / 60_000.0

        // Writes this decision to the queue, whatever it was
        fun record(action: String, line: String?) = queueDecision(this, JSONObject().apply {
            put("decidedMs", now)
            put("episodeStartMs", ep.startMs)
            put("firstApp", ep.firstApp)
            put("firstCategory", ep.firstCategory)
            put("activeMinutes", minutes)
            put("risk", risk)
            put("threshold", m.threshold)
            put("modelId", m.id)
            put("action", action)
            put("line", line ?: JSONObject.NULL)
        })

        if (risk < m.threshold) {
            record("quiet_low_risk", null)             // looks fine: stay quiet, but note it
            return
        }

        // Max 3 nudges in any hour
        while (recentNudges.isNotEmpty() && now - recentNudges.first() > 3_600_000L) recentNudges.removeFirst()
        if (recentNudges.size >= 3) {
            record("quiet_hourly_cap", null)
            return
        }
        recentNudges.addLast(now)

        val line = robotLine(minutes.toInt(), APP_NAMES[ep.firstApp] ?: "this")
        robotSay(this, line)
        record("nudged", line)
        runCatching { flushDecisions(this) }           // send it straight away
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}