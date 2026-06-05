package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.local.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class GoogleDriveSyncManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("google_drive_sync_prefs", Context.MODE_PRIVATE)
    private val client = OkHttpClient()

    companion object {
        const val CLIENT_ID = "1084226176503-6ufrq8lcrjptno8t4s06l78r8v02u8i6.apps.googleusercontent.com"
        const val REDIRECT_URI = "http://localhost/"
        const val SCOPE = "https://www.googleapis.com/auth/drive.file"
        const val OAUTH_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth?client_id=$CLIENT_ID&redirect_uri=$REDIRECT_URI&response_type=token&scope=$SCOPE&prompt=consent"
        const val BACKUP_FILENAME = "backup_horadoremedio.json"
    }

    var accessToken: String?
        get() = prefs.getString("access_token", null)
        private set(value) = prefs.edit().putString("access_token", value).apply()

    var userEmail: String?
        get() = prefs.getString("user_email", null)
        private set(value) = prefs.edit().putString("user_email", value).apply()

    var userName: String?
        get() = prefs.getString("user_name", null)
        private set(value) = prefs.edit().putString("user_name", value).apply()

    var lastSyncTimestamp: Long
        get() = prefs.getLong("last_sync_timestamp", 0L)
        private set(value) = prefs.edit().putLong("last_sync_timestamp", value).apply()

    val isConnected: Boolean
        get() = accessToken != null

    fun disconnect() {
        prefs.edit().clear().apply()
    }

    fun saveToken(token: String, expiresInSec: Long) {
        accessToken = token
        // Token typically expires in 1 hour; we can store the approximate expiration if needed.
        val expirationTime = System.currentTimeMillis() + (expiresInSec * 1000)
        prefs.edit().putLong("token_expiration", expirationTime).apply()
    }

    // Fetches the user info (name, email) from Google to provide a personalized connection status
    suspend fun fetchUserInfo(): Boolean = withContext(Dispatchers.IO) {
        val token = accessToken ?: return@withContext false
        val request = Request.Builder()
            .url("https://www.googleapis.com/oauth2/v3/userinfo")
            .header("Authorization", "Bearer $token")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: return@withContext false
                    val json = JSONObject(bodyStr)
                    userEmail = json.optString("email", "")
                    userName = json.optString("name", "Usuário Google")
                    return@withContext true
                }
            }
        } catch (e: Exception) {
            Log.e("GoogleDriveSync", "Error fetching user info", e)
        }
        return@withContext false
    }

    // Locates the backup JSON file in the user's Google Drive. 
    // Returns the fileId if found, null otherwise.
    suspend fun findBackupFileId(): String? = withContext(Dispatchers.IO) {
        val token = accessToken ?: return@withContext null
        val queryUrl = "https://www.googleapis.com/drive/v3/files?q=name='$BACKUP_FILENAME'+and+trashed=false"
        val request = Request.Builder()
            .url(queryUrl)
            .header("Authorization", "Bearer $token")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: return@withContext null
                    val json = JSONObject(bodyStr)
                    val filesArray = json.optJSONArray("files")
                    if (filesArray != null && filesArray.length() > 0) {
                        return@withContext filesArray.getJSONObject(0).optString("id", null)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("GoogleDriveSync", "Error searching backup file", e)
        }
        return@withContext null
    }

    // Downloads the backup file from Google Drive and returns its JSON content as String
    suspend fun downloadBackup(fileId: String): String? = withContext(Dispatchers.IO) {
        val token = accessToken ?: return@withContext null
        val request = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
            .header("Authorization", "Bearer $token")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    return@withContext response.body?.string()
                }
            }
        } catch (e: Exception) {
            Log.e("GoogleDriveSync", "Error downloading backup", e)
        }
        return@withContext null
    }

    // Uploads or overwrites the application's states into Google Drive.
    suspend fun uploadBackup(jsonContent: String): Boolean = withContext(Dispatchers.IO) {
        val token = accessToken ?: return@withContext false
        val fileId = findBackupFileId()

        if (fileId != null) {
            // Overwrite existing backup file: PATCH uploadType=media
            val request = Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media")
                .header("Authorization", "Bearer $token")
                .patch(jsonContent.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        lastSyncTimestamp = System.currentTimeMillis()
                        return@withContext true
                    }
                }
            } catch (e: Exception) {
                Log.e("GoogleDriveSync", "Error patches/updating file on Drive", e)
            }
        } else {
            // Create a new backup file: Multipart upload (metadata + media payload)
            val metadata = JSONObject()
            metadata.put("name", BACKUP_FILENAME)
            metadata.put("mimeType", "application/json")

            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addPart(
                    metadata.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                )
                .addPart(
                    jsonContent.toRequestBody("application/json; charset=utf-8".toMediaType())
                )
                .build()

            val request = Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
                .header("Authorization", "Bearer $token")
                .post(body)
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        lastSyncTimestamp = System.currentTimeMillis()
                        return@withContext true
                    }
                }
            } catch (e: Exception) {
                Log.e("GoogleDriveSync", "Error creating file on Drive", e)
            }
        }
        return@withContext false
    }

    // Helper serialization function
    fun generateJson(
        profileName: String,
        profileWeight: String,
        profileHeight: String,
        medications: List<Medication>,
        doseLogs: List<DoseLog>,
        bloodPressureLogs: List<BloodPressureLog>,
        glucoseLogs: List<GlucoseLog>
    ): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("timestamp", System.currentTimeMillis())

        val profile = JSONObject()
        profile.put("profileName", profileName)
        profile.put("profileWeight", profileWeight)
        profile.put("profileHeight", profileHeight)
        root.put("profile", profile)

        val medArray = JSONArray()
        for (m in medications) {
            val obj = JSONObject()
            obj.put("id", m.id)
            obj.put("name", m.name)
            obj.put("dosage", m.dosage)
            obj.put("stockQuantity", m.stockQuantity)
            obj.put("stockUnit", m.stockUnit)
            obj.put("dosageQuantity", m.dosageQuantity)
            obj.put("frequencyHours", m.frequencyHours)
            obj.put("firstDoseTimestamp", m.firstDoseTimestamp)
            obj.put("nextDoseTimestamp", m.nextDoseTimestamp)
            obj.put("notificationEnabled", m.notificationEnabled)
            obj.put("notes", m.notes)
            obj.put("isContinuous", m.isContinuous)
            obj.put("controlType", m.controlType)
            medArray.put(obj)
        }
        root.put("medications", medArray)

        val logArray = JSONArray()
        for (l in doseLogs) {
            val obj = JSONObject()
            obj.put("id", l.id)
            obj.put("medicationId", l.medicationId)
            obj.put("medicationName", l.medicationName)
            obj.put("takenTimestamp", l.takenTimestamp)
            obj.put("scheduledTimestamp", l.scheduledTimestamp)
            obj.put("dosageTaken", l.dosageTaken)
            obj.put("status", l.status)
            logArray.put(obj)
        }
        root.put("doseLogs", logArray)

        val bpArray = JSONArray()
        for (bp in bloodPressureLogs) {
            val obj = JSONObject()
            obj.put("id", bp.id)
            obj.put("timestamp", bp.timestamp)
            obj.put("systole", bp.systole)
            obj.put("diastole", bp.diastole)
            obj.put("pulse", bp.pulse ?: -1)
            obj.put("notes", bp.notes)
            bpArray.put(obj)
        }
        root.put("bloodPressureLogs", bpArray)

        val glArray = JSONArray()
        for (g in glucoseLogs) {
            val obj = JSONObject()
            obj.put("id", g.id)
            obj.put("timestamp", g.timestamp)
            obj.put("glucoseValue", g.glucoseValue)
            obj.put("state", g.state)
            obj.put("notes", g.notes)
            glArray.put(obj)
        }
        root.put("glucoseLogs", glArray)

        return root.toString()
    }

    // Helper deserializer function
    fun parseJson(jsonStr: String): ParsedBackup {
        val root = JSONObject(jsonStr)

        val profile = root.optJSONObject("profile")
        val profileName = profile?.optString("profileName", "Paciente Crônico") ?: "Paciente Crônico"
        val profileWeight = profile?.optString("profileWeight", "") ?: ""
        val profileHeight = profile?.optString("profileHeight", "") ?: ""

        val medications = mutableListOf<Medication>()
        val medArray = root.optJSONArray("medications")
        if (medArray != null) {
            for (i in 0 until medArray.length()) {
                val obj = medArray.getJSONObject(i)
                medications.add(
                    Medication(
                        id = obj.optInt("id", 0),
                        name = obj.optString("name", ""),
                        dosage = obj.optString("dosage", ""),
                        stockQuantity = obj.optDouble("stockQuantity", 0.0),
                        stockUnit = obj.optString("stockUnit", ""),
                        dosageQuantity = obj.optDouble("dosageQuantity", 0.0),
                        frequencyHours = obj.optInt("frequencyHours", 12),
                        firstDoseTimestamp = obj.optLong("firstDoseTimestamp", 0L),
                        nextDoseTimestamp = obj.optLong("nextDoseTimestamp", 0L),
                        notificationEnabled = obj.optBoolean("notificationEnabled", true),
                        notes = obj.optString("notes", ""),
                        isContinuous = obj.optBoolean("isContinuous", false),
                        controlType = obj.optString("controlType", "Nenhum")
                    )
                )
            }
        }

        val doseLogs = mutableListOf<DoseLog>()
        val logArray = root.optJSONArray("doseLogs")
        if (logArray != null) {
            for (i in 0 until logArray.length()) {
                val obj = logArray.getJSONObject(i)
                doseLogs.add(
                    DoseLog(
                        id = obj.optInt("id", 0),
                        medicationId = obj.optInt("medicationId", 0),
                        medicationName = obj.optString("medicationName", ""),
                        takenTimestamp = obj.optLong("takenTimestamp", 0L),
                        scheduledTimestamp = obj.optLong("scheduledTimestamp", 0L),
                        dosageTaken = obj.optString("dosageTaken", ""),
                        status = obj.optString("status", "")
                    )
                )
            }
        }

        val bloodPressureLogs = mutableListOf<BloodPressureLog>()
        val bpArray = root.optJSONArray("bloodPressureLogs")
        if (bpArray != null) {
            for (i in 0 until bpArray.length()) {
                val obj = bpArray.getJSONObject(i)
                val pulseVal = obj.optInt("pulse", -1)
                bloodPressureLogs.add(
                    BloodPressureLog(
                        id = obj.optInt("id", 0),
                        timestamp = obj.optLong("timestamp", 0L),
                        systole = obj.optInt("systole", 120),
                        diastole = obj.optInt("diastole", 80),
                        pulse = if (pulseVal == -1) null else pulseVal,
                        notes = obj.optString("notes", "")
                    )
                )
            }
        }

        val glucoseLogs = mutableListOf<GlucoseLog>()
        val glArray = root.optJSONArray("glucoseLogs")
        if (glArray != null) {
            for (i in 0 until glArray.length()) {
                val obj = glArray.getJSONObject(i)
                glucoseLogs.add(
                    GlucoseLog(
                        id = obj.optInt("id", 0),
                        timestamp = obj.optLong("timestamp", 0L),
                        glucoseValue = obj.optDouble("glucoseValue", 100.0),
                        state = obj.optString("state", ""),
                        notes = obj.optString("notes", "")
                    )
                )
            }
        }

        return ParsedBackup(
            profileName = profileName,
            profileWeight = profileWeight,
            profileHeight = profileHeight,
            medications = medications,
            doseLogs = doseLogs,
            bloodPressureLogs = bloodPressureLogs,
            glucoseLogs = glucoseLogs
        )
    }
}

data class ParsedBackup(
    val profileName: String,
    val profileWeight: String,
    val profileHeight: String,
    val medications: List<Medication>,
    val doseLogs: List<DoseLog>,
    val bloodPressureLogs: List<BloodPressureLog>,
    val glucoseLogs: List<GlucoseLog>
)
