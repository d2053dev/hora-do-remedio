package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medications")
data class Medication(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val dosage: String, // e.g., "50mg", "1 comprimido"
    val stockQuantity: Double, // Current quantity left (pills or ml)
    val stockUnit: String, // e.g., "comprimidos", "ml", "gotas"
    val dosageQuantity: Double, // amount of pill/ml taken each time
    val frequencyHours: Int, // intervals (e.g., every 8 hours, 12 hours)
    val firstDoseTimestamp: Long, // initial start time
    val nextDoseTimestamp: Long, // next scheduled time
    val notificationEnabled: Boolean = true,
    val notes: String = "",
    val isContinuous: Boolean = false,
    val controlType: String = "Nenhum" // "Nenhum", "Pressão Arterial", "Glicose"
) {
    // Check if the medicine is running low in stock (less than 5 doses left)
    fun isStockLow(): Boolean {
        return stockQuantity <= (dosageQuantity * 5)
    }

    // Check if continuous use medication has less than 1 week (7 days) of stock left
    fun isStockOneWeekLeft(): Boolean {
        if (!isContinuous) return false
        val dailyConsumption = (24.0 / frequencyHours.coerceAtLeast(1)) * dosageQuantity
        val oneWeekRequirement = dailyConsumption * 7.0
        return stockQuantity <= oneWeekRequirement
    }
}
