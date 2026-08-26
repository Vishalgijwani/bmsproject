package com.rhythm.app.model

import com.rhythm.app.data.SessionEntity
import java.util.Calendar

/**
 * A transition from one app to the next, same day, different app.
 * hour = hour of the midpoint between the two start times.
 */
data class Transition(
    val prev: String,
    val next: String,
    val hour: Int,        // 0-23, midpoint hour
    val isWeekend: Boolean,
    val gapMin: Long      // minutes between prev start and next start
)

object TransitionExtractor {

    /**
     * Build transitions from a chronologically-sorted list of sessions.
     * Consecutive sessions on the same calendar day with different apps.
     */
    fun extract(sessions: List<SessionEntity>): List<Transition> {
        if (sessions.size < 2) return emptyList()

        val sorted = sessions.sortedBy { it.startTime }
        val cal = Calendar.getInstance()

        fun dayKey(ts: Long): Int {
            cal.timeInMillis = ts
            return cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
        }

        fun hourOf(ts: Long): Int {
            cal.timeInMillis = ts
            return cal.get(Calendar.HOUR_OF_DAY)
        }

        fun isWeekend(ts: Long): Boolean {
            cal.timeInMillis = ts
            val dow = cal.get(Calendar.DAY_OF_WEEK)
            return dow == Calendar.SATURDAY || dow == Calendar.SUNDAY
        }

        val transitions = mutableListOf<Transition>()
        for (i in 1 until sorted.size) {
            val prev = sorted[i - 1]
            val next = sorted[i]

            if (prev.packageName == next.packageName) continue
            if (dayKey(prev.startTime) != dayKey(next.startTime)) continue

            val midTs = (prev.startTime + next.startTime) / 2
            transitions.add(
                Transition(
                    prev = prev.packageName,
                    next = next.packageName,
                    hour = hourOf(midTs),
                    isWeekend = isWeekend(midTs),
                    gapMin = (next.startTime - prev.startTime) / 60000
                )
            )
        }
        return transitions
    }
}
