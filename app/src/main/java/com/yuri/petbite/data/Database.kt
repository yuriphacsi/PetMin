package com.yuri.petbite.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "schedules")
data class MealSchedule(
    @PrimaryKey val id: String,
    val hour: Int,
    val minute: Int,
    val grams: Int,
    val enabled: Boolean = true,
    val lastDemoDay: String = ""
)

@Entity(tableName = "feed_events")
data class FeedEvent(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val requestedGrams: Int,
    val deliveredGrams: Double?,
    val demo: Boolean,
    val source: String,
    val status: String,
    val detail: String = ""
)

@Dao
interface PetDao {
    @Query("SELECT * FROM schedules ORDER BY hour, minute")
    fun schedules(): Flow<List<MealSchedule>>
    @Query("SELECT * FROM feed_events ORDER BY timestamp DESC")
    fun events(): Flow<List<FeedEvent>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSchedule(schedule: MealSchedule)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun importEvents(events: List<FeedEvent>)
    @Delete suspend fun deleteSchedule(schedule: MealSchedule)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveEvent(event: FeedEvent)
    // PETMIN: elimina únicamente registros antiguos; los horarios no se modifican.
    @Query("DELETE FROM feed_events WHERE timestamp < :cutoff")
    suspend fun deleteEventsBefore(cutoff: Long)
    @Query("UPDATE feed_events SET status = 'Sin confirmar', detail = 'La aplicación se cerró antes de recibir confirmación. Comprueba el dispensador antes de repetir.' WHERE status = 'En proceso'")
    suspend fun recoverInterrupted()
}

@Database(entities = [MealSchedule::class, FeedEvent::class], version = 1, exportSchema = false)
abstract class PetDatabase : RoomDatabase() {
    abstract fun dao(): PetDao
    companion object {
        @Volatile private var instance: PetDatabase? = null
        fun get(context: Context): PetDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, PetDatabase::class.java, "petbite.db")
                .build().also { instance = it }
        }
    }
}
