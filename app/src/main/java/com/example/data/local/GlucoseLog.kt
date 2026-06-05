package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "glucose_logs")
data class GlucoseLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val glucoseValue: Double, // mg/dL
    val state: String = "", // e.g. "Jejum", "Pré-prandial", "Pós-prandial"
    val notes: String = ""
)
