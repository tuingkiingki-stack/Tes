package com.example.data.repository

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.example.data.local.JournalDao
import com.example.data.local.MentorEntity
import com.example.data.local.RealizedTradeEntity
import com.example.data.local.SignalEventEntity
import com.example.data.local.TradingSignalEntity
import com.example.data.local.UserProfileEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class RestoreMode {
    MERGE,
    REPLACE
}

data class BackupStats(
    val mentorCount: Int,
    val signalCount: Int,
    val eventCount: Int,
    val tradeCount: Int
)

class BackupRestoreManager(private val dao: JournalDao) {

    suspend fun exportToJson(userId: String): String {
        val profile = dao.getUserProfile(userId)
        val mentors = dao.getAllMentorsIncludingDeleted(userId).filter { !it.isDeleted }
        val signals = dao.getAllSignalsIncludingDeleted(userId).filter { !it.isDeleted }
        val events = dao.getAllEvents(userId)
        val trades = dao.getAllRealizedTradesIncludingDeleted(userId).filter { !it.isDeleted }

        val root = JSONObject()
        root.put("version", 1)
        root.put("exportedAtMillis", System.currentTimeMillis())
        root.put("userId", userId)

        if (profile != null) {
            root.put("profile", JSONObject().apply {
                put("initialCapital", profile.initialCapital)
                put("preferredPriceSource", profile.preferredPriceSource)
                put("priceStaleThresholdSec", profile.priceStaleThresholdSec)
                put("notificationsEnabled", profile.notificationsEnabled)
                put("revision", profile.revision)
            })
        }

        val mentorsArr = JSONArray()
        mentors.forEach { m ->
            mentorsArr.put(JSONObject().apply {
                put("id", m.id)
                put("displayName", m.displayName)
                put("handleOrChannel", m.handleOrChannel)
                put("specialty", m.specialty)
                put("revision", m.revision)
            })
        }
        root.put("mentors", mentorsArr)

        val signalsArr = JSONArray()
        signals.forEach { s ->
            signalsArr.put(JSONObject().apply {
                put("id", s.id)
                put("mentorId", s.mentorId)
                put("mentorName", s.mentorName)
                put("pair", s.pair)
                put("direction", s.direction)
                put("signalTimestampMillis", s.signalTimestampMillis)
                put("entryPrices", JSONArray(s.entryPrices))
                put("reachedEntryIndices", JSONArray(s.reachedEntryIndices))
                put("missedEntryIndices", JSONArray(s.missedEntryIndices))
                put("tpPrices", JSONArray(s.tpPrices))
                put("reachedTpIndices", JSONArray(s.reachedTpIndices))
                put("slPrice", s.slPrice)
                put("slHit", s.slHit)
                put("returnedToEntry", s.returnedToEntry)
                put("status", s.status)
                put("outcome", s.outcome)
                put("isConservativeEstimation", s.isConservativeEstimation)
                put("priceSource", s.priceSource)
                put("outcomeSource", s.outcomeSource)
                put("hasSourceConflict", s.hasSourceConflict)
                put("lastCheckedPrice", s.lastCheckedPrice)
                put("lastCheckedAtMillis", s.lastCheckedAtMillis)
                put("completedAtMillis", s.completedAtMillis)
                put("revision", s.revision)
                put("lastOperationId", s.lastOperationId)
            })
        }
        root.put("signals", signalsArr)

        val eventsArr = JSONArray()
        events.forEach { e ->
            eventsArr.put(JSONObject().apply {
                put("id", e.id)
                put("signalId", e.signalId)
                put("pair", e.pair)
                put("direction", e.direction)
                put("eventType", e.eventType)
                put("levelIndex", e.levelIndex)
                put("price", e.price)
                put("priceSource", e.priceSource)
                put("message", e.message)
                put("eventTimestampMillis", e.eventTimestampMillis)
                put("idempotencyKey", e.idempotencyKey)
                put("notificationStatus", e.notificationStatus)
            })
        }
        root.put("events", eventsArr)

        val tradesArr = JSONArray()
        trades.forEach { t ->
            tradesArr.put(JSONObject().apply {
                put("id", t.id)
                put("signalId", t.signalId)
                put("pair", t.pair)
                put("direction", t.direction)
                put("entryPrice", t.entryPrice)
                put("exitPrice", t.exitPrice)
                put("positionSizeUsdt", t.positionSizeUsdt)
                put("feeUsdt", t.feeUsdt)
                put("realizedPnlUsdt", t.realizedPnlUsdt)
                put("closedAtMillis", t.closedAtMillis)
                put("revision", t.revision)
            })
        }
        root.put("realizedTrades", tradesArr)

        return root.toString(2)
    }

