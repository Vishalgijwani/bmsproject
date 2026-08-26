package com.rhythm.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SessionDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(sessions: List<SessionEntity>)

    /** Latest endTime we have stored, for incremental ingest. */
    @Query("SELECT MAX(endTime) FROM sessions")
    suspend fun maxEndTime(): Long?

    @Query("SELECT * FROM sessions ORDER BY startTime ASC")
    suspend fun getAll(): List<SessionEntity>

    @Query("SELECT COUNT(*) FROM sessions")
    suspend fun count(): Int

    @Query("SELECT DISTINCT packageName FROM sessions")
    suspend fun distinctPackages(): List<String>

    @Query("DELETE FROM sessions")
    suspend fun deleteAll()

    @Query("DELETE FROM sessions WHERE packageName = :pkg")
    suspend fun deleteByPackage(pkg: String)
}
