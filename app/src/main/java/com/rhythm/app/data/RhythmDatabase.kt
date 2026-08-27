package com.rhythm.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.rhythm.app.optimem.MetricDao
import com.rhythm.app.optimem.MetricEntity

@Database(
    entities = [SessionEntity::class, MetricEntity::class],
    version = 2,
    exportSchema = false
)
abstract class RhythmDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun metricDao(): MetricDao

    companion object {
        @Volatile
        private var INSTANCE: RhythmDatabase? = null

        fun get(context: Context): RhythmDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RhythmDatabase::class.java,
                    "rhythm.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}