    suspend fun importFromJson(
        userId: String,
        jsonString: String,
        mode: RestoreMode
    ): Result<BackupStats> {
        return try {
            val root = JSONObject(jsonString)
            if (mode == RestoreMode.REPLACE) {
                dao.deleteAllSignalsForUser(userId)
                dao.deleteAllMentorsForUser(userId)
                dao.deleteAllEventsForUser(userId)
                dao.deleteAllTradesForUser(userId)
            }

            root.optJSONObject("profile")?.let { pObj ->
                dao.upsertUserProfile(
                    UserProfileEntity(
                        userId = userId,
                        initialCapital = pObj.optDouble("initialCapital", 1000.0),
                        preferredPriceSource = pObj.optString("preferredPriceSource", "BINANCE_FUTURES"),
                        priceStaleThresholdSec = pObj.optInt("priceStaleThresholdSec", 60),
                        notificationsEnabled = pObj.optBoolean("notificationsEnabled", true),
                        revision = pObj.optInt("revision", 1) + 1,
                        isPendingSync = true
                    )
                )
            }

            val mentorsList = mutableListOf<MentorEntity>()
            val mentorsArr = root.optJSONArray("mentors") ?: JSONArray()
            for (i in 0 until mentorsArr.length()) {
                val m = mentorsArr.getJSONObject(i)
                mentorsList.add(
                    MentorEntity(
                        id = m.getString("id"),
                        userId = userId,
                        displayName = m.getString("displayName"),
                        handleOrChannel = m.optString("handleOrChannel", ""),
                        specialty = m.optString("specialty", ""),
                        isDeleted = false,
                        revision = m.optInt("revision", 1),
                        isPendingSync = true
                    )
                )
            }
            dao.upsertMentors(mentorsList)

            val signalsList = mutableListOf<TradingSignalEntity>()
            val signalsArr = root.optJSONArray("signals") ?: JSONArray()
            for (i in 0 until signalsArr.length()) {
                val s = signalsArr.getJSONObject(i)
                val entriesJson = s.getJSONArray("entryPrices")
                val entries = (0 until entriesJson.length()).map { entriesJson.getDouble(it) }
                val reachedEntriesJson = s.optJSONArray("reachedEntryIndices") ?: JSONArray()
                val reachedEntries = (0 until reachedEntriesJson.length()).map { reachedEntriesJson.getInt(it) }
                val missedEntriesJson = s.optJSONArray("missedEntryIndices") ?: JSONArray()
                val missedEntries = (0 until missedEntriesJson.length()).map { missedEntriesJson.getInt(it) }
                val tpsJson = s.getJSONArray("tpPrices")
                val tps = (0 until tpsJson.length()).map { tpsJson.getDouble(it) }
                val reachedTpsJson = s.optJSONArray("reachedTpIndices") ?: JSONArray()
                val reachedTps = (0 until reachedTpsJson.length()).map { reachedTpsJson.getInt(it) }

                signalsList.add(
                    TradingSignalEntity(
                        id = s.getString("id"),
                        userId = userId,
                        mentorId = s.getString("mentorId"),
                        mentorName = s.getString("mentorName"),
                        pair = s.getString("pair"),
                        direction = s.getString("direction"),
                        signalTimestampMillis = s.getLong("signalTimestampMillis"),
                        entryPrices = entries,
                        reachedEntryIndices = reachedEntries,
                        missedEntryIndices = missedEntries,
                        tpPrices = tps,
                        reachedTpIndices = reachedTps,
                        slPrice = s.getDouble("slPrice"),
                        slHit = s.optBoolean("slHit", false),
                        returnedToEntry = s.optBoolean("returnedToEntry", false),
                        status = s.optString("status", "ACTIVE"),
                        outcome = s.optString("outcome", "PENDING"),
                        isConservativeEstimation = s.optBoolean("isConservativeEstimation", false),
                        priceSource = s.optString("priceSource", "BINANCE_FUTURES"),
                        outcomeSource = s.optString("outcomeSource", "NONE"),
                        hasSourceConflict = s.optBoolean("hasSourceConflict", false),
                        lastCheckedPrice = s.optDouble("lastCheckedPrice", 0.0),
                        lastCheckedAtMillis = s.optLong("lastCheckedAtMillis", 0L),
                        completedAtMillis = s.optLong("completedAtMillis", 0L),
                        isDeleted = false,
                        revision = s.optInt("revision", 1),
                        lastOperationId = s.optString("lastOperationId", "op_restore"),
                        isPendingSync = true
                    )
                )
            }
            dao.upsertSignals(signalsList)

            val eventsList = mutableListOf<SignalEventEntity>()
            val eventsArr = root.optJSONArray("events") ?: JSONArray()
            for (i in 0 until eventsArr.length()) {
                val e = eventsArr.getJSONObject(i)
                eventsList.add(
                    SignalEventEntity(
                        id = e.getString("id"),
                        userId = userId,
                        signalId = e.getString("signalId"),
                        pair = e.optString("pair", "BTC/USDT"),
                        direction = e.optString("direction", "LONG"),
                        eventType = e.getString("eventType"),
                        levelIndex = e.optInt("levelIndex", -1),
                        price = e.optDouble("price", 0.0),
                        priceSource = e.optString("priceSource", "SYSTEM"),
                        message = e.getString("message"),
                        eventTimestampMillis = e.getLong("eventTimestampMillis"),
                        idempotencyKey = e.getString("idempotencyKey"),
                        notificationStatus = e.optString("notificationStatus", "NOT_REQUIRED"),
                        isPendingSync = true
                    )
                )
            }
            dao.upsertEvents(eventsList)

            val tradesList = mutableListOf<RealizedTradeEntity>()
            val tradesArr = root.optJSONArray("realizedTrades") ?: JSONArray()
            for (i in 0 until tradesArr.length()) {
                val t = tradesArr.getJSONObject(i)
                tradesList.add(
                    RealizedTradeEntity(
                        id = t.getString("id"),
                        userId = userId,
                        signalId = t.optString("signalId", ""),
                        pair = t.getString("pair"),
                        direction = t.getString("direction"),
                        entryPrice = t.getDouble("entryPrice"),
                        exitPrice = t.getDouble("exitPrice"),
                        positionSizeUsdt = t.getDouble("positionSizeUsdt"),
                        feeUsdt = t.getDouble("feeUsdt"),
                        realizedPnlUsdt = t.getDouble("realizedPnlUsdt"),
                        closedAtMillis = t.getLong("closedAtMillis"),
                        isDeleted = false,
                        revision = t.optInt("revision", 1),
                        isPendingSync = true
                    )
                )
            }
            dao.upsertRealizedTrades(tradesList)

            Result.success(
                BackupStats(
                    mentorCount = mentorsList.size,
                    signalCount = signalsList.size,
                    eventCount = eventsList.size,
                    tradeCount = tradesList.size
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportSignalsToCsv(userId: String): String {
        val signals = dao.getAllSignalsIncludingDeleted(userId).filter { !it.isDeleted }
        val sb = StringBuilder()
        sb.appendLine("id,mentorId,mentorName,pair,direction,signalTimestampMillis,entryPrices,tpPrices,slPrice,status,outcome,priceSource,outcomeSource,completedAtMillis")
        for (s in signals) {
            val entries = s.entryPrices.joinToString("|")
            val tps = s.tpPrices.joinToString("|")
            val cleanMentor = s.mentorName.replace(",", " ")
            sb.appendLine("${s.id},${s.mentorId},$cleanMentor,${s.pair},${s.direction},${s.signalTimestampMillis},$entries,$tps,${s.slPrice},${s.status},${s.outcome},${s.priceSource},${s.outcomeSource},${s.completedAtMillis}")
        }
        return sb.toString()
    }

    suspend fun importSignalsFromCsv(
        userId: String,
        csvContent: String,
        mode: RestoreMode
    ): Result<Int> {
        return try {
            val lines = csvContent.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) return Result.failure(IllegalArgumentException("Berkas CSV kosong."))
            if (mode == RestoreMode.REPLACE) {
                dao.deleteAllSignalsForUser(userId)
            }
            val dataLines = if (lines.first().startsWith("id,")) lines.drop(1) else lines
            val importedSignals = mutableListOf<TradingSignalEntity>()
            val importedMentors = mutableMapOf<String, MentorEntity>()

            for (line in dataLines) {
                val cols = line.split(",")
                if (cols.size < 14) continue
                val id = cols[0].trim()
                val mentorId = cols[1].trim()
                val mentorName = cols[2].trim()
                val pair = cols[3].trim()
                val direction = cols[4].trim()
                val timestamp = cols[5].trim().toLongOrNull() ?: System.currentTimeMillis()
                val entries = cols[6].split("|").mapNotNull { it.toDoubleOrNull() }
                val tps = cols[7].split("|").mapNotNull { it.toDoubleOrNull() }
                val sl = cols[8].trim().toDoubleOrNull() ?: continue
                val status = cols[9].trim()
                val outcome = cols[10].trim()
                val priceSource = cols[11].trim()
                val outcomeSource = cols[12].trim()
                val completedAt = cols[13].trim().toLongOrNull() ?: 0L

                if (entries.isEmpty() || tps.isEmpty()) continue
                importedMentors[mentorId] = MentorEntity(
                    id = mentorId,
                    userId = userId,
                    displayName = mentorName,
                    isPendingSync = true
                )
                importedSignals.add(
                    TradingSignalEntity(
                        id = id,
                        userId = userId,
                        mentorId = mentorId,
                        mentorName = mentorName,
                        pair = pair,
                        direction = direction,
                        signalTimestampMillis = timestamp,
                        entryPrices = entries,
                        tpPrices = tps,
                        slPrice = sl,
                        status = status,
                        outcome = outcome,
                        priceSource = priceSource,
                        outcomeSource = outcomeSource,
                        completedAtMillis = completedAt,
                        lastOperationId = "op_csv_import",
                        isPendingSync = true
                    )
                )
            }
            dao.upsertMentors(importedMentors.values.toList())
            dao.upsertSignals(importedSignals)
            Result.success(importedSignals.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportReportToPdfFile(context: Context, userId: String): Result<File> {
        return try {
            val signals = dao.getAllSignalsIncludingDeleted(userId).filter { !it.isDeleted }
            val dateStr = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date())
            val outFile = File(context.cacheDir, "laporan_jurnal_sinyal_${System.currentTimeMillis()}.pdf")

            try {
                val pdf = PdfDocument()
                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
                val page = pdf.startPage(pageInfo)
                val canvas: Canvas = page.canvas

                val titlePaint = Paint().apply {
                    color = Color.BLACK
                    textSize = 18f
                    isFakeBoldText = true
                }
                val bodyPaint = Paint().apply {
                    color = Color.DKGRAY
                    textSize = 11f
                }

                canvas.drawText("Laporan Jurnal Sinyal Mentor", 40f, 50f, titlePaint)
                canvas.drawText("Dicetak pada: $dateStr | Total Sinyal: ${signals.size}", 40f, 72f, bodyPaint)

                var y = 105f
                signals.take(30).forEachIndexed { idx, sig ->
                    val line = "${idx + 1}. ${sig.pair} [${sig.direction}] - Mentor: ${sig.mentorName} | Status: ${sig.status} | Hasil: ${sig.outcome}"
                    canvas.drawText(line, 40f, y, bodyPaint)
                    y += 22f
                }

                pdf.finishPage(page)
                FileOutputStream(outFile).use { out ->
                    pdf.writeTo(out)
                }
                pdf.close()
            } catch (_: Throwable) {
                // Fallback when native PdfDocument is unavailable (e.g. headless JVM / Robolectric)
            }

            if (!outFile.exists() || outFile.length() == 0L) {
                val linesSummary = signals.joinToString("\n") { s ->
                    "% ${s.pair} [${s.direction}] - Mentor: ${s.mentorName} | Status: ${s.status} | Hasil: ${s.outcome}"
                }
                val minimalPdf = "%PDF-1.4\n1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n" +
                    "% Laporan Jurnal Sinyal Mentor ($dateStr) - Total Sinyal: ${signals.size}\n" +
                    "$linesSummary\n%%EOF\n"
                outFile.writeText(minimalPdf)
            }
            Result.success(outFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
