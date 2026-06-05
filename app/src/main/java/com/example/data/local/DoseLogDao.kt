package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DoseLogDao {
    @Query("SELECT * FROM dose_logs ORDER BY takenTimestamp DESC")
    fun getAllLogs(): Flow<List<DoseLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: DoseLog): Long

    @Query("DELETE FROM dose_logs WHERE medicationId = :medicationId")
    suspend fun deleteLogsForMedication(medicationId: Int)

    @Query("DELETE FROM dose_logs")
    suspend fun clearAllLogs()
}
