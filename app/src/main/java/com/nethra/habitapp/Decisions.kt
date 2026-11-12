package com.nethra.habitapp

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// Decisions wait in a small queue on the phone until they reach the server,
// so nothing is lost if you're offline when the robot decides.
private const val PREFS = "decisions"
private const val KEY = "pending"

@Synchronized
fun queueDecision(context: Context, decision: JSONObject) {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val pending = JSONArray(prefs.getString(KEY, "[]"))
    pending.put(decision)
    prefs.edit().putString(KEY, pending.toString()).apply()
}

// Sends everything waiting; on success, removes exactly what was sent
suspend fun flushDecisions(context: Context) = withContext(Dispatchers.IO) {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val pending = JSONArray(prefs.getString(KEY, "[]"))
    if (pending.length() == 0) return@withContext

    val conn = URL("${BuildConfig.API_URL}/api/decisions").openConnection() as HttpURLConnection
    try {
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer ${BuildConfig.INGEST_TOKEN}")
        conn.outputStream.use { it.write(pending.toString().toByteArray()) }
        if (conn.responseCode != 200) return@withContext   // keep them, try next time
    } finally {
        conn.disconnect()
    }

    // Remove the ones we sent (new ones may have been added meanwhile)
    synchronized(::queueDecision) {
        val now = JSONArray(prefs.getString(KEY, "[]"))
        val rest = JSONArray()
        for (i in pending.length() until now.length()) rest.put(now.get(i))
        prefs.edit().putString(KEY, rest.toString()).apply()
    }
}