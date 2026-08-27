package com.rhythm.app.optimem

/**
 * A resource that Optimem legitimately controls and can prefetch/cache.
 *
 * Android's sandbox means we cannot reach into a third-party app's private
 * cache. So every predicted "next app" is mapped to a resource *inside*
 * Optimem's own demo content module (e.g. a thumbnail/article bundle that
 * would conceptually belong to that app's content feed). This keeps the
 * predict -> decide -> prefetch -> cache -> measure loop fully real and
 * legitimate instead of pretending to control system-wide caches.
 *
 * sizeKB and baseFetchLatencyMs are the "cold" cost of fetching this
 * resource with nothing cached. They are used by DecisionEngine to weigh
 * benefit against cost.
 */
data class DemoResource(
    val id: String,
    val forPackage: String,
    val label: String,
    val sizeKB: Int,
    val baseFetchLatencyMs: Long
)

/** Snapshot of device conditions relevant to a prefetch decision. */
data class DeviceContext(
    val batteryPercent: Int,
    val isCharging: Boolean,
    val isWifi: Boolean,
    val availableStorageMB: Long
)

enum class Action { PREFETCH, SKIP }

/** Output of the DecisionEngine for one candidate resource. */
data class Decision(
    val resource: DemoResource,
    val predictedProbability: Double,
    val expectedBenefitMs: Double,
    val estimatedCost: Double,
    val netScore: Double,
    val action: Action,
    val reason: String
)

/**
 * One row of the ground-truth outcome log: what we predicted/decided, and
 * what actually happened when the user acted. This is what the Model /
 * Dashboard screens read to compute cache hit rate, wasted-prefetch rate,
 * and latency saved vs. the "always fetch on demand" baseline.
 */
data class MetricEvent(
    val id: Long = 0,
    val timestamp: Long,
    val predictedPackage: String,
    val predictedProbability: Double,
    val decision: Action,
    val actualNextPackage: String?,   // filled in once the next session starts
    val predictionCorrect: Boolean?,  // null until resolved
    val cacheHit: Boolean?,           // null until resolved
    val latencySavedMs: Long?,        // null until resolved; 0 if no benefit
    val wastedPrefetch: Boolean?      // prefetched but prediction was wrong
)
