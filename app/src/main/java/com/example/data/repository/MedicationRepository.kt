package com.example.data.repository

import com.example.data.local.*
import kotlinx.coroutines.flow.Flow

class MedicationRepository(
    private val medicationDao: MedicationDao,
    private val doseLogDao: DoseLogDao,
    private val bloodPressureLogDao: BloodPressureLogDao,
    private val glucoseLogDao: GlucoseLogDao
) {
    val allMedications: Flow<List<Medication>> = medicationDao.getAllMedications()
    val allLogs: Flow<List<DoseLog>> = doseLogDao.getAllLogs()
    val allBloodPressureLogs: Flow<List<BloodPressureLog>> = bloodPressureLogDao.getAllLogs()
    val allGlucoseLogs: Flow<List<GlucoseLog>> = glucoseLogDao.getAllLogs()

    suspend fun getMedicationById(id: Int): Medication? {
        return medicationDao.getMedicationById(id)
    }

    suspend fun insertMedication(medication: Medication): Long {
        return medicationDao.insertMedication(medication)
    }

    suspend fun updateMedication(medication: Medication) {
        medicationDao.updateMedication(medication)
    }

    suspend fun deleteMedication(medication: Medication) {
        doseLogDao.deleteLogsForMedication(medication.id)
        medicationDao.deleteMedication(medication)
    }

    // Records taking a dose of medication
    suspend fun takeDose(medication: Medication, status: String = "TOMADO", takenTime: Long = System.currentTimeMillis()) {
        // Reduzir o estoque (stock) correspondente à quantidade recomendada
        val newStock = (medication.stockQuantity - medication.dosageQuantity).coerceAtLeast(0.0)
        
        // Calcular próximo horário de dosagem com base na frequência
        val nextScheduled = takenTime + (medication.frequencyHours.toLong() * 60 * 60 * 1000)
        
        val updatedMedication = medication.copy(
            stockQuantity = newStock,
            nextDoseTimestamp = nextScheduled
        )
        medicationDao.updateMedication(updatedMedication)

        // Registrar no histórico de consumo
        val doseUnitText = "${medication.dosageQuantity} ${medication.stockUnit}"
        val log = DoseLog(
            medicationId = medication.id,
            medicationName = medication.name,
            takenTimestamp = takenTime,
            scheduledTimestamp = medication.nextDoseTimestamp,
            dosageTaken = "${medication.dosage} ($doseUnitText)",
            status = status
        )
        doseLogDao.insertLog(log)
    }

    // Records skipping a dose
    suspend fun skipDose(medication: Medication, scheduledTime: Long = medication.nextDoseTimestamp) {
        val nextScheduled = System.currentTimeMillis() + (medication.frequencyHours.toLong() * 60 * 60 * 1000)
        
        val updatedMedication = medication.copy(
            nextDoseTimestamp = nextScheduled
        )
        medicationDao.updateMedication(updatedMedication)

        val doseUnitText = "${medication.dosageQuantity} ${medication.stockUnit}"
        val log = DoseLog(
            medicationId = medication.id,
            medicationName = medication.name,
            takenTimestamp = System.currentTimeMillis(),
            scheduledTimestamp = scheduledTime,
            dosageTaken = "${medication.dosage} ($doseUnitText)",
            status = "IGNORADO"
        )
        doseLogDao.insertLog(log)
    }

    // Clears consumption logs history
    suspend fun clearHistory() {
        doseLogDao.clearAllLogs()
    }

    suspend fun insertDoseLog(log: DoseLog): Long {
        return doseLogDao.insertLog(log)
    }

    suspend fun clearAllMedications() {
        medicationDao.clearAllMedications()
    }

    suspend fun clearAllDoseLogs() {
        doseLogDao.clearAllLogs()
    }

    suspend fun clearAllBloodPressureLogs() {
        bloodPressureLogDao.clearAllLogs()
    }

    suspend fun clearAllGlucoseLogs() {
        glucoseLogDao.clearAllLogs()
    }

    suspend fun insertBloodPressureLog(log: BloodPressureLog): Long {
        return bloodPressureLogDao.insertLog(log)
    }

    suspend fun deleteBloodPressureLog(log: BloodPressureLog) {
        bloodPressureLogDao.deleteLog(log)
    }

    suspend fun clearBloodPressureLogs() {
        bloodPressureLogDao.clearAllLogs()
    }

    suspend fun insertGlucoseLog(log: GlucoseLog): Long {
        return glucoseLogDao.insertLog(log)
    }

    suspend fun deleteGlucoseLog(log: GlucoseLog) {
        glucoseLogDao.deleteLog(log)
    }

    suspend fun clearGlucoseLogs() {
        glucoseLogDao.clearAllLogs()
    }
}
