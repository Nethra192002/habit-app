package com.nethra.habitapp

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ContentValues
import android.content.Context
import android.os.Process
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

// Copies new events from Android's log into our database. Returns how many were new.
suspend fun syncUsageEvents(context: Context): Int = withContext(Dispatchers.IO) {
    val dao = HabitDatabase.get(context).usageEventDao()
    val usageStats = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    val end = System.currentTimeMillis()
    // Start from the newest event already saved. First time ever: go back 30 days.
    val start = dao.newestTimestamp() ?: (end - 30 * 24L * 60 * 60 * 1000)

    val events = usageStats.queryEvents(start, end)
    val event = UsageEvents.Event()
    val batch = mutableListOf<UsageEventEntity>()
    var newCount = 0

    while (events.hasNextEvent()) {
        events.getNextEvent(event)
        batch.add(
            UsageEventEntity(
                timestamp = event.timeStamp,
                eventType = event.eventType,
                packageName = event.packageName ?: "",
                className = event.className ?: ""
            )
        )
        if (batch.size == 1000) {
            newCount += dao.insertAll(batch).count { it != -1L }
            batch.clear()
        }
    }
    if (batch.isNotEmpty()) {
        newCount += dao.insertAll(batch).count { it != -1L }
    }
    newCount
}

// Writes every saved event to a CSV file in the phone's Downloads folder
suspend fun exportEventsCsv(context: Context): String = withContext(Dispatchers.IO) {
    val rows = HabitDatabase.get(context).usageEventDao().getAll()
    val fileName = "habitapp_events_${System.currentTimeMillis()}.csv"

    val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
        put(MediaStore.Downloads.MIME_TYPE, "text/csv")
    }
    val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        ?: error("Couldn't create the file")

    val stream = context.contentResolver.openOutputStream(uri)
        ?: error("Couldn't open the file")

    stream.bufferedWriter().use { out ->
        out.write("timestamp,event_type,package_name,class_name\n")
        for (r in rows) {
            out.write("${r.timestamp},${r.eventType},${r.packageName},${r.className}\n")
        }
    }
    fileName
}

// Turns a timestamp into "Mon 5 Oct, 08:22"
fun formatDateTime(millis: Long): String =
    Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("EEE d MMM, HH:mm"))