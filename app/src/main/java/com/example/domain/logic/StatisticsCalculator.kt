package com.example.domain.logic

import com.example.data.local.MentorEntity
import com.example.data.local.RealizedTradeEntity
import com.example.data.local.TradingSignalEntity
import com.example.domain.model.DashboardSummary
import com.example.domain.model.MentorStats
import com.example.domain.model.PriceSourceType
import com.example.domain.model.SignalDirection
import com.example.domain.model.SignalOutcome
import com.example.domain.model.SignalStatus

object StatisticsCalculator {

    private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L

    /**
     * Filters completed signals that have a valid final outcome (WIN, LOSS, BREAKEVEN)
     * within the requested period window based on completedAtMillis.
     * Excludes ACTIVE signals and CANCELLED_NO_RESULT signals per Section 9.
     */
    fun filterEvaluatedSignalsForPeriod(
        signals: List<TradingSignalEntity>,
        nowMillis: Long,
        periodDays: Int? = 30
    ): List<TradingSignalEntity> {
        val cutoffMillis = if (periodDays != null && periodDays > 0) {
            nowMillis - (periodDays * MILLIS_PER_DAY)
        } else {
            0L
        }

        return signals.filter { sig ->
            !sig.isDeleted &&
                sig.status != SignalStatus.ACTIVE.name &&
                sig.outcome in setOf(
                    SignalOutcome.WIN.name,
                    SignalOutcome.LOSS.name,
                    SignalOutcome.BREAKEVEN.name
                ) &&
                (sig.completedAtMillis.takeIf { it > 0L } ?: sig.updatedAtMillis) >= cutoffMillis
        }
    }

    fun calculateWinRate(evaluatedSignals: List<TradingSignalEntity>): Double {
        if (evaluatedSignals.isEmpty()) return 0.0
        val wins = evaluatedSignals.count { it.outcome == SignalOutcome.WIN.name }
        return (wins.toDouble() / evaluatedSignals.size.toDouble()) * 100.0
    }

    fun calculateMentorRankings(
        mentors: List<MentorEntity>,
        signals: List<TradingSignalEntity>,
        nowMillis: Long,
        periodDays: Int? = 30
    ): List<MentorStats> {
        val nonDeletedSignals = signals.filter { !it.isDeleted }
        val evaluatedInPeriod = filterEvaluatedSignalsForPeriod(nonDeletedSignals, nowMillis, periodDays)

        // Ensure all mentors from both the mentor table and signal references are included by stable mentorId
        val mentorMap = mutableMapOf<String, MentorEntity>()
        mentors.filter { !it.isDeleted }.forEach { mentorMap[it.id] = it }
        nonDeletedSignals.forEach { sig ->
            if (sig.mentorId !in mentorMap) {
                mentorMap[sig.mentorId] = MentorEntity(
                    id = sig.mentorId,
                    userId = sig.userId,
                    displayName = sig.mentorName
                )
            }
        }

        val unrankedStats = mentorMap.values.map { mentor ->
            val allMentorSignals = nonDeletedSignals.filter { it.mentorId == mentor.id }
            val evaluatedMentorSignals = evaluatedInPeriod.filter { it.mentorId == mentor.id }

            val winCount = evaluatedMentorSignals.count { it.outcome == SignalOutcome.WIN.name }
            val lossCount = evaluatedMentorSignals.count { it.outcome == SignalOutcome.LOSS.name }
            val breakevenCount = evaluatedMentorSignals.count { it.outcome == SignalOutcome.BREAKEVEN.name }
            val completedCount = evaluatedMentorSignals.size
            val activeCount = allMentorSignals.count { it.status == SignalStatus.ACTIVE.name }

            val combinedWinRate = calculateWinRate(evaluatedMentorSignals)

            val binanceSignals = evaluatedMentorSignals.filter {
                val src = if (it.outcomeSource != "NONE") it.outcomeSource else it.priceSource
                src == PriceSourceType.BINANCE_FUTURES.name || src == PriceSourceType.BINANCE_SPOT.name
            }
            val coingeckoSignals = evaluatedMentorSignals.filter {
                val src = if (it.outcomeSource != "NONE") it.outcomeSource else it.priceSource
                src == PriceSourceType.COINGECKO.name
            }

            MentorStats(
                mentorId = mentor.id,
                displayName = mentor.displayName,
                handleOrChannel = mentor.handleOrChannel,
                specialty = mentor.specialty,
                totalSignals = allMentorSignals.size,
                completedSignals = completedCount,
                activeSignals = activeCount,
                winCount = winCount,
                lossCount = lossCount,
                breakevenCount = breakevenCount,
                winRatePercent = combinedWinRate,
                binanceWinRatePercent = calculateWinRate(binanceSignals),
                binanceSampleCount = binanceSignals.size,
                coingeckoWinRatePercent = calculateWinRate(coingeckoSignals),
                coingeckoSampleCount = coingeckoSignals.size,
                rank = 0
            )
        }

        // Sort by highest Win Rate first, then highest completedSignals count, then totalSignals
        val sorted = unrankedStats.sortedWith(
            compareByDescending<MentorStats> { it.completedSignals > 0 }
                .thenByDescending { it.winRatePercent }
                .thenByDescending { it.completedSignals }
                .thenByDescending { it.totalSignals }
                .thenBy { it.displayName.lowercase() }
        )

        return sorted.mapIndexed { idx, stat -> stat.copy(rank = idx + 1) }
    }

