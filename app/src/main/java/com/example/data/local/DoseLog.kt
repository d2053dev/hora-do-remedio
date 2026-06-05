package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dose_logs")
data class DoseLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val medicationId: Int,
    val medicationName: String, // cached to keep record even if medication is deleted
    val takenTimestamp: Long, // timestamp when user recorded this action
    val scheduledTimestamp: Long, // when it was scheduled to be taken
    val dosageTaken: String, // dosage taken (dosage + stockUnit, e.g., "50mg / 1 comprimido")
    val status: String // "TOMADO" (taken), "ATRASADO" (taken late), "IGNORADO" (skipped)
)
