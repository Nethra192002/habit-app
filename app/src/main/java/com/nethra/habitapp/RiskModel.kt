package com.nethra.habitapp

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin

// Everything the Python model exported, as the phone holds it
class RiskModel(json: JSONObject) {
    val id = json.getInt("id")
    val threshold = json.getDouble("threshold")
    val gapFillMin = json.getDouble("gap_fill_min")
    val dayStartsHour = json.getInt("day_starts_hour")
    private val features = json.getJSONArray("features").let { a -> List(a.length()) { a.getString(it) } }
    private val mean = json.getJSONArray("mean").let { a -> DoubleArray(a.length()) { a.getDouble(it) } }
    private val scale = json.getJSONArray("scale").let { a -> DoubleArray(a.length()) { a.getDouble(it) } }
    private val coef = json.getJSONArray("coef").let { a -> DoubleArray(a.length()) { a.getDouble(it) } }
    private val intercept = json.getDouble("intercept")

    // package name -> "feed" / "watch" / "games" / "drift"
    val categories: Map<String, String> = json.getJSONObject("categories").let { o ->
        o.keys().asSequence().associateWith { o.getString(it) }
    }

    // The same maths as scikit-learn: scale each feature, weigh it, add up, squash to 0..1
    fun score(values: Map<String, Double>): Double {
        var z = intercept
        features.forEachIndexed { i, name ->
            z += coef[i] * ((values[name] ?: 0.0) - mean[i]) / scale[i]
        }
        return 1.0 / (1.0 + exp(-z))
    }

    // Builds the features exactly like make_X in the notebook
    fun featuresFor(
        startMs: Long,              // when the episode started
        gapMin: Double?,            // minutes since the previous episode ended (null = none)
        minsTodayBefore: Double,    // tracked minutes earlier today (day = 4am to 4am)
        episodesTodayBefore: Int,   // episodes earlier today
        firstCategory: String       // category of the app it started on
    ): Map<String, Double> {
        val start = Instant.ofEpochMilli(startMs).atZone(ZoneId.systemDefault())
        val hour = start.hour
        val weekday = start.minusHours(dayStartsHour.toLong()).dayOfWeek.value  // 1 = Mon .. 7 = Sun
        return mapOf(
            "hour_sin" to sin(2 * PI * hour / 24),
            "hour_cos" to cos(2 * PI * hour / 24),
            "weekend" to if (weekday >= 6) 1.0 else 0.0,
            "log_gap" to ln(1 + (gapMin ?: gapFillMin)),
            "mins_today_before" to minsTodayBefore,
            "episodes_today_before" to episodesTodayBefore.toDouble(),
            "cat_feed" to if (firstCategory == "feed") 1.0 else 0.0,
            "cat_watch" to if (firstCategory == "watch") 1.0 else 0.0,
            "cat_games" to if (firstCategory == "games") 1.0 else 0.0,
            "cat_drift" to if (firstCategory == "drift") 1.0 else 0.0
        )
    }
}

// Downloads the newest model; if offline, uses the last one saved on the phone
suspend fun loadRiskModel(context: Context): RiskModel? = withContext(Dispatchers.IO) {
    val prefs = context.getSharedPreferences("model", Context.MODE_PRIVATE)
    val fresh = runCatching {
        val conn = URL("${BuildConfig.API_URL}/api/model").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("Authorization", "Bearer ${BuildConfig.INGEST_TOKEN}")
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    if (fresh != null) prefs.edit().putString("json", fresh).apply()   // keep a copy for offline
    val text = fresh ?: prefs.getString("json", null) ?: return@withContext null
    RiskModel(JSONObject(text))
}