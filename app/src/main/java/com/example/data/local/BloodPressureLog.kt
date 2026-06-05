package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blood_pressure_logs")
data class BloodPressureLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val systole: Int, // Sístole (Máxima) mmHg
    val diastole: Int, // Diástole (Mínima) mmHg
    val pulse: Int?, // Batimentos por Minuto (opcional)
    val notes: String = ""
)
