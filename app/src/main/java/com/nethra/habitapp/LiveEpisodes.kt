package com.nethra.habitapp

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import java.time.Instant
import java.time.ZoneId

// One visit to a tracked app
private data class LiveSession(val pkg: String, val start: Long, val end: Long)

// One episode: visits less than 2 minutes apart, joined (same rule as the Neon view)
data class LiveEpisode(
    val startMs: Long,
    val endMs: Long,
    val activeMs: Long,         // time actually in tracked apps
    val firstCategory: String,  // category of the app it started on
    val ongoing: Boolean,       // true = you're in it right now
    val firstApp: String        // package it started on, e.g. com.instagram.android
)

// What the watcher needs to know at this moment
data class LiveSnapshot(
    val current: LiveEpisode?,  // the episode you're in now, or null
    val gapMin: Double?,        // minutes between the previous episode and this one
    val minsTodayBefore: Double,
    val episodesTodayBefore: Int
)

// Start of "today" in your 4am-to-4am days
fun dayStartMs(nowMs: Long, dayStartsHour: Int): Long {
    val zone = ZoneId.systemDefault()
    val now = Instant.ofEpochMilli(nowMs).atZone(zone)
    var start = now.toLocalDate().atTime(dayStartsHour, 0).atZone(zone)
    if (now.isBefore(start)) start = start.minusDays(1)
    return start.toInstant().toEpochMilli()
}

fun liveSnapshot(context: Context, model: RiskModel, nowMs: Long = System.currentTimeMillis()): LiveSnapshot {
    val dayStart = dayStartMs(nowMs, model.dayStartsHour)
    val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    // Read from 12 hours before today began, so the gap to yesterday's last episode is known
    val events = usm.queryEvents(dayStart - 12 * 3_600_000L, nowMs)
    val e = UsageEvents.Event()

    // 1. Events -> sessions in tracked apps
    val sessions = mutableListOf<LiveSession>()
    var openPkg: String? = null
    var openStart = 0L
    while (events.hasNextEvent()) {
        events.getNextEvent(e)
        val pkg = e.packageName ?: ""
        when (e.eventType) {
            // An app came to the front
            UsageEvents.Event.ACTIVITY_RESUMED -> if (pkg != openPkg) {
                openPkg?.let { sessions.add(LiveSession(it, openStart, e.timeStamp)) }   // close the old one
                openPkg = if (model.categories.containsKey(pkg)) pkg else null           // open if tracked
                openStart = e.timeStamp
            }
            // Screen off or locked: whatever was open ends here
            UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.KEYGUARD_SHOWN -> {
                openPkg?.let { sessions.add(LiveSession(it, openStart, e.timeStamp)) }
                openPkg = null
            }
        }
    }
    val stillOpen = openPkg != null
    openPkg?.let { sessions.add(LiveSession(it, openStart, nowMs)) }   // still on it right now

    // 2. Sessions -> episodes (2-minute rule)
    val episodes = mutableListOf<LiveEpisode>()
    sessions.forEachIndexed { i, s ->
        val isNow = stillOpen && i == sessions.lastIndex
        val last = episodes.lastOrNull()
        if (last != null && s.start - last.endMs <= 2 * 60_000L) {
            episodes[episodes.lastIndex] = last.copy(
                endMs = maxOf(last.endMs, s.end),
                activeMs = last.activeMs + (s.end - s.start),
                ongoing = isNow
            )
        } else {
            episodes.add(LiveEpisode(s.start, s.end, s.end - s.start,
                model.categories[s.pkg] ?: "feed", isNow, s.pkg))
        }
    }

    // 3. The numbers the model wants
    val current = episodes.lastOrNull()?.takeIf { it.ongoing }
    val before = if (current != null) episodes.dropLast(1) else episodes
    val today = before.filter { it.startMs >= dayStart }
    val gap = if (current != null) before.lastOrNull()?.let { (current.startMs - it.endMs) / 60_000.0 } else null

    return LiveSnapshot(
        current = current,
        gapMin = gap,
        minsTodayBefore = today.sumOf { it.activeMs } / 60_000.0,
        episodesTodayBefore = today.size
    )
}