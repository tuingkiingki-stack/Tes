package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.SignalEventEntity
import com.example.data.local.TradingSignalEntity
import com.example.domain.logic.PriceMonitoringEngine
import com.example.domain.model.LevelDistanceInfo
import com.example.domain.model.NotificationDeliveryStatus
import com.example.domain.model.PriceQuote
import com.example.domain.model.PriceSourceType
import com.example.domain.model.SignalDirection
import com.example.domain.model.SignalOutcome
import com.example.domain.model.SignalStatus
import com.example.ui.components.TradingViewSignalChartSection
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CardSurface
import com.example.ui.theme.CrimsonShort
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.ElevatedCardSurface
import com.example.ui.theme.EmeraldLong
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.NavyBackground
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SignalDetailScreen(
    signal: TradingSignalEntity,
    events: List<SignalEventEntity>,
    liveQuote: PriceQuote?,
    isOnline: Boolean,
    onBack: () -> Unit,
    onEditSignal: (TradingSignalEntity) -> Unit,
    onCancelSignal: (TradingSignalEntity) -> Unit,
    onCloseBreakeven: (TradingSignalEntity) -> Unit,
    onArchiveSignal: (TradingSignalEntity) -> Unit,
    onRestoreSignal: (TradingSignalEntity) -> Unit,
    onRefreshPrice: () -> Unit,
    onRetryNotification: (SignalEventEntity) -> Unit
) {
    BackHandler { onBack() }

    val isLong = signal.direction == SignalDirection.LONG.name
    val dirColor = if (isLong) EmeraldLong else CrimsonShort
    val currentPrice = liveQuote?.price ?: signal.lastCheckedPrice
    val lastUpdatedMillis = liveQuote?.timestampMillis ?: signal.lastCheckedAtMillis
    val sourceLabel = liveQuote?.source?.label ?: try {
        PriceSourceType.valueOf(signal.priceSource).label
    } catch (_: Exception) {
        signal.priceSource
    }

    val distances = remember(signal, currentPrice) {
        PriceMonitoringEngine.computeLevelDistances(signal, currentPrice)
    }

    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyBackground)
            .testTag("signal_detail_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali",
                            tint = TextPrimary
                        )
                    }
                    Text(
                        text = "Detail Sinyal ${signal.pair}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = onRefreshPrice,
                    modifier = Modifier.testTag("detail_refresh_price_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Segarkan Harga",
                        tint = CyanAccent
                    )
                }
            }
        }

        // 1. RINGKASAN SINYAL
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardSurface)
                    .border(1.dp, SubtleBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp)
                    .testTag("detail_section_1_summary"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = dirColor.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = signal.direction,
                                style = MaterialTheme.typography.titleSmall,
                                color = dirColor,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                        Column {
                            Text(
                                text = signal.pair,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Mentor: ${signal.mentorName} (ID: ${signal.mentorId.take(10)})",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }

                    StatusPillBadge(
                        text = "${signal.status} • Rev #${signal.revision}",
                        color = if (signal.status == SignalStatus.ACTIVE.name) CyanAccent else TextSecondary
                    )
                }

                Text(
                    text = "Waktu Sinyal: ${dateFormat.format(Date(signal.signalTimestampMillis))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                // Action buttons based on lifecycle state
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (signal.status == SignalStatus.ACTIVE.name) {
                        OutlinedButton(
                            onClick = { onEditSignal(signal) },
                            modifier = Modifier.testTag("detail_edit_signal_button")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Sinyal", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit Level")
                        }

                        if (signal.reachedTpIndices.isEmpty() && !signal.slHit) {
                            OutlinedButton(
                                onClick = { onCloseBreakeven(signal) },
                                modifier = Modifier.testTag("detail_breakeven_button")
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "Tutup Impas", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Tutup Impas (BE)")
                            }
                        }

                        OutlinedButton(
                            onClick = { onCancelSignal(signal) },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonShort),
                            modifier = Modifier.testTag("detail_cancel_signal_button")
                        ) {
                            Icon(Icons.Default.Cancel, contentDescription = "Batalkan Sinyal", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Batalkan (Server)")
                        }
                    }

                    if (signal.status != SignalStatus.ARCHIVED.name) {
                        OutlinedButton(
                            onClick = { onArchiveSignal(signal) },
                            modifier = Modifier.testTag("detail_archive_button")
                        ) {
                            Icon(Icons.Default.Archive, contentDescription = "Arsipkan", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Arsipkan")
                        }
                    } else {
                        Button(
                            onClick = { onRestoreSignal(signal) },
                            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                            modifier = Modifier.testTag("detail_restore_button")
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = "Pulihkan dari Arsip", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Pulihkan & Pantau")
                        }
                    }
                }
            }
        }

        // 2. GRAFIK PAIR (TradingView Candlestick + Synchronized Level Panel)
        item {
            TradingViewSignalChartSection(
                signal = signal,
                isOnline = isOnline
            )
        }

        // 3. HARGA TERAKHIR BESERTA SUMBER DAN WAKTU PEMBARUAN
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardSurface)
                    .border(1.dp, SubtleBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp)
                    .testTag("detail_section_3_last_price"),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "3. Harga Terakhir & Sumber Verifikasi",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (currentPrice > 0.0) "$currentPrice USDT" else "Menunggu pembaruan harga...",
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = JetBrainsMonoFamily,
                        color = CyanAccent,
                        fontWeight = FontWeight.Bold
                    )
                    StatusPillBadge(text = sourceLabel, color = CyanAccent)
                }
                Text(
                    text = if (lastUpdatedMillis > 0L) {
                        "Waktu Pembaruan: ${dateFormat.format(Date(lastUpdatedMillis))}"
                    } else {
                        "Waktu Pembaruan: Belum ada sampel harga"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        // 4. DAFTAR LEVEL ENTRY
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardSurface)
                    .border(1.dp, SubtleBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp)
                    .testTag("detail_section_4_entries"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "4. Daftar Level Entry (${signal.reachedEntryIndices.size}/${signal.entryPrices.size} Tersentuh)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                signal.entryPrices.forEachIndexed { idx, price ->
                    val isHit = idx in signal.reachedEntryIndices
                    val isMissed = idx in signal.missedEntryIndices
                    val statusText = when {
                        isHit -> "TERSENTUH"
                        isMissed -> "TIDAK TERCAPAI"
                        else -> "MENUNGGU SENTUHAN"
                    }
                    val badgeColor = when {
                        isHit -> CyanAccent
                        isMissed -> AmberWarning
                        else -> TextSecondary
                    }
                    LevelStatusRow(
                        label = "Entry ${idx + 1}",
                        price = price,
                        statusText = statusText,
                        badgeColor = badgeColor
                    )
                }
            }
        }

        // 5. DAFTAR TP
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardSurface)
                    .border(1.dp, SubtleBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp)
                    .testTag("detail_section_5_tps"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "5. Daftar Target Take Profit (${signal.reachedTpIndices.size}/${signal.tpPrices.size} Tercapai)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                signal.tpPrices.forEachIndexed { idx, price ->
                    val isHit = idx in signal.reachedTpIndices
                    LevelStatusRow(
                        label = "TP${idx + 1}",
                        price = price,
                        statusText = if (isHit) "TERCAPAI" else "TARGET AKTIF",
                        badgeColor = if (isHit) EmeraldLong else TextSecondary
                    )
                }
            }
        }

        // 6. LEVEL SL
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardSurface)
                    .border(1.dp, SubtleBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp)
                    .testTag("detail_section_6_sl"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "6. Level Stop Loss (SL)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                LevelStatusRow(
                    label = "Stop Loss (SL)",
                    price = signal.slPrice,
                    statusText = if (signal.slHit) "SL TERSENTUH" else "BATAS RISIKO AKTIF",
                    badgeColor = CrimsonShort
                )
            }
        }

        // 7. JARAK HARGA MENUJU ENTRY, TP BERIKUTNYA, DAN SL (HARGA & PERSENTASE)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardSurface)
                    .border(1.dp, SubtleBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp)
                    .testTag("detail_section_7_distances"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "7. Jarak Harga menuju Entry, TP Berikutnya & SL",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (distances.isEmpty()) {
                    Text(
                        text = "Jarak harga dan persentase akan dihitung otomatis segera setelah harga terakhir tersedia.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                } else {
                    distances.forEach { d ->
                        DistanceInfoRow(info = d)
                    }
                }
            }
        }

        // 8. STATUS HASIL SINYAL
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardSurface)
                    .border(1.dp, SubtleBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp)
                    .testTag("detail_section_8_outcome"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "8. Status Hasil Sinyal",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                val outcomeLabel = try {
                    SignalOutcome.valueOf(signal.outcome).label
                } catch (_: Exception) {
                    signal.outcome
                }
                val outcomeColor = when (signal.outcome) {
                    SignalOutcome.WIN.name -> EmeraldLong
                    SignalOutcome.LOSS.name -> CrimsonShort
                    SignalOutcome.BREAKEVEN.name -> AmberWarning
                    else -> CyanAccent
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Hasil Akhir: $outcomeLabel",
                        style = MaterialTheme.typography.titleMedium,
                        color = outcomeColor,
                        fontWeight = FontWeight.Bold
                    )
                    if (signal.outcomeSource.isNotBlank()) {
                        StatusPillBadge(text = "Sumber: ${signal.outcomeSource}", color = outcomeColor)
                    }
                }

                if (signal.returnedToEntry) {
                    StatusPillBadge(
                        text = "KEMBALI KE ENTRY — Harga sempat kembali ke level Entry sebelum TP tercapai",
                        color = AmberWarning
                    )
                }
                if (signal.isConservativeEstimation) {
                    StatusPillBadge(
                        text = "ESTIMASI KONSERVATIF — Jeda data melewati TP & SL bersamaan (SL didahulukan)",
                        color = AmberWarning
                    )
                }
                if (signal.hasSourceConflict) {
                    StatusPillBadge(
                        text = "REFERENSI COINGECKO — TIDAK TERVERIFIKASI BINANCE",
                        color = AmberWarning
                    )
                }
            }
        }

        // 9. TIMELINE PERISTIWA
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.testTag("detail_section_9_timeline_header")
            ) {
                Icon(Icons.Default.History, contentDescription = "Timeline Peristiwa", tint = CyanAccent)
                Text(
                    text = "9. Timeline Peristiwa (${events.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (events.isEmpty()) {
            item {
                Text(
                    text = "Belum ada peristiwa tercatat untuk sinyal ini.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        } else {
            items(events, key = { it.id }) { ev ->
                TimelineEventCard(
                    event = ev,
                    dateFormat = dateFormat,
                    onRetryNotification = { onRetryNotification(ev) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(64.dp))
        }
    }
}

@Composable
private fun LevelStatusRow(
    label: String,
    price: Double,
    statusText: String,
    badgeColor: androidx.compose.ui.graphics.Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(ElevatedCardSurface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = label, style = MaterialTheme.typography.labelLarge, color = TextPrimary)
            Text(
                text = "$price USDT",
                style = MaterialTheme.typography.titleSmall,
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold,
                color = CyanAccent
            )
        }
        StatusPillBadge(text = statusText, color = badgeColor)
    }
}

@Composable
private fun DistanceInfoRow(info: LevelDistanceInfo) {
    val sign = if (info.priceDiff >= 0) "+" else ""
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(ElevatedCardSurface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = info.label, style = MaterialTheme.typography.labelMedium, color = TextPrimary)
            Text(
                text = "Target: ${info.targetPrice}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = JetBrainsMonoFamily,
                color = TextSecondary
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "$sign${String.format("%.4f", info.priceDiff)} (${sign}${String.format("%.2f", info.percentDiff)}%)",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = JetBrainsMonoFamily,
                color = if (info.isReached) EmeraldLong else if (info.isMissed) AmberWarning else CyanAccent,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = when {
                    info.isReached -> "Sudah Tercapai"
                    info.isMissed -> "Tidak Tercapai"
                    else -> "Dari harga saat ini"
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }
}

@Composable
private fun TimelineEventCard(
    event: SignalEventEntity,
    dateFormat: SimpleDateFormat,
    onRetryNotification: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardSurface)
            .border(1.dp, SubtleBorder, RoundedCornerShape(12.dp))
            .padding(12.dp)
            .testTag("timeline_event_${event.id}"),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(10.dp)
                .clip(CircleShape)
                .background(CyanAccent)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = event.eventType,
                    style = MaterialTheme.typography.labelLarge,
                    color = CyanAccent,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = dateFormat.format(Date(event.eventTimestampMillis)),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
            Text(
                text = event.message,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Harga: ${event.price} • Sumber: ${event.priceSource}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = JetBrainsMonoFamily,
                    color = TextSecondary
                )
                if (event.notificationStatus == NotificationDeliveryStatus.FAILED.name ||
                    event.notificationStatus == NotificationDeliveryStatus.PENDING.name
                ) {
                    OutlinedButton(
                        onClick = onRetryNotification,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("retry_notification_button_${event.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = "Kirim Ulang Notifikasi",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Kirim Ulang Notifikasi", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
