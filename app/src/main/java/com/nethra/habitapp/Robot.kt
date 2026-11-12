package com.nethra.habitapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.time.LocalTime

const val ROBOT_CHANNEL = "robot"

// A notification "channel" is a category you can control in Android settings.
// The robot gets its own, so you can mute it without muting the rest of the app.
fun createRobotChannel(context: Context) {
    val channel = NotificationChannel(ROBOT_CHANNEL, "Robot", NotificationManager.IMPORTANCE_HIGH)
        .apply { description = "Nudges when a scroll looks like it's running away" }
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
}

fun canNotify(context: Context): Boolean =
    Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

@SuppressLint("MissingPermission")  // we check canNotify() first
fun robotSay(context: Context, text: String) {
    if (!canNotify(context)) return
    val n = NotificationCompat.Builder(context, ROBOT_CHANNEL)
        .setSmallIcon(android.R.drawable.ic_dialog_info)   // placeholder until the design pass
        .setContentTitle("Robot")
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .build()
    NotificationManagerCompat.from(context).notify(1, n)   // id 1: a new line replaces the old one
}

// Dry, grounded lines: always about the moment, never about you.
fun robotLine(minutes: Int, app: String): String {
    val night = LocalTime.now().let { it.hour >= 21 || it.hour < 4 }
    val lines = if (night) listOf(
        "$app at this hour. It'll still be there tomorrow. Will you be awake for it?",
        "Minute $minutes, and it's late. This is the one that usually eats an hour.",
        "Past you set a bedtime. I'm just the messenger."
    ) else listOf(
        "Minute $minutes on $app. I'm not judging. I'm logging, which is worse.",
        "$minutes minutes into $app. Statistically, this is the one that runs long.",
        "$app again. Bold. Is there something you were avoiding?"
    )
    return lines.random()
}