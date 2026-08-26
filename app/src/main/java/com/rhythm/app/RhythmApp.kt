package com.rhythm.app

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.rhythm.app.work.IngestWorker
import java.util.concurrent.TimeUnit

class RhythmApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Schedule 15-minute periodic ingest+retrain.
        val req = PeriodicWorkRequestBuilder<IngestWorker>(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "rhythm-ingest",
            ExistingPeriodicWorkPolicy.UPDATE,
            req
        )
    }
}
