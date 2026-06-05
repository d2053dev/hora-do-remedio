package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BloodPressureLogDao {
    @Query("SELECT * FROM blood_pressure_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<BloodPressureLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: BloodPressureLog): Long

    @Delete
    suspend fun deleteLog(log: BloodPressureLog)

    @Query("DELETE FROM blood_pressure_logs")
    suspend fun clearAllLogs()
}
