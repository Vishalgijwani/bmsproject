package com.rhythm.app.optimem

import kotlinx.coroutines.delay

/**
 * A small, fixed catalog of demo resources Optimem legitimately owns, one
 * conceptually "belonging to" each app's content feed. This is what gets
 * prefetched/cached -- never a third-party app's real private cache, which
 * Android's sandbox does not allow a normal app to touch.
 *
 * simulateFetch() stands in for a real network/disk fetch so the demo can
 * show genuine before/after latency numbers without needing a backend.
 */
class DemoCacheManager(
    private val catalog: List<DemoResource>
) {
    private val cached = mutableSetOf<String>() // resource ids currently cached
    private var wastedPrefetches = 0
    private var usefulPrefetches = 0

    fun resourceFor(packageName: String): DemoResource? =
        catalog.firstOrNull { it.forPackage == packageName }

    fun isCached(resourceId: String): Boolean = cached.contains(resourceId)

    /** Perform the prefetch: simulate the fetch, then mark cached. */
    suspend fun prefetch(resource: DemoResource) {
        delay(resource.baseFetchLatencyMs)
        cached.add(resource.id)
    }

    /**
     * User actually requested this resource. Returns the latency the user
     * experienced: near-zero if it was already cached (a real hit), or the
     * full base latency if we have to fetch on demand now.
     */
    suspend fun request(resource: DemoResource): Long {
        return if (cached.contains(resource.id)) {
            usefulPrefetches++
            5L // cache hit: negligible local read latency
        } else {
            delay(resource.baseFetchLatencyMs)
            resource.baseFetchLatencyMs
        }
    }

    /** Call when a prefetch turned out to be for the wrong app. */
    fun markWasted(resourceId: String) {
        cached.remove(resourceId)
        wastedPrefetches++
    }

    fun stats(): CacheStats = CacheStats(
        cachedCount = cached.size,
        usefulPrefetches = usefulPrefetches,
        wastedPrefetches = wastedPrefetches
    )

    fun evictAll() {
        cached.clear()
    }

    data class CacheStats(
        val cachedCount: Int,
        val usefulPrefetches: Int,
        val wastedPrefetches: Int
    )

    companion object {
        /**
         * Builds a catalog from whatever apps the predictor has seen, so the
         * demo always matches this device's real usage instead of a
         * hardcoded app list.
         */
        fun buildCatalog(apps: Set<String>): List<DemoResource> =
            apps.map { pkg ->
                DemoResource(
                    id = "res_$pkg",
                    forPackage = pkg,
                    label = "$pkg content bundle",
                    sizeKB = 150 + (pkg.hashCode().mod(600)), // stable, varied size per app
                    baseFetchLatencyMs = 200L + (pkg.hashCode().mod(400)).toLong()
                )
            }
    }
}
