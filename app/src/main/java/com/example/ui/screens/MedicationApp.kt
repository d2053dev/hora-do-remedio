package com.example.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.DoseLog
import com.example.data.local.Medication
import com.example.data.repository.GoogleDriveSyncManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.ColorDanger
import com.example.ui.theme.ColorSuccess
import com.example.ui.theme.ColorWarning
import com.example.ui.viewmodel.MedicationViewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import java.text.SimpleDateFormat
import java.util.*

enum class AppTab {
    DOSES, MEDICAMENTOS, HISTORICO, SAUDE, AJUSTES
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationApp(
    viewModel: MedicationViewModel,
    isDarkTheme: Boolean,
    onThemeToggle: (Boolean) -> Unit
) {
    var selectedTab by remember { mutableStateOf(AppTab.DOSES) }
    var showAddDialog by remember { mutableStateOf(false) }
    var medToEdit by remember { mutableStateOf<Medication?>(null) }
    
    val context = LocalContext.current
    val medications by viewModel.allMedications.collectAsStateWithLifecycle()
    val logs by viewModel.allLogs.collectAsStateWithLifecycle()
    val profileName by viewModel.profileName.collectAsStateWithLifecycle()
    val profileWeight by viewModel.profileWeight.collectAsStateWithLifecycle()
    val profileHeight by viewModel.profileHeight.collectAsStateWithLifecycle()
    val soundEnabled by viewModel.soundEnabled.collectAsStateWithLifecycle()
    val vibrationEnabled by viewModel.vibrationEnabled.collectAsStateWithLifecycle()

    val isGoogleConnected by viewModel.isGoogleConnected.collectAsStateWithLifecycle()
    val googleAccountName by viewModel.googleAccountName.collectAsStateWithLifecycle()
    val googleAccountEmail by viewModel.googleAccountEmail.collectAsStateWithLifecycle()
    val lastSyncTime by viewModel.lastSyncTime.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val syncStatusMessage by viewModel.syncStatusMessage.collectAsStateWithLifecycle()
    val pendingRestoreData by viewModel.pendingRestoreData.collectAsStateWithLifecycle()

    var showOAuthDialog by remember { mutableStateOf(false) }

    val notificationsAccepted by viewModel.notificationsAccepted.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.setNotificationsAccepted(isGranted)
        viewModel.rescheduleAllAlarms()
    }

