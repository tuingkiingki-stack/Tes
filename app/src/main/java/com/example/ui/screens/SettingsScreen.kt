package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.SyncConflictEntity
import com.example.data.local.UserProfileEntity
import com.example.data.repository.RestoreMode
import com.example.domain.model.PriceSourceType
import com.example.domain.model.SyncStatus
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CardSurface
import com.example.ui.theme.CrimsonShort
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.ElevatedCardSurface
import com.example.ui.theme.EmeraldLong
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.NavyBackground
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.io.File

@Composable
fun SettingsScreen(
    userProfile: UserProfileEntity,
    userEmail: String?,
    syncStatus: SyncStatus,
    syncConflicts: List<SyncConflictEntity>,
    onSaveSettings: (Double, PriceSourceType, Int, Boolean) -> Unit,
    onTriggerCloudSync: () -> Unit,
    onResolveConflict: (SyncConflictEntity, Boolean) -> Unit,
    onExportJson: ((String) -> Unit) -> Unit,
    onImportJson: (String, RestoreMode) -> Unit,
    onExportCsv: ((String) -> Unit) -> Unit,
    onImportCsv: (String, RestoreMode) -> Unit,
    onGeneratePdf: (android.content.Context, (Result<File>) -> Unit) -> Unit,
    onLogout: () -> Unit,
    onDeleteAccountAndData: () -> Unit
) {
    val context = LocalContext.current
    var capitalText by remember(userProfile.initialCapital) {
        mutableStateOf(userProfile.initialCapital.toString())
    }
    var selectedSource by remember(userProfile.preferredPriceSource) {
        mutableStateOf(
            try {
                PriceSourceType.valueOf(userProfile.preferredPriceSource)
            } catch (_: Exception) {
                PriceSourceType.BINANCE_FUTURES
            }
        )
    }
    var staleSecText by remember(userProfile.priceStaleThresholdSec) {
        mutableStateOf(userProfile.priceStaleThresholdSec.toString())
    }
    var notificationsEnabled by remember(userProfile.notificationsEnabled) {
        mutableStateOf(userProfile.notificationsEnabled)
    }

    var backupModalData by remember { mutableStateOf<String?>(null) }
    var backupModalTitle by remember { mutableStateOf("") }
    var isCsvModal by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var pdfResultMessage by remember { mutableStateOf<String?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsEnabled = granted
    }

    LaunchedEffect(userProfile) {
        capitalText = userProfile.initialCapital.toString()
        staleSecText = userProfile.priceStaleThresholdSec.toString()
        notificationsEnabled = userProfile.notificationsEnabled
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyBackground)
            .testTag("settings_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "Pengaturan & Cloud Jurnal",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        // 1. Modal Awal, Sumber Harga, Batas Umur Data Harga, Notifikasi
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardSurface)
                    .border(1.dp, SubtleBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Parameter Trading & Sumber Harga",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = capitalText,
                    onValueChange = { capitalText = it },
                    label = { Text("Modal Awal Portofolio (USDT)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_initial_capital_input")
                )

                Text(
                    text = "Sumber Harga Utama (Deteksi Entry/TP/SL):",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )

                listOf(
                    PriceSourceType.BINANCE_FUTURES,
                    PriceSourceType.BINANCE_SPOT,
                    PriceSourceType.COINGECKO
                ).forEach { src ->
                    val selected = selectedSource == src
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) CyanAccent.copy(alpha = 0.2f) else ElevatedCardSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, if (selected) CyanAccent else SubtleBorder, RoundedCornerShape(10.dp))
                            .clickable { selectedSource = src }
                            .testTag("price_source_option_${src.name}")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = src.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (selected) CyanAccent else TextPrimary,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                            if (selected) {
                                StatusPillBadge("AKTIF", CyanAccent)
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = staleSecText,
                    onValueChange = { staleSecText = it },
                    label = { Text("Batas Umur Data Harga Sebelum Kedaluwarsa (Detik)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_stale_threshold_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Notifications, contentDescription = "Notifikasi", tint = CyanAccent)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Notifikasi Entry, TP & SL",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "Kirim peringatan suara & getaran berbeda saat level tersentuh",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                notificationsEnabled = enabled
                            }
                        },
                        modifier = Modifier.testTag("settings_notifications_switch")
                    )
                }

                Button(
                    onClick = {
                        val cap = capitalText.toDoubleOrNull() ?: userProfile.initialCapital
                        val stale = staleSecText.toIntOrNull() ?: userProfile.priceStaleThresholdSec
                        onSaveSettings(cap, selectedSource, stale, notificationsEnabled)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldLong),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("save_settings_button")
                ) {
                    Icon(Icons.Default.Save, contentDescription = "Simpan Pengaturan")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Simpan Pengaturan", fontWeight = FontWeight.Bold)
                }
            }
        }

        // 2. Status Sinkronisasi & Resolusi Konflik
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardSurface)
                    .border(1.dp, SubtleBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Status Sinkronisasi Cloud",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    StatusPillBadge(
                        text = syncStatus.badgeText,
                        color = when (syncStatus) {
                            SyncStatus.TERSINKRONISASI -> EmeraldLong
                            SyncStatus.SINKRONISASI -> CyanAccent
                            SyncStatus.OFFLINE -> AmberWarning
                            else -> CrimsonShort
                        }
                    )
                }

                Text(
                    text = syncStatus.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                OutlinedButton(
                    onClick = onTriggerCloudSync,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_sync_button")
                ) {
                    Icon(Icons.Default.CloudSync, contentDescription = "Sinkronisasi Sekarang")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sinkronkan ke Cloud Sekarang")
                }

                if (syncConflicts.isNotEmpty()) {
                    Text(
                        text = "Konflik Sinkronisasi Membutuhkan Peninjauan (${syncConflicts.size}):",
                        style = MaterialTheme.typography.labelLarge,
                        color = AmberWarning,
                        fontWeight = FontWeight.Bold
                    )
                    syncConflicts.forEach { conflict ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(ElevatedCardSurface)
                                .border(1.dp, AmberWarning.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = conflict.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary
                            )
                            Text(
                                text = "Lokal: ${conflict.localSummary} | Cloud: ${conflict.remoteSummary}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = JetBrainsMonoFamily,
                                color = TextSecondary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { onResolveConflict(conflict, true) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Gunakan Lokal", style = MaterialTheme.typography.labelSmall)
                                }
                                Button(
                                    onClick = { onResolveConflict(conflict, false) },
                                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Gunakan Cloud", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Backup & Pemulihan (JSON, CSV, PDF)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardSurface)
                    .border(1.dp, SubtleBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Backup, Pemulihan & Ekspor Laporan",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Ekspor data lengkap (JSON), daftar sinyal (CSV), atau laporan PDF ringkas. Saat memulihkan, pilih mode Gabungkan (Merge) atau Ganti (Replace).",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onExportJson { json ->
                                isCsvModal = false
                                backupModalTitle = "Ekspor Backup Lengkap (JSON)"
                                backupModalData = json
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("export_json_button")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "Ekspor JSON")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ekspor JSON", style = MaterialTheme.typography.labelSmall)
                    }

                    OutlinedButton(
                        onClick = {
                            onExportCsv { csv ->
                                isCsvModal = true
                                backupModalTitle = "Ekspor Daftar Sinyal (CSV)"
                                backupModalData = csv
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("export_csv_button")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "Ekspor CSV")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ekspor CSV", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            isCsvModal = false
                            showImportDialog = true
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("import_json_button")
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = "Impor JSON")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pulihkan JSON", style = MaterialTheme.typography.labelSmall)
                    }

                    OutlinedButton(
                        onClick = {
                            isCsvModal = true
                            showImportDialog = true
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("import_csv_button")
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = "Impor CSV")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pulihkan CSV", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Button(
                    onClick = {
                        onGeneratePdf(context) { result ->
                            pdfResultMessage = if (result.isSuccess) {
                                "Laporan PDF berhasil dibuat: ${result.getOrNull()?.absolutePath}"
                            } else {
                                "Gagal membuat PDF: ${result.exceptionOrNull()?.localizedMessage}"
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("export_pdf_button")
                ) {
                    Icon(Icons.Default.Description, contentDescription = "Laporan PDF")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Buat Laporan PDF Ringkas", fontWeight = FontWeight.Bold)
                }

                if (pdfResultMessage != null) {
                    Text(
                        text = pdfResultMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = EmeraldLong
                    )
                }
            }
        }

        // 4. Akun Google, Keamanan & Penghapusan Akun
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardSurface)
                    .border(1.dp, SubtleBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = "Akun & Keamanan", tint = CyanAccent)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Akun Google & Keamanan Data",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Email: ${userEmail ?: "Pengguna Terautentikasi"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
                Text(
                    text = "UID Pemilik: ${userProfile.userId}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = JetBrainsMonoFamily,
                    color = TextSecondary
                )

                OutlinedButton(
                    onClick = onLogout,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("logout_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Logout")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Keluar (Logout) dari Akun Google")
                }

                Button(
                    onClick = { showDeleteConfirmDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonShort),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("delete_account_button")
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = "Hapus Akun & Data")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Hapus Seluruh Data & Akun", fontWeight = FontWeight.Bold)
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(72.dp))
        }
    }

    if (backupModalData != null) {
        AlertDialog(
            onDismissRequest = { backupModalData = null },
            containerColor = CardSurface,
            title = { Text(backupModalTitle) },
            text = {
                OutlinedTextField(
                    value = backupModalData!!,
                    onValueChange = {},
                    readOnly = true,
                    maxLines = 12,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("exported_data_text_field")
                )
            },
            confirmButton = {
                Button(onClick = { backupModalData = null }) {
                    Text("Tutup")
                }
            }
        )
    }

    if (showImportDialog) {
        var rawInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            containerColor = CardSurface,
            title = {
                Text(if (isCsvModal) "Pulihkan Sinyal dari CSV" else "Pulihkan Backup dari JSON")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Tempelkan konten ${if (isCsvModal) "CSV" else "JSON"} di bawah, lalu pilih mode pemulihan (Gabungkan atau Ganti Seluruh Data):",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = rawInput,
                        onValueChange = { rawInput = it },
                        placeholder = { Text(if (isCsvModal) "id,mentorId,mentorName,pair..." else "{ \"schemaVersion\": 1, ... }") },
                        maxLines = 10,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("import_data_input")
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            if (rawInput.isNotBlank()) {
                                if (isCsvModal) onImportCsv(rawInput, RestoreMode.MERGE)
                                else onImportJson(rawInput, RestoreMode.MERGE)
                                showImportDialog = false
                            }
                        },
                        modifier = Modifier.testTag("confirm_merge_restore_button")
                    ) {
                        Text("Gabungkan (Merge)")
                    }
                    Button(
                        onClick = {
                            if (rawInput.isNotBlank()) {
                                if (isCsvModal) onImportCsv(rawInput, RestoreMode.REPLACE)
                                else onImportJson(rawInput, RestoreMode.REPLACE)
                                showImportDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonShort),
                        modifier = Modifier.testTag("confirm_replace_restore_button")
                    ) {
                        Text("Ganti (Replace)")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            containerColor = CardSurface,
            title = { Text("Konfirmasi Penghapusan Data & Akun") },
            text = {
                Text(
                    text = "Tindakan ini akan menghapus seluruh sinyal, mentor, histori peristiwa, dan transaksi Anda di perangkat lokal maupun Cloud Firestore secara permanen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeleteAccountAndData()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonShort),
                    modifier = Modifier.testTag("confirm_delete_account_button")
                ) {
                    Text("Hapus Permanen")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}
