package com.rhythm.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single foreground session: from ACTIVITY_RESUMED to ACTIVITY_PAUSED.
 */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val startTime: Long,   // epoch millis
    val endTime: Long,     // epoch millis
    val durationSec: Long  // (endTime - startTime) / 1000
)
