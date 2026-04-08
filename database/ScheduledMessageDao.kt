package com.hemax.database.dao

import androidx.room.*
import com.hemax.database.entities.ScheduledMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduledMessageDao {
    @Query("SELECT * FROM scheduled_messages WHERE isSent = 0 ORDER BY scheduledTime ASC")
    fun getAllPending(): Flow<List<ScheduledMessageEntity>>

    @Insert
    suspend fun insert(message: ScheduledMessageEntity): Long

    @Update
    suspend fun update(message: ScheduledMessageEntity)

    @Query("DELETE FROM scheduled_messages WHERE id = :id")
    suspend fun deleteById(id: Long)
}
