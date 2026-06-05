package com.example.ui.viewmodel

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.MainActivity
import com.example.data.local.AppDatabase
import com.example.data.local.Medication
import com.example.data.local.DoseLog
import com.example.data.local.BloodPressureLog
import com.example.data.local.GlucoseLog
import com.example.data.repository.MedicationRepository
import com.example.data.repository.GoogleDriveSyncManager
import com.example.data.repository.ParsedBackup
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MedicationViewModel(
    application: Application,
    private val repository: MedicationRepository
) : AndroidViewModel(application) {

    val allMedications: StateFlow<List<Medication>> = repository.allMedications
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allLogs: StateFlow<List<DoseLog>> = repository.allLogs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allBloodPressureLogs: StateFlow<List<BloodPressureLog>> = repository.allBloodPressureLogs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allGlucoseLogs: StateFlow<List<GlucoseLog>> = repository.allGlucoseLogs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Ajustes de Notificação Personalizáveis
    private val prefs = application.getSharedPreferences("patient_profile_prefs", Context.MODE_PRIVATE)

    private val _notificationsAccepted = MutableStateFlow<Boolean?>(
        if (prefs.contains("notifications_accepted")) {
            prefs.getBoolean("notifications_accepted", false)
        } else null
    )
    val notificationsAccepted: StateFlow<Boolean?> = _notificationsAccepted.asStateFlow()

    private val _vibrationEnabled = MutableStateFlow(prefs.getBoolean("vibration_enabled", true))
    val vibrationEnabled: StateFlow<Boolean> = _vibrationEnabled.asStateFlow()

    private val _soundEnabled = MutableStateFlow(prefs.getBoolean("sound_enabled", true))
    val soundEnabled: StateFlow<Boolean> = _soundEnabled.asStateFlow()

    private val _profileName = MutableStateFlow(prefs.getString("profile_name", "Paciente Crônico") ?: "Paciente Crônico")
    val profileName: StateFlow<String> = _profileName.asStateFlow()

    private val _profileWeight = MutableStateFlow(prefs.getString("profile_weight", "") ?: "")
    val profileWeight: StateFlow<String> = _profileWeight.asStateFlow()

    private val _profileHeight = MutableStateFlow(prefs.getString("profile_height", "") ?: "")
    val profileHeight: StateFlow<String> = _profileHeight.asStateFlow()

    init {
        createNotificationChannel()
    }

    fun updateProfileName(newName: String) {
        _profileName.value = newName
        prefs.edit().putString("profile_name", newName).apply()
    }

    fun updateProfileWeight(newWeight: String) {
        _profileWeight.value = newWeight
        prefs.edit().putString("profile_weight", newWeight).apply()
    }

    fun updateProfileHeight(newHeight: String) {
        _profileHeight.value = newHeight
        prefs.edit().putString("profile_height", newHeight).apply()
    }

    fun setNotificationsAccepted(accepted: Boolean) {
        prefs.edit().putBoolean("notifications_accepted", accepted).apply()
        _notificationsAccepted.value = accepted
        
        // Match settings to accepted
        prefs.edit().putBoolean("sound_enabled", accepted).apply()
        prefs.edit().putBoolean("vibration_enabled", accepted).apply()
        _soundEnabled.value = accepted
        _vibrationEnabled.value = accepted
    }

    fun toggleVibration(enabled: Boolean) {
        prefs.edit().putBoolean("vibration_enabled", enabled).apply()
        _vibrationEnabled.value = enabled
    }

    fun toggleSound(enabled: Boolean) {
        prefs.edit().putBoolean("sound_enabled", enabled).apply()
        _soundEnabled.value = enabled
    }

    // SQLite Operations
    fun addMedication(
        name: String,
        dosage: String,
        stock: Double,
        stockUnit: String,
        dosageQty: Double,
        frequencyHours: Int,
        firstDoseTimestamp: Long,
        notes: String = "",
        isContinuous: Boolean = false,
        controlType: String = "Nenhum"
    ) {
        viewModelScope.launch {
            val calculatedNextDose = calculateNextScheduledTimestamp(firstDoseTimestamp, frequencyHours)
            val med = Medication(
                name = name,
                dosage = dosage,
                stockQuantity = stock,
                stockUnit = stockUnit,
                dosageQuantity = dosageQty,
                frequencyHours = frequencyHours,
                firstDoseTimestamp = firstDoseTimestamp,
                nextDoseTimestamp = calculatedNextDose,
                notes = notes,
                isContinuous = isContinuous,
                controlType = controlType
            )
            val longId = repository.insertMedication(med)
            
            // Re-schedule alarm for this newly saved medication
            val insertedMed = med.copy(id = longId.toInt())
            scheduleAlarmForMedication(insertedMed)

            // Auto-check if the newly registered continuous medicine starts with low stock (< 1 week left)
            if (med.isStockOneWeekLeft() && med.stockQuantity > 0.0) {
                val dailyConsumption = (24.0 / med.frequencyHours.coerceAtLeast(1)) * med.dosageQuantity
                val daysLeft = if (dailyConsumption > 0.0) med.stockQuantity / dailyConsumption else 0.0
                sendLocalNotification(
                    title = "Aviso de Estoque Limiar (Uso Contínuo)",
                    message = "O remédio de uso contínuo '${med.name}' foi cadastrado com estoque para menos de uma semana! Restam apenas ${med.stockQuantity} ${med.stockUnit} (suficiente para aprox. ${"%.1f".format(daysLeft)} dias)."
                )
            }
        }
    }

    fun updateMedication(medication: Medication) {
        viewModelScope.launch {
            val currentInDb = repository.getMedicationById(medication.id)
            var updatedMed = medication
            if (currentInDb != null && (currentInDb.firstDoseTimestamp != medication.firstDoseTimestamp || currentInDb.frequencyHours != medication.frequencyHours)) {
                val nextDose = calculateNextScheduledTimestamp(medication.firstDoseTimestamp, medication.frequencyHours)
                updatedMed = medication.copy(nextDoseTimestamp = nextDose)
            }
            repository.updateMedication(updatedMed)
            scheduleAlarmForMedication(updatedMed)
            
            // Send warning if the updated or replenished stock is still low (< 1 week)
            if (updatedMed.isStockOneWeekLeft() && updatedMed.stockQuantity > 0.0) {
                val dailyConsumption = (24.0 / updatedMed.frequencyHours.coerceAtLeast(1)) * updatedMed.dosageQuantity
                val daysLeft = if (dailyConsumption > 0.0) updatedMed.stockQuantity / dailyConsumption else 0.0
                sendLocalNotification(
                    title = "Aviso de Estoque Limiar (Uso Contínuo)",
                    message = "O remédio de uso contínuo '${updatedMed.name}' está com estoque baixo! Restam apenas ${updatedMed.stockQuantity} ${updatedMed.stockUnit} (suficiente para aprox. ${"%.1f".format(daysLeft)} dias)."
                )
            }
        }
    }

    fun deleteMedication(medication: Medication) {
        viewModelScope.launch {
            cancelAlarmForMedication(medication)
            repository.deleteMedication(medication)
        }
    }

    fun markAsTaken(medication: Medication, takenTime: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            repository.takeDose(medication, status = "TOMADO", takenTime = takenTime)
            
            // Retrieve updated medication with the calculated next scheduled dose
            val updatedInDb = repository.getMedicationById(medication.id)
            if (updatedInDb != null) {
                scheduleAlarmForMedication(updatedInDb)
            }
            
            // Firing a notifications success trigger if enabled
            if (medication.notificationEnabled) {
                sendLocalNotification(
                    title = "Dose Confirmada",
                    message = "Você tomou ${medication.dosageQuantity} ${medication.stockUnit} de ${medication.name} com sucesso."
                )
            }

            // Check if stock runs under 1-week supply mark after intake
            val newStock = (medication.stockQuantity - medication.dosageQuantity).coerceAtLeast(0.0)
            val updatedMed = medication.copy(stockQuantity = newStock)
            if (updatedMed.isStockOneWeekLeft()) {
                val dailyConsumption = (24.0 / medication.frequencyHours.coerceAtLeast(1)) * medication.dosageQuantity
                val daysLeft = if (dailyConsumption > 0.0) newStock / dailyConsumption else 0.0
                
                sendLocalNotification(
                    title = "Falta uma semana para o fim do estoque!",
                    message = "Atenção: O estoque do remédio de uso contínuo '${medication.name}' acabará em breve. Restam apenas ${newStock} ${medication.stockUnit} (suficiente para ${"%.1f".format(daysLeft)} dias)."
                )
            }
        }
    }

    fun markAsSkipped(medication: Medication) {
        viewModelScope.launch {
            repository.skipDose(medication)
            
            // Retrieve updated medication with the calculated next scheduled dose
            val updatedInDb = repository.getMedicationById(medication.id)
            if (updatedInDb != null) {
                scheduleAlarmForMedication(updatedInDb)
            }
            
            if (medication.notificationEnabled) {
                sendLocalNotification(
                    title = "Dose Ignorada",
                    message = "Você pulou a dose de de ${medication.name} agendada."
                )
            }
        }
    }

    fun calculateNextScheduledTimestamp(baseTime: Long, frequencyHours: Int): Long {
        val currentTime = System.currentTimeMillis()
        if (baseTime >= currentTime) {
            return baseTime
        }
        val intervalMs = frequencyHours.toLong() * 60 * 60 * 1000
        if (intervalMs <= 0) return currentTime
        
        var tempTime = baseTime
        while (tempTime < currentTime) {
            tempTime += intervalMs
        }
        return tempTime
    }

    fun scheduleAlarmForMedication(medication: Medication) {
        val context = getApplication<Application>()
        if (!medication.notificationEnabled || _notificationsAccepted.value == false) {
            cancelAlarmForMedication(medication)
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = Intent(context, com.example.receiver.AlarmReceiver::class.java).apply {
            putExtra("medication_id", medication.id)
            putExtra("medication_name", medication.name)
            putExtra("medication_dosage", medication.dosage)
            putExtra("medication_dosage_qty", medication.dosageQuantity)
            putExtra("medication_stock_unit", medication.stockUnit)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            medication.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (medication.nextDoseTimestamp > System.currentTimeMillis()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        android.app.AlarmManager.RTC_WAKEUP,
                        medication.nextDoseTimestamp,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        android.app.AlarmManager.RTC_WAKEUP,
                        medication.nextDoseTimestamp,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    android.app.AlarmManager.RTC_WAKEUP,
                    medication.nextDoseTimestamp,
                    pendingIntent
                )
            }
        }
    }

    fun cancelAlarmForMedication(medication: Medication) {
        val context = getApplication<Application>()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = Intent(context, com.example.receiver.AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            medication.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    fun rescheduleAllAlarms() {
        val medications = allMedications.value
        for (med in medications) {
            scheduleAlarmForMedication(med)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    // export report to doctors
    fun generateShareableReport(): String {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val stringBuilder = StringBuilder()
        
        stringBuilder.append("===================================\n")
        stringBuilder.append("RELATÓRIO DE CONTROLE DE MEDICAMENTOS\n")
        stringBuilder.append("===================================\n")
        stringBuilder.append("Data de Geração: ${dateFormat.format(Date())}\n")
        stringBuilder.append("Paciente: ${_profileName.value}\n\n")

        stringBuilder.append("1. MEDICAMENTOS CADASTRADOS:\n")
        val medications = allMedications.value
        if (medications.isEmpty()) {
            stringBuilder.append("- Nenhum medicamento cadastrado.\n")
        } else {
            for (med in medications) {
                val stockLowWarning = if (med.isStockLow()) " (⚠️ ESTOQUE BAIXO!)" else ""
                stringBuilder.append("- ${med.name} (${med.dosage})\n")
                stringBuilder.append("  Frequência: A cada ${med.frequencyHours} horas\n")
                stringBuilder.append("  Próxima dose: ${dateFormat.format(Date(med.nextDoseTimestamp))}\n")
                stringBuilder.append("  Estoque: ${med.stockQuantity} ${med.stockUnit} restantes$stockLowWarning\n")
                if (med.notes.isNotEmpty()) {
                    stringBuilder.append("  Observações: ${med.notes}\n")
                }
                stringBuilder.append("\n")
            }
        }

        stringBuilder.append("2. HISTÓRICO DE CONSUMO RECENTE:\n")
        val logs = allLogs.value
        if (logs.isEmpty()) {
            stringBuilder.append("- Nenhum histórico registrado.\n")
        } else {
            for (log in logs) {
                val takenStr = dateFormat.format(Date(log.takenTimestamp))
                stringBuilder.append("- [$takenStr] ${log.medicationName} (${log.dosageTaken})\n")
                stringBuilder.append("  Status: ${log.status}\n")
                stringBuilder.append("\n")
            }
        }
        stringBuilder.append("===================================\n")
        stringBuilder.append("Gerado por aplicativo: Hora do Remédio\n")
        
        return stringBuilder.toString()
    }

    // Notification channel set up
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Lembretes de Remédio"
            val descriptionText = "Canal de notificações para medicamentos inteligentes e alertas de estoque baixo."
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel("med_reminder_channel_id", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getApplication<Application>().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    // Send a local immediate notification
    fun sendLocalNotification(title: String, message: String) {
        val context = getApplication<Application>()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notificationBuilder = NotificationCompat.Builder(context, "med_reminder_channel_id")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm) // Safe standard platform drawable
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (_soundEnabled.value) {
            notificationBuilder.setDefaults(NotificationCompat.DEFAULT_SOUND)
        }
        if (_vibrationEnabled.value) {
            notificationBuilder.setDefaults(NotificationCompat.DEFAULT_VIBRATE)
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }

    // Blood Pressure and Glucose Database Operations
    fun addBloodPressureLog(systole: Int, diastole: Int, pulse: Int?, notes: String) {
        viewModelScope.launch {
            val log = BloodPressureLog(
                timestamp = System.currentTimeMillis(),
                systole = systole,
                diastole = diastole,
                pulse = pulse,
                notes = notes
            )
            repository.insertBloodPressureLog(log)
        }
    }

    fun deleteBloodPressureLog(log: BloodPressureLog) {
        viewModelScope.launch {
            repository.deleteBloodPressureLog(log)
        }
    }

    fun clearBloodPressureLogs() {
        viewModelScope.launch {
            repository.clearBloodPressureLogs()
        }
    }

    fun addGlucoseLog(glucoseValue: Double, state: String, notes: String) {
        viewModelScope.launch {
            val log = GlucoseLog(
                timestamp = System.currentTimeMillis(),
                glucoseValue = glucoseValue,
                state = state,
                notes = notes
            )
            repository.insertGlucoseLog(log)
        }
    }

    fun deleteGlucoseLog(log: GlucoseLog) {
        viewModelScope.launch {
            repository.deleteGlucoseLog(log)
        }
    }

    fun clearGlucoseLogs() {
        viewModelScope.launch {
            repository.clearGlucoseLogs()
        }
    }

    // PDF Reports Generators
    fun generateBloodPressurePdf(): java.io.File? {
        val context = getApplication<Application>()
        val logs = allBloodPressureLogs.value
        val pdfFolder = java.io.File(context.cacheDir, "reports")
        if (!pdfFolder.exists()) {
            pdfFolder.mkdirs()
        }
        val pdfFile = java.io.File(pdfFolder, "relatorio_pressao_arterial.pdf")
        
        try {
            val pdfDocument = android.graphics.pdf.PdfDocument()
            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas
            
            val paint = android.graphics.Paint()
            val textPaint = android.graphics.Paint().apply {
                textSize = 12f
                color = android.graphics.Color.BLACK
                isAntiAlias = true
            }
            val headerPaint = android.graphics.Paint().apply {
                textSize = 18f
                color = android.graphics.Color.parseColor("#121212")
                isFakeBoldText = true
                isAntiAlias = true
            }
            val titlePaint = android.graphics.Paint().apply {
                textSize = 14f
                color = android.graphics.Color.parseColor("#34495e")
                isFakeBoldText = true
                isAntiAlias = true
            }
            
            var yPosition = 50f
            
            canvas.drawText("RELATÓRIO DE PRESSÃO ARTERIAL", 50f, yPosition, headerPaint)
            yPosition += 30f
            
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            canvas.drawText("Paciente: ${_profileName.value}", 50f, yPosition, textPaint)
            yPosition += 20f
            canvas.drawText("Data de Emissão: ${dateFormat.format(Date())}", 50f, yPosition, textPaint)
            yPosition += 25f
            
            paint.color = android.graphics.Color.LTGRAY
            canvas.drawLine(50f, yPosition, 545f, yPosition, paint)
            yPosition += 20f
            
            // --- DRAW MINI CHART (LAST 30 DAYS OF REGISTRATION) ---
            canvas.drawText("GRÁFICO DE VARIAÇÃO (ÚLTIMOS 30 DIAS DE REGISTRO):", 50f, yPosition, titlePaint)
            
            val chartTop = yPosition + 15f
            val chartBottom = chartTop + 90f
            val chartHeight = 90f
            val chartLeft = 70f
            val chartRight = 525f
            val chartWidth = 455f
            
            val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000L)
            var chartLogs = logs.filter { it.timestamp >= thirtyDaysAgo }.sortedBy { it.timestamp }
            if (chartLogs.isEmpty() && logs.isNotEmpty()) {
                chartLogs = logs.sortedBy { it.timestamp }.takeLast(10)
            }
            
            // Draw chart background
            val bgPaint = android.graphics.Paint().apply {
                style = android.graphics.Paint.Style.FILL
                color = android.graphics.Color.parseColor("#FAFAFA")
                isAntiAlias = true
            }
            canvas.drawRect(chartLeft, chartTop, chartRight, chartBottom, bgPaint)
            
            val borderPaint = android.graphics.Paint().apply {
                style = android.graphics.Paint.Style.STROKE
                color = android.graphics.Color.parseColor("#E0E0E0")
                strokeWidth = 1f
                isAntiAlias = true
            }
            canvas.drawRect(chartLeft, chartTop, chartRight, chartBottom, borderPaint)
            
            // Draw grid lines
            val gridPaint = android.graphics.Paint().apply {
                style = android.graphics.Paint.Style.STROKE
                color = android.graphics.Color.parseColor("#EEEEEE")
                strokeWidth = 1f
                isAntiAlias = true
            }
            canvas.drawLine(chartLeft, chartTop + chartHeight * 0.25f, chartRight, chartTop + chartHeight * 0.25f, gridPaint)
            canvas.drawLine(chartLeft, chartTop + chartHeight * 0.5f, chartRight, chartTop + chartHeight * 0.5f, gridPaint)
            canvas.drawLine(chartLeft, chartTop + chartHeight * 0.75f, chartRight, chartTop + chartHeight * 0.75f, gridPaint)
            
            val labelPaint = android.graphics.Paint().apply {
                textSize = 8f
                color = android.graphics.Color.GRAY
                isAntiAlias = true
            }
            
            // Set paints for curves
            val sysPointPaint = android.graphics.Paint().apply {
                style = android.graphics.Paint.Style.FILL
                color = android.graphics.Color.parseColor("#D32F2F")
                isAntiAlias = true
            }
            val diaPointPaint = android.graphics.Paint().apply {
                style = android.graphics.Paint.Style.FILL
                color = android.graphics.Color.parseColor("#1976D2")
                isAntiAlias = true
            }
            
            if (chartLogs.isEmpty()) {
                val noDataPaint = android.graphics.Paint().apply {
                    textSize = 10f
                    color = android.graphics.Color.GRAY
                    isAntiAlias = true
                }
                canvas.drawText("Sem dados de registro suficientes nos últimos 30 dias.", 180f, chartTop + 50f, noDataPaint)
            } else {
                val rawMaxSys = chartLogs.maxOf { it.systole.toFloat() }
                val rawMinDia = chartLogs.minOf { it.diastole.toFloat() }
                var maxVal = maxOf(160f, rawMaxSys + 25f)
                var minVal = minOf(40f, rawMinDia - 15f)
                if (maxVal == minVal) {
                    maxVal += 20f
                    minVal -= 20f
                }
                
                // Draw Y labels
                canvas.drawText("${maxVal.toInt()} mmHg", 10f, chartTop + 6f, labelPaint)
                canvas.drawText("${((maxVal + minVal)/2).toInt()}", 10f, chartTop + chartHeight * 0.5f + 3f, labelPaint)
                canvas.drawText("${minVal.toInt()}", 10f, chartBottom - 2f, labelPaint)
                
                // Draw X labels
                val dayFormat = SimpleDateFormat("dd/MM", Locale.getDefault())
                canvas.drawText(dayFormat.format(Date(chartLogs.first().timestamp)), chartLeft, chartBottom + 12f, labelPaint)
                if (chartLogs.size > 1) {
                    canvas.drawText(dayFormat.format(Date(chartLogs.last().timestamp)), chartRight - 25f, chartBottom + 12f, labelPaint)
                }
                
                val sysLinePaint = android.graphics.Paint().apply {
                    style = android.graphics.Paint.Style.STROKE
                    color = android.graphics.Color.parseColor("#E53935")
                    strokeWidth = 2f
                    isAntiAlias = true
                }
                val diaLinePaint = android.graphics.Paint().apply {
                    style = android.graphics.Paint.Style.STROKE
                    color = android.graphics.Color.parseColor("#1E88E5")
                    strokeWidth = 2f
                    isAntiAlias = true
                }
                
                // Draw lines and dots
                for (i in chartLogs.indices) {
                    val log = chartLogs[i]
                    val x = if (chartLogs.size > 1) {
                        chartLeft + (i.toFloat() / (chartLogs.size - 1)) * chartWidth
                    } else {
                        (chartLeft + chartRight) / 2f
                    }
                    val ySys = chartTop + chartHeight - ((log.systole - minVal) / (maxVal - minVal) * chartHeight)
                    val yDia = chartTop + chartHeight - ((log.diastole - minVal) / (maxVal - minVal) * chartHeight)
                    
                    canvas.drawCircle(x, ySys, 3f, sysPointPaint)
                    canvas.drawCircle(x, yDia, 3f, diaPointPaint)
                    
                    if (i > 0) {
                        val prevLog = chartLogs[i - 1]
                        val prevX = chartLeft + ((i - 1).toFloat() / (chartLogs.size - 1)) * chartWidth
                        val prevYSys = chartTop + chartHeight - ((prevLog.systole - minVal) / (maxVal - minVal) * chartHeight)
                        val prevYDia = chartTop + chartHeight - ((prevLog.diastole - minVal) / (maxVal - minVal) * chartHeight)
                        
                        canvas.drawLine(prevX, prevYSys, x, ySys, sysLinePaint)
                        canvas.drawLine(prevX, prevYDia, x, yDia, diaLinePaint)
                    }
                }
                
                // Draw legend
                val legendPaint = android.graphics.Paint().apply {
                    textSize = 8f
                    color = android.graphics.Color.parseColor("#333333")
                    isAntiAlias = true
                }
                canvas.drawCircle(390f, yPosition + 2f, 3f, sysPointPaint)
                canvas.drawText("Sistólica", 397f, yPosition + 5f, legendPaint)
                canvas.drawCircle(460f, yPosition + 2f, 3f, diaPointPaint)
                canvas.drawText("Diastólica", 467f, yPosition + 5f, legendPaint)
            }
            
            yPosition += 130f
            
            paint.color = android.graphics.Color.LTGRAY
            canvas.drawLine(50f, yPosition, 545f, yPosition, paint)
            yPosition += 25f
            
            canvas.drawText("HISTÓRICO DE MEDIÇÕES:", 50f, yPosition, titlePaint)
            yPosition += 25f
            
            val colDate = 50f
            val colPA = 175f
            val colStatus = 270f
            val colPulse = 360f
            val colNotes = 450f
            
            paint.color = android.graphics.Color.parseColor("#ECEFF1")
            canvas.drawRect(50f, yPosition - 15f, 545f, yPosition + 10f, paint)
            
            val thPaint = android.graphics.Paint(textPaint).apply { isFakeBoldText = true }
            canvas.drawText("Data / Hora", colDate, yPosition, thPaint)
            canvas.drawText("Pressão (mmHg)", colPA, yPosition, thPaint)
            canvas.drawText("Classificação", colStatus, yPosition, thPaint)
            canvas.drawText("Pulso (BPM)", colPulse, yPosition, thPaint)
            canvas.drawText("Observações", colNotes, yPosition, thPaint)
            yPosition += 25f
            
            val statusPaint = android.graphics.Paint(textPaint).apply { isFakeBoldText = true }
            
            if (logs.isEmpty()) {
                canvas.drawText("Nenhuma medição registrada.", 50f, yPosition, textPaint)
            } else {
                for (log in logs) {
                    if (yPosition > 800f) {
                        break
                    }
                    
                    canvas.drawText(dateFormat.format(Date(log.timestamp)), colDate, yPosition, textPaint)
                    canvas.drawText("${log.systole} x ${log.diastole}", colPA, yPosition, textPaint)
                    
                    // Classification
                    val isHigh = log.systole >= 140 || log.diastole >= 90
                    val isLow = log.systole < 90 || log.diastole < 60
                    val (statusText, statusColor) = when {
                        isHigh -> Pair("ALTA", "#E53935")
                        isLow -> Pair("BAIXA", "#1E88E5")
                        else -> Pair("NORMAL", "#2E7D32")
                    }
                    statusPaint.color = android.graphics.Color.parseColor(statusColor)
                    canvas.drawText(statusText, colStatus, yPosition, statusPaint)
                    
                    canvas.drawText(log.pulse?.toString() ?: "-", colPulse, yPosition, textPaint)
                    
                    var noteStr = log.notes
                    if (noteStr.length > 15) {
                        noteStr = noteStr.substring(0, 12) + "..."
                    }
                    canvas.drawText(noteStr, colNotes, yPosition, textPaint)
                    
                    paint.color = android.graphics.Color.parseColor("#EEEEEE")
                    canvas.drawLine(50f, yPosition + 5f, 545f, yPosition + 5f, paint)
                    
                    yPosition += 25f
                }
            }
            
            yPosition = 810f
            paint.color = android.graphics.Color.LTGRAY
            canvas.drawLine(50f, yPosition, 545f, yPosition, paint)
            yPosition += 15f
            val footerPaint = android.graphics.Paint(textPaint).apply { textSize = 10f; color = android.graphics.Color.GRAY }
            canvas.drawText("Gerado por Hora do Remédio - Controle de Saúde Integrado", 50f, yPosition, footerPaint)
            
            pdfDocument.finishPage(page)
            
            val fileOutputStream = java.io.FileOutputStream(pdfFile)
            pdfDocument.writeTo(fileOutputStream)
            pdfDocument.close()
            fileOutputStream.close()
            
            return pdfFile
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun generateGlucosePdf(): java.io.File? {
        val context = getApplication<Application>()
        val logs = allGlucoseLogs.value
        val pdfFolder = java.io.File(context.cacheDir, "reports")
        if (!pdfFolder.exists()) {
            pdfFolder.mkdirs()
        }
        val pdfFile = java.io.File(pdfFolder, "relatorio_glicemia.pdf")
        
        try {
            val pdfDocument = android.graphics.pdf.PdfDocument()
            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas
            
            val paint = android.graphics.Paint()
            val textPaint = android.graphics.Paint().apply {
                textSize = 12f
                color = android.graphics.Color.BLACK
                isAntiAlias = true
            }
            val headerPaint = android.graphics.Paint().apply {
                textSize = 18f
                color = android.graphics.Color.parseColor("#121212")
                isFakeBoldText = true
                isAntiAlias = true
            }
            val titlePaint = android.graphics.Paint().apply {
                textSize = 14f
                color = android.graphics.Color.parseColor("#27ae60")
                isFakeBoldText = true
                isAntiAlias = true
            }
            
            var yPosition = 50f
            
            canvas.drawText("RELATÓRIO DE GLICEMIA (GLICOSE)", 50f, yPosition, headerPaint)
            yPosition += 30f
            
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            canvas.drawText("Paciente: ${_profileName.value}", 50f, yPosition, textPaint)
            yPosition += 20f
            canvas.drawText("Data de Emissão: ${dateFormat.format(Date())}", 50f, yPosition, textPaint)
            yPosition += 25f
            
            paint.color = android.graphics.Color.LTGRAY
            canvas.drawLine(50f, yPosition, 545f, yPosition, paint)
            yPosition += 20f
            
            // --- DRAW MINI CHART (LAST 30 DAYS OF REGISTRATION) ---
            canvas.drawText("GRÁFICO DE VARIAÇÃO (ÚLTIMOS 30 DIAS DE REGISTRO):", 50f, yPosition, titlePaint)
            
            val chartTop = yPosition + 15f
            val chartBottom = chartTop + 90f
            val chartHeight = 90f
            val chartLeft = 70f
            val chartRight = 525f
            val chartWidth = 455f
            
            val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000L)
            var chartLogs = logs.filter { it.timestamp >= thirtyDaysAgo }.sortedBy { it.timestamp }
            if (chartLogs.isEmpty() && logs.isNotEmpty()) {
                chartLogs = logs.sortedBy { it.timestamp }.takeLast(10)
            }
            
            // Draw chart background
            val bgPaint = android.graphics.Paint().apply {
                style = android.graphics.Paint.Style.FILL
                color = android.graphics.Color.parseColor("#FAFAFA")
                isAntiAlias = true
            }
            canvas.drawRect(chartLeft, chartTop, chartRight, chartBottom, bgPaint)
            
            val borderPaint = android.graphics.Paint().apply {
                style = android.graphics.Paint.Style.STROKE
                color = android.graphics.Color.parseColor("#E0E0E0")
                strokeWidth = 1f
                isAntiAlias = true
            }
            canvas.drawRect(chartLeft, chartTop, chartRight, chartBottom, borderPaint)
            
            // Draw grid lines
            val gridPaint = android.graphics.Paint().apply {
                style = android.graphics.Paint.Style.STROKE
                color = android.graphics.Color.parseColor("#EEEEEE")
                strokeWidth = 1f
                isAntiAlias = true
            }
            canvas.drawLine(chartLeft, chartTop + chartHeight * 0.25f, chartRight, chartTop + chartHeight * 0.25f, gridPaint)
            canvas.drawLine(chartLeft, chartTop + chartHeight * 0.5f, chartRight, chartTop + chartHeight * 0.5f, gridPaint)
            canvas.drawLine(chartLeft, chartTop + chartHeight * 0.75f, chartRight, chartTop + chartHeight * 0.75f, gridPaint)
            
            val labelPaint = android.graphics.Paint().apply {
                textSize = 8f
                color = android.graphics.Color.GRAY
                isAntiAlias = true
            }
            
            val glucPointPaint = android.graphics.Paint().apply {
                style = android.graphics.Paint.Style.FILL
                color = android.graphics.Color.parseColor("#1B5E20")
                isAntiAlias = true
            }
            
            if (chartLogs.isEmpty()) {
                val noDataPaint = android.graphics.Paint().apply {
                    textSize = 10f
                    color = android.graphics.Color.GRAY
                    isAntiAlias = true
                }
                canvas.drawText("Sem dados de registro suficientes nos últimos 30 dias.", 180f, chartTop + 50f, noDataPaint)
            } else {
                val rawMaxGluc = chartLogs.maxOf { it.glucoseValue.toFloat() }
                val rawMinGluc = chartLogs.minOf { it.glucoseValue.toFloat() }
                var maxVal = maxOf(180f, rawMaxGluc + 20f)
                var minVal = minOf(50f, rawMinGluc - 10f)
                if (maxVal == minVal) {
                    maxVal += 20f
                    minVal -= 20f
                }
                
                // Draw Y labels
                canvas.drawText("${maxVal.toInt()} mg/dL", 10f, chartTop + 6f, labelPaint)
                canvas.drawText("${((maxVal + minVal)/2).toInt()}", 10f, chartTop + chartHeight * 0.5f + 3f, labelPaint)
                canvas.drawText("${minVal.toInt()}", 10f, chartBottom - 2f, labelPaint)
                
                // Draw X labels
                val dayFormat = SimpleDateFormat("dd/MM", Locale.getDefault())
                canvas.drawText(dayFormat.format(Date(chartLogs.first().timestamp)), chartLeft, chartBottom + 12f, labelPaint)
                if (chartLogs.size > 1) {
                    canvas.drawText(dayFormat.format(Date(chartLogs.last().timestamp)), chartRight - 25f, chartBottom + 12f, labelPaint)
                }
                
                val glucLinePaint = android.graphics.Paint().apply {
                    style = android.graphics.Paint.Style.STROKE
                    color = android.graphics.Color.parseColor("#2E7D32")
                    strokeWidth = 2f
                    isAntiAlias = true
                }
                
                // Draw lines and dots
                for (i in chartLogs.indices) {
                    val log = chartLogs[i]
                    val x = if (chartLogs.size > 1) {
                        chartLeft + (i.toFloat() / (chartLogs.size - 1)) * chartWidth
                    } else {
                        (chartLeft + chartRight) / 2f
                    }
                    val yGluc = chartTop + chartHeight - ((log.glucoseValue.toFloat() - minVal) / (maxVal - minVal) * chartHeight)
                    
                    canvas.drawCircle(x, yGluc, 3f, glucPointPaint)
                    
                    if (i > 0) {
                        val prevLog = chartLogs[i - 1]
                        val prevX = chartLeft + ((i - 1).toFloat() / (chartLogs.size - 1)) * chartWidth
                        val prevYGluc = chartTop + chartHeight - ((prevLog.glucoseValue.toFloat() - minVal) / (maxVal - minVal) * chartHeight)
                        
                        canvas.drawLine(prevX, prevYGluc, x, yGluc, glucLinePaint)
                    }
                }
                
                // Draw legend
                val legendPaint = android.graphics.Paint().apply {
                    textSize = 8f
                    color = android.graphics.Color.parseColor("#333333")
                    isAntiAlias = true
                }
                canvas.drawCircle(440f, yPosition + 2f, 3f, glucPointPaint)
                canvas.drawText("Glicemia", 447f, yPosition + 5f, legendPaint)
            }
            
            yPosition += 130f
            
            paint.color = android.graphics.Color.LTGRAY
            canvas.drawLine(50f, yPosition, 545f, yPosition, paint)
            yPosition += 25f
            
            canvas.drawText("HISTÓRICO DE MEDIÇÕES:", 50f, yPosition, titlePaint)
            yPosition += 25f
            
            val colDate = 50f
            val colGlucose = 175f
            val colStatus = 270f
            val colState = 360f
            val colNotes = 450f
            
            paint.color = android.graphics.Color.parseColor("#E8F5E9")
            canvas.drawRect(50f, yPosition - 15f, 545f, yPosition + 10f, paint)
            
            val thPaint = android.graphics.Paint(textPaint).apply { isFakeBoldText = true }
            canvas.drawText("Data / Hora", colDate, yPosition, thPaint)
            canvas.drawText("Glicose (mg/dL)", colGlucose, yPosition, thPaint)
            canvas.drawText("Classificação", colStatus, yPosition, thPaint)
            canvas.drawText("Estado / Momento", colState, yPosition, thPaint)
            canvas.drawText("Observações", colNotes, yPosition, thPaint)
            yPosition += 25f
            
            val statusPaint = android.graphics.Paint(textPaint).apply { isFakeBoldText = true }
            
            if (logs.isEmpty()) {
                canvas.drawText("Nenhuma medição registrada.", 50f, yPosition, textPaint)
            } else {
                for (log in logs) {
                    if (yPosition > 800f) {
                        break
                    }
                    
                    canvas.drawText(dateFormat.format(Date(log.timestamp)), colDate, yPosition, textPaint)
                    canvas.drawText("${log.glucoseValue.toInt()} mg/dL", colGlucose, yPosition, textPaint)
                    
                    // Classification
                    val isFasting = log.state.contains("Jejum", ignoreCase = true)
                    val isHigh = if (isFasting) log.glucoseValue >= 100.0 else log.glucoseValue >= 140.0
                    val isHypo = log.glucoseValue < 70.0
                    val (statusText, statusColor) = when {
                        isHypo -> Pair("HIPOGLICEMIA", "#E53935")
                        isHigh -> Pair("ALTA", "#E53935")
                        else -> Pair("NORMAL", "#2E7D32")
                    }
                    statusPaint.color = android.graphics.Color.parseColor(statusColor)
                    canvas.drawText(statusText, colStatus, yPosition, statusPaint)
                    
                    canvas.drawText(log.state, colState, yPosition, textPaint)
                    
                    var noteStr = log.notes
                    if (noteStr.length > 15) {
                        noteStr = noteStr.substring(0, 12) + "..."
                    }
                    canvas.drawText(noteStr, colNotes, yPosition, textPaint)
                    
                    paint.color = android.graphics.Color.parseColor("#EEEEEE")
                    canvas.drawLine(50f, yPosition + 5f, 545f, yPosition + 5f, paint)
                    
                    yPosition += 25f
                }
            }
            
            yPosition = 810f
            paint.color = android.graphics.Color.LTGRAY
            canvas.drawLine(50f, yPosition, 545f, yPosition, paint)
            yPosition += 15f
            val footerPaint = android.graphics.Paint(textPaint).apply { textSize = 10f; color = android.graphics.Color.GRAY }
            canvas.drawText("Gerado por Hora do Remédio - Controle de Saúde Integrado", 50f, yPosition, footerPaint)
            
            pdfDocument.finishPage(page)
            
            val fileOutputStream = java.io.FileOutputStream(pdfFile)
            pdfDocument.writeTo(fileOutputStream)
            pdfDocument.close()
            fileOutputStream.close()
            
            return pdfFile
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun generateMedicationPdf(): java.io.File? {
        val context = getApplication<Application>()
        val medications = allMedications.value
        val logs = allLogs.value
        val pdfFolder = java.io.File(context.cacheDir, "reports")
        if (!pdfFolder.exists()) {
            pdfFolder.mkdirs()
        }
        val pdfFile = java.io.File(pdfFolder, "relatorio_controle_medicamentos.pdf")
        
        try {
            val pdfDocument = android.graphics.pdf.PdfDocument()
            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas
            
            val paint = android.graphics.Paint()
            val textPaint = android.graphics.Paint().apply {
                textSize = 10f
                color = android.graphics.Color.BLACK
                isAntiAlias = true
            }
            val headerPaint = android.graphics.Paint().apply {
                textSize = 18f
                color = android.graphics.Color.parseColor("#1A237E")
                isFakeBoldText = true
                isAntiAlias = true
            }
            val titlePaint = android.graphics.Paint().apply {
                textSize = 12f
                color = android.graphics.Color.parseColor("#0C2340")
                isFakeBoldText = true
                isAntiAlias = true
            }
            
            var yPosition = 50f
            
            canvas.drawText("RELATÓRIO DE ACOMPANHAMENTO MÉDICO", 50f, yPosition, headerPaint)
            yPosition += 25f
            
            canvas.drawText("Hora do Remédio - Controle de Saúde Integrado", 50f, yPosition, textPaint)
            yPosition += 20f
            
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            
            // Draw Patient Profile Card
            paint.color = android.graphics.Color.parseColor("#F5F5F5")
            canvas.drawRect(50f, yPosition, 545f, yPosition + 55f, paint)
            
            val boldPaint = android.graphics.Paint(textPaint).apply { isFakeBoldText = true }
            canvas.drawText("DADOS DO PACIENTE:", 60f, yPosition + 18f, boldPaint)
            canvas.drawText("Nome: ${_profileName.value}", 60f, yPosition + 32f, textPaint)
            
            val weightStr = if (_profileWeight.value.isNotEmpty()) "${_profileWeight.value} kg" else "Não informado"
            val heightStr = if (_profileHeight.value.isNotEmpty()) "${_profileHeight.value} cm" else "Não informado"
            canvas.drawText("Peso: $weightStr", 300f, yPosition + 32f, textPaint)
            canvas.drawText("Altura: $heightStr", 420f, yPosition + 32f, textPaint)
            canvas.drawText("Data de Emissão: ${dateFormat.format(Date())}", 60f, yPosition + 46f, textPaint)
            
            yPosition += 80f
            
            // Section 1: Registered Medications
            canvas.drawText("1. MEDICAMENTOS CADASTRADOS", 50f, yPosition, titlePaint)
            yPosition += 15f
            paint.color = android.graphics.Color.LTGRAY
            canvas.drawLine(50f, yPosition, 545f, yPosition, paint)
            yPosition += 20f
            
            if (medications.isEmpty()) {
                canvas.drawText("- Nenhum medicamento cadastrado no momento.", 50f, yPosition, textPaint)
                yPosition += 20f
            } else {
                for (med in medications) {
                    if (yPosition > 780f) break
                    
                    val continuousStr = if (med.isContinuous) " [Contínuo]" else ""
                    val controlStr = if (med.controlType != "Nenhum") " (${med.controlType})" else ""
                    canvas.drawText("• ${med.name} (${med.dosage})$continuousStr$controlStr", 50f, yPosition, boldPaint)
                    yPosition += 15f
                    
                    val stockLowText = if (med.isStockLow()) " (⚠️ ESTOQUE BAIXO!)" else ""
                    val medDetails = "Frequência: A cada ${med.frequencyHours}h | Próxima Dose: ${dateFormat.format(Date(med.nextDoseTimestamp))}"
                    canvas.drawText(medDetails, 60f, yPosition, textPaint)
                    yPosition += 15f
                    
                    val stockDetails = "Estoque: ${med.stockQuantity} ${med.stockUnit} restantes$stockLowText"
                    canvas.drawText(stockDetails, 60f, yPosition, textPaint)
                    yPosition += 15f
                    
                    if (med.notes.isNotEmpty()) {
                        canvas.drawText("Obs: ${med.notes}", 60f, yPosition, textPaint)
                        yPosition += 15f
                    }
                    yPosition += 5f
                }
            }
            
            yPosition += 15f
            if (yPosition < 780f) {
                // Section 2: Historic dose consumption logs
                canvas.drawText("2. HISTÓRICO DE DOSES CONFIRMADAS", 50f, yPosition, titlePaint)
                yPosition += 15f
                canvas.drawLine(50f, yPosition, 545f, yPosition, paint)
                yPosition += 20f
                
                if (logs.isEmpty()) {
                    canvas.drawText("- Nenhum histórico de doses registrado.", 50f, yPosition, textPaint)
                    yPosition += 20f
                } else {
                    val colDate = 50f
                    val colName = 180f
                    val colDose = 350f
                    val colStatus = 460f
                    
                    // Table Header Background
                    paint.color = android.graphics.Color.parseColor("#EAF2F8")
                    canvas.drawRect(50f, yPosition - 15f, 545f, yPosition + 5f, paint)
                    
                    canvas.drawText("Data/Hora", colDate, yPosition - 2f, boldPaint)
                    canvas.drawText("Medicamento", colName, yPosition - 2f, boldPaint)
                    canvas.drawText("Dose", colDose, yPosition - 2f, boldPaint)
                    canvas.drawText("Status", colStatus, yPosition - 2f, boldPaint)
                    
                    yPosition += 15f
                    
                    for (log in logs) {
                        if (yPosition > 800f) {
                            break
                        }
                        
                        canvas.drawText(dateFormat.format(Date(log.takenTimestamp)), colDate, yPosition, textPaint)
                        
                        var medNameStr = log.medicationName
                        if (medNameStr.length > 25) {
                            medNameStr = medNameStr.substring(0, 22) + "..."
                        }
                        canvas.drawText(medNameStr, colName, yPosition, textPaint)
                        canvas.drawText(log.dosageTaken, colDose, yPosition, textPaint)
                        canvas.drawText(log.status, colStatus, yPosition, textPaint)
                        
                        paint.color = android.graphics.Color.parseColor("#EEEEEE")
                        canvas.drawLine(50f, yPosition + 3f, 545f, yPosition + 3f, paint)
                        
                        yPosition += 20f
                    }
                }
            }
            
            yPosition = 810f
            paint.color = android.graphics.Color.LTGRAY
            canvas.drawLine(50f, yPosition, 545f, yPosition, paint)
            yPosition += 15f
            val footerPaint = android.graphics.Paint(textPaint).apply { textSize = 8f; color = android.graphics.Color.GRAY }
            canvas.drawText("Gerado automaticamente pelo aplicativo Hora do Remédio", 50f, yPosition, footerPaint)
            
            pdfDocument.finishPage(page)
            
            val fileOutputStream = java.io.FileOutputStream(pdfFile)
            pdfDocument.writeTo(fileOutputStream)
            pdfDocument.close()
            fileOutputStream.close()
            
            return pdfFile
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    // --- GOOGLE DRIVE BACKUP & RESTORE INTEGRATION ---
    val syncManager = GoogleDriveSyncManager(getApplication())

    private val _isGoogleConnected = MutableStateFlow(syncManager.isConnected)
    val isGoogleConnected: StateFlow<Boolean> = _isGoogleConnected.asStateFlow()

    private val _googleAccountName = MutableStateFlow(syncManager.userName)
    val googleAccountName: StateFlow<String?> = _googleAccountName.asStateFlow()

    private val _googleAccountEmail = MutableStateFlow(syncManager.userEmail)
    val googleAccountEmail: StateFlow<String?> = _googleAccountEmail.asStateFlow()

    private val _googleAccountPictureUrl = MutableStateFlow(syncManager.userPictureUrl)
    val googleAccountPictureUrl: StateFlow<String?> = _googleAccountPictureUrl.asStateFlow()

    private val _lastSyncTime = MutableStateFlow(syncManager.lastSyncTimestamp)
    val lastSyncTime: StateFlow<Long> = _lastSyncTime.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncStatusMessage = MutableStateFlow<String?>(null)
    val syncStatusMessage: StateFlow<String?> = _syncStatusMessage.asStateFlow()

    private val _pendingRestoreData = MutableStateFlow<ParsedBackup?>(null)
    val pendingRestoreData: StateFlow<ParsedBackup?> = _pendingRestoreData.asStateFlow()

    fun handleGoogleSignInSuccess(token: String, expiresInSec: Long) {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncStatusMessage.value = "Conectando ao Google Drive..."
            syncManager.saveToken(token, expiresInSec)
            _isGoogleConnected.value = true
            
            // Fetch User info (name, email & picture)
            val successUserInfo = syncManager.fetchUserInfo()
            if (successUserInfo) {
                _googleAccountName.value = syncManager.userName
                _googleAccountEmail.value = syncManager.userEmail
                _googleAccountPictureUrl.value = syncManager.userPictureUrl
            }
            
            // Check if there is an existing backup on Google Drive
            _syncStatusMessage.value = "Verificando se há backups na nuvem..."
            val backupFileId = syncManager.findBackupFileId()
            if (backupFileId != null) {
                _syncStatusMessage.value = "Backup encontrado! Baixando informações..."
                val jsonContent = syncManager.downloadBackup(backupFileId)
                if (jsonContent != null) {
                    try {
                        val parsed = syncManager.parseJson(jsonContent)
                        // Hold this data and trigger the prompt on the UI
                        _pendingRestoreData.value = parsed
                        _syncStatusMessage.value = "Selecione se deseja usar o backup da nuvem ou manter seus dados locais."
                    } catch (e: Exception) {
                        Log.e("MedicationViewModel", "Error parsing backup JSON", e)
                        _syncStatusMessage.value = "Erro ao processar arquivo de backup. Criando novo backup..."
                        performManualBackup()
                    }
                } else {
                    _syncStatusMessage.value = "Falha ao baixar backup. Criando novo backup..."
                    performManualBackup()
                }
            } else {
                _syncStatusMessage.value = "Nenhum backup existente na nuvem. Criando inicial..."
                performManualBackup()
            }
            _isSyncing.value = false
        }
    }

    fun performManualBackup() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncStatusMessage.value = "Gerando dados de backup..."
            
            val medications = allMedications.value
            val doseLogs = allLogs.value
            val bpLogs = allBloodPressureLogs.value
            val glucoseLogs = allGlucoseLogs.value
            
            val json = syncManager.generateJson(
                profileName = _profileName.value,
                profileWeight = _profileWeight.value,
                profileHeight = _profileHeight.value,
                medications = medications,
                doseLogs = doseLogs,
                bloodPressureLogs = bpLogs,
                glucoseLogs = glucoseLogs
            )
            
            _syncStatusMessage.value = "Enviando para o Google Drive..."
            val success = syncManager.uploadBackup(json)
            if (success) {
                _lastSyncTime.value = syncManager.lastSyncTimestamp
                _syncStatusMessage.value = "Backup realizado com sucesso!"
            } else {
                _syncStatusMessage.value = "Erro ao enviar backup para o Google Drive."
            }
            _isSyncing.value = false
        }
    }

    fun restoreCloudBackup() {
        val backup = _pendingRestoreData.value ?: return
        viewModelScope.launch {
            _isSyncing.value = true
            _syncStatusMessage.value = "Restaurando dados do backup..."
            
            try {
                // 1. Clear database tables
                repository.clearAllMedications()
                repository.clearAllDoseLogs()
                repository.clearAllBloodPressureLogs()
                repository.clearAllGlucoseLogs()
                
                // 2. Clear then override profile fields
                updateProfileName(backup.profileName)
                updateProfileWeight(backup.profileWeight)
                updateProfileHeight(backup.profileHeight)
                
                // 3. Insert medications (preserving active values)
                for (m in backup.medications) {
                    val longId = repository.insertMedication(m)
                    val insertedMed = m.copy(id = longId.toInt())
                    scheduleAlarmForMedication(insertedMed)
                }
                
                // 4. Insert dose logs, bp logs, glucose logs directly if possible
                for (l in backup.doseLogs) {
                    repository.insertDoseLog(l)
                }
                
                // 5. Insert BP logs
                for (bp in backup.bloodPressureLogs) {
                    repository.insertBloodPressureLog(bp)
                }
                
                // 6. Insert Glucose logs
                for (g in backup.glucoseLogs) {
                    repository.insertGlucoseLog(g)
                }
                
                _lastSyncTime.value = syncManager.lastSyncTimestamp
                _syncStatusMessage.value = "Restauração concluída de forma bem sucedida!"
            } catch (e: Exception) {
                Log.e("MedicationViewModel", "Error restoring backup", e)
                _syncStatusMessage.value = "Erro durante a restauração do backup na nuvem."
            } finally {
                _pendingRestoreData.value = null
                _isSyncing.value = false
            }
        }
    }

    fun keepLocalDataAndUpload() {
        _pendingRestoreData.value = null
        performManualBackup()
    }

    fun disconnectGoogle() {
        syncManager.disconnect()
        _isGoogleConnected.value = false
        _googleAccountName.value = null
        _googleAccountEmail.value = null
        _googleAccountPictureUrl.value = null
        _pendingRestoreData.value = null
        _syncStatusMessage.value = "Sessão do Google desconectada."
    }

    fun clearSyncStatusMessage() {
        _syncStatusMessage.value = null
    }

    fun cancelPendingRestore() {
        _pendingRestoreData.value = null
    }

    // Factory pattern setup VM
    companion object {
        fun Factory(application: Application): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val database = AppDatabase.getDatabase(application)
                val repository = MedicationRepository(
                    database.medicationDao(),
                    database.doseLogDao(),
                    database.bloodPressureLogDao(),
                    database.glucoseLogDao()
                )
                return MedicationViewModel(application, repository) as T
            }
        }
    }
}
