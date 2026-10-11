package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.TradingSignalEntity
import com.example.domain.model.FeedConnectionStatus
import com.example.domain.model.SignalStatus
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.MentorScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SignalDetailScreen
import com.example.ui.screens.SignalFormDialog
import com.example.ui.screens.SignalsScreen
import com.example.ui.theme.CardSurface
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.ElevatedCardSurface
import com.example.ui.theme.EmeraldLong
import com.example.ui.theme.NavyBackground
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

enum class MainNavTab(val label: String, val icon: ImageVector, val testTag: String) {
    DASHBOARD("Dashboard", Icons.Default.Dashboard, "nav_tab_dashboard"),
    SINYAL("Sinyal", Icons.AutoMirrored.Filled.ShowChart, "nav_tab_sinyal"),
    MENTOR("Mentor", Icons.Default.EmojiEvents, "nav_tab_mentor"),
    PENGATURAN("Pengaturan", Icons.Default.Settings, "nav_tab_pengaturan")
}

@Composable
fun JournalAppContent(
    viewModel: JournalViewModel,
    onLogout: () -> Unit
) {
    val summary by viewModel.dashboardSummary.collectAsStateWithLifecycle()
    val mentorRankings by viewModel.mentorRankings.collectAsStateWithLifecycle()
    val signals by viewModel.signals.collectAsStateWithLifecycle()
    val mentors by viewModel.mentors.collectAsStateWithLifecycle()
    val allEvents by viewModel.allEvents.collectAsStateWithLifecycle()
    val latestQuotes by viewModel.latestPairQuotes.collectAsStateWithLifecycle()
    val feedStatus by viewModel.feedConnectionStatus.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val syncConflicts by viewModel.syncConflicts.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val selectedPeriodDays by viewModel.selectedPeriodDays.collectAsStateWithLifecycle()
    val bannerMessage by viewModel.statusBannerMessage.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(MainNavTab.DASHBOARD) }
    var selectedSignalIdForDetail by remember { mutableStateOf<String?>(null) }
    var showSignalFormDialog by remember { mutableStateOf(false) }
    var editingSignal by remember { mutableStateOf<TradingSignalEntity?>(null) }

    val activeSignals = remember(signals) {
        signals.filter { it.status == SignalStatus.ACTIVE.name }
    }

    val selectedDetailSignal = remember(signals, selectedSignalIdForDetail) {
        signals.firstOrNull { it.id == selectedSignalIdForDetail }
    }

    BackHandler(enabled = selectedSignalIdForDetail != null || currentTab != MainNavTab.DASHBOARD) {
        if (selectedSignalIdForDetail != null) {
            selectedSignalIdForDetail = null
        } else {
            currentTab = MainNavTab.DASHBOARD
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpandedScreen = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = NavyBackground,
            floatingActionButton = {
                if (selectedDetailSignal == null && (currentTab == MainNavTab.DASHBOARD || currentTab == MainNavTab.SINYAL)) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            editingSignal = null
                            showSignalFormDialog = true
                        },
                        containerColor = EmeraldLong,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        icon = { Icon(Icons.Default.Add, contentDescription = "Tambah Sinyal") },
                        text = { Text("Tambah Sinyal", fontWeight = FontWeight.Bold) },
                        modifier = Modifier.testTag("fab_add_signal")
                    )
                }
            },
            bottomBar = {
                if (!isExpandedScreen && selectedDetailSignal == null) {
                    NavigationBar(
                        containerColor = CardSurface,
                        contentColor = TextPrimary
                    ) {
                        MainNavTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    selectedSignalIdForDetail = null
                                    currentTab = tab
                                },
                                icon = {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = EmeraldLong,
                                    selectedTextColor = EmeraldLong,
                                    indicatorColor = EmeraldLong.copy(alpha = 0.16f),
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isExpandedScreen) {
                    NavigationRail(
                        containerColor = CardSurface,
                        contentColor = TextPrimary,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        MainNavTab.entries.forEach { tab ->
                            val selected = currentTab == tab && selectedDetailSignal == null
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    selectedSignalIdForDetail = null
                                    currentTab = tab
                                },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    // Global status notification banner
                    if (bannerMessage != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(ElevatedCardSurface)
                                .border(1.dp, CyanAccent, RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .testTag("global_status_banner"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = bannerMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = { viewModel.clearBannerMessage() }) {
                                Icon(Icons.Default.Close, contentDescription = "Tutup pesan", tint = TextSecondary)
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        if (selectedDetailSignal != null) {
                            val signalEvents = remember(allEvents, selectedDetailSignal.id) {
                                allEvents.filter { it.signalId == selectedDetailSignal.id }
                                    .sortedByDescending { it.eventTimestampMillis }
                            }
                            SignalDetailScreen(
                                signal = selectedDetailSignal,
                                events = signalEvents,
                                liveQuote = latestQuotes[selectedDetailSignal.pair],
                                isOnline = feedStatus != FeedConnectionStatus.OFFLINE,
                                onBack = { selectedSignalIdForDetail = null },
                                onEditSignal = { sig ->
                                    editingSignal = sig
                                    showSignalFormDialog = true
                                },
                                onCancelSignal = { sig ->
                                    viewModel.cancelSignalWithServerConfirmation(sig)
                                },
                                onCloseBreakeven = { sig ->
                                    viewModel.closeSignalAsBreakeven(sig)
                                },
                                onArchiveSignal = { sig ->
                                    viewModel.archiveSignal(sig)
                                },
                                onRestoreSignal = { sig ->
                                    viewModel.restoreArchivedSignal(sig)
                                },
                                onRefreshPrice = { viewModel.triggerManualPriceCheck() },
                                onRetryNotification = { ev -> viewModel.retryNotification(ev) }
                            )
                        } else {
                            when (currentTab) {
                                MainNavTab.DASHBOARD -> {
                                    DashboardScreen(
                                        summary = summary,
                                        mentorRankings = mentorRankings,
                                        activeSignals = activeSignals,
                                        latestQuotes = latestQuotes,
                                        feedStatus = feedStatus,
                                        syncStatus = syncStatus,
                                        selectedPeriodDays = selectedPeriodDays,
                                        onSelectPeriodDays = { viewModel.setPeriodDays(it) },
                                        onRefreshPrices = { viewModel.triggerManualPriceCheck() },
                                        onAddSignalClick = {
                                            editingSignal = null
                                            showSignalFormDialog = true
                                        },
                                        onSignalClick = { sigId -> selectedSignalIdForDetail = sigId },
                                        onRecordRealizedTrade = { pair, dir, entry, exit, size, fee ->
                                            viewModel.recordRealizedTrade(
                                                signalId = "",
                                                pair = pair,
                                                direction = dir,
                                                entryPrice = entry,
                                                exitPrice = exit,
                                                positionSizeUsdt = size,
                                                feeUsdt = fee
                                            )
                                        }
                                    )
                                }

                                MainNavTab.SINYAL -> {
                                    SignalsScreen(
                                        signals = signals,
                                        latestQuotes = latestQuotes,
                                        onSignalClick = { sigId -> selectedSignalIdForDetail = sigId },
                                        onAddSignalClick = {
                                            editingSignal = null
                                            showSignalFormDialog = true
                                        }
                                    )
                                }

                                MainNavTab.MENTOR -> {
                                    MentorScreen(
                                        mentorRankings = mentorRankings,
                                        selectedPeriodDays = selectedPeriodDays,
                                        onSelectPeriodDays = { viewModel.setPeriodDays(it) },
                                        onSaveMentor = { name, handle, spec ->
                                            viewModel.saveMentor(name, handle, spec)
                                        }
                                    )
                                }

                                MainNavTab.PENGATURAN -> {
                                    SettingsScreen(
                                        userProfile = userProfile,
                                        userEmail = viewModel.userEmail,
                                        syncStatus = syncStatus,
                                        syncConflicts = syncConflicts,
                                        onSaveSettings = { cap, src, stale, notif ->
                                            viewModel.updateSettings(cap, src, stale, notif)
                                        },
                                        onTriggerCloudSync = { viewModel.triggerManualCloudSync() },
                                        onResolveConflict = { conflict, keepLocal ->
                                            viewModel.resolveSyncConflict(conflict, keepLocal)
                                        },
                                        onExportJson = { cb -> viewModel.exportBackupJson(cb) },
                                        onImportJson = { json, mode -> viewModel.importBackupJson(json, mode) },
                                        onExportCsv = { cb -> viewModel.exportBackupCsv(cb) },
                                        onImportCsv = { csv, mode -> viewModel.importBackupCsv(csv, mode) },
                                        onGeneratePdf = { ctx, cb -> viewModel.generatePdfReport(ctx, cb) },
                                        onLogout = onLogout,
                                        onDeleteAccountAndData = {
                                            viewModel.deleteAccountAndAllData {
                                                onLogout()
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSignalFormDialog) {
        SignalFormDialog(
            existingSignal = editingSignal,
            existingMentors = mentors,
            onDismiss = {
                showSignalFormDialog = false
                editingSignal = null
            },
            onSave = { existingId, mentorId, mentorName, pair, dir, ts, entries, tps, sl ->
                viewModel.saveSignal(
                    existingSignalId = existingId,
                    mentorIdInput = mentorId,
                    mentorNameInput = mentorName,
                    pairInput = pair,
                    direction = dir,
                    signalTimestampMillis = ts,
                    entryPrices = entries,
                    tpPrices = tps,
                    slPrice = sl,
                    onSuccess = {
                        showSignalFormDialog = false
                        editingSignal = null
                    },
                    onError = {}
                )
            }
        )
    }
}
