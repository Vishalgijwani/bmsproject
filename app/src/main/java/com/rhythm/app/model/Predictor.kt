package com.rhythm.app.model

import kotlin.math.roundToInt

/**
 * Hierarchical backoff predictor.
 *
 * Count tables (dayType = "wd" or "we"):
 *   ctx[dayType|hour|prev|next]   -> with hour kernel
 *   hourly[dayType|hour|next]     -> with hour kernel
 *   pair[prev|next]               -> weight 1.0, no kernel
 *   prior[next]                   -> weight 1.0, no kernel
 *   gaps[prev|hour]               -> list of gapMin
 *
 * Hour kernel: offset -1 = 0.30, 0 = 1.00, +1 = 0.30 (wrap mod 24)
 *
 * Predict(prev, hour, isWeekend), K=6.0, PR=0.35:
 *   wc = ctxTotal/(ctxTotal+K)
 *   wp = pairTotal/(pairTotal+K)
 *   wh = hourTotal/(hourTotal+K)
 *   back(a) = (wp*Ppair(a) + wh*Phour(a) + PR*Pprior(a)) / (wp + wh + PR)
 *   score(a) = wc*Pctx(a) + (1-wc)*back(a) + 0.002
 *   Normalise to sum 1, return sorted desc.
 */
class Predictor(
    private val transitions: List<Transition>,
    private val K: Double = 6.0,
    private val PR: Double = 0.35
) {

    private val hourKernel = mapOf(-1 to 0.30, 0 to 1.00, 1 to 0.30)

    // Context: (dayType, hour, prev) -> (next -> weightedCount)
    private val ctx = mutableMapOf<CtxKey, MutableMap<String, Double>>()
    // Hourly: (dayType, hour) -> (next -> weightedCount)
    private val hourly = mutableMapOf<HourKey, MutableMap<String, Double>>()
    // Pair: prev -> (next -> count)
    private val pair = mutableMapOf<String, MutableMap<String, Double>>()
    // Prior: next -> count
    private val prior = mutableMapOf<String, Double>()
    // Gaps: (prev, hour) -> list of gapMin
    private val gaps = mutableMapOf<GapKey, MutableList<Long>>()

    // Totals
    private val ctxTotal = mutableMapOf<CtxKey, Double>()
    private val hourTotal = mutableMapOf<HourKey, Double>()
    private var pairTotal = mutableMapOf<String, Double>()
    private var priorTotal = 0.0

    private data class CtxKey(val dayType: String, val hour: Int, val prev: String)
    private data class HourKey(val dayType: String, val hour: Int)
    private data class GapKey(val prev: String, val hour: Int)

    init {
        train()
    }

    private fun dayType(isWeekend: Boolean) = if (isWeekend) "we" else "wd"

    private fun train() {
        for (t in transitions) {
            val dt = dayType(t.isWeekend)

            // ctx with hour kernel
            for ((offset, weight) in hourKernel) {
                val h = ((t.hour + offset) % 24 + 24) % 24
                val key = CtxKey(dt, h, t.prev)
                ctx.getOrPut(key) { mutableMapOf() }
                ctx[key]!![t.next] = ctx[key]!!.getOrDefault(t.next, 0.0) + weight
                ctxTotal[key] = ctxTotal.getOrDefault(key, 0.0) + weight
            }

            // hourly with hour kernel
            for ((offset, weight) in hourKernel) {
                val h = ((t.hour + offset) % 24 + 24) % 24
                val key = HourKey(dt, h)
                hourly.getOrPut(key) { mutableMapOf() }
                hourly[key]!![t.next] = hourly[key]!!.getOrDefault(t.next, 0.0) + weight
                hourTotal[key] = hourTotal.getOrDefault(key, 0.0) + weight
            }

            // pair (no kernel)
            pair.getOrPut(t.prev) { mutableMapOf() }
            pair[t.prev]!![t.next] = pair[t.prev]!!.getOrDefault(t.next, 0.0) + 1.0
            pairTotal[t.prev] = pairTotal.getOrDefault(t.prev, 0.0) + 1.0

            // prior (no kernel)
            prior[t.next] = prior.getOrDefault(t.next, 0.0) + 1.0
            priorTotal += 1.0

            // gaps
            val gk = GapKey(t.prev, t.hour)
            gaps.getOrPut(gk) { mutableListOf() }.add(t.gapMin)
        }
    }

    data class Prediction(
        val packageName: String,
        val score: Double
    )

    fun canPredict(): Boolean = transitions.size >= 50

    fun predict(prev: String, hour: Int, isWeekend: Boolean): List<Prediction> {
        val dt = dayType(isWeekend)

        // --- Context distribution ---
        val ctxKey = CtxKey(dt, hour, prev)
        val ctot = ctxTotal[ctxKey] ?: 0.0
        val wc = if (ctot > 0) ctot / (ctot + K) else 0.0
        val pCtx = ctx[ctxKey] ?: emptyMap()

        // --- Pair distribution ---
        val ptot = pairTotal[prev] ?: 0.0
        val wp = if (ptot > 0) ptot / (ptot + K) else 0.0
        val pPair = pair[prev] ?: emptyMap()

        // --- Hourly distribution ---
        val hKey = HourKey(dt, hour)
        val htot = hourTotal[hKey] ?: 0.0
        val wh = if (htot > 0) htot / (htot + K) else 0.0
        val pHour = hourly[hKey] ?: emptyMap()

        // --- Prior distribution ---
        val pPrior = prior

        // Collect all candidate next apps
        val candidates = mutableSetOf<String>()
        candidates.addAll(pCtx.keys)
        candidates.addAll(pPair.keys)
        candidates.addAll(pHour.keys)
        candidates.addAll(pPrior.keys)

        if (candidates.isEmpty()) return emptyList()

        val scores = mutableMapOf<String, Double>()
        for (a in candidates) {
            val pCtxA = if (ctot > 0) (pCtx[a] ?: 0.0) / ctot else 0.0
            val pPairA = if (ptot > 0) (pPair[a] ?: 0.0) / ptot else 0.0
            val pHourA = if (htot > 0) (pHour[a] ?: 0.0) / htot else 0.0
            val pPriorA = if (priorTotal > 0) (pPrior[a] ?: 0.0) / priorTotal else 0.0

            val backA = if ((wp + wh + PR) > 0) {
                (wp * pPairA + wh * pHourA + PR * pPriorA) / (wp + wh + PR)
            } else 0.0

            val score = wc * pCtxA + (1 - wc) * backA + 0.002
            scores[a] = score
        }

        // Normalise to sum 1
        val sum = scores.values.sum()
        val normalised = if (sum > 0) {
            scores.mapValues { it.value / sum }
        } else {
            scores
        }

        return normalised.entries
            .sortedByDescending { it.value }
            .map { Prediction(it.key, it.value) }
    }

    /**
     * ETA: median of gaps[prev|hour], searching hour offsets 0,+1,-1,+2,-2
     * for the first bucket with >=3 samples, else 12.
     */
    fun etaMinutes(prev: String, hour: Int): Long {
        val offsets = listOf(0, 1, -1, 2, -2)
        for (offset in offsets) {
            val h = ((hour + offset) % 24 + 24) % 24
            val bucket = gaps[GapKey(prev, h)]
            if (bucket != null && bucket.size >= 3) {
                return median(bucket)
            }
        }
        return 12
    }

    private fun median(values: List<Long>): Long {
        val sorted = values.sorted()
        val n = sorted.size
        return if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2
    }

    /** All distinct app names seen in transitions. */
    fun allApps(): Set<String> {
        val apps = mutableSetOf<String>()
        transitions.forEach { apps.add(it.prev); apps.add(it.next) }
        return apps
    }

    /** Raw transitions for matrix display. */
    fun rawTransitions(): List<Transition> = transitions
}
