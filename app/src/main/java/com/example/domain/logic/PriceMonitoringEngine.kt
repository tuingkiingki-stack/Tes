package com.example.domain.logic

import com.example.data.local.SignalEventEntity
import com.example.data.local.TradingSignalEntity
import com.example.domain.model.LevelDistanceInfo
import com.example.domain.model.NotificationDeliveryStatus
import com.example.domain.model.PriceQuote
import com.example.domain.model.PriceSourceType
import com.example.domain.model.SignalDirection
import com.example.domain.model.SignalEventType
import com.example.domain.model.SignalOutcome
import com.example.domain.model.SignalStatus
import java.util.UUID
import kotlin.math.abs

data class MonitoringEvaluationResult(
    val updatedSignal: TradingSignalEntity,
    val newEvents: List<SignalEventEntity>,
    val signalChanged: Boolean,
    val rejectedBecauseExpired: Boolean = false
)

object PriceMonitoringEngine {

    /**
     * Evaluates a live price quote against an active signal.
     * Enforces:
     * - Expired data rejection (never confirms level touches with stale data)
     * - Each Entry generates only 1 notification/event on its first touch
     * - Return to Entry ("KEMBALI KE ENTRY") detection when price moves away and returns before TP
     * - Each TP generates a separate event
     * - Monitoring continues after TP1 for subsequent TPs, remaining Entries, and SL
     * - When all TPs are reached, completes signal as WIN and stops monitoring
     * - When SL is reached before certain Entries, marks those Entries as TIDAK TERCAPAI
     * - When SL is reached after >= 1 TP, outcome remains WIN ("MENANG"); before any TP, outcome is LOSS ("KALAH")
     * - Data gap ambiguity handling: if both TP and SL are crossed in an ambiguous range/gap, applies conservative SL-first ("ESTIMASI KONSERVATIF")
     */
    fun evaluatePriceTick(
        signal: TradingSignalEntity,
        quote: PriceQuote,
        nowMillis: Long,
        staleThresholdSec: Int,
        rangeLowPrice: Double? = null,
        rangeHighPrice: Double? = null
    ): MonitoringEvaluationResult {
        if (signal.status != SignalStatus.ACTIVE.name || signal.isDeleted) {
            return MonitoringEvaluationResult(signal, emptyList(), signalChanged = false)
        }

        if (quote.isExpired(nowMillis, staleThresholdSec)) {
            return MonitoringEvaluationResult(
                updatedSignal = signal,
                newEvents = emptyList(),
                signalChanged = false,
                rejectedBecauseExpired = true
            )
        }

        val currentPrice = quote.price
        val low = rangeLowPrice ?: currentPrice
        val high = rangeHighPrice ?: currentPrice
        val isLong = signal.direction == SignalDirection.LONG.name
        val sourceName = quote.source.name
        val sourceLabel = if (quote.source == PriceSourceType.COINGECKO) {
            "REFERENSI COINGECKO — TIDAK TERVERIFIKASI BINANCE"
        } else {
            quote.source.label
        }

        val reachedEntries = signal.reachedEntryIndices.toMutableSet()
        val missedEntries = signal.missedEntryIndices.toMutableSet()
        val reachedTps = signal.reachedTpIndices.toMutableSet()
        var slHit = signal.slHit
        var returnedToEntry = signal.returnedToEntry
        var status = signal.status
        var outcome = signal.outcome
        var outcomeSource = signal.outcomeSource
        var isConservative = signal.isConservativeEstimation
        var completedAt = signal.completedAtMillis

        val generatedEvents = mutableListOf<SignalEventEntity>()

        fun addEvent(
            type: SignalEventType,
            levelIndex: Int,
            eventPrice: Double,
            message: String,
            idempotencySuffix: String,
            requiresNotification: Boolean
        ) {
            val idempotencyKey = "${signal.id}_${idempotencySuffix}"
            val eventId = idempotencyKey.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            generatedEvents.add(
                SignalEventEntity(
                    id = eventId,
                    userId = signal.userId,
                    signalId = signal.id,
                    pair = signal.pair,
                    direction = signal.direction,
                    eventType = type.name,
                    levelIndex = levelIndex,
                    price = eventPrice,
                    priceSource = sourceName,
                    message = message,
                    eventTimestampMillis = quote.timestampMillis,
                    idempotencyKey = idempotencyKey,
                    notificationStatus = if (requiresNotification) {
                        NotificationDeliveryStatus.PENDING.name
                    } else {
                        NotificationDeliveryStatus.NOT_REQUIRED.name
                    },
                    isPendingSync = true
                )
            )
        }

        // Check if SL and any unreached TP would both be crossed during a data gap / high-low candle range
        val slTriggeredInTick = if (isLong) low <= signal.slPrice else high >= signal.slPrice
        val unreachedTpTriggeredInTick = signal.tpPrices.indices.any { idx ->
            idx !in reachedTps && (if (isLong) high >= signal.tpPrices[idx] else low <= signal.tpPrices[idx])
        }

        val ambiguousDataGap = slTriggeredInTick && unreachedTpTriggeredInTick
        if (ambiguousDataGap) {
            // Conservative rule: assume SL was hit first ("ESTIMASI KONSERVATIF")
            isConservative = true
            addEvent(
                type = SignalEventType.CONSERVATIVE_ESTIMATE,
                levelIndex = -1,
                eventPrice = signal.slPrice,
                message = "ESTIMASI KONSERVATIF: Jeda data melewati TP dan SL secara bersamaan. SL didahulukan (${signal.slPrice}) [$sourceLabel].",
                idempotencySuffix = "CONSERVATIVE_GAP_${quote.timestampMillis}",
                requiresNotification = false
            )
        }

        // 1. Evaluate Entry Touches (if SL is not hit first in an ambiguous gap)
        if (!ambiguousDataGap && !slTriggeredInTick) {
            signal.entryPrices.forEachIndexed { index, entryLevel ->
                if (index !in reachedEntries && index !in missedEntries) {
                    val touched = if (isLong) {
                        low <= entryLevel && currentPrice > signal.slPrice
                    } else {
                        high >= entryLevel && currentPrice < signal.slPrice
                    }
                    if (touched) {
                        reachedEntries.add(index)
                        addEvent(
                            type = SignalEventType.ENTRY_HIT,
                            levelIndex = index,
                            eventPrice = currentPrice,
                            message = "Entry ${index + 1} (${entryLevel}) tersentuh pada harga $currentPrice [$sourceLabel].",
                            idempotencySuffix = "ENTRY_$index",
                            requiresNotification = true
                        )
                    }
                }
            }

            // Check "KEMBALI KE ENTRY" (Return to Entry before any TP is reached)
            if (reachedEntries.isNotEmpty() && reachedTps.isEmpty() && !returnedToEntry && signal.lastCheckedPrice > 0.0) {
                val primaryEntry = signal.entryPrices.first()
                val hadMovedInProfit = if (isLong) {
                    signal.lastCheckedPrice > primaryEntry * 1.002
                } else {
                    signal.lastCheckedPrice < primaryEntry * 0.998
                }
                val isBackAtEntry = if (isLong) {
                    currentPrice <= primaryEntry && currentPrice > signal.slPrice
                } else {
                    currentPrice >= primaryEntry && currentPrice < signal.slPrice
                }
                if (hadMovedInProfit && isBackAtEntry) {
                    returnedToEntry = true
                    addEvent(
                        type = SignalEventType.RETURN_TO_ENTRY,
                        levelIndex = 0,
                        eventPrice = currentPrice,
                        message = "KEMBALI KE ENTRY: Harga kembali ke level Entry ($primaryEntry) sebelum TP tercapai [$sourceLabel]. Sinyal tetap aktif.",
                        idempotencySuffix = "RETURN_TO_ENTRY",
                        requiresNotification = false
                    )
                }
            }
        }

        // 2. If NOT ambiguousDataGap, evaluate TP touches before SL
        if (!ambiguousDataGap) {
            signal.tpPrices.forEachIndexed { index, tpLevel ->
                if (index !in reachedTps) {
                    val tpTouched = if (isLong) high >= tpLevel else low <= tpLevel
                    if (tpTouched) {
                        reachedTps.add(index)
                        outcome = SignalOutcome.WIN.name
                        outcomeSource = sourceName
                        addEvent(
                            type = SignalEventType.TP_HIT,
                            levelIndex = index,
                            eventPrice = currentPrice,
                            message = "TP${index + 1} ($tpLevel) tercapai pada harga $currentPrice [$sourceLabel].",
                            idempotencySuffix = "TP_$index",
                            requiresNotification = true
                        )
                    }
                }
            }
        }

        // 3. Check if all TPs are now reached (and SL was not hit)
        if (!slTriggeredInTick && reachedTps.size == signal.tpPrices.size && signal.tpPrices.isNotEmpty()) {
            status = SignalStatus.COMPLETED.name
            outcome = SignalOutcome.WIN.name
            outcomeSource = sourceName
            completedAt = quote.timestampMillis
            addEvent(
                type = SignalEventType.COMPLETED,
                levelIndex = signal.tpPrices.lastIndex,
                eventPrice = currentPrice,
                message = "Seluruh target TP (TP1..TP${signal.tpPrices.size}) tercapai! Sinyal selesai dengan hasil MENANG [$sourceLabel].",
                idempotencySuffix = "COMPLETED_ALL_TP",
                requiresNotification = false
            )
        }

        // 4. Evaluate SL Touch
        if (slTriggeredInTick && !slHit) {
            slHit = true
            // Mark any unreached Entries as TIDAK TERCAPAI
            signal.entryPrices.forEachIndexed { index, entryLevel ->
                if (index !in reachedEntries && index !in missedEntries) {
                    missedEntries.add(index)
                    addEvent(
                        type = SignalEventType.ENTRY_MISSED,
                        levelIndex = index,
                        eventPrice = currentPrice,
                        message = "Entry ${index + 1} ($entryLevel) TIDAK TERCAPAI karena SL tersentuh terlebih dahulu [$sourceLabel].",
                        idempotencySuffix = "ENTRY_MISSED_$index",
                        requiresNotification = false
                    )
                }
            }

            // Record SL_HIT event
            addEvent(
                type = SignalEventType.SL_HIT,
                levelIndex = -1,
                eventPrice = currentPrice,
                message = if (isConservative) {
                    "SL (${signal.slPrice}) tercapai [ESTIMASI KONSERVATIF — $sourceLabel] pada harga $currentPrice."
                } else {
                    "SL (${signal.slPrice}) tersentuh pada harga $currentPrice [$sourceLabel]."
                },
                idempotencySuffix = "SL_HIT",
                requiresNotification = true
            )

            // Final outcome rule (Section 9):
            // "Jika minimal satu TP tercapai, hasil sinyal adalah MENANG meskipun SL tersentuh setelahnya."
            // "Jika SL tersentuh sebelum TP mana pun, hasil adalah KALAH."
            outcome = if (reachedTps.isNotEmpty()) {
                SignalOutcome.WIN.name
            } else {
                SignalOutcome.LOSS.name
            }
            outcomeSource = sourceName
            status = SignalStatus.COMPLETED.name
            completedAt = quote.timestampMillis
        }

        val hasStructuralChanges = generatedEvents.isNotEmpty() ||
            reachedEntries != signal.reachedEntryIndices.toSet() ||
            missedEntries != signal.missedEntryIndices.toSet() ||
            reachedTps != signal.reachedTpIndices.toSet() ||
            slHit != signal.slHit ||
            returnedToEntry != signal.returnedToEntry ||
            status != signal.status ||
            outcome != signal.outcome ||
            isConservative != signal.isConservativeEstimation

        val nextRevision = if (hasStructuralChanges) signal.revision + 1 else signal.revision
        val nextOpId = if (hasStructuralChanges) {
            "op_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        } else {
            signal.lastOperationId
        }

        val updatedSignal = signal.copy(
            reachedEntryIndices = reachedEntries.sorted(),
            missedEntryIndices = missedEntries.sorted(),
            reachedTpIndices = reachedTps.sorted(),
            slPrice = signal.slPrice,
            slHit = slHit,
            returnedToEntry = returnedToEntry,
            status = status,
            outcome = outcome,
            isConservativeEstimation = isConservative,
            outcomeSource = outcomeSource,
            lastCheckedPrice = currentPrice,
            lastCheckedAtMillis = quote.timestampMillis,
            completedAtMillis = completedAt,
            revision = nextRevision,
            lastOperationId = nextOpId,
            isPendingSync = hasStructuralChanges || signal.isPendingSync,
            updatedAtMillis = nowMillis
        )

        return MonitoringEvaluationResult(
            updatedSignal = updatedSignal,
            newEvents = generatedEvents,
            signalChanged = hasStructuralChanges
        )
    }

