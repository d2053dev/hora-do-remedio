package com.example.receiver

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.local.AppDatabase
import com.example.data.local.Medication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(context)
                    val medDao = db.medicationDao()
                    val medications = medDao.getAllMedicationsDirect()
                    for (med in medications) {
                        if (med.notificationEnabled) {
                            scheduleAlarmForMedicationDirect(context, med)
                        }
                    }
                    Log.d("AlarmReceiver", "Successfully rescheduled all alarms after boot.")
                } catch (e: Exception) {
                    Log.e("AlarmReceiver", "Error rescheduling alarms on boot", e)
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        val medId = intent.getIntExtra("medication_id", -1)
        val medName = intent.getStringExtra("medication_name") ?: "Medicamento"
        val medDosage = intent.getStringExtra("medication_dosage") ?: ""
        val medDosageQty = intent.getDoubleExtra("medication_dosage_qty", 1.0)
        val medStockUnit = intent.getStringExtra("medication_stock_unit") ?: "comprimido"

        if (medId == -1) return

        val title = "Hora do Remédio! ⏰"
        val message = "Está na hora de tomar: $medName ($medDosage). Dose sugerida: $medDosageQty $medStockUnit."

        val sharedPrefs = context.getSharedPreferences("patient_profile_prefs", Context.MODE_PRIVATE)
        val soundEnabled = sharedPrefs.getBoolean("sound_enabled", true)
        val vibrationEnabled = sharedPrefs.getBoolean("vibration_enabled", true)

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            medId,
            mainIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notificationBuilder = NotificationCompat.Builder(context, "med_reminder_channel_id")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (soundEnabled) {
            notificationBuilder.setDefaults(NotificationCompat.DEFAULT_SOUND)
        }
        if (vibrationEnabled) {
            notificationBuilder.setDefaults(NotificationCompat.DEFAULT_VIBRATE)
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(medId, notificationBuilder.build())

        // Reschedule next schedule alarm automatically if notificationEnabled
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val medDao = db.medicationDao()
                val medication = medDao.getMedicationById(medId)
                if (medication != null && medication.notificationEnabled) {
                    // Update nextDoseTimestamp by adding the interval
                    val nextTime = System.currentTimeMillis() + (medication.frequencyHours.toLong() * 60 * 60 * 1000)
                    val updatedMed = medication.copy(nextDoseTimestamp = nextTime)
                    medDao.updateMedication(updatedMed)
                    
                    // Alarm for the next period
                    scheduleAlarmForMedicationDirect(context, updatedMed)
                }
            } catch (e: Exception) {
                Log.e("AlarmReceiver", "Error auto-advancing next medication alarm", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun scheduleAlarmForMedicationDirect(context: Context, medication: Medication) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
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
}
