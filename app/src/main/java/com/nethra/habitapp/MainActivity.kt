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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            Text(status)
        }
    }
}

// Saves new events, then describes what the database holds
suspend fun saveAndDescribe(context: Context): String {
    val added = syncUsageEvents(context)
    val dao = HabitDatabase.get(context).usageEventDao()
    val total = dao.count()
    val oldest = dao.oldestTimestamp()?.let { formatDateTime(it) } ?: "—"
    return "Saved $added new events.\nDatabase holds $total events, going back to $oldest."
}