    /**
     * Evaluates discrepancy between primary Binance quote and reference CoinGecko quote.
     * When CoinGecko indicates a level touch that Binance has NOT verified, records a separate
     * event labeled "REFERENSI COINGECKO — TIDAK TERVERIFIKASI BINANCE" and sets hasSourceConflict = true
     * without overriding the verified Binance state.
     */
    fun evaluateCrossSourceConflict(
        signal: TradingSignalEntity,
        binanceQuote: PriceQuote,
        coingeckoQuote: PriceQuote,
        nowMillis: Long,
        staleThresholdSec: Int
    ): MonitoringEvaluationResult {
        if (binanceQuote.isExpired(nowMillis, staleThresholdSec) ||
            coingeckoQuote.isExpired(nowMillis, staleThresholdSec)
        ) {
            return MonitoringEvaluationResult(signal, emptyList(), signalChanged = false)
        }

        val binanceEval = evaluatePriceTick(signal, binanceQuote, nowMillis, staleThresholdSec)
        val cgHypothetical = evaluatePriceTick(signal, coingeckoQuote, nowMillis, staleThresholdSec)

        val binanceEventKeys = binanceEval.newEvents.map { it.eventType to it.levelIndex }.toSet()
        val conflictEvents = mutableListOf<SignalEventEntity>()

        for (cgEvent in cgHypothetical.newEvents) {
            val key = cgEvent.eventType to cgEvent.levelIndex
            if (key !in binanceEventKeys && (cgEvent.eventType == SignalEventType.TP_HIT.name ||
                    cgEvent.eventType == SignalEventType.SL_HIT.name ||
                    cgEvent.eventType == SignalEventType.ENTRY_HIT.name)
            ) {
                val conflictKey = "${signal.id}_CG_CONFLICT_${cgEvent.eventType}_${cgEvent.levelIndex}"
                conflictEvents.add(
                    SignalEventEntity(
                        id = conflictKey.replace(Regex("[^a-zA-Z0-9_\\-]"), "_"),
                        userId = signal.userId,
                        signalId = signal.id,
                        pair = signal.pair,
                        direction = signal.direction,
                        eventType = SignalEventType.COINGECKO_CONFLICT.name,
                        levelIndex = cgEvent.levelIndex,
                        price = coingeckoQuote.price,
                        priceSource = PriceSourceType.COINGECKO.name,
                        message = "REFERENSI COINGECKO — TIDAK TERVERIFIKASI BINANCE: Level ${cgEvent.eventType} tersentuh menurut CoinGecko (${coingeckoQuote.price}), namun harga Binance (${binanceQuote.price}) belum menyentuh level tersebut.",
                        eventTimestampMillis = coingeckoQuote.timestampMillis,
                        idempotencyKey = conflictKey,
                        notificationStatus = NotificationDeliveryStatus.NOT_REQUIRED.name,
                        isPendingSync = true
                    )
                )
            }
        }

        val finalSignal = if (conflictEvents.isNotEmpty()) {
            binanceEval.updatedSignal.copy(
                hasSourceConflict = true,
                revision = binanceEval.updatedSignal.revision + 1,
                isPendingSync = true
            )
        } else {
            binanceEval.updatedSignal
        }

        return MonitoringEvaluationResult(
            updatedSignal = finalSignal,
            newEvents = binanceEval.newEvents + conflictEvents,
            signalChanged = binanceEval.signalChanged || conflictEvents.isNotEmpty()
        )
    }

    /**
     * Computes price and percentage distances from currentPrice to Entry levels, next TP, and SL.
     */
    fun computeLevelDistances(signal: TradingSignalEntity, currentPrice: Double): List<LevelDistanceInfo> {
        if (currentPrice <= 0.0) return emptyList()
        val result = mutableListOf<LevelDistanceInfo>()

        // Entries
        signal.entryPrices.forEachIndexed { index, entry ->
            val diff = entry - currentPrice
            val pct = (diff / currentPrice) * 100.0
            result.add(
                LevelDistanceInfo(
                    label = "Entry ${index + 1}",
                    targetPrice = entry,
                    currentPrice = currentPrice,
                    priceDiff = diff,
                    percentDiff = pct,
                    isReached = index in signal.reachedEntryIndices,
                    isMissed = index in signal.missedEntryIndices
                )
            )
        }

        // Next unreached TP (or all TPs with status)
        signal.tpPrices.forEachIndexed { index, tp ->
            val diff = tp - currentPrice
            val pct = (diff / currentPrice) * 100.0
            val isNextTp = index !in signal.reachedTpIndices &&
                signal.tpPrices.indices.firstOrNull { it !in signal.reachedTpIndices } == index
            result.add(
                LevelDistanceInfo(
                    label = if (isNextTp) "TP${index + 1} (Target Berikutnya)" else "TP${index + 1}",
                    targetPrice = tp,
                    currentPrice = currentPrice,
                    priceDiff = diff,
                    percentDiff = pct,
                    isReached = index in signal.reachedTpIndices
                )
            )
        }

        // SL
        val slDiff = signal.slPrice - currentPrice
        val slPct = (slDiff / currentPrice) * 100.0
        result.add(
            LevelDistanceInfo(
                label = "Stop Loss (SL)",
                targetPrice = signal.slPrice,
                currentPrice = currentPrice,
                priceDiff = slDiff,
                percentDiff = slPct,
                isReached = signal.slHit
            )
        )

        return result
    }
}
