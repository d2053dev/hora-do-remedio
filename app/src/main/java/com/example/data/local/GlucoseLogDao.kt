package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GlucoseLogDao {
    @Query("SELECT * FROM glucose_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<GlucoseLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: GlucoseLog): Long

    @Delete
    suspend fun deleteLog(log: GlucoseLog)

    @Query("DELETE FROM glucose_logs")
    suspend fun clearAllLogs()
}