    fun calculateDashboardSummary(
        initialCapital: Double,
        signals: List<TradingSignalEntity>,
        realizedTrades: List<RealizedTradeEntity>,
        nowMillis: Long,
        periodDays: Int = 30
    ): DashboardSummary {
        val validSignals = signals.filter { !it.isDeleted }
        val evaluated = filterEvaluatedSignalsForPeriod(validSignals, nowMillis, periodDays)

        val winCount = evaluated.count { it.outcome == SignalOutcome.WIN.name }
        val lossCount = evaluated.count { it.outcome == SignalOutcome.LOSS.name }
        val breakevenCount = evaluated.count { it.outcome == SignalOutcome.BREAKEVEN.name }

        val binanceEvaluated = evaluated.filter {
            val src = if (it.outcomeSource != "NONE") it.outcomeSource else it.priceSource
            src == PriceSourceType.BINANCE_FUTURES.name || src == PriceSourceType.BINANCE_SPOT.name
        }
        val coingeckoEvaluated = evaluated.filter {
            val src = if (it.outcomeSource != "NONE") it.outcomeSource else it.priceSource
            src == PriceSourceType.COINGECKO.name
        }

        val tpHitTotalCount = validSignals.sumOf { it.reachedTpIndices.size }
        val slHitSignalCount = validSignals.count { it.slHit }
        val activeCount = validSignals.count { it.status == SignalStatus.ACTIVE.name }
        val completedCount = validSignals.count { it.status == SignalStatus.COMPLETED.name }

        // Theoretical Signal Performance % (strictly separated from Actual Realized Balance/P&L)
        val estimatedReturnPct = evaluated.sumOf { sig ->
            val entry = sig.entryPrices.firstOrNull() ?: return@sumOf 0.0
            if (entry <= 0.0) return@sumOf 0.0
            val isLong = sig.direction == SignalDirection.LONG.name
            when (sig.outcome) {
                SignalOutcome.WIN.name -> {
                    val highestTpIdx = sig.reachedTpIndices.maxOrNull() ?: 0
                    val tpPrice = sig.tpPrices.getOrNull(highestTpIdx) ?: sig.tpPrices.firstOrNull() ?: entry
                    if (isLong) ((tpPrice - entry) / entry) * 100.0 else ((entry - tpPrice) / entry) * 100.0
                }
                SignalOutcome.LOSS.name -> {
                    if (isLong) ((sig.slPrice - entry) / entry) * 100.0 else ((entry - sig.slPrice) / entry) * 100.0
                }
                else -> 0.0
            }
        }

        // Actual Realized P/L & Balance (ONLY from actual recorded RealizedTradeEntities per Section 10)
        val activeRealizedTrades = realizedTrades.filter { !it.isDeleted }
        val totalRealizedPnl = if (activeRealizedTrades.isNotEmpty()) {
            activeRealizedTrades.sumOf { it.realizedPnlUsdt }
        } else {
            null
        }
        val currentRealizedBalance = if (totalRealizedPnl != null) {
            initialCapital + totalRealizedPnl
        } else {
            null
        }

        return DashboardSummary(
            initialCapital = initialCapital,
            currentRealizedBalance = currentRealizedBalance,
            totalRealizedPnl = totalRealizedPnl,
            realizedTradeCount = activeRealizedTrades.size,
            estimatedSignalReturnPercent = estimatedReturnPct,
            combinedWinRate = calculateWinRate(evaluated),
            combinedSampleCount = evaluated.size,
            binanceWinRate = calculateWinRate(binanceEvaluated),
            binanceSampleCount = binanceEvaluated.size,
            coingeckoWinRate = calculateWinRate(coingeckoEvaluated),
            coingeckoSampleCount = coingeckoEvaluated.size,
            totalSignals = validSignals.size,
            tpHitTotalCount = tpHitTotalCount,
            slHitSignalCount = slHitSignalCount,
            activeSignalsCount = activeCount,
            completedSignalsCount = completedCount,
            winCount = winCount,
            lossCount = lossCount,
            breakevenCount = breakevenCount,
            periodDays = periodDays
        )
    }
}
