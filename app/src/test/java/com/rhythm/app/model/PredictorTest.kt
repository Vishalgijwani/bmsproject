package com.rhythm.app.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Hand-built fixture where the answer is obvious:
 *  - In the morning (hour 8), after "Email", the user always opens "Calendar".
 *  - In the evening (hour 20), after "Email", the user always opens "Video".
 *  - "Video" is the most frequent app overall (baseline).
 *
 * The model should predict "Calendar" at 8am after Email, not "Video".
 */
class PredictorTest {

    private fun makeTransition(prev: String, next: String, hour: Int, weekend: Boolean, gap: Long): Transition {
        return Transition(prev = prev, next = next, hour = hour, isWeekend = weekend, gapMin = gap)
    }

    @Test
    fun context_dominates_general_prior() {
        // 60 morning transitions: Email -> Calendar at hour 8
        val morning = (1..60).map {
            makeTransition("com.email", "com.calendar", 8, false, 5)
        }
        // 60 evening transitions: Email -> Video at hour 20
        val evening = (1..60).map {
            makeTransition("com.email", "com.video", 20, false, 10)
        }
        // Video appears more overall (120 times as next vs 60 for calendar)
        val extraVideo = (1..80).map {
            makeTransition("com.browser", "com.video", 20, false, 8)
        }

        val transitions = morning + evening + extraVideo
        val predictor = Predictor(transitions)

        // Predict at hour 8, weekday, prev = Email
        val preds = predictor.predict("com.email", 8, false)

        assertThat(preds).isNotEmpty()
        assertThat(preds[0].packageName).isEqualTo("com.calendar")

        // Predict at hour 20, weekday, prev = Email
        val predsEve = predictor.predict("com.email", 20, false)
        assertThat(predsEve).isNotEmpty()
        assertThat(predsEve[0].packageName).isEqualTo("com.video")
    }

    @Test
    fun canPredict_requires_50_transitions() {
        val few = (1..30).map { makeTransition("a", "b", 10, false, 5) }
        val predictor = Predictor(few)
        assertThat(predictor.canPredict()).isFalse()

        val enough = (1..60).map { makeTransition("a", "b", 10, false, 5) }
        val predictor2 = Predictor(enough)
        assertThat(predictor2.canPredict()).isTrue()
    }

    @Test
    fun predictions_sum_to_one() {
        val transitions = (1..20).map {
            makeTransition("com.a", "com.b", 10, false, 5)
        } + (1..20).map {
            makeTransition("com.a", "com.c", 10, false, 5)
        } + (1..20).map {
            makeTransition("com.a", "com.d", 10, false, 5)
        }
        val predictor = Predictor(transitions)
        val preds = predictor.predict("com.a", 10, false)
        val sum = preds.sumOf { it.score }
        assertThat(sum).isWithin(0.001).of(1.0)
    }

    @Test
    fun eta_returns_median_when_enough_samples() {
        val gaps = listOf(5L, 10L, 15L, 20L, 25L) // median = 15
        val transitions = gaps.mapIndexed { i, g ->
            makeTransition("com.a", "com.b", 10, false, g)
        }
        val predictor = Predictor(transitions)
        val eta = predictor.etaMinutes("com.a", 10)
        assertThat(eta).isEqualTo(15)
    }

    @Test
    fun eta_returns_12_when_insufficient_samples() {
        val transitions = (1..2).map {
            makeTransition("com.a", "com.b", 10, false, 5)
        }
        val predictor = Predictor(transitions)
        val eta = predictor.etaMinutes("com.a", 10)
        assertThat(eta).isEqualTo(12)
    }

    @Test
    fun evaluator_chronological_split() {
        // 100 transitions, first 75 train, last 25 test
        val transitions = (1..75).map {
            makeTransition("com.a", "com.b", 8, false, 5)
        } + (1..25).map {
            makeTransition("com.a", "com.b", 8, false, 5)
        }
        val result = Evaluator.evaluate(transitions)
        assertThat(result.trainCount).isEqualTo(75)
        assertThat(result.testCount).isEqualTo(25)
        assertThat(result.top1).isGreaterThan(0.0)
    }

    @Test
    fun evaluator_baseline_is_most_frequent() {
        // "com.b" is the most frequent next app
        val transitions = (1..50).map {
            makeTransition("com.a", "com.b", 8, false, 5)
        } + (1..10).map {
            makeTransition("com.a", "com.c", 8, false, 5)
        }
        val result = Evaluator.evaluate(transitions)
        // Baseline should be high since b dominates
        assertThat(result.baseline).isGreaterThan(0.0)
    }

    @Test
    fun flat_weighted_sum_would_fail_but_backoff_succeeds() {
        // Strong context: 60x Email->Calendar at hour 8
        // Strong prior: 200x Browser->Video at hour 20
        // A flat weighted sum might let Video outvote Calendar at hour 8.
        val transitions = (1..60).map {
            makeTransition("com.email", "com.calendar", 8, false, 5)
        } + (1..200).map {
            makeTransition("com.browser", "com.video", 20, false, 10)
        }
        val predictor = Predictor(transitions)
        val preds = predictor.predict("com.email", 8, false)
        assertThat(preds[0].packageName).isEqualTo("com.calendar")
    }
}
