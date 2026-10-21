package com.nethra.habitapp

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// Apps never sent to the cloud. They stay on the phone only.
// Matched against the app's package name, e.g. "com.revolut.revolut" contains "revolut".
private val EXCLUDED_KEYWORDS = listOf(
    "bank", "revolut", "paypal", "wallet", "finance", "crypto", "aib.", "boi.",
    "spay", "securefolder", "authenticator", "duosecurity", "yuno"
)

private fun isExcluded(packageName: String): Boolean =
    EXCLUDED_KEYWORDS.any { packageName.lowercase().contains(it) }

// Sends every event saved on the phone that hasn't been uploaded yet.
// Returns how many were new on the server.
suspend fun uploadNewEvents(context: Context): Int = withContext(Dispatchers.IO) {
    if (BuildConfig.API_URL.isBlank()) error("API_URL missing from local.properties")

    val prefs = context.getSharedPreferences("sync", Context.MODE_PRIVATE)
    val dao = HabitDatabase.get(context).usageEventDao()
    var since = prefs.getLong("uploadedUpTo", 0L)  // remembers how far we got last time
    var total = 0

    while (true) {
        val batch = dao.eventsSince(since, 2000)
        if (batch.isEmpty()) break

        val toSend = batch.filterNot { isExcluded(it.packageName) }
        if (toSend.isNotEmpty()) total += postEvents(toSend)

        val lastTs = batch.last().timestamp
        prefs.edit().putLong("uploadedUpTo", lastTs).apply()

        if (batch.size < 2000 || lastTs == since) break  // reached the end
        since = lastTs
    }
    total
}

// One web request: turns events into JSON and POSTs them to the API
private fun postEvents(events: List<UsageEventEntity>): Int {
    val body = JSONArray()
    for (e in events) {
        body.put(JSONObject().apply {
            put("ts", e.timestamp)
            put("eventType", e.eventType)
            put("packageName", e.packageName)
            put("className", e.className)
        })
    }

    val conn = URL("${BuildConfig.API_URL}/api/events").openConnection() as HttpURLConnection
    try {
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer ${BuildConfig.INGEST_TOKEN}")
        conn.outputStream.use { it.write(body.toString().toByteArray()) }

        val code = conn.responseCode
        if (code != 200) error("Upload failed: HTTP $code")
        val reply = conn.inputStream.bufferedReader().use { it.readText() }
        return JSONObject(reply).getInt("inserted")
    } finally {
        conn.disconnect()
    }
}