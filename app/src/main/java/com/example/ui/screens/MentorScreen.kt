package com.example.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.MentorStats
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
fun MentorScreen(
    mentorRankings: List<MentorStats>,
    selectedPeriodDays: Int,
    onSelectPeriodDays: (Int) -> Unit,
    onSaveMentor: (String, String, String) -> Unit
) {
    var showAddMentorDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyBackground)
            .testTag("mentor_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Ranking & Statistik Mentor",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Identitas mentor stabil • Evaluasi objektif 1 hasil akhir per sinyal",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Button(
                    onClick = { showAddMentorDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                    modifier = Modifier.testTag("add_mentor_button")
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = "Tambah Mentor")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Mentor Baru")
                }
            }
        }

        // Period Filter
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Periode Evaluasi:",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
                listOf(7 to "7 Hari", 30 to "30 Hari (Default)", 90 to "90 Hari", 0 to "Semua Waktu").forEach { (days, label) ->
                    val selected = selectedPeriodDays == days
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (selected) EmeraldLong.copy(alpha = 0.2f) else ElevatedCardSurface,
                        modifier = Modifier
                            .border(1.dp, if (selected) EmeraldLong else SubtleBorder, RoundedCornerShape(8.dp))
                            .clickable { onSelectPeriodDays(days) }
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

        if (mentorRankings.isEmpty()) {
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
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = "Belum Ada Mentor",
                        tint = AmberWarning,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Belum Ada Data Mentor",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Tambahkan mentor secara langsung atau buat sinyal trading baru untuk mulai membandingkan performa mentor.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        } else {
            items(mentorRankings, key = { it.mentorId }) { mentor ->
                MentorRankingCard(mentor = mentor)
            }
        }

        item {
            Spacer(modifier = Modifier.height(72.dp))
        }
    }

    if (showAddMentorDialog) {
        AddMentorDialog(
            onDismiss = { showAddMentorDialog = false },
            onSave = { name, handle, specialty ->
                onSaveMentor(name, handle, specialty)
                showAddMentorDialog = false
            }
        )
    }
}

@Composable
private fun MentorRankingCard(mentor: MentorStats) {
    val rankColor = when (mentor.rank) {
        1 -> AmberWarning
        2 -> CyanAccent
        3 -> EmeraldLong
        else -> TextSecondary
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardSurface)
            .border(1.dp, SubtleBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
            .testTag("mentor_card_${mentor.mentorId}"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(rankColor.copy(alpha = 0.2f))
                        .border(1.dp, rankColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "#${mentor.rank}",
                        style = MaterialTheme.typography.titleSmall,
                        color = rankColor,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column {
                    Text(
                        text = mentor.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = buildString {
                            append("ID Stabil: ${mentor.mentorId.take(10)}")
                            if (mentor.handleOrChannel.isNotBlank()) append(" • ${mentor.handleOrChannel}")
                            if (mentor.specialty.isNotBlank()) append(" • ${mentor.specialty}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${String.format("%.1f", mentor.winRatePercent)}%",
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = JetBrainsMonoFamily,
                    color = if (mentor.winRatePercent >= 50.0) EmeraldLong else CrimsonShort,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Win Rate (${mentor.completedSignals} sampel)",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }

        // Outcomes breakdown row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(ElevatedCardSurface)
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            MentorMetricColumn("Total Sinyal", "${mentor.totalSignals}", TextPrimary)
            MentorMetricColumn("Aktif", "${mentor.activeSignals}", CyanAccent)
            MentorMetricColumn("Menang", "${mentor.winCount}", EmeraldLong)
            MentorMetricColumn("Kalah", "${mentor.lossCount}", CrimsonShort)
            MentorMetricColumn("Impas (BE)", "${mentor.breakevenCount}", AmberWarning)
        }

        // Source-specific Win Rate breakdown
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Binance Win Rate: ${String.format("%.1f", mentor.binanceWinRatePercent)}% (${mentor.binanceSampleCount} sampel)",
                style = MaterialTheme.typography.bodySmall,
                color = CyanAccent
            )
            Text(
                text = "Ref. CoinGecko: ${String.format("%.1f", mentor.coingeckoWinRatePercent)}% (${mentor.coingeckoSampleCount} sampel)",
                style = MaterialTheme.typography.bodySmall,
                color = AmberWarning
            )
        }
    }
}

@Composable
private fun MentorMetricColumn(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontFamily = JetBrainsMonoFamily,
            color = valueColor,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun AddMentorDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var handle by remember { mutableStateOf("") }
    var specialty by remember { mutableStateOf("Futures & Spot") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardSurface,
        title = { Text("Tambah / Perbarui Profil Mentor") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama Tampilan Mentor (Wajib)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("mentor_dialog_name_input")
                )
                OutlinedTextField(
                    value = handle,
                    onValueChange = { handle = it },
                    label = { Text("Channel / Handle (contoh: @CryptoMasterID)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = specialty,
                    onValueChange = { specialty = it },
                    label = { Text("Spesialisasi (contoh: Scalping BTC/ETH)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(name.trim(), handle.trim(), specialty.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldLong),
                modifier = Modifier.testTag("mentor_dialog_save_button")
            ) {
                Text("Simpan Mentor")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}
