package com.nethra.habitapp

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nethra.habitapp.ui.theme.HabitAppTheme

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
    var hasAccess by remember { mutableStateOf(hasUsageAccess(context)) }
    var summary by remember { mutableStateOf<UsageSummary?>(null) }

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
            Button(onClick = { summary = readUsageSummary(context) }) {
                Text("Read my phone's history")
            }

            summary?.let { s ->
                Text("Events found: ${s.totalEvents}")
                Text("App opens: ${s.appOpens}")
                Text("Unlocks: ${s.unlocks}")
                Text("Screen turned on: ${s.screenOns}")
                Text("Oldest event: ${s.oldestEventTime?.let { formatDateTime(it) } ?: "none"}")
            }
        }
    }
}