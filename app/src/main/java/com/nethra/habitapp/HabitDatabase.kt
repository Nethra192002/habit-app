package com.nethra.habitapp

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

// One row = one event from the phone's log
@Entity(
    tableName = "usage_events",
    primaryKeys = ["timestamp", "eventType", "packageName", "className"]
)
data class UsageEventEntity(
    val timestamp: Long,
    val eventType: Int,
    val packageName: String,
    val className: String
)

// The questions and commands we can send to the table
@Dao
interface UsageEventDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(events: List<UsageEventEntity>): List<Long>

    @Query("SELECT COUNT(*) FROM usage_events")
    suspend fun count(): Int

    @Query("SELECT MIN(timestamp) FROM usage_events")
    suspend fun oldestTimestamp(): Long?

    @Query("SELECT MAX(timestamp) FROM usage_events")
    suspend fun newestTimestamp(): Long?

    @Query("SELECT * FROM usage_events ORDER BY timestamp")
    suspend fun getAll(): List<UsageEventEntity>
}

// The database itself
@Database(entities = [UsageEventEntity::class], version = 1, exportSchema = false)
abstract class HabitDatabase : RoomDatabase() {
    abstract fun usageEventDao(): UsageEventDao

    companion object {
        @Volatile
        private var instance: HabitDatabase? = null

        fun get(context: Context): HabitDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    HabitDatabase::class.java,
                    "habit.db"
                ).build().also { instance = it }
            }
    }
}