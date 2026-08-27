package com.rhythm.app.optimem

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update

/**
 * Room-persisted version of MetricEvent. Kept separate from the plain
 * MetricEvent data class so the domain model (used by DecisionEngine/tests)
 * doesn't depend on Room annotations.
 */
@Entity(tableName = "metric_events")
data class MetricEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val predictedPackage: String,
    val predictedProbability: Double,
    val decision: String,          // Action.name
    val actualNextPackage: String? = null,
    val predictionCorrect: Boolean? = null,
    val cacheHit: Boolean? = null,
    val latencySavedMs: Long? = null,
    val wastedPrefetch: Boolean? = null
)

@Dao
interface MetricDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: MetricEntity): Long

    @Update
    suspend fun update(event: MetricEntity)

    @Query("SELECT * FROM metric_events ORDER BY timestamp DESC")
    suspend fun getAll(): List<MetricEntity>

    @Query("SELECT * FROM metric_events WHERE actualNextPackage IS NULL ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestUnresolved(): MetricEntity?

    @Query("SELECT COUNT(*) FROM metric_events WHERE predictionCorrect = 1")
    suspend fun correctCount(): Int

    @Query("SELECT COUNT(*) FROM metric_events WHERE predictionCorrect IS NOT NULL")
    suspend fun resolvedCount(): Int

    @Query("SELECT COUNT(*) FROM metric_events WHERE cacheHit = 1")
    suspend fun cacheHitCount(): Int

    @Query("SELECT COUNT(*) FROM metric_events WHERE wastedPrefetch = 1")
    suspend fun wastedCount(): Int

    @Query("SELECT COUNT(*) FROM metric_events WHERE decision = 'PREFETCH'")
    suspend fun prefetchCount(): Int

    @Query("SELECT SUM(latencySavedMs) FROM metric_events WHERE latencySavedMs IS NOT NULL")
    suspend fun totalLatencySavedMs(): Long?

    @Query("DELETE FROM metric_events")
    suspend fun deleteAll()
}
