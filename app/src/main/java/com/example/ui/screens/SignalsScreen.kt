package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.TradingSignalEntity
import com.example.domain.model.PriceQuote
import com.example.domain.model.PriceSourceType
import com.example.domain.model.SignalDirection
import com.example.domain.model.SignalOutcome
import com.example.domain.model.SignalStatus
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SignalsScreen(
    signals: List<TradingSignalEntity>,
    latestQuotes: Map<String, PriceQuote>,
    onSignalClick: (String) -> Unit,
    onAddSignalClick: () -> Unit
) {
    // Tabs: 0 = Aktif, 1 = Selesai, 2 = Riwayat (Semua termasuk Dibatalkan & Arsip)
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedMentorFilter by remember { mutableStateOf<String?>(null) }
    var selectedOutcomeFilter by remember { mutableStateOf<String?>(null) }
    var selectedSourceFilter by remember { mutableStateOf<String?>(null) }

    val availableMentors = remember(signals) {
        signals.map { it.mentorName }.distinct().sorted()
    }

    val filteredSignals = remember(
        signals,
        selectedTabIndex,
        searchQuery,
        selectedMentorFilter,
        selectedOutcomeFilter,
        selectedSourceFilter
    ) {
        signals.filter { sig ->
            val tabMatches = when (selectedTabIndex) {
                0 -> sig.status == SignalStatus.ACTIVE.name
                1 -> sig.status == SignalStatus.COMPLETED.name
                else -> true // Riwayat menampilkan seluruh sinyal (Aktif, Selesai, Dibatalkan, Arsip)
            }
            val queryMatches = if (searchQuery.isBlank()) {
                true
            } else {
                val q = searchQuery.trim().lowercase()
                val dateStr = SimpleDateFormat("yyyy-MM-dd dd MMM yyyy", Locale.getDefault())
                    .format(Date(sig.signalTimestampMillis)).lowercase()
                sig.pair.lowercase().contains(q) ||
                    sig.mentorName.lowercase().contains(q) ||
                    sig.status.lowercase().contains(q) ||
                    sig.outcome.lowercase().contains(q) ||
                    dateStr.contains(q)
            }
            val mentorMatches = selectedMentorFilter == null || sig.mentorName == selectedMentorFilter
            val outcomeMatches = selectedOutcomeFilter == null || sig.outcome == selectedOutcomeFilter
            val sourceMatches = selectedSourceFilter == null || sig.priceSource == selectedSourceFilter || sig.outcomeSource == selectedSourceFilter

            tabMatches && queryMatches && mentorMatches && outcomeMatches && sourceMatches
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyBackground)
            .testTag("signals_screen")
    ) {
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = CardSurface,
            contentColor = EmeraldLong
        ) {
            listOf("Aktif", "Selesai", "Riwayat").forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = {
                        Text(
                            text = title,
                            fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    modifier = Modifier.testTag("signals_tab_$title")
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardSurface)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Cari pair (BTC/USDT), mentor, tanggal (YYYY-MM-DD)...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Cari") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Hapus pencarian")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("signals_search_input")
            )

            // Filter chips row: Mentor, Hasil, Sumber Data
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChipButton(
                    label = if (selectedOutcomeFilter == null) "Semua Hasil" else "Hasil: $selectedOutcomeFilter",
                    active = selectedOutcomeFilter != null,
                    onClick = {
                        selectedOutcomeFilter = when (selectedOutcomeFilter) {
                            null -> SignalOutcome.WIN.name
                            SignalOutcome.WIN.name -> SignalOutcome.LOSS.name
                            SignalOutcome.LOSS.name -> SignalOutcome.BREAKEVEN.name
                            else -> null
                        }
                    }
                )

                FilterChipButton(
                    label = if (selectedSourceFilter == null) "Semua Sumber" else "Sumber: $selectedSourceFilter",
                    active = selectedSourceFilter != null,
                    onClick = {
                        selectedSourceFilter = when (selectedSourceFilter) {
                            null -> PriceSourceType.BINANCE_FUTURES.name
                            PriceSourceType.BINANCE_FUTURES.name -> PriceSourceType.BINANCE_SPOT.name
                            PriceSourceType.BINANCE_SPOT.name -> PriceSourceType.COINGECKO.name
                            else -> null
                        }
                    }
                )

                availableMentors.forEach { mentor ->
                    FilterChipButton(
                        label = "Mentor: $mentor",
                        active = selectedMentorFilter == mentor,
                        onClick = {
                            selectedMentorFilter = if (selectedMentorFilter == mentor) null else mentor
                        }
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (filteredSignals.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(CardSurface)
                            .border(1.dp, SubtleBorder, RoundedCornerShape(16.dp))
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Tidak Ada Sinyal Ditemukan",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tambahkan sinyal baru atau ubah kata kunci pencarian dan filter.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = onAddSignalClick,
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldLong)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Tambah Sinyal")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Tambah Sinyal Baru")
                        }
                    }
                }
            } else {
                items(filteredSignals, key = { it.id }) { sig ->
                    SignalFullListCard(
                        signal = sig,
                        liveQuote = latestQuotes[sig.pair],
                        onClick = { onSignalClick(sig.id) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }
}

@Composable
private fun FilterChipButton(
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (active) CyanAccent.copy(alpha = 0.2f) else ElevatedCardSurface,
        modifier = Modifier
            .border(1.dp, if (active) CyanAccent else SubtleBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) CyanAccent else TextSecondary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun SignalFullListCard(
    signal: TradingSignalEntity,
    liveQuote: PriceQuote?,
    onClick: () -> Unit
) {
    val isLong = signal.direction == SignalDirection.LONG.name
    val dirColor = if (isLong) EmeraldLong else CrimsonShort
    val displayPrice = liveQuote?.price ?: signal.lastCheckedPrice
    val displaySource = liveQuote?.source?.label ?: try {
        PriceSourceType.valueOf(signal.priceSource).label
    } catch (_: Exception) {
        signal.priceSource
    }
    val dateFormatted = remember(signal.signalTimestampMillis) {
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(signal.signalTimestampMillis))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardSurface)
            .border(1.dp, SubtleBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
            .testTag("signal_list_item_${signal.id}"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = dirColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = signal.direction,
                        style = MaterialTheme.typography.labelMedium,
                        color = dirColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Column {
                    Text(
                        text = signal.pair,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Mentor: ${signal.mentorName} • $dateFormatted",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            StatusPillBadge(
                text = "${signal.status} (${signal.outcome})",
                color = when (signal.outcome) {
                    SignalOutcome.WIN.name -> EmeraldLong
                    SignalOutcome.LOSS.name -> CrimsonShort
                    SignalOutcome.BREAKEVEN.name -> AmberWarning
                    else -> CyanAccent
                }
            )
        }

        // Entry, TP, SL Grid
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(ElevatedCardSurface)
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Entry (${signal.reachedEntryIndices.size}/${signal.entryPrices.size})", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Text(
                    text = signal.entryPrices.joinToString(", "),
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = JetBrainsMonoFamily,
                    color = TextPrimary
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("TP (${signal.reachedTpIndices.size}/${signal.tpPrices.size})", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Text(
                    text = signal.tpPrices.joinToString(", "),
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = JetBrainsMonoFamily,
                    color = EmeraldLong
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("SL", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Text(
                    text = "${signal.slPrice}",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = JetBrainsMonoFamily,
                    color = CrimsonShort
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (displayPrice > 0.0) "Harga Terakhir: $displayPrice" else "Harga Terakhir: Menunggu data",
                    style = MaterialTheme.typography.labelMedium,
                    color = CyanAccent
                )
                Text(
                    text = "Sumber: $displaySource",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            OutlinedButton(
                onClick = onClick,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.testTag("open_chart_button_${signal.id}")
            ) {
                Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = "Grafik & Detail")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Grafik & Detail", style = MaterialTheme.typography.labelSmall)
            }
        }

        if (signal.returnedToEntry || signal.isConservativeEstimation || signal.hasSourceConflict) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (signal.returnedToEntry) {
                    StatusPillBadge("KEMBALI KE ENTRY", AmberWarning)
                }
                if (signal.isConservativeEstimation) {
                    StatusPillBadge("ESTIMASI KONSERVATIF", AmberWarning)
                }
                if (signal.hasSourceConflict) {
                    StatusPillBadge("REFERENSI COINGECKO — TIDAK TERVERIFIKASI BINANCE", AmberWarning)
                }
            }
        }
    }
}
