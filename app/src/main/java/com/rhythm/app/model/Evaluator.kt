package com.rhythm.app.model

/**
 * Chronological evaluation: earliest 75% train, latest 25% test.
 * Reports top-1, top-3 accuracy, and a baseline of always guessing
 * the most frequent app.
 */
data class EvalResult(
    val top1: Double,
    val top3: Double,
    val baseline: Double,
    val trainCount: Int,
    val testCount: Int
)

object Evaluator {

    fun evaluate(transitions: List<Transition>, K: Double = 6.0, PR: Double = 0.35): EvalResult {
        if (transitions.size < 4) {
            return EvalResult(0.0, 0.0, 0.0, transitions.size, 0)
        }

        // Transitions arrive in chronological order from the extractor.
        val chrono = transitions.toList()

        val splitIdx = (chrono.size * 0.75).toInt().coerceAtLeast(1)
        val train = chrono.subList(0, splitIdx)
        val test = chrono.subList(splitIdx, chrono.size)

        if (train.isEmpty() || test.isEmpty()) {
            return EvalResult(0.0, 0.0, 0.0, transitions.size, 0)
        }

        val predictor = Predictor(train, K, PR)

        // Baseline: most frequent next app in training set
        val nextCounts = mutableMapOf<String, Int>()
        train.forEach { nextCounts[it.next] = nextCounts.getOrDefault(it.next, 0) + 1 }
        val baselineApp = nextCounts.maxByOrNull { it.value }?.key ?: ""
        val baselineCorrect = test.count { it.next == baselineApp }
        val baselineAcc = if (test.isNotEmpty()) baselineCorrect.toDouble() / test.size else 0.0

        var top1Correct = 0
        var top3Correct = 0

        for (t in test) {
            val preds = predictor.predict(t.prev, t.hour, t.isWeekend)
            if (preds.isEmpty()) continue
            if (preds[0].packageName == t.next) top1Correct++
            if (preds.take(3).any { it.packageName == t.next }) top3Correct++
        }

        val top1 = if (test.isNotEmpty()) top1Correct.toDouble() / test.size else 0.0
        val top3 = if (test.isNotEmpty()) top3Correct.toDouble() / test.size else 0.0

        return EvalResult(
            top1 = top1,
            top3 = top3,
            baseline = baselineAcc,
            trainCount = train.size,
            testCount = test.size
        )
    }
}
