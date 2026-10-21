package com.nethra.habitapp

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

// The background job: Android runs this about once an hour, even with the app closed
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!hasUsageAccess(applicationContext)) return Result.success()

        return try {
            syncUsageEvents(applicationContext)                  // 1. save new events on the phone
            runCatching { uploadNewEvents(applicationContext) }  // 2. upload; if offline, try next hour
            Result.success()
        } catch (e: Exception) {
            Result.retry()  // local save failed: let WorkManager try again soon
        }
    }
}

// Registers the hourly job. KEEP means: if it's already scheduled, leave it alone.
fun scheduleSync(context: Context) {
    val request = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS).build()
    WorkManager.getInstance(context)
        .enqueueUniquePeriodicWork("sync", ExistingPeriodicWorkPolicy.KEEP, request)
}