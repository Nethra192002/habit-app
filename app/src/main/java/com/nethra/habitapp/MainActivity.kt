package com.nethra.habitapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nethra.habitapp.ui.theme.HabitAppTheme
import kotlinx.coroutines.launch
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scheduleSync(this)
        createRobotChannel(this)
        if (hasUsageAccess(this)) {
            startForegroundService(Intent(this, WatcherService::class.java))
        }
        enableEdgeToEdge()
        setContent {
            HabitAppTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    UsageScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun UsageScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hasAccess by remember { mutableStateOf(hasUsageAccess(context)) }
    var status by remember { mutableStateOf("") }
    // Ask once for permission to show notifications (Android 13+)
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !canNotify(context)) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    // Runs on its own whenever the screen opens with access granted
    LaunchedEffect(hasAccess) {
        if (hasAccess) {
            status = "Saving…"
            status = saveAndDescribe(context)
        }
    }

    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!hasAccess) {
            Text("HabitApp needs Usage access to read your screen time, unlocks and app sessions.")
            Button(onClick = {
                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }) {
                Text("Open Usage access settings")
            }
            Button(onClick = { hasAccess = hasUsageAccess(context) }) {
                Text("I've switched it on")
            }
        } else {
            Button(onClick = {
                scope.launch {
                    status = "Saving…"
                    status = saveAndDescribe(context)
                }
            }) {
                Text("Save my phone's history now")
            }
            Button(onClick = {
                scope.launch {
                    status = "Exporting…"
                    val name = exportEventsCsv(context)
                    status = "Exported $name to your Downloads folder."
                }
            }) {
                Text("Export CSV")
            }

            Button(onClick = {
                scope.launch {
                    status = "Checking…"
                    val model = loadRiskModel(context)
                    if (model == null) {
                        status = "Couldn't load the model."
                        return@launch
                    }
                    val snap = withContext(Dispatchers.IO) { liveSnapshot(context, model) }
                    // "What if you opened Instagram right now?"
                    val risk = model.score(
                        model.featuresFor(
                            startMs = System.currentTimeMillis(),
                            gapMin = null,
                            minsTodayBefore = snap.minsTodayBefore,
                            episodesTodayBefore = snap.episodesTodayBefore,
                            firstCategory = "feed"
                        )
                    )
                    val lastTick = context.getSharedPreferences("watcher", Context.MODE_PRIVATE)
                        .getLong("lastTick", 0L)
                    val tickText = if (lastTick == 0L) "never" else formatDateTime(lastTick)
                    status = "Watcher last checked: $tickText\nModel v${model.id} · today so far: ${snap.episodesTodayBefore} episodes, " +
                            "${snap.minsTodayBefore.toInt()} min\n" +
                            "If you opened Instagram now: risk ${"%.2f".format(risk)} " +
                            "(robot speaks above ${"%.2f".format(model.threshold)})"
                }
            }) {
                Text("Check risk now")
            }

            Text(status)
            LabelSection()
        }
    }
}

// Saves new events, uploads them, then describes what happened
suspend fun saveAndDescribe(context: Context): String {
    val added = syncUsageEvents(context)
    val upload = runCatching { uploadNewEvents(context) }
    val dao = HabitDatabase.get(context).usageEventDao()
    val total = dao.count()
    val oldest = dao.oldestTimestamp()?.let { formatDateTime(it) } ?: "—"
    val uploadText = upload.fold(
        onSuccess = { "Uploaded $it new events to the cloud." },
        onFailure = { "Upload failed: ${it.message}" }
    )
    return "Saved $added new events.\n$uploadText\nDatabase holds $total events, going back to $oldest."
}