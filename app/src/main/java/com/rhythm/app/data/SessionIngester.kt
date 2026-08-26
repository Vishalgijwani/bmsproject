package com.rhythm.app.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager

/**
 * Ingests raw UsageEvents and pairs ACTIVITY_RESUMED/ACTIVITY_PAUSED into sessions.
 * Drops sessions <30s, launcher, systemui, and this app's own package.
 */
class SessionIngester(
    private val context: Context,
    private val excludedPackages: Set<String> = emptySet()
) {

    private val ownPackage: String = context.packageName

    private val systemDropList: Set<String> = setOf(
        "com.android.systemui",
        ownPackage,
        *excludedPackages.toTypedArray()
    )

    /**
     * Query events from [fromTime] to now and return a list of sessions.
     */
    fun fetchSessions(fromTime: Long): List<SessionEntity> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usageStatsManager.queryEvents(fromTime, now)

        val results = mutableListOf<SessionEntity>()
        val pending = mutableMapOf<String, Long>() // package -> resumeTime

        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    pending[pkg] = event.timeStamp
                }
                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    val start = pending.remove(pkg)
                    if (start != null && start < event.timeStamp) {
                        val dur = (event.timeStamp - start) / 1000
                        if (dur >= 30 && pkg !in systemDropList) {
                            results.add(
                                SessionEntity(
                                    packageName = pkg,
                                    startTime = start,
                                    endTime = event.timeStamp,
                                    durationSec = dur
                                )
                            )
                        }
                    }
                }
            }
        }
        return results
    }

    /** Resolve a package name to a human-readable app label. */
    fun resolveLabel(pkg: String): String {
        return try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            pkg.substringAfterLast('.')
        }
    }
}
