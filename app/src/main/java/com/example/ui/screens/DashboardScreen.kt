package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.TradingSignalEntity
import com.example.domain.model.DashboardSummary
import com.example.domain.model.FeedConnectionStatus
import com.example.domain.model.MentorStats
import com.example.domain.model.PriceQuote
import com.example.domain.model.SignalDirection
import com.example.domain.model.SignalStatus
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
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun DashboardScreen(
    summary: DashboardSummary,
    mentorRankings: List<MentorStats>,
    activeSignals: List<TradingSignalEntity>,
    latestQuotes: Map<String, PriceQuote>,
    feedStatus: FeedConnectionStatus,
    syncStatus: SyncStatus,
    selectedPeriodDays: Int,
    onSelectPeriodDays: (Int) -> Unit,
    onRefreshPrices: () -> Unit,
    onAddSignalClick: () -> Unit,
    onSignalClick: (String) -> Unit,
    onRecordRealizedTrade: (String, SignalDirection, Double, Double, Double, Double) -> Unit
) {
    var showRealizedTradeDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyBackground)
            .testTag("dashboard_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Header Banner + Feed & Sync Status
        item {
            DashboardHeroBanner(
                feedStatus = feedStatus,
                syncStatus = syncStatus,
                selectedPeriodDays = selectedPeriodDays,
                onSelectPeriodDays = onSelectPeriodDays,
                onRefreshPrices = onRefreshPrices
            )
        }

        // 2. Connection / Feed Warning Banner if offline or Binance unavailable
        if (feedStatus != FeedConnectionStatus.TERHUBUNG) {
            item {
                val bannerColor = when (feedStatus) {
                    FeedConnectionStatus.OFFLINE -> CrimsonShort
                    FeedConnectionStatus.DATA_BINANCE_TIDAK_TERSEDIA -> AmberWarning
                    FeedConnectionStatus.HARGA_KEDALUWARSA -> AmberWarning
                    else -> CyanAccent
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(bannerColor.copy(alpha = 0.16f))
                        .border(1.dp, bannerColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                        .testTag("feed_status_warning_banner"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (feedStatus == FeedConnectionStatus.OFFLINE) Icons.Default.CloudOff else Icons.Default.Warning,
                        contentDescription = feedStatus.label,
                        tint = bannerColor
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = feedStatus.label,
                            style = MaterialTheme.typography.titleSmall,
                            color = bannerColor,
                            fontWeight = FontWeight.Bold
                        )
                        val detail = when (feedStatus) {
                            FeedConnectionStatus.OFFLINE -> "Internet terputus. Data disimpan lokal & monitoring akan dilanjutkan saat koneksi kembali."
                            FeedConnectionStatus.DATA_BINANCE_TIDAK_TERSEDIA -> "Feed Binance sedang tidak dapat diakses. Menggunakan referensi alternatif CoinGecko bila tersedia."
                            FeedConnectionStatus.HARGA_KEDALUWARSA -> "Data harga melebihi batas umur valid. Data kedaluwarsa tidak digunakan untuk konfirmasi sentuhan level baru."
                            else -> ""
                        }
                        Text(text = detail, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                    }
                }
            }
        }

        // 3. Capital, Realized Balance & P/L Card (Strictly separated from Signal Estimation per Section 10)
        item {
            CapitalAndPnlSection(
                summary = summary,
                onOpenRecordTradeDialog = { showRealizedTradeDialog = true }
            )
        }

        // 4. Multi-Source Win Rate & Signal Metrics Grid
        item {
            WinRateAndMetricsSection(summary = summary)
        }

        // 5. Visual Performance Chart
        item {
            PerformanceDistributionChartCard(summary = summary)
        }

        // 6. Mentor Ranking Section
        item {
            MentorRankingPreviewCard(mentorRankings = mentorRankings)
        }

        // 7. Recent Active Signals Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sinyal Aktif Terbaru (${activeSignals.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(
                    onClick = onAddSignalClick,
                    modifier = Modifier.testTag("dashboard_add_signal_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Tambah Sinyal")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Tambah Sinyal")
                }
            }
        }

        if (activeSignals.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(CardSurface)
                        .border(1.dp, SubtleBorder, RoundedCornerShape(16.dp))
                        .padding(24.dp)
                        .testTag("empty_active_signals_card"),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Belum Ada Sinyal Aktif",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tambahkan sinyal trading dari mentor untuk memantau Entry, TP, dan SL secara otomatis.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        } else {
            items(activeSignals.take(5), key = { it.id }) { sig ->
                SignalCompactCard(
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

    if (showRealizedTradeDialog) {
        RecordRealizedTradeDialog(
            onDismiss = { showRealizedTradeDialog = false },
            onSave = { pair, dir, entry, exit, size, fee ->
                onRecordRealizedTrade(pair, dir, entry, exit, size, fee)
                showRealizedTradeDialog = false
            }
        )
    }
}

@Composable
private fun DashboardHeroBanner(
    feedStatus: FeedConnectionStatus,
    syncStatus: SyncStatus,
    selectedPeriodDays: Int,
    onSelectPeriodDays: (Int) -> Unit,
    onRefreshPrices: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CardSurface)
            .border(1.dp, SubtleBorder, RoundedCornerShape(18.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(115.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_dashboard_hero_1791658244363),
                contentDescription = "Header Dashboard",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                NavyBackground.copy(alpha = 0.35f),
                                CardSurface.copy(alpha = 0.95f)
                            )
                        )
                    )
                    .padding(14.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusPillBadge(
                            text = syncStatus.badgeText,
                            color = when (syncStatus) {
                                SyncStatus.TERSINKRONISASI -> EmeraldLong
                                SyncStatus.SINKRONISASI -> CyanAccent
                                SyncStatus.OFFLINE -> AmberWarning
                                else -> CrimsonShort
                            }
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            StatusPillBadge(
                                text = feedStatus.label,
                                color = when (feedStatus) {
                                    FeedConnectionStatus.TERHUBUNG -> EmeraldLong
                                    FeedConnectionStatus.OFFLINE -> CrimsonShort
                                    else -> AmberWarning
                                }
                            )
                            IconButton(
                                onClick = onRefreshPrices,
                                modifier = Modifier
                                    .size(34.dp)
                                    .testTag("refresh_prices_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Perbarui Harga",
                                    tint = TextPrimary
                                )
                            }
                        }
                    }

                    Text(
                        text = "Ringkasan Performa & Portofolio",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Period selector row (Default: 30 Hari)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Periode Statistik:",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
            listOf(7 to "7 Hari", 30 to "30 Hari (Default)", 90 to "90 Hari", 0 to "Semua").forEach { (days, label) ->
                val selected = selectedPeriodDays == days
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (selected) EmeraldLong.copy(alpha = 0.2f) else ElevatedCardSurface,
                    modifier = Modifier
                        .border(
                            1.dp,
                            if (selected) EmeraldLong else SubtleBorder,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { onSelectPeriodDays(days) }
                        .testTag("period_chip_$days")
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) EmeraldLong else TextSecondary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CapitalAndPnlSection(
    summary: DashboardSummary,
    onOpenRecordTradeDialog: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardSurface)
            .border(1.dp, SubtleBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
            .testTag("capital_and_pnl_card"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Modal & Saldo Aktual (Transaksi Nyata)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Tersentuhnya TP tidak otomatis mengubah saldo tanpa transaksi nyata",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            OutlinedButton(
                onClick = onOpenRecordTradeDialog,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.testTag("record_realized_trade_button")
            ) {
                Text("Catat Transaksi", style = MaterialTheme.typography.labelSmall)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricTile(
                label = "Modal Awal",
                value = "${String.format("%.2f", summary.initialCapital)} USDT",
                subLabel = "Dapat diubah di Pengaturan",
                valueColor = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = "Saldo Saat Ini (Aktual)",
                value = if (summary.currentRealizedBalance != null) {
                    "${String.format("%.2f", summary.currentRealizedBalance)} USDT"
                } else {
                    "Belum Ada Transaksi"
                },
                subLabel = if (summary.realizedTradeCount > 0) {
                    "Dari ${summary.realizedTradeCount} posisi ditutup"
                } else {
                    "Transaksi nyata belum dicatat"
                },
                valueColor = if (summary.currentRealizedBalance != null) CyanAccent else TextSecondary,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val pnl = summary.totalRealizedPnl
            MetricTile(
                label = "Total P/L Terealisasi",
                value = if (pnl != null) {
                    val sign = if (pnl >= 0) "+" else ""
                    "$sign${String.format("%.2f", pnl)} USDT"
                } else {
                    "0 Transaksi Nyata"
                },
                subLabel = "Termasuk potongan biaya (fee)",
                valueColor = when {
                    pnl == null -> TextSecondary
                    pnl > 0 -> EmeraldLong
                    pnl < 0 -> CrimsonShort
                    else -> TextPrimary
                },
                modifier = Modifier.weight(1f)
            )

            val estPct = summary.estimatedSignalReturnPercent
            val estSign = if (estPct >= 0) "+" else ""
            MetricTile(
                label = "Estimasi Performa Sinyal",
                value = "$estSign${String.format("%.2f", estPct)}%",
                subLabel = "Simulasi teoretis level sinyal",
                valueColor = if (estPct >= 0) EmeraldLong else CrimsonShort,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun WinRateAndMetricsSection(summary: DashboardSummary) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardSurface)
            .border(1.dp, SubtleBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
            .testTag("win_rate_metrics_card"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Statistik Sinyal & Win Rate (1 Hasil Akhir / Sinyal)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Win Rate Gabungan mencakup hasil dari berbagai sumber harga (Binance & Referensi CoinGecko). Sinyal aktif dan dibatalkan tanpa hasil dikecualikan.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricTile(
                label = "Win Rate Gabungan",
                value = "${String.format("%.1f", summary.combinedWinRate)}%",
                subLabel = "Sampel: ${summary.combinedSampleCount} sinyal (Multi-Sumber)",
                valueColor = EmeraldLong,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = "Win Rate Binance",
                value = "${String.format("%.1f", summary.binanceWinRate)}%",
                subLabel = "Sampel: ${summary.binanceSampleCount} sinyal",
                valueColor = CyanAccent,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = "Ref. CoinGecko",
                value = "${String.format("%.1f", summary.coingeckoWinRate)}%",
                subLabel = "Sampel: ${summary.coingeckoSampleCount} sinyal",
                valueColor = AmberWarning,
                modifier = Modifier.weight(1f)
            )
        }

        // Counts grid: Total Sinyal, Aktif, Selesai, Menang, Kalah, Impas, TP Tercapai, SL Tercapai
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmallStatBadge("Total Sinyal", "${summary.totalSignals}", TextPrimary, Modifier.weight(1f))
            SmallStatBadge("Sinyal Aktif", "${summary.activeSignalsCount}", CyanAccent, Modifier.weight(1f))
            SmallStatBadge("Sinyal Selesai", "${summary.completedSignalsCount}", TextPrimary, Modifier.weight(1f))
            SmallStatBadge("Impas (BE)", "${summary.breakevenCount}", TextSecondary, Modifier.weight(1f))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmallStatBadge("Menang", "${summary.winCount}", EmeraldLong, Modifier.weight(1f))
            SmallStatBadge("Kalah", "${summary.lossCount}", CrimsonShort, Modifier.weight(1f))
            SmallStatBadge("Jml TP Tercapai", "${summary.tpHitTotalCount}", EmeraldLong, Modifier.weight(1f))
            SmallStatBadge("Jml SL Tercapai", "${summary.slHitSignalCount}", CrimsonShort, Modifier.weight(1f))
        }
    }
}

@Composable
private fun PerformanceDistributionChartCard(summary: DashboardSummary) {
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
            text = "Grafik Distribusi Hasil Sinyal",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        val totalEvaluated = (summary.winCount + summary.lossCount + summary.breakevenCount).coerceAtLeast(1)
        val winRatio = summary.winCount.toFloat() / totalEvaluated.toFloat()
        val beRatio = summary.breakevenCount.toFloat() / totalEvaluated.toFloat()
        val lossRatio = summary.lossCount.toFloat() / totalEvaluated.toFloat()

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ElevatedCardSurface)
        ) {
            val w = size.width
            val h = size.height
            if (summary.winCount + summary.lossCount + summary.breakevenCount == 0) {
                drawRoundRect(
                    color = SubtleBorder,
                    size = Size(w, h),
                    cornerRadius = CornerRadius(8f, 8f)
                )
            } else {
                val winWidth = w * winRatio
                val beWidth = w * beRatio
                val lossWidth = w * lossRatio

                drawRect(
                    color = EmeraldLong,
                    topLeft = Offset(0f, 0f),
                    size = Size(winWidth, h)
                )
                drawRect(
                    color = TextSecondary,
                    topLeft = Offset(winWidth, 0f),
                    size = Size(beWidth, h)
                )
                drawRect(
                    color = CrimsonShort,
                    topLeft = Offset(winWidth + beWidth, 0f),
                    size = Size(lossWidth, h)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Menang: ${summary.winCount}",
                style = MaterialTheme.typography.labelSmall,
                color = EmeraldLong
            )
            Text(
                text = "Impas: ${summary.breakevenCount}",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            Text(
                text = "Kalah: ${summary.lossCount}",
                style = MaterialTheme.typography.labelSmall,
                color = CrimsonShort
            )
        }
    }
}

@Composable
private fun MentorRankingPreviewCard(mentorRankings: List<MentorStats>) {
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
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.EmojiEvents,
                contentDescription = "Ranking Mentor",
                tint = AmberWarning
            )
            Text(
                text = "Ranking Mentor (Win Rate Tertinggi)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }

        if (mentorRankings.isEmpty()) {
            Text(
                text = "Belum ada data mentor. Mentor akan otomatis tercatat saat Anda menambahkan sinyal.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        } else {
            mentorRankings.take(5).forEach { m ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(ElevatedCardSurface)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(if (m.rank == 1) AmberWarning.copy(alpha = 0.25f) else CardSurface),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "#${m.rank}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (m.rank == 1) AmberWarning else TextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column {
                            Text(
                                text = m.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Sampel: ${m.completedSignals} selesai (${m.winCount}M / ${m.lossCount}K / ${m.breakevenCount}I) • Total: ${m.totalSignals}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }

                    Text(
                        text = "${String.format("%.1f", m.winRatePercent)}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = JetBrainsMonoFamily,
                        color = if (m.winRatePercent >= 50.0) EmeraldLong else CrimsonShort,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun SignalCompactCard(
    signal: TradingSignalEntity,
    liveQuote: PriceQuote?,
    onClick: () -> Unit
) {
    val isLong = signal.direction == SignalDirection.LONG.name
    val dirColor = if (isLong) EmeraldLong else CrimsonShort
    val displayPrice = liveQuote?.price ?: signal.lastCheckedPrice

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardSurface)
            .border(1.dp, SubtleBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
            .testTag("signal_card_${signal.id}"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = signal.direction,
                        style = MaterialTheme.typography.labelSmall,
                        color = dirColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Text(
                    text = signal.pair,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            StatusPillBadge(
                text = "${signal.status} • ${signal.outcome}",
                color = when (signal.outcome) {
                    "WIN" -> EmeraldLong
                    "LOSS" -> CrimsonShort
                    else -> CyanAccent
                }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Mentor: ${signal.mentorName}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Text(
                text = if (displayPrice > 0.0) "Harga: $displayPrice" else "Menunggu feed",
                style = MaterialTheme.typography.labelMedium,
                color = CyanAccent
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Entry: ${signal.entryPrices.joinToString(", ")}",
                style = MaterialTheme.typography.labelSmall,
                color = TextPrimary
            )
            Text(
                text = "TP: ${signal.tpPrices.joinToString(", ")} | SL: ${signal.slPrice}",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
fun StatusPillBadge(
    text: String,
    color: androidx.compose.ui.graphics.Color
) {
    Surface(
        color = color.copy(alpha = 0.18f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    subLabel: String,
    valueColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ElevatedCardSurface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = JetBrainsMonoFamily,
            color = valueColor,
            fontWeight = FontWeight.Bold
        )
        Text(text = subLabel, style = MaterialTheme.typography.bodySmall, color = TextMuted)
    }
}

@Composable
private fun SmallStatBadge(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(ElevatedCardSurface)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontFamily = JetBrainsMonoFamily,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun RecordRealizedTradeDialog(
    onDismiss: () -> Unit,
    onSave: (String, SignalDirection, Double, Double, Double, Double) -> Unit
) {
    var pair by remember { mutableStateOf("BTC/USDT") }
    var direction by remember { mutableStateOf(SignalDirection.LONG) }
    var entryText by remember { mutableStateOf("") }
    var exitText by remember { mutableStateOf("") }
    var sizeText by remember { mutableStateOf("") }
    var feeText by remember { mutableStateOf("0") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Catat Transaksi Nyata (Terealisasi)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Saldo dan P/L aktual dihitung murni dari posisi nyata yang benar-benar Anda tutup.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                OutlinedTextField(
                    value = pair,
                    onValueChange = { pair = it.uppercase() },
                    label = { Text("Pair (contoh: BTC/USDT)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SignalDirection.entries.forEach { dir ->
                        val selected = direction == dir
                        Button(
                            onClick = { direction = dir },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selected) {
                                    if (dir == SignalDirection.LONG) EmeraldLong else CrimsonShort
                                } else ElevatedCardSurface
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(dir.name)
                        }
                    }
                }
                OutlinedTextField(
                    value = entryText,
                    onValueChange = { entryText = it },
                    label = { Text("Harga Eksekusi Entry") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = exitText,
                    onValueChange = { exitText = it },
                    label = { Text("Harga Eksekusi Exit / Tutup") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = sizeText,
                    onValueChange = { sizeText = it },
                    label = { Text("Ukuran Posisi (USDT)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = feeText,
                    onValueChange = { feeText = it },
                    label = { Text("Biaya / Fee Bursa (USDT)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMsg != null) {
                    Text(text = errorMsg!!, color = CrimsonShort, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val entry = entryText.toDoubleOrNull()
                    val exit = exitText.toDoubleOrNull()
                    val size = sizeText.toDoubleOrNull()
                    val fee = feeText.toDoubleOrNull() ?: 0.0
                    if (entry == null || entry <= 0 || exit == null || exit <= 0 || size == null || size <= 0) {
                        errorMsg = "Masukkan angka valid (> 0) untuk Entry, Exit, dan Ukuran Posisi."
                    } else {
                        onSave(pair, direction, entry, exit, size, fee.coerceAtLeast(0.0))
                    }
                }
            ) {
                Text("Simpan Transaksi")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}
