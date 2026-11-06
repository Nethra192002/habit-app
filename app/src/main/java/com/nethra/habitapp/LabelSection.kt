package com.nethra.habitapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// Friendly names for the feed apps; anything else shows its package name
private val APP_NAMES = mapOf(
    "com.instagram.android" to "Instagram",
    "com.google.android.youtube" to "YouTube",
    "com.pinterest" to "Pinterest",
    "com.netflix.mediaclient" to "Netflix",
    "com.linkedin.android" to "LinkedIn",
    "com.anthropic.claude" to "Claude",
    "com.netflix.NGP.Pandora" to "Netflix game",
    "com.nytimes.crossword" to "NYT games"
)

// Shows one unanswered session at a time with the four answer buttons
@Composable
fun LabelSection() {
    val scope = rememberCoroutineScope()
    var queue by remember { mutableStateOf<List<SessionToLabel>>(emptyList()) }
    var message by remember { mutableStateOf("Loading sessions…") }
    var busy by remember { mutableStateOf(false) }  // stops double taps while saving

    // Runs once when the screen opens: fetch the sessions to ask about
    LaunchedEffect(Unit) {
        runCatching { fetchUnlabelled() }
            .onSuccess {
                queue = it
                message = if (it.isEmpty()) "Nothing to answer right now." else ""
            }
            .onFailure { message = "Couldn't load sessions: ${it.message}" }
    }

    val current = queue.firstOrNull()
    if (current == null) {
        Text(message)
        return
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("How was this one?", style = MaterialTheme.typography.titleMedium)
            Text(
                "${current.apps.split(", ").joinToString(" + ") { APP_NAMES[it] ?: it }} · " +
                        "${formatDateTime(current.startedMs)} (${daysAgo(current.startedMs)}) · ${current.minutes} min"
            )

            // Four buttons, two per row
            Outcome.entries.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { outcome ->
                        OutlinedButton(
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                scope.launch {
                                    busy = true
                                    runCatching { sendLabel(current, outcome) }
                                        .onSuccess {
                                            queue = queue.drop(1)  // move to the next session
                                            message = if (queue.isEmpty()) "All answered." else ""
                                        }
                                        .onFailure { message = "Couldn't save: ${it.message}" }
                                    busy = false
                                }
                            }
                        ) { Text(outcome.button) }
                    }
                }
            }

            Text("${queue.size} left", style = MaterialTheme.typography.bodySmall)
            if (message.isNotEmpty()) Text(message)  // only shows if saving failed
        }
    }
}

// "today", "yesterday" or "12 days ago", by calendar day on the phone
private fun daysAgo(millis: Long): String {
    val zone = java.time.ZoneId.systemDefault()
    val then = java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    val days = java.time.temporal.ChronoUnit.DAYS.between(then, java.time.LocalDate.now(zone))
    return when (days) {
        0L -> "today"
        1L -> "yesterday"
        else -> "$days days ago"
    }
}