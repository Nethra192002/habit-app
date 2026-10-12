package com.nethra.habitapp

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Is Usage access switched on for this app?
fun hasUsageAccess(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.unsafeCheckOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        Process.myUid(),
        context.packageName
    )
    return mode == AppOpsManager.MODE_ALLOWED
}

// What the phone has recorded, counted up
data class UsageSummary(
    val totalEvents: Int,
    val appOpens: Int,
    val unlocks: Int,
    val screenOns: Int,
    val oldestEventTime: Long?
)

// Reads every recorded event from the last `days` days
fun readUsageSummary(context: Context, days: Int = 30): UsageSummary {
    val usageStats = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val end = System.currentTimeMillis()
    val start = end - days * 24L * 60 * 60 * 1000

    val events = usageStats.queryEvents(start, end)
    val event = UsageEvents.Event()

    var total = 0
    var opens = 0
    var unlocks = 0
    var screenOns = 0
    var oldest: Long? = null

    while (events.hasNextEvent()) {
        events.getNextEvent(event)
        total++
        if (oldest == null) oldest = event.timeStamp

        when (event.eventType) {
            UsageEvents.Event.ACTIVITY_RESUMED -> opens++
            UsageEvents.Event.KEYGUARD_HIDDEN -> unlocks++
            UsageEvents.Event.SCREEN_INTERACTIVE -> screenOns++
        }
    }

    return UsageSummary(total, opens, unlocks, screenOns, oldest)
}

// Turns a timestamp into "Mon 5 Oct, 08:22"
fun formatDateTime(millis: Long): String =
    Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("EEE d MMM, HH:mm"))