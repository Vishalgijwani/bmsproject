package com.rhythm.app.optimem

import android.content.Context
import com.rhythm.app.data.RhythmDatabase
import com.rhythm.app.data.SessionEntity
import com.rhythm.app.model.Predictor
import com.rhythm.app.model.Transition
import com.rhythm.app.model.TransitionExtractor
import java.util.Calendar

/**
 * Ties the pieces together for one full cycle:
 *   observe (sessions already in Room, via SessionIngester/IngestWorker)
 *     -> learn (Predictor, trained on Transitions)
 *     -> predict (top candidate for "what's next")
 *     -> decide (DecisionEngine: prefetch or skip)
 *     -> prefetch/cache (DemoCacheManager)
 *     -> on the *next* real session, resolve the outcome and log it
 *        (MetricDao) so the dashboard can show real, non-hardcoded numbers.
 *
 * This class does not read UsageStats itself -- that stays in
 * SessionIngester/IngestWorker, unchanged. OptimemEngine only consumes the
 * sessions already stored in Room.
 */
class OptimemEngine(context: Context) {

    private val db = RhythmDatabase.get(context)
    private val appContext = context.applicationContext
    private val decisionEngine = DecisionEngine()

    // Rebuilt each cycle, same "cheap to retrain from scratch" approach as Predictor.
    private var predictor: Predictor? = null
    private var cacheManager: DemoCacheManager? = null

    /**
     * Call this on the same cadence as ingestion (e.g. from IngestWorker,
     * after new sessions are stored). It:
     *  1. Resolves the previous cycle's prediction against what the user
     *     actually did (the most recent session).
     *  2. Retrains the predictor on all sessions.
     *  3. Predicts the next app from the current context and decides
     *     whether to prefetch it.
     */
    suspend fun runCycle() {
        val sessions = db.sessionDao().getAll()
        if (sessions.size < 2) return

        resolvePreviousPrediction(sessions)

        val transitions = TransitionExtractor.extract(sessions)
        val p = Predictor(transitions)
        predictor = p

        val cm = cacheManager ?: DemoCacheManager(DemoCacheManager.buildCatalog(p.allApps()))
        cacheManager = cm

        if (!p.canPredict()) return // "still learning" state, same threshold as Predictor

        val last = sessions.last()
        val cal = Calendar.getInstance().apply { timeInMillis = System.currentTimeMillis() }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val isWeekend = cal.get(Calendar.DAY_OF_WEEK).let {
            it == Calendar.SATURDAY || it == Calendar.SUNDAY
        }

        val predictions = p.predict(last.packageName, hour, isWeekend)
        val top = predictions.firstOrNull() ?: return

        val resource = cm.resourceFor(top.packageName) ?: return
        val deviceContext = DeviceContextProvider.read(appContext)
        val decision = decisionEngine.decide(resource, top.score, deviceContext)

        if (decision.action == Action.PREFETCH) {
            cm.prefetch(resource)
        }

        db.metricDao().insert(
            MetricEntity(
                timestamp = System.currentTimeMillis(),
                predictedPackage = top.packageName,
                predictedProbability = top.score,
                decision = decision.action.name
            )
        )
    }

    /**
     * When a new session has started since the last cycle, compare it
     * against the most recent *unresolved* prediction and record whether
     * we were right, whether it was a cache hit, and the latency the user
     * actually experienced vs. the "always fetch on demand" baseline.
     */
    private suspend fun resolvePreviousPrediction(sessions: List<SessionEntity>) {
        val pending = db.metricDao().getLatestUnresolved() ?: return
        val cm = cacheManager ?: return
        val newest = sessions.lastOrNull() ?: return

        // Only resolve if this session started after the prediction was made.
        if (newest.startTime <= pending.timestamp) return

        val correct = newest.packageName == pending.predictedPackage
        val resource = cm.resourceFor(pending.predictedPackage)

        var cacheHit = false
        var latencySaved = 0L
        var wasted = false

        if (pending.decision == Action.PREFETCH.name && resource != null) {
            if (correct) {
                val experienced = cm.request(resource)
                cacheHit = experienced < resource.baseFetchLatencyMs
                latencySaved = (resource.baseFetchLatencyMs - experienced).coerceAtLeast(0)
            } else {
                cm.markWasted(resource.id)
                wasted = true
            }
        }

        db.metricDao().update(
            pending.copy(
                actualNextPackage = newest.packageName,
                predictionCorrect = correct,
                cacheHit = cacheHit,
                latencySavedMs = latencySaved,
                wastedPrefetch = wasted
            )
        )
    }

    /** Snapshot for the dashboard. All numbers come from real logged events. */
    suspend fun metricsSnapshot(): MetricsSnapshot {
        val dao = db.metricDao()
        val resolved = dao.resolvedCount()
        val correct = dao.correctCount()
        val prefetches = dao.prefetchCount()
        val wasted = dao.wastedCount()
        val hits = dao.cacheHitCount()
        val latencySaved = dao.totalLatencySavedMs() ?: 0L

        return MetricsSnapshot(
            resolvedPredictions = resolved,
            top1Correct = correct,
            top1Accuracy = if (resolved > 0) correct.toDouble() / resolved else 0.0,
            prefetchCount = prefetches,
            wastedPrefetchCount = wasted,
            cacheHitCount = hits,
            totalLatencySavedMs = latencySaved
        )
    }

    /**
     * Read-only "what would happen right now" peek — computes the current
     * top prediction and decision without touching the cache or metrics
     * log. Used by the dashboard so opening the screen never mutates state.
     */
    suspend fun currentDecision(): Decision? {
        val sessions = db.sessionDao().getAll()
        if (sessions.size < 2) return null

        val transitions = TransitionExtractor.extract(sessions)
        val p = Predictor(transitions)
        if (!p.canPredict()) return null

        val last = sessions.last()
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val isWeekend = cal.get(Calendar.DAY_OF_WEEK).let {
            it == Calendar.SATURDAY || it == Calendar.SUNDAY
        }

        val predictions = p.predict(last.packageName, hour, isWeekend)
        val top = predictions.firstOrNull() ?: return null

        val catalog = DemoCacheManager.buildCatalog(p.allApps())
        val resource = catalog.firstOrNull { it.forPackage == top.packageName } ?: return null

        val deviceContext = DeviceContextProvider.read(appContext)
        return decisionEngine.decide(resource, top.score, deviceContext)
    }

    data class MetricsSnapshot(
        val resolvedPredictions: Int,
        val top1Correct: Int,
        val top1Accuracy: Double,
        val prefetchCount: Int,
        val wastedPrefetchCount: Int,
        val cacheHitCount: Int,
        val totalLatencySavedMs: Long
    )
}