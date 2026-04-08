package com.hemax.database.dao

import androidx.room.*
import com.hemax.database.entities.SessionEntity

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions LIMIT 1")
    suspend fun getActiveSession(): SessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity)

    @Query("DELETE FROM sessions")
    suspend fun clearSession()
}