    if (notificationsAccepted == null) {
        AlertDialog(
            onDismissRequest = { viewModel.setNotificationsAccepted(false) },
            icon = {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            },
            title = {
                Text(
                    text = "Deseja receber notificações?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "O aplicativo pode avisar quando for a hora exata de tomar seus medicamentos. Gostaria de ativar as notificações?",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.setNotificationsAccepted(true)
                            viewModel.rescheduleAllAlarms()
                        }
                    }
                ) {
                    Text("Sim, ativar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.setNotificationsAccepted(false) }
                ) {
                    Text("Agora não")
                }
            }
        )
    }

    // Toast check for sync status updates
    LaunchedEffect(syncStatusMessage) {
        syncStatusMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            viewModel.clearSyncStatusMessage()
        }
    }

    // Google Drive Backup - Cloud Restore Conflict Dialog
    if (pendingRestoreData != null) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelPendingRestore() },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Backup,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("Backup na Nuvem Encontrado")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Encontramos dados de backup salvos em seu Google Drive vinculados à conta ${googleAccountEmail ?: ""}.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "• Paciente: ${pendingRestoreData?.profileName ?: ""}\n" +
                               "• Medicamentos: ${pendingRestoreData?.medications?.size ?: 0} cadastrados\n" +
                               "• Histórico de doses e medições de saúde incluos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Como você deseja proceder?",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Ao escolher 'Usar Backup na Nuvem', os dados atuais deste aparelho serão substituídos. Ao escolher 'Manter Dados Locais', o histórico local deste celular será mantido e enviado como o backup mais recente.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.restoreCloudBackup() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Usar Backup na Nuvem")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.keepLocalDataAndUpload() }
                ) {
                    Text("Manter Dados Locais")
                }
            }
        )
    }

    // Google In-App OAuth Custom WebView Dialog
    if (showOAuthDialog) {
        GoogleOAuthWebViewDialog(
            onDismiss = { showOAuthDialog = false },
            onSuccess = { token, expiresSec ->
                viewModel.handleGoogleSignInSuccess(token, expiresSec)
            }
        )
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "BOM DIA, ${profileName.ifBlank { "PACIENTE" }}".uppercase(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "DoseWise",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconButton(
                            onClick = { onThemeToggle(!isDarkTheme) },
                            modifier = Modifier.testTag("theme_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = if (isDarkTheme) "Mudar para modo claro" else "Mudar para modo escuro",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (profileName.isNotBlank()) profileName.take(2).uppercase() else "JP",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                windowInsets = WindowInsets.navigationBars,
                modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                NavigationBarItem(
                    selected = selectedTab == AppTab.DOSES,
                    onClick = { selectedTab = AppTab.DOSES },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == AppTab.DOSES) Icons.Filled.Alarm else Icons.Outlined.Alarm,
                            contentDescription = "Aba Doses"
                        )
                    },
                    label = { Text("Doses") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.testTag("nav_tab_doses")
                )

                NavigationBarItem(
                    selected = selectedTab == AppTab.MEDICAMENTOS,
                    onClick = { selectedTab = AppTab.MEDICAMENTOS },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == AppTab.MEDICAMENTOS) Icons.Filled.Vaccines else Icons.Outlined.Vaccines,
                            contentDescription = "Aba Medicamentos"
                        )
                    },
                    label = { Text("Remédios") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.testTag("nav_tab_meds")
                )

                NavigationBarItem(
                    selected = selectedTab == AppTab.HISTORICO,
                    onClick = { selectedTab = AppTab.HISTORICO },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == AppTab.HISTORICO) Icons.Filled.History else Icons.Outlined.History,
                            contentDescription = "Aba Histórico"
                        )
                    },
                    label = { Text("Histórico") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.testTag("nav_tab_history")
                )

                NavigationBarItem(
                    selected = selectedTab == AppTab.SAUDE,
                    onClick = { selectedTab = AppTab.SAUDE },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == AppTab.SAUDE) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Aba Saúde"
                        )
                    },
                    label = { Text("Saúde") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.testTag("nav_tab_saude")
                )

                NavigationBarItem(
                    selected = selectedTab == AppTab.AJUSTES,
                    onClick = { selectedTab = AppTab.AJUSTES },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == AppTab.AJUSTES) Icons.Filled.Settings else Icons.Outlined.Settings,
                            contentDescription = "Aba Ajustes"
                        )
                    },
                    label = { Text("Ajustes") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.testTag("nav_tab_settings")
                )
            }
        },
        floatingActionButton = {
            if (selectedTab == AppTab.MEDICAMENTOS) {
                ExtendedFloatingActionButton(
                    onClick = { showAddDialog = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = "Cadastrar medicamento") },
                    text = { Text("Cadastrar") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.testTag("add_medication_fab")
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (selectedTab) {
                AppTab.DOSES -> DashboardTab(
                    medications = medications,
                    logs = logs,
                    patientName = profileName,
                    onTake = { viewModel.markAsTaken(it) },
                    onSkip = { viewModel.markAsSkipped(it) },
                    onShareReport = {
                        val pdfFile = viewModel.generateMedicationPdf()
                        if (pdfFile != null) {
                            sharePdf(context, pdfFile, "Relatório de Medicamentos do Paciente")
                        } else {
                            Toast.makeText(context, "Erro ao gerar PDF", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                AppTab.MEDICAMENTOS -> MedicationsTab(
                    medications = medications,
                    onEdit = { medToEdit = it },
                    onDelete = { viewModel.deleteMedication(it) }
                )
                AppTab.HISTORICO -> HistoryTab(
                    logs = logs,
                    onClear = { viewModel.clearHistory() }
                )
                AppTab.SAUDE -> HealthTab(
                    viewModel = viewModel,
                    context = context
                )
                AppTab.AJUSTES -> SettingsTab(
                    profileName = profileName,
                    onProfileNameChange = { viewModel.updateProfileName(it) },
                    profileWeight = profileWeight,
                    onProfileWeightChange = { viewModel.updateProfileWeight(it) },
                    profileHeight = profileHeight,
                    onProfileHeightChange = { viewModel.updateProfileHeight(it) },
                    notificationsAccepted = notificationsAccepted == true,
                    onNotificationsAcceptedToggle = { checked ->
                        viewModel.setNotificationsAccepted(checked)
                        viewModel.rescheduleAllAlarms()
                    },
                    soundEnabled = soundEnabled,
                    onSoundToggle = { viewModel.toggleSound(it) },
                    vibrationEnabled = vibrationEnabled,
                    onVibrationToggle = { viewModel.toggleVibration(it) },
                    isDarkTheme = isDarkTheme,
                    onThemeToggle = onThemeToggle,
                    isGoogleConnected = isGoogleConnected,
                    googleAccountName = googleAccountName,
                    googleAccountEmail = googleAccountEmail,
                    lastSyncTime = lastSyncTime,
                    isSyncing = isSyncing,
                    onConnectGoogle = { showOAuthDialog = true },
                    onDisconnectGoogle = { viewModel.disconnectGoogle() },
                    onManualBackup = { viewModel.performManualBackup() },
                    onTriggerTestNotification = {
                        viewModel.sendLocalNotification(
                            title = "Teste do Hora do Remédio ⏰",
                            message = "Seu lembrete inteligente está funcionando! Não se esqueça de manter seu estoque de remédios em dia."
                        )
                    },
                    onShareReport = {
                        val pdfFile = viewModel.generateMedicationPdf()
                        if (pdfFile != null) {
                            sharePdf(context, pdfFile, "Relatório de Medicamentos do Paciente")
                        } else {
                            Toast.makeText(context, "Erro ao gerar PDF", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            // Dialogs for Adding or Editing Medications
            if (showAddDialog) {
                MedicationFormDialog(
                    onDismiss = { showAddDialog = false },
                    onSave = { name, dosage, stock, unit, dosageQty, freq, firstTime, notes, isContinuous, controlType ->
                        viewModel.addMedication(name, dosage, stock, unit, dosageQty, freq, firstTime, notes, isContinuous, controlType)
                        showAddDialog = false
                    }
                )
            }

            if (medToEdit != null) {
                MedicationFormDialog(
                    medication = medToEdit,
                    onDismiss = { medToEdit = null },
                    onSave = { name, dosage, stock, unit, dosageQty, freq, firstTime, notes, isContinuous, controlType ->
                        medToEdit?.let { existing ->
                            viewModel.updateMedication(
                                existing.copy(
                                    name = name,
                                    dosage = dosage,
                                    stockQuantity = stock,
                                    stockUnit = unit,
                                    dosageQuantity = dosageQty,
                                    frequencyHours = freq,
                                    firstDoseTimestamp = firstTime,
                                    // if frequency or start timestamp changes, re-calc next dose
                                    nextDoseTimestamp = if (existing.frequencyHours != freq || existing.firstDoseTimestamp != firstTime) firstTime else existing.nextDoseTimestamp,
                                    notes = notes,
                                    isContinuous = isContinuous,
                                    controlType = controlType
                                )
                            )
                        }
                        medToEdit = null
                    }
                )
            }
        }
    }
}

// -----------------------------------------------------
// 1. DASHBOARD / DOSES TAB (Today's Medications Queue)
// -----------------------------------------------------
@Composable
fun DashboardTab(
    medications: List<Medication>,
    logs: List<DoseLog>,
    patientName: String,
    onTake: (Medication) -> Unit,
    onSkip: (Medication) -> Unit,
    onShareReport: () -> Unit
) {
    val currentTime = System.currentTimeMillis()
    
    // Sort medications so late/upcoming doses appear first
    val sortedMeds = remember(medications) {
        medications.sortedBy { it.nextDoseTimestamp }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Today's Overview Grid
        item {
            val totalExpected = medications.sumOf { 24 / it.frequencyHours.coerceAtLeast(1) }.coerceAtLeast(1)
            val takenCount = logs.count { 
                it.status in listOf("TOMADO", "ATRASADO") && 
                System.currentTimeMillis() - it.takenTimestamp < 24 * 60 * 60 * 1000 
            }
            val progress = (takenCount.toFloat() / totalExpected).coerceIn(0f, 1f)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Card 1: Consumo Hoje
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "CONSUMO HOJE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "$takenCount",
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.Light
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "/ $totalExpected doses",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }
                        
                        // Progress bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(progress)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }

                // Card 2: Relatórios
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onShareReport() }
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "RELATÓRIOS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Enviar Médico",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Medium
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Text(
                            text = "Toque para compartilhar",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "Fila de Doses",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
        }

        if (sortedMeds.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = ColorSuccess,
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = "Tudo em ordem!",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Nenhum medicamento agendado. Adicione remédios na aba de medicamentos.",
                            textAlign = TextAlign.Center,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            itemsIndexed(sortedMeds, key = { _, med -> med.id }) { index, med ->
                DoseQueueCard(
                    medication = med,
                    currentTime = currentTime,
                    isHero = index == 0,
                    onTake = { onTake(med) },
                    onSkip = { onSkip(med) }
                )
            }
        }
    }
}

@Composable
fun DoseQueueCard(
    medication: Medication,
    currentTime: Long,
    isHero: Boolean,
    onTake: () -> Unit,
    onSkip: () -> Unit
) {
    val diffMillis = medication.nextDoseTimestamp - currentTime
    val isLate = diffMillis < 0
    val diffAbs = Math.abs(diffMillis)
    val diffHours = diffAbs / (1000 * 60 * 60)
    val diffMinutes = (diffAbs / (1000 * 60)) % 60
    
    val timeLabel = when {
        isLate -> {
            if (diffHours > 0) "Atrasado há ${diffHours}h e ${diffMinutes}m"
            else "Atrasado há ${diffMinutes}m"
        }
        else -> {
            if (diffHours > 0) "Próximo em ${diffHours}h e ${diffMinutes}m"
            else "Próximo em ${diffMinutes}m"
        }
    }

    val statusColor = when {
        isLate -> ColorDanger
        diffMillis < 45 * 60 * 1000 -> ColorWarning
        else -> MaterialTheme.colorScheme.primary
    }

    val cardBgColor = if (isHero) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    val cardContentColor = if (isHero) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardBgColor
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dose_card_${medication.id}")
            .let { 
                if (!isHero) {
                    it.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(28.dp))
                } else it
            }
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isHero) "PRÓXIMA DOSE" else "INDICAÇÃO",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = if (isHero) cardContentColor.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = medication.name,
                            fontWeight = FontWeight.Bold,
                            style = if (isHero) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.titleLarge,
                            color = cardContentColor,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (medication.isContinuous) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(cardContentColor.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "CONTÍNUO",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = cardContentColor
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${medication.dosage} • ${medication.dosageQuantity.toInt()} ${medication.stockUnit}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cardContentColor.copy(alpha = 0.8f)
                    )
                }

                // Countdown badge
                Surface(
                    color = if (isHero) cardContentColor.copy(alpha = 0.15f) else statusColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = timeLabel,
                        color = if (isHero) cardContentColor else statusColor,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            if (medication.notes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isHero) cardContentColor.copy(alpha = 0.08f) else MaterialTheme.colorScheme.background)
                        .padding(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = if (isHero) cardContentColor else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = medication.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = cardContentColor.copy(alpha = 0.85f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Inventory alert inside card
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Stock warning
                Column {
                    Text(
                        text = "Estoque: ${medication.stockQuantity} ${medication.stockUnit}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (medication.isStockLow()) ColorDanger else cardContentColor.copy(alpha = 0.6f),
                        fontWeight = if (medication.isStockLow()) FontWeight.Bold else FontWeight.Normal
                    )
                    if (medication.isStockLow()) {
                        Text(
                            text = "⚠️ Comprar em breve!",
                            color = ColorDanger,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Interactive targets
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Skip button
                    IconButton(
                        onClick = onSkip,
                        modifier = Modifier
                            .testTag("skip_button_${medication.id}")
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (isHero) cardContentColor.copy(alpha = 0.1f) 
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Snooze,
                            contentDescription = "Pular dose",
                            tint = cardContentColor
                        )
                    }

                    // Take button
                    Button(
                        onClick = onTake,
                        shape = RoundedCornerShape(22.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isHero) MaterialTheme.colorScheme.onPrimaryContainer else ColorSuccess,
                            contentColor = if (isHero) MaterialTheme.colorScheme.primaryContainer else Color.White
                        ),
                        modifier = Modifier
                            .testTag("take_button_${medication.id}")
                            .height(44.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Confirmar", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------
// 2. MEDICATIONS TAB (Edit / Manage registered inventory)
// -----------------------------------------------------
@Composable
fun MedicationsTab(
    medications: List<Medication>,
    onEdit: (Medication) -> Unit,
    onDelete: (Medication) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Meus Medicamentos",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
        }

        if (medications.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Vaccines,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = "Nenhum Remédio Registrado",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Toque no botão 'Cadastrar' no canto inferior direito para adicionar o primeiro.",
                            textAlign = TextAlign.Center,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(medications) { med ->
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
                        .testTag("med_item_card_${med.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Circle,
                                    contentDescription = null,
                                    tint = if (med.isStockLow()) ColorDanger else ColorSuccess,
                                    modifier = Modifier.size(10.dp)
                                )
                                Text(
                                    text = med.name,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (med.isContinuous) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "CONTÍNUO",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Dosagem: ${med.dosage}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Estoque: ${med.stockQuantity} ${med.stockUnit}" + if (med.isStockLow()) " (BAIXO!)" else "",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (med.isStockLow()) FontWeight.Bold else FontWeight.Normal,
                                color = if (med.isStockLow()) ColorDanger else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Horário: A cada ${med.frequencyHours} horas",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { onEdit(med) },
                                modifier = Modifier
                                    .testTag("edit_med_${med.id}")
                                    .size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Editar remédio",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { onDelete(med) },
                                modifier = Modifier
                                    .testTag("delete_med_${med.id}")
                                    .size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Excluir remédio",
                                    tint = ColorDanger
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------
// 3. HISTORY TAB (Consumption Logs logbook)
// -----------------------------------------------------
@Composable
fun HistoryTab(
    logs: List<DoseLog>,
    onClear: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
    var showConfirmDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Histórico de Consumo",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            if (logs.isNotEmpty()) {
                TextButton(
                    onClick = { showConfirmDialog = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = ColorDanger),
                    modifier = Modifier.testTag("clear_history_button")
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Limpar", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                }
            }
        }

        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        text = "Sem Registros de Uso",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "As doses tomadas ou ignoradas aparecerão aqui em ordem cronológica.",
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(logs) { log ->
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val logColor = when (log.status) {
                                    "TOMADO" -> ColorSuccess
                                    "IGNORADO" -> ColorDanger
                                    else -> ColorWarning
                                }
                                val icon = when (log.status) {
                                    "TOMADO" -> Icons.Default.CheckCircle
                                    "IGNORADO" -> Icons.Default.Cancel
                                    else -> Icons.Default.WatchLater
                                }

                                Icon(
                                    imageVector = icon,
                                    contentDescription = log.status,
                                    tint = logColor,
                                    modifier = Modifier.size(28.dp)
                                )

                                Column {
                                    Text(
                                        text = log.medicationName,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Dose: ${log.dosageTaken}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = dateFormat.format(Date(log.takenTimestamp)),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = log.status,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = when (log.status) {
                                        "TOMADO" -> ColorSuccess
                                        "IGNORADO" -> ColorDanger
                                        else -> ColorWarning
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showConfirmDialog = false },
                title = { Text("Limpar Histórico?") },
                text = { Text("Tem certeza de que deseja apagar permanentemente todo o histórico de doses? Essa ação não pode ser desfeita.") },
                confirmButton = {
                    Button(
                        onClick = {
                            onClear()
                            showConfirmDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ColorDanger)
                    ) {
                        Text("Sim, Apagar", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmDialog = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}

// -----------------------------------------------------
// 4. SETTINGS & REPORT TAB
// -----------------------------------------------------
@Composable
fun SettingsTab(
    profileName: String,
    onProfileNameChange: (String) -> Unit,
    profileWeight: String,
    onProfileWeightChange: (String) -> Unit,
    profileHeight: String,
    onProfileHeightChange: (String) -> Unit,
    notificationsAccepted: Boolean,
    onNotificationsAcceptedToggle: (Boolean) -> Unit,
    soundEnabled: Boolean,
    onSoundToggle: (Boolean) -> Unit,
    vibrationEnabled: Boolean,
    onVibrationToggle: (Boolean) -> Unit,
    isDarkTheme: Boolean,
    onThemeToggle: (Boolean) -> Unit,
    isGoogleConnected: Boolean,
    googleAccountName: String?,
    googleAccountEmail: String?,
    lastSyncTime: Long,
    isSyncing: Boolean,
    onConnectGoogle: () -> Unit,
    onDisconnectGoogle: () -> Unit,
    onManualBackup: () -> Unit,
    onTriggerTestNotification: () -> Unit,
    onShareReport: () -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var tempName by remember { mutableStateOf(profileName) }
    var tempWeight by remember { mutableStateOf(profileWeight) }
    var tempHeight by remember { mutableStateOf(profileHeight) }

    // Keep temp values in sync when editing or values update
    LaunchedEffect(isEditing, profileName, profileWeight, profileHeight) {
        if (!isEditing) {
            tempName = profileName
            tempWeight = profileWeight
            tempHeight = profileHeight
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Relatório & Lembretes",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
        }

        // Section: Profile
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Identificação do Paciente",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    if (isEditing) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = tempName,
                                onValueChange = { tempName = it },
                                label = { Text("Nome do Paciente") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("patient_name_input")
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = tempWeight,
                                    onValueChange = { input -> if (input.all { it.isDigit() || it == '.' }) tempWeight = input },
                                    label = { Text("Peso (kg)") },
                                    placeholder = { Text("Ex: 75") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("patient_weight_input")
                                )

                                OutlinedTextField(
                                    value = tempHeight,
                                    onValueChange = { input -> if (input.all { it.isDigit() }) tempHeight = input },
                                    label = { Text("Altura (cm)") },
                                    placeholder = { Text("Ex: 175") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("patient_height_input")
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { isEditing = false }) {
                                    Text("Cancelar")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        if (tempName.isNotBlank()) {
                                            onProfileNameChange(tempName.trim())
                                            onProfileWeightChange(tempWeight.trim())
                                            onProfileHeightChange(tempHeight.trim())
                                            isEditing = false
                                        }
                                    },
                                    enabled = tempName.isNotBlank()
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = "Salvar")
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Salvar")
                                }
                            }
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = profileName,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Identificação completa para relatórios.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { isEditing = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Editar identificação",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            Spacer(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // Weight & Height Display
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Weight info
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Scale,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "Peso",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = if (profileWeight.isNotEmpty()) "$profileWeight kg" else "Não informado",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                // Height info
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Straighten,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "Altura",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = if (profileHeight.isNotEmpty()) "$profileHeight cm" else "Não informado",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section: Google Drive Backup & Restore
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Backup na Nuvem (Google Drive)",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Salve todo o seu histórico de doses, medicamentos, perfis médicos, e medições de saúde (glicose e pressão arterial) de forma segura na sua conta Google. Transfira dados entre dispositivos em tempo real.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (isGoogleConnected) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "CONECTADO À CONTA GOOGLE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = ColorSuccess
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${googleAccountName ?: "Usuário Google"}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${googleAccountEmail ?: ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Último Sincronismo: " + if (lastSyncTime > 0) {
                                    val dateFmt = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                                    dateFmt.format(Date(lastSyncTime))
                                } else {
                                    "Nunca Sincronizado"
                                },
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (isSyncing) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Text("Sincronizando...", style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onManualBackup,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Fazer Backup", style = MaterialTheme.typography.labelSmall)
                                }

                                OutlinedButton(
                                    onClick = onDisconnectGoogle,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorDanger)
                                ) {
                                    Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Sair", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    } else {
                        Button(
                            onClick = onConnectGoogle,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("connect_google_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(Icons.Default.Cloud, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Conectar Conta Google", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
        }

        // Section: Appearance Choice (Dark Mode / Light Mode)
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Aparência",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onThemeToggle(!isDarkTheme) }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Modo Escuro",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Ativar o visual escuro para maior conforto visual.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isDarkTheme,
                            onCheckedChange = onThemeToggle,
                            modifier = Modifier.testTag("dark_theme_switch")
                        )
                    }
                }
            }
        }

        // Section: Intelligent Reminders (Custom Alert System)
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Ajustes de Alertas Inteligentes",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Master Notifications Preference
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNotificationsAcceptedToggle(!notificationsAccepted) }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Notificações do Remédio", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Ativar os lembretes do aplicativo no celular.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = notificationsAccepted,
                            onCheckedChange = onNotificationsAcceptedToggle,
                            modifier = Modifier.testTag("notifications_master_switch")
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 12.dp))

                    // Vibration preference
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onVibrationToggle(!vibrationEnabled) }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Vibrar nos Lembretes", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Vibrar celular ao disparar a hora da medicação.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = vibrationEnabled,
                            onCheckedChange = onVibrationToggle,
                            modifier = Modifier.testTag("vibration_switch")
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 12.dp))

                    // Sound preference
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSoundToggle(!soundEnabled) }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Som de Notificação", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Tocar som padrão para alertar o paciente.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = soundEnabled,
                            onCheckedChange = onSoundToggle,
                            modifier = Modifier.testTag("sound_switch")
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 12.dp))
                    Spacer(modifier = Modifier.height(4.dp))

                    // Test Alarms Trigger
                    Button(
                        onClick = onTriggerTestNotification,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("test_alert_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Testar Alerta de Remédio Agora", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }

        // Section: Share doctors report
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Compartilhamento com Médicos",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Gere um registro resumido e formatado de todo os seus remédios cadastrados, estoque, frequências de consumo e o histórico de intakes, ideal para consultas e acompanhamentos de saúde para doenças crônicas.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onShareReport,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("share_report_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Gerar & Compartilhar Relatório", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------
// 5. MEDICATION FORM DIALOG (Re-usable for Creating and Updating Meds)
// -----------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationFormDialog(
    medication: Medication? = null,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        dosage: String,
        stock: Double,
        stockUnit: String,
        dosageQty: Double,
        frequencyHours: Int,
        firstDoseTime: Long,
        notes: String,
        isContinuous: Boolean,
        controlType: String
    ) -> Unit
) {
    var name by remember { mutableStateOf(medication?.name ?: "") }
    var dosage by remember { mutableStateOf(medication?.dosage ?: "") }
    var stockInput by remember { mutableStateOf(medication?.stockQuantity?.toString() ?: "30") }
    var stockUnit by remember { mutableStateOf(medication?.stockUnit ?: "comprimidos") }
    var dosageQtyInput by remember { mutableStateOf(medication?.dosageQuantity?.toString() ?: "1") }
    var frequencyInput by remember { mutableStateOf(medication?.frequencyHours?.toString() ?: "12") }
    var notes by remember { mutableStateOf(medication?.notes ?: "") }
    var isContinuous by remember { mutableStateOf(medication?.isContinuous ?: false) }
    var controlType by remember { mutableStateOf(medication?.controlType ?: "Nenhum") }
    
    // Starting schedule timestamp (defaults to current time if new)
    var selectedCalendar by remember {
        mutableStateOf(
            Calendar.getInstance().apply {
                if (medication != null) {
                    timeInMillis = medication.firstDoseTimestamp
                }
            }
        )
    }

    val context = LocalContext.current
    val dateFormatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
        ) {
            LazyColumn(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = if (medication == null) "Cadastrar Remédio" else "Editar Remédio",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                // Field: Medical name
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nome do medicamento") },
                        placeholder = { Text("Ex: Metformina, Atenolol...") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_med_name"),
                        leadingIcon = { Icon(Icons.Default.Medication, contentDescription = null) }
                    )
                }

                // Field: Dosage
                item {
                    OutlinedTextField(
                        value = dosage,
                        onValueChange = { dosage = it },
                        label = { Text("Dosagem") },
                        placeholder = { Text("Ex: 50mg, 850mg, 5ml...") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_med_dosage"),
                        leadingIcon = { Icon(Icons.Default.Scale, contentDescription = null) }
                    )
                }

                // Row: Dosage Qty taken each time & Stock quantity left
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = dosageQtyInput,
                            onValueChange = { dosageQtyInput = it },
                            label = { Text("Qtde Dose") },
                            placeholder = { Text("Ex: 1") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_med_dose_qty")
                        )

                        OutlinedTextField(
                            value = stockInput,
                            onValueChange = { stockInput = it },
                            label = { Text("Estoque Total") },
                            placeholder = { Text("Ex: 30") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_med_stock")
                        )
                    }
                }

                // Field: Unit of stock (Comprimidos, ml, gotas etc)
                item {
                    val units = listOf("comprimidos", "ml", "gotas", "flaconetes", "aplicações")
                    Text("Unidade do Remédio:", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        units.take(3).forEach { unit ->
                            val isSelected = stockUnit == unit
                            ElevatedFilterChip(
                                selected = isSelected,
                                onClick = { stockUnit = unit },
                                label = { Text(unit, fontSize = 11.sp) },
                                modifier = Modifier.testTag("chip_unit_$unit")
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        units.drop(3).forEach { unit ->
                            val isSelected = stockUnit == unit
                            ElevatedFilterChip(
                                selected = isSelected,
                                onClick = { stockUnit = unit },
                                label = { Text(unit, fontSize = 11.sp) },
                                modifier = Modifier.testTag("chip_unit_$unit")
                            )
                        }
                    }
                }

                // Field: Frequency in hours
                item {
                    OutlinedTextField(
                        value = frequencyInput,
                        onValueChange = { frequencyInput = it },
                        label = { Text("Frequência de Uso") },
                        placeholder = { Text("Intervalo em Horas (Ex: 8 ou 12)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        prefix = { Text("A cada ") },
                        suffix = { Text(" horas") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_med_frequency"),
                        leadingIcon = { Icon(Icons.Default.HourglassEmpty, contentDescription = null) }
                    )
                }

                // Field: Starting Date and Time selector
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .clickable {
                                // Launch DatePicker first
                                val calendar = Calendar.getInstance()
                                DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth ->
                                        selectedCalendar.set(Calendar.YEAR, year)
                                        selectedCalendar.set(Calendar.MONTH, month)
                                        selectedCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)

                                        // Launch TimePicker next
                                        TimePickerDialog(
                                            context,
                                            { _, hourOfDay, minute ->
                                                selectedCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                                                selectedCalendar.set(Calendar.MINUTE, minute)
                                                selectedCalendar.set(Calendar.SECOND, 0)
                                                selectedCalendar.set(Calendar.MILLISECOND, 0)

                                                // Update state forcing re-evaluation
                                                selectedCalendar = selectedCalendar.clone() as Calendar
                                            },
                                            selectedCalendar.get(Calendar.HOUR_OF_DAY),
                                            selectedCalendar.get(Calendar.MINUTE),
                                            true
                                        ).show()
                                    },
                                    selectedCalendar.get(Calendar.YEAR),
                                    selectedCalendar.get(Calendar.MONTH),
                                    selectedCalendar.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            }
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Início do Cronograma:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = dateFormatter.format(selectedCalendar.time),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = "Selecionar data de início",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Field: Tipo de Controle (Glucose vs BP)
                item {
                    Text(
                        text = "Este medicamento é para controle de:",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val controlOptions = listOf(
                            "Nenhum" to "💊 Nenhum",
                            "Pressão Arterial" to "🩺 Pressão",
                            "Glicose" to "🩸 Glicose"
                        )
                        controlOptions.forEach { (optionValue, labelText) ->
                            val isSelected = controlType == optionValue
                            ElevatedFilterChip(
                                selected = isSelected,
                                onClick = { controlType = optionValue },
                                label = { Text(labelText) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("chip_control_$optionValue")
                            )
                        }
                    }
                }

                // Field: Uso Contínuo Switch
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .clickable { isContinuous = !isContinuous }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Medicamento de Uso Contínuo",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Alerta quando restar menos de uma semana de estoque (com base no uso diário).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isContinuous,
                            onCheckedChange = { isContinuous = it },
                            modifier = Modifier.testTag("input_med_continuous")
                        )
                    }
                }

                // Field: Notes/Special intake guidelines
                item {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Instruções ou Observações") },
                        placeholder = { Text("Ex: Tomar com estômago cheio, evitar derivados de leite...") },
                        maxLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_med_notes"),
                        leadingIcon = { Icon(Icons.Default.Assignment, contentDescription = null) }
                    )
                }

                // Action controls: Confirm / Cancel
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("cancel_med_btn")
                        ) {
                            Text("Cancelar")
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Button(
                            onClick = {
                                val stockVal = stockInput.toDoubleOrNull() ?: 0.0
                                val dosageQtyVal = dosageQtyInput.toDoubleOrNull() ?: 1.0
                                val freqVal = frequencyInput.toIntOrNull() ?: 12
                                
                                if (name.isNotBlank() && dosage.isNotBlank()) {
                                    onSave(
                                        name.trim(),
                                        dosage.trim(),
                                        stockVal,
                                        stockUnit,
                                        dosageQtyVal,
                                        freqVal,
                                        selectedCalendar.timeInMillis,
                                        notes.trim(),
                                        isContinuous,
                                        controlType
                                    )
                                }
                            },
                            enabled = name.isNotBlank() && dosage.isNotBlank(),
                            modifier = Modifier.testTag("save_med_btn")
                        ) {
                            Text("Salvar")
                        }
                    }
                }
            }
        }
    }
}

private fun sharePdf(context: Context, file: File, title: String) {
    try {
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, uri)
            type = "application/pdf"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        context.startActivity(Intent.createChooser(shareIntent, title))
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Erro ao exportar PDF: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun GoogleOAuthWebViewDialog(
    onDismiss: () -> Unit,
    onSuccess: (String, Long) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Bar (Built using a custom Row to remain completely compile-safe across Compose versions)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Voltar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Conectar Conta Google",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // WebView Container
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    factory = { context ->
                        WebView(context).apply {
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                useWideViewPort = true
                                loadWithOverviewMode = true
                            }
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    val currentUrl = url ?: return
                                    if (currentUrl.startsWith(GoogleDriveSyncManager.REDIRECT_URI)) {
                                        if (currentUrl.contains("access_token=")) {
                                            val anchor = currentUrl.substringAfter("access_token=")
                                            val token = anchor.substringBefore("&")
                                            val expiresInStr = if (currentUrl.contains("expires_in=")) {
                                                currentUrl.substringAfter("expires_in=").substringBefore("&")
                                            } else "3600"
                                            val expiresIn = expiresInStr.toLongOrNull() ?: 3600L
                                            
                                            onSuccess(token, expiresIn)
                                            onDismiss()
                                        } else if (currentUrl.contains("error=")) {
                                            onDismiss()
                                        }
                                    }
                                }
                            }
                            loadUrl(GoogleDriveSyncManager.OAUTH_AUTH_URL)
                        }
                    }
                )
            }
        }
    }
}

