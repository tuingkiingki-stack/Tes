package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.MentorEntity
import com.example.data.local.TradingSignalEntity
import com.example.domain.logic.SignalValidator
import com.example.domain.model.SignalDirection
import com.example.ui.theme.CardSurface
import com.example.ui.theme.CrimsonShort
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.ElevatedCardSurface
import com.example.ui.theme.EmeraldLong
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.TextSecondary

@Composable
fun SignalFormDialog(
    existingSignal: TradingSignalEntity? = null,
    existingMentors: List<MentorEntity>,
    onDismiss: () -> Unit,
    onSave: (
        existingSignalId: String?,
        mentorId: String?,
        mentorName: String,
        pair: String,
        direction: SignalDirection,
        signalTimestampMillis: Long,
        entryPrices: List<Double>,
        tpPrices: List<Double>,
        slPrice: Double?
    ) -> Unit
) {
    var selectedMentorId by remember { mutableStateOf(existingSignal?.mentorId) }
    var mentorName by remember { mutableStateOf(existingSignal?.mentorName ?: "") }
    var pair by remember { mutableStateOf(existingSignal?.pair ?: "BTC/USDT") }
    var direction by remember {
        mutableStateOf(
            if (existingSignal?.direction == SignalDirection.SHORT.name) SignalDirection.SHORT else SignalDirection.LONG
        )
    }
    var entriesText by remember {
        mutableStateOf(existingSignal?.entryPrices?.joinToString(", ") ?: "")
    }
    var tpsText by remember {
        mutableStateOf(existingSignal?.tpPrices?.joinToString(", ") ?: "")
    }
    var slText by remember {
        mutableStateOf(existingSignal?.slPrice?.toString() ?: "")
    }
    var validationError by remember { mutableStateOf<String?>(null) }

    val popularPairs = listOf("BTC/USDT", "ETH/USDT", "SOL/USDT", "BNB/USDT", "XRP/USDT", "DOGE/USDT")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardSurface,
        title = {
            Text(
                text = if (existingSignal == null) "Tambah Sinyal Trading" else "Edit Sinyal Aktif (${existingSignal.pair})",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (existingMentors.isNotEmpty()) {
                    Text(
                        text = "Pilih Mentor Tersimpan:",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        existingMentors.forEach { m ->
                            val selected = selectedMentorId == m.id
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (selected) CyanAccent.copy(alpha = 0.2f) else ElevatedCardSurface,
                                modifier = Modifier
                                    .border(1.dp, if (selected) CyanAccent else SubtleBorder, RoundedCornerShape(8.dp))
                                    .clickable {
                                        selectedMentorId = m.id
                                        mentorName = m.displayName
                                    }
                            ) {
                                Text(
                                    text = m.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) CyanAccent else TextSecondary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = mentorName,
                    onValueChange = {
                        mentorName = it
                        val match = existingMentors.firstOrNull { m -> m.displayName.equals(it.trim(), ignoreCase = true) }
                        selectedMentorId = match?.id
                    },
                    label = { Text("Nama Mentor (Wajib)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_mentor_name")
                )

                Text(
                    text = "Pair Crypto (Dinormalisasi ke BASE/QUOTE):",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    popularPairs.forEach { p ->
                        val selected = SignalValidator.normalizePair(pair) == p
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (selected) EmeraldLong.copy(alpha = 0.2f) else ElevatedCardSurface,
                            modifier = Modifier
                                .border(1.dp, if (selected) EmeraldLong else SubtleBorder, RoundedCornerShape(8.dp))
                                .clickable { pair = p }
                        ) {
                            Text(
                                text = p,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) EmeraldLong else TextSecondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = pair,
                    onValueChange = { pair = it.uppercase() },
                    label = { Text("Pair (contoh: BTC/USDT)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_pair")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SignalDirection.entries.forEach { dir ->
                        val isSelected = direction == dir
                        val activeColor = if (dir == SignalDirection.LONG) EmeraldLong else CrimsonShort
                        Button(
                            onClick = { direction = dir },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) activeColor else ElevatedCardSurface
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("direction_button_${dir.name}")
                        ) {
                            Text(
                                text = dir.name,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = entriesText,
                    onValueChange = { entriesText = it },
                    label = { Text("Harga Entry (Pisahkan koma untuk Multi-Entry)") },
                    placeholder = { Text("Contoh: 60000, 59500") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_entries")
                )

                OutlinedTextField(
                    value = tpsText,
                    onValueChange = { tpsText = it },
                    label = { Text("Target TP1..TPn (Pisahkan koma, berurutan)") },
                    placeholder = {
                        Text(if (direction == SignalDirection.LONG) "Contoh: 62000, 64000" else "Contoh: 58000, 56000")
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_tps")
                )

                OutlinedTextField(
                    value = slText,
                    onValueChange = { slText = it },
                    label = { Text("Stop Loss / SL (Wajib)") },
                    placeholder = {
                        Text(if (direction == SignalDirection.LONG) "Contoh: 58000 (< Entry)" else "Contoh: 62000 (> Entry)")
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_sl")
                )

                if (validationError != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = validationError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = CrimsonShort,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("signal_validation_error_text")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedEntries = entriesText.split(",", ";", " ")
                        .mapNotNull { it.trim().toDoubleOrNull() }
                    val parsedTps = tpsText.split(",", ";", " ")
                        .mapNotNull { it.trim().toDoubleOrNull() }
                    val parsedSl = slText.trim().toDoubleOrNull()
                    val normalizedPair = SignalValidator.normalizePair(pair)

                    val check = SignalValidator.validateSignalInput(
                        mentorName = mentorName,
                        pair = normalizedPair,
                        direction = direction,
                        entryPrices = parsedEntries,
                        tpPrices = parsedTps,
                        slPrice = parsedSl
                    )
                    if (!check.isValid) {
                        validationError = check.errorMessage
                    } else {
                        validationError = null
                        onSave(
                            existingSignal?.id,
                            selectedMentorId,
                            mentorName.trim(),
                            normalizedPair,
                            direction,
                            existingSignal?.signalTimestampMillis ?: System.currentTimeMillis(),
                            parsedEntries,
                            parsedTps,
                            parsedSl
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldLong),
                modifier = Modifier.testTag("save_signal_submit_button")
            ) {
                Text("Simpan Sinyal", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_signal_dialog_button")
            ) {
                Text("Batal")
            }
        }
    )
}
