package com.rhythm.app.optimem

/**
 * Rule-based cost-benefit decision engine.
 *
 * Why not Q-learning for v1: the state space (app x hour x battery-bucket x
 * network x cache-state) is large relative to how much real usage data a
 * single phone accumulates in a college-project timeframe, and an RL policy
 * is hard to validate/explain in a report or viva. A transparent scoring
 * rule captures the same "should I prefetch this?" decision, is trivial to
 * unit test, and every number in it can be shown on the dashboard. If this
 * prototype later has weeks of real multi-user data, swapping this class
 * for a contextual bandit or Q-learner is a contained change (same
 * predict() -> decide() contract).
 *
 * Score = confidence * expectedBenefitMs - estimatedCost
 *   expectedBenefitMs = probability * resource.baseFetchLatencyMs
 *   estimatedCost      = size-based network cost (higher on mobile data)
 *                         + battery penalty (higher cost when battery low
 *                         and not charging)
 *                         + storage guard (refuse if storage is tight)
 *
 * PREFETCH only if:
 *   - probability >= minConfidence
 *   - netScore > 0
 *   - enough storage headroom
 */
class DecisionEngine(
    private val minConfidence: Double = 0.30,
    private val minStorageHeadroomMB: Long = 50,
    private val minBatteryToPrefetch: Int = 15
) {

    /** Cost in ms-equivalent units per KB, mobile data vs WiFi. */
    private val costPerKbWifi = 0.05
    private val costPerKbMobile = 0.35

    fun decide(
        resource: DemoResource,
        probability: Double,
        context: DeviceContext
    ): Decision {
        val expectedBenefitMs = probability * resource.baseFetchLatencyMs

        val networkCostPerKb = if (context.isWifi) costPerKbWifi else costPerKbMobile
        var cost = resource.sizeKB * networkCostPerKb

        // Battery penalty: prefetching when low and not charging is expensive
        // in a way plain ms-cost doesn't capture, so we inflate cost sharply
        // below the safety threshold rather than hard-blocking at exactly
        // minBatteryToPrefetch (keeps the score continuous/explainable).
        if (!context.isCharging && context.batteryPercent < 40) {
            val deficit = (40 - context.batteryPercent).coerceAtLeast(0)
            cost += deficit * 4.0
        }

        val netScore = expectedBenefitMs - cost

        val lowStorage = context.availableStorageMB < minStorageHeadroomMB
        val lowBattery = !context.isCharging && context.batteryPercent < minBatteryToPrefetch
        val lowConfidence = probability < minConfidence

        val (action, reason) = when {
            lowConfidence -> Action.SKIP to "confidence ${pct(probability)} below threshold ${pct(minConfidence)}"
            lowStorage -> Action.SKIP to "storage headroom too low (${context.availableStorageMB}MB)"
            lowBattery -> Action.SKIP to "battery too low (${context.batteryPercent}%) and not charging"
            netScore <= 0 -> Action.SKIP to "cost exceeds expected benefit (${netScore.toInt()}ms net)"
            else -> Action.PREFETCH to "confidence ${pct(probability)}, net benefit ${netScore.toInt()}ms" +
                (if (context.isWifi) " on WiFi" else " on mobile data")
        }

        return Decision(
            resource = resource,
            predictedProbability = probability,
            expectedBenefitMs = expectedBenefitMs,
            estimatedCost = cost,
            netScore = netScore,
            action = action,
            reason = reason
        )
    }

    private fun pct(p: Double): String = "${(p * 100).toInt()}%"
}
