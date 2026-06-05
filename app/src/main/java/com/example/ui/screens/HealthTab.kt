package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.BloodPressureLog
import com.example.data.local.GlucoseLog
import com.example.ui.viewmodel.MedicationViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HealthTab(
    viewModel: MedicationViewModel,
    context: Context = LocalContext.current
) {
    var activeSubTab by remember { mutableStateOf(0) } // 0 = Pressão Arterial, 1 = Glicose
    
    val bpLogs by viewModel.allBloodPressureLogs.collectAsStateWithLifecycle()
    val glucoseLogs by viewModel.allGlucoseLogs.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("health_tab_container"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Main Tab Title & Info
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "Controle de Medições",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Acompanhe e configure relatórios para sua pressão arterial e taxa de glicose no sangue.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Sub-Tabs selection (Blood Pressure / Glucose)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Blood Pressure Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (activeSubTab == 0) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { activeSubTab = 0 }
                        .padding(vertical = 12.dp)
                        .testTag("select_subtab_bp"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MonitorHeart,
                            contentDescription = null,
                            tint = if (activeSubTab == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Pressão Arterial",
                            fontWeight = FontWeight.Bold,
                            color = if (activeSubTab == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // Glucose Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (activeSubTab == 1) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { activeSubTab = 1 }
                        .padding(vertical = 12.dp)
                        .testTag("select_subtab_glucose"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.WaterDrop,
                            contentDescription = null,
                            tint = if (activeSubTab == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Glicemia Glicose",
                            fontWeight = FontWeight.Bold,
                            color = if (activeSubTab == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        if (activeSubTab == 0) {
            // == BLOOD PRESSURE VIEW ==
            item {
                BloodPressureFormCard(
                    onSave = { systole, diastole, pulse, notes ->
                        viewModel.addBloodPressureLog(systole, diastole, pulse, notes)
                        Toast.makeText(context, "Medição de pressão adicionada!", Toast.LENGTH_SHORT).show()
                    },
                    onExportReport = {
                        val file = viewModel.generateBloodPressurePdf()
                        if (file != null) {
                            sharePdf(context, file, "Relatório de Pressão Arterial")
                        } else {
                            Toast.makeText(context, "Não foi possível gerar o PDF", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Histórico de Medições (${bpLogs.size})",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (bpLogs.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                viewModel.clearBloodPressureLogs()
                                Toast.makeText(context, "Histórico limpo!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Filled.ClearAll, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Limpar")
                        }
                    }
                }
            }

            if (bpLogs.isEmpty()) {
                item {
                    EmptyHistoryPlaceholder(
                        icon = Icons.Filled.MonitorHeart,
                        text = "Nenhuma medição de pressão registrada ainda."
                    )
                }
            } else {
                items(bpLogs) { log ->
                    BloodPressureLogItem(
                        log = log,
                        onDelete = { viewModel.deleteBloodPressureLog(log) }
                    )
                }
            }
        } else {
            // == GLUCOSE VIEW ==
            item {
                GlucoseFormCard(
                    onSave = { valDouble, state, notes ->
                        viewModel.addGlucoseLog(valDouble, state, notes)
                        Toast.makeText(context, "Medição de glicemia adicionada!", Toast.LENGTH_SHORT).show()
                    },
                    onExportReport = {
                        val file = viewModel.generateGlucosePdf()
                        if (file != null) {
                            sharePdf(context, file, "Relatório de Glicemia")
                        } else {
                            Toast.makeText(context, "Não foi possível gerar o PDF", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Histórico de Medições (${glucoseLogs.size})",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (glucoseLogs.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                viewModel.clearGlucoseLogs()
                                Toast.makeText(context, "Histórico limpo!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Filled.ClearAll, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Limpar")
                        }
                    }
                }
            }

            if (glucoseLogs.isEmpty()) {
                item {
                    EmptyHistoryPlaceholder(
                        icon = Icons.Filled.WaterDrop,
                        text = "Nenhuma medição de glicose registrada ainda."
                    )
                }
            } else {
                items(glucoseLogs) { log ->
                    GlucoseLogItem(
                        log = log,
                        onDelete = { viewModel.deleteGlucoseLog(log) }
                    )
                }
            }
        }
    }
}

// == FORM CARDS ==

@Composable
fun BloodPressureFormCard(
    onSave: (systole: Int, diastole: Int, pulse: Int?, notes: String) -> Unit,
    onExportReport: () -> Unit
) {
    var systoleInput by remember { mutableStateOf("") }
    var diastoleInput by remember { mutableStateOf("") }
    var pulseInput by remember { mutableStateOf("") }
    var notesInput by remember { mutableStateOf("") }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("bp_form_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Cadastrar Pressão Arterial",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Sístole Input
                OutlinedTextField(
                    value = systoleInput,
                    onValueChange = { input -> if (input.all { it.isDigit() }) systoleInput = input },
                    label = { Text("Máxima (Sístole)") },
                    placeholder = { Text("Ex: 120") },
                    suffix = { Text("mmHg") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("input_bp_systole")
                )

                // Diástole Input
                OutlinedTextField(
                    value = diastoleInput,
                    onValueChange = { input -> if (input.all { it.isDigit() }) diastoleInput = input },
                    label = { Text("Mínima (Diástole)") },
                    placeholder = { Text("Ex: 80") },
                    suffix = { Text("mmHg") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("input_bp_diastole")
                )
            }

            // Pulse Input (BPM - Optional)
            OutlinedTextField(
                value = pulseInput,
                onValueChange = { input -> if (input.all { it.isDigit() }) pulseInput = input },
                label = { Text("Pulso / Batimentos (Opcional)") },
                placeholder = { Text("Ex: 72") },
                suffix = { Text("BPM") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_bp_pulse")
            )

            // Observações
            OutlinedTextField(
                value = notesInput,
                onValueChange = { notesInput = it },
                label = { Text("Observação / Guidelines (Opcional)") },
                placeholder = { Text("Ex: Sentindo tontura ou repousando") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_bp_notes")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // PDF Export Button
                OutlinedButton(
                    onClick = onExportReport,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("action_bp_export"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PictureAsPdf,
                        contentDescription = "Exportar PDF"
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Gerar PDF")
                }

                // Save Button
                Button(
                    onClick = {
                        val sysVal = systoleInput.toIntOrNull()
                        val diaVal = diastoleInput.toIntOrNull()
                        if (sysVal != null && diaVal != null) {
                            onSave(sysVal, diaVal, pulseInput.toIntOrNull(), notesInput.trim())
                            // Clear fields
                            systoleInput = ""
                            diastoleInput = ""
                            pulseInput = ""
                            notesInput = ""
                        }
                    },
                    enabled = systoleInput.isNotEmpty() && diastoleInput.isNotEmpty(),
                    modifier = Modifier
                        .weight(1.2f)
                        .height(50.dp)
                        .testTag("action_bp_save"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Registrar")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GlucoseFormCard(
    onSave: (glucose: Double, state: String, notes: String) -> Unit,
    onExportReport: () -> Unit
) {
    var glucoseInput by remember { mutableStateOf("") }
    var selectedState by remember { mutableStateOf("Jejum") }
    var notesInput by remember { mutableStateOf("") }

    val states = listOf("Jejum", "Pré-prandial", "Pós-prandial", "Antes de Dormir")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("glucose_form_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Cadastrar Glicose Sanguínea",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            // Glucose input
            OutlinedTextField(
                value = glucoseInput,
                onValueChange = { input -> if (input.all { it.isDigit() || it == '.' }) glucoseInput = input },
                label = { Text("Valor da Glicose") },
                placeholder = { Text("Ex: 99") },
                suffix = { Text("mg/dL") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_glucose_value")
            )

            // Select Status Chip
            Text(
                text = "Momento da Medição:",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                states.forEach { state ->
                    val isSelected = selectedState == state
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedState = state },
                        label = { Text(state) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("chip_glucose_$state")
                    )
                }
            }

            // Extra notes
            OutlinedTextField(
                value = notesInput,
                onValueChange = { notesInput = it },
                label = { Text("Observação / Sintomas (Opcional)") },
                placeholder = { Text("Ex: Após pedaço de bolo, sentindo bem") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_glucose_notes")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // PDF Export Button
                OutlinedButton(
                    onClick = onExportReport,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("action_glucose_export"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PictureAsPdf,
                        contentDescription = "Exportar PDF"
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Gerar PDF")
                }

                // Save Button
                Button(
                    onClick = {
                        val glucVal = glucoseInput.toDoubleOrNull()
                        if (glucVal != null) {
                            onSave(glucVal, selectedState, notesInput.trim())
                            // Clear
                            glucoseInput = ""
                            notesInput = ""
                        }
                    },
                    enabled = glucoseInput.isNotEmpty(),
                    modifier = Modifier
                        .weight(1.2f)
                        .height(50.dp)
                        .testTag("action_glucose_save"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Registrar")
                }
            }
        }
    }
}

// == INDIVIDUAL HISTORY LOG CONTENT ITEMS ==

@Composable
fun BloodPressureLogItem(
    log: BloodPressureLog,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    
    // Check if blood pressure is in the normal range (120/80 is normal, 140/90 is high, etc.)
    val isHigh = log.systole >= 140 || log.diastole >= 90
    val isLow = log.systole < 90 || log.diastole < 60
    
    val badgeColor = when {
        isHigh -> MaterialTheme.colorScheme.errorContainer
        isLow -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val badgeTextColor = when {
        isHigh -> MaterialTheme.colorScheme.onErrorContainer
        isLow -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    val statusText = when {
        isHigh -> "ALTA"
        isLow -> "BAIXA"
        else -> "NORMAL"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("bp_log_item_${log.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${log.systole} x ${log.diastole}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "mmHg",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    // Alert badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(badgeColor)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = statusText,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeTextColor
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Date
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AccessTime,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = dateFormat.format(Date(log.timestamp)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    // Optional BPM
                    if (log.pulse != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Favorite,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "${log.pulse} BPM",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                if (log.notes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Obs: ${log.notes}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Light
                    )
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("delete_bp_log_${log.id}")
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Excluir medição",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun GlucoseLogItem(
    log: GlucoseLog,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    
    // Evaluate if glucose level is standard or alert-worthy (e.g., fasting normal is 70-100)
    val isFasting = log.state.contains("Jejum", ignoreCase = true)
    val isHigh = if (isFasting) log.glucoseValue >= 100.0 else log.glucoseValue >= 140.0
    val isHypo = log.glucoseValue < 70.0

    val badgeColor = when {
        isHypo -> MaterialTheme.colorScheme.errorContainer // hypoglycemia is dangerous
        isHigh -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val badgeTextColor = when {
        isHypo -> MaterialTheme.colorScheme.onErrorContainer
        isHigh -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    val statusText = when {
        isHypo -> "HIPOGLICEMIA"
        isHigh -> "ALTA"
        else -> "NORMAL"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("glucose_log_item_${log.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${log.glucoseValue.toInt()}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "mg/dL",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    // Alert badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(badgeColor)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = statusText,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeTextColor
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Date
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AccessTime,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = dateFormat.format(Date(log.timestamp)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Moment/State text
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Restaurant,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = log.state,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (log.notes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Obs: ${log.notes}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Light
                    )
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("delete_glucose_log_${log.id}")
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Excluir medição",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun EmptyHistoryPlaceholder(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Normal
            )
        }
    }
}

// == PDF SHARING NATIVE SHEET INVOCATION ==

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
