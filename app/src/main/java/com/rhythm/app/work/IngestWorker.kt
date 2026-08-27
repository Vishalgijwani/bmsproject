package com.rhythm.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rhythm.app.data.ExclusionStore
import com.rhythm.app.data.RhythmDatabase
import com.rhythm.app.data.SessionIngester
import com.rhythm.app.optimem.OptimemEngine
import com.rhythm.app.util.PermissionUtil

/**
 * Periodic ingest. Scheduled every 15 minutes by [com.rhythm.app.RhythmApp].
 *
 * Pulls UsageEvents since the newest stored session and writes any new
 * sessions to Room. Retraining happens on read, so there is nothing to
 * persist beyond the sessions themselves.
 *
 * After ingesting, also runs one Optimem cycle: resolve the previous
 * prediction's outcome, retrain, predict the next app, and decide whether
 * to prefetch. Wrapped separately so a failure here never blocks ingestion.
 */
class IngestWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            // Without usage access there is nothing to read — succeed quietly
            // so WorkManager keeps the periodic schedule alive.
            if (!PermissionUtil.hasUsageStatsPermission(applicationContext)) {
                return Result.success()
            }

            val dao = RhythmDatabase.get(applicationContext).sessionDao()
            val excluded = ExclusionStore(applicationContext).getExcluded()
            val ingester = SessionIngester(applicationContext, excluded)

            // First run: reach back a week. UsageStats keeps roughly 7 days of events.
            val lastEnd = dao.maxEndTime()
                ?: (System.currentTimeMillis() - SEVEN_DAYS_MS)

            val sessions = ingester.fetchSessions(lastEnd)
            if (sessions.isNotEmpty()) {
                dao.insertAll(sessions)
            }

            try {
                OptimemEngine(applicationContext).runCycle()
            } catch (e: Exception) {
                // Optimem is additive; never let it fail the ingest cycle.
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val SEVEN_DAYS_MS = 7L * 24 * 60 * 60 * 1000
    }
}