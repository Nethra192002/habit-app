package com.nethra.habitapp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// One feed episode the app wants your answer on
data class SessionToLabel(
    val startedMs: Long,
    val endedMs: Long,
    val minutes: Int,
    val apps: String          // e.g. "com.google.android.youtube, com.instagram.android"
)

// The four answers. `button` is what you see; `code` is what the database stores.
enum class Outcome(val button: String, val code: String) {
    ALL_GOOD("All good", "all_good"),
    BIT_OVER("Bit longer", "bit_over"),
    LOST_TRACK("Lost track", "lost_track"),
    NOT_REAL("Doesn't count", "not_real"),
    UNSURE("Can't remember", "unsure")
}

// Asks the server for recent feed episodes you haven't answered yet
suspend fun fetchUnlabelled(): List<SessionToLabel> = withContext(Dispatchers.IO) {
    val conn = URL("${BuildConfig.API_URL}/api/labels").openConnection() as HttpURLConnection
    try {
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("Authorization", "Bearer ${BuildConfig.INGEST_TOKEN}")

        if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
        val reply = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })

        // Turn each JSON object into a SessionToLabel
        List(reply.length()) { i ->
            val o = reply.getJSONObject(i)
            SessionToLabel(
                o.getLong("startedMs"),
                o.getLong("endedMs"),
                o.getInt("minutes"),
                o.getString("apps")
            )
        }
    } finally {
        conn.disconnect()
    }
}

// Sends one answer for one episode
suspend fun sendLabel(session: SessionToLabel, outcome: Outcome) = withContext(Dispatchers.IO) {
    val body = JSONObject().apply {
        put("startedMs", session.startedMs)
        put("endedMs", session.endedMs)
        put("outcome", outcome.code)
    }

    val conn = URL("${BuildConfig.API_URL}/api/labels").openConnection() as HttpURLConnection
    try {
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer ${BuildConfig.INGEST_TOKEN}")
        conn.outputStream.use { it.write(body.toString().toByteArray()) }

        if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
    } finally {
        conn.disconnect()
    }
}