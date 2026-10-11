package com.example

import com.example.data.local.MentorEntity
import com.example.data.local.RealizedTradeEntity
import com.example.data.local.TradingSignalEntity
import com.example.domain.logic.PriceMonitoringEngine
import com.example.domain.logic.SignalValidator
import com.example.domain.logic.StatisticsCalculator
import com.example.domain.model.PriceQuote
import com.example.domain.model.PriceSourceType
import com.example.domain.model.SignalDirection
import com.example.domain.model.SignalEventType
import com.example.domain.model.SignalOutcome
import com.example.domain.model.SignalStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `pair normalization converts raw formats to standard BASE_QUOTE`() {
        assertEquals("BTC/USDT", SignalValidator.normalizePair("btcusdt"))
        assertEquals("ETH/USDT", SignalValidator.normalizePair("eth-usdt"))
        assertEquals("SOL/USDC", SignalValidator.normalizePair("SOL_USDC"))
        assertEquals("XRP/USDT", SignalValidator.normalizePair("  xrp / usdt "))
    }

    @Test
    fun `validateSignalInput enforces LONG and SHORT level ordering and no duplicates`() {
        // Valid LONG
        val validLong = SignalValidator.validateSignalInput(
            mentorName = "Mentor Alpha",
            pair = "BTC/USDT",
            direction = SignalDirection.LONG,
            entryPrices = listOf(60000.0, 59500.0),
            tpPrices = listOf(62000.0, 64000.0),
            slPrice = 58000.0
        )
        assertTrue(validLong.isValid)

        // Invalid LONG: SL >= Entry
        val invalidLongSl = SignalValidator.validateSignalInput(
            mentorName = "Mentor Alpha",
            pair = "BTC/USDT",
            direction = SignalDirection.LONG,
            entryPrices = listOf(60000.0, 59500.0),
            tpPrices = listOf(62000.0),
            slPrice = 59600.0
        )
        assertFalse(invalidLongSl.isValid)

        // Invalid LONG: TP not strictly ascending
        val invalidLongTpOrder = SignalValidator.validateSignalInput(
            mentorName = "Mentor Alpha",
            pair = "BTC/USDT",
            direction = SignalDirection.LONG,
            entryPrices = listOf(60000.0),
            tpPrices = listOf(64000.0, 62000.0),
            slPrice = 58000.0
        )
        assertFalse(invalidLongTpOrder.isValid)

        // Valid SHORT
        val validShort = SignalValidator.validateSignalInput(
            mentorName = "Mentor Beta",
            pair = "ETH/USDT",
            direction = SignalDirection.SHORT,
            entryPrices = listOf(3000.0, 3050.0),
            tpPrices = listOf(2900.0, 2800.0),
            slPrice = 3150.0
        )
        assertTrue(validShort.isValid)

        // Invalid SHORT: SL <= Entry
        val invalidShortSl = SignalValidator.validateSignalInput(
            mentorName = "Mentor Beta",
            pair = "ETH/USDT",
            direction = SignalDirection.SHORT,
            entryPrices = listOf(3000.0, 3050.0),
            tpPrices = listOf(2900.0),
            slPrice = 3040.0
        )
        assertFalse(invalidShortSl.isValid)

        // Duplicate Entry level rejected
        val dupEntry = SignalValidator.validateSignalInput(
            mentorName = "Mentor Beta",
            pair = "ETH/USDT",
            direction = SignalDirection.LONG,
            entryPrices = listOf(3000.0, 3000.0),
            tpPrices = listOf(3100.0),
            slPrice = 2900.0
        )
        assertFalse(dupEntry.isValid)
    }

    @Test
    fun `price monitoring detects Multi-Entry, Return to Entry, Multi-TP, and SL after TP1`() {
        val now = 1_700_000_000_000L
        var sig = TradingSignalEntity(
            id = "sig_test_1",
            userId = "u1",
            mentorId = "m1",
            mentorName = "Mentor A",
            pair = "BTC/USDT",
            direction = SignalDirection.LONG.name,
            signalTimestampMillis = now,
            entryPrices = listOf(60000.0, 59000.0),
            tpPrices = listOf(62000.0, 64000.0),
            slPrice = 58000.0,
            status = SignalStatus.ACTIVE.name,
            outcome = SignalOutcome.PENDING.name
        )

        // 1. Expired quote is rejected
        val expiredQuote = PriceQuote("BTC/USDT", 60000.0, PriceSourceType.BINANCE_FUTURES, now - 120_000L)
        val expiredRes = PriceMonitoringEngine.evaluatePriceTick(sig, expiredQuote, now, staleThresholdSec = 60)
        assertTrue(expiredRes.rejectedBecauseExpired)
        assertTrue(expiredRes.newEvents.isEmpty())

        // 2. Entry 1 touched at 59950
        val quoteEntry1 = PriceQuote("BTC/USDT", 59950.0, PriceSourceType.BINANCE_FUTURES, now)
        val res1 = PriceMonitoringEngine.evaluatePriceTick(sig, quoteEntry1, now, 60)
        sig = res1.updatedSignal
        assertEquals(listOf(0), sig.reachedEntryIndices)
        assertEquals(1, res1.newEvents.size)
        assertEquals(SignalEventType.ENTRY_HIT.name, res1.newEvents.first().eventType)

        // 3. Price moves into profit (60500), then returns to Entry (59980) before TP1 -> KEMBALI KE ENTRY
        val quoteProfit = PriceQuote("BTC/USDT", 60500.0, PriceSourceType.BINANCE_FUTURES, now + 5000)
        sig = PriceMonitoringEngine.evaluatePriceTick(sig, quoteProfit, now + 5000, 60).updatedSignal

        val quoteReturn = PriceQuote("BTC/USDT", 59980.0, PriceSourceType.BINANCE_FUTURES, now + 10000)
        val resReturn = PriceMonitoringEngine.evaluatePriceTick(sig, quoteReturn, now + 10000, 60)
        sig = resReturn.updatedSignal
        assertTrue(sig.returnedToEntry)
        assertTrue(resReturn.newEvents.any { it.eventType == SignalEventType.RETURN_TO_ENTRY.name })

        // 4. TP1 (62000) reached -> Signal stays ACTIVE to monitor TP2 and SL, outcome becomes WIN
        val quoteTp1 = PriceQuote("BTC/USDT", 62100.0, PriceSourceType.BINANCE_FUTURES, now + 15000)
        val resTp1 = PriceMonitoringEngine.evaluatePriceTick(sig, quoteTp1, now + 15000, 60)
        sig = resTp1.updatedSignal
        assertEquals(SignalStatus.ACTIVE.name, sig.status)
        assertEquals(SignalOutcome.WIN.name, sig.outcome)
        assertEquals(listOf(0), sig.reachedTpIndices)

        // 5. Price drops to SL (57900) after TP1 was already hit ->
        // Entry 2 (59000) marked TIDAK TERCAPAI, SL_HIT recorded, Signal COMPLETED, final outcome remains WIN!
        val quoteSl = PriceQuote("BTC/USDT", 57900.0, PriceSourceType.BINANCE_FUTURES, now + 20000)
        val resSl = PriceMonitoringEngine.evaluatePriceTick(sig, quoteSl, now + 20000, 60)
        sig = resSl.updatedSignal
        assertEquals(SignalStatus.COMPLETED.name, sig.status)
        assertEquals(SignalOutcome.WIN.name, sig.outcome)
        assertTrue(sig.slHit)
        assertEquals(listOf(1), sig.missedEntryIndices)
        assertTrue(resSl.newEvents.any { it.eventType == SignalEventType.ENTRY_MISSED.name })
        assertTrue(resSl.newEvents.any { it.eventType == SignalEventType.SL_HIT.name })
    }

    @Test
    fun `ambiguous data gap crossing both TP and SL applies conservative SL-first rule`() {
        val now = 1_700_000_000_000L
        val sig = TradingSignalEntity(
            id = "sig_gap",
            userId = "u1",
            mentorId = "m1",
            mentorName = "Mentor Gap",
            pair = "ETH/USDT",
            direction = SignalDirection.LONG.name,
            signalTimestampMillis = now,
            entryPrices = listOf(3000.0),
            tpPrices = listOf(3200.0),
            slPrice = 2900.0,
            status = SignalStatus.ACTIVE.name,
            outcome = SignalOutcome.PENDING.name
        )

        val quote = PriceQuote("ETH/USDT", 2890.0, PriceSourceType.BINANCE_FUTURES, now)
        val eval = PriceMonitoringEngine.evaluatePriceTick(
            signal = sig,
            quote = quote,
            nowMillis = now,
            staleThresholdSec = 60,
            rangeLowPrice = 2850.0,
            rangeHighPrice = 3250.0
        )

        assertTrue(eval.updatedSignal.isConservativeEstimation)
        assertTrue(eval.updatedSignal.slHit)
        assertEquals(SignalOutcome.LOSS.name, eval.updatedSignal.outcome)
        assertEquals(SignalStatus.COMPLETED.name, eval.updatedSignal.status)
        assertTrue(eval.newEvents.any { it.eventType == SignalEventType.CONSERVATIVE_ESTIMATE.name })
    }

    @Test
    fun `win rate and mentor ranking follow strict sample, breakeven, and tie-breaker rules`() {
        val now = 1_700_000_000_000L
        val mentors = listOf(
            MentorEntity(id = "m_alpha", userId = "u1", displayName = "Alpha Trader"),
            MentorEntity(id = "m_beta", userId = "u1", displayName = "Beta Scalper"),
            MentorEntity(id = "m_gamma", userId = "u1", displayName = "Gamma Swing")
        )

        val signals = listOf(
            // Alpha: 1 WIN (Binance), 1 BREAKEVEN (Binance) -> Win Rate = 1 / 2 = 50%
            TradingSignalEntity(
                id = "s1", userId = "u1", mentorId = "m_alpha", mentorName = "Alpha Trader",
                pair = "BTC/USDT", direction = "LONG", signalTimestampMillis = now - 1000,
                entryPrices = listOf(60000.0), tpPrices = listOf(62000.0), slPrice = 58000.0,
                reachedTpIndices = listOf(0), status = SignalStatus.COMPLETED.name,
                outcome = SignalOutcome.WIN.name, outcomeSource = PriceSourceType.BINANCE_FUTURES.name
            ),
            TradingSignalEntity(
                id = "s2", userId = "u1", mentorId = "m_alpha", mentorName = "Alpha Trader",
                pair = "ETH/USDT", direction = "LONG", signalTimestampMillis = now - 1000,
                entryPrices = listOf(3000.0), tpPrices = listOf(3200.0), slPrice = 2900.0,
                status = SignalStatus.COMPLETED.name,
                outcome = SignalOutcome.BREAKEVEN.name, outcomeSource = PriceSourceType.BINANCE_FUTURES.name
            ),
            // Alpha: 1 CANCELLED_NO_RESULT -> excluded from Win Rate denominator
            TradingSignalEntity(
                id = "s3", userId = "u1", mentorId = "m_alpha", mentorName = "Alpha Trader",
                pair = "SOL/USDT", direction = "LONG", signalTimestampMillis = now - 1000,
                entryPrices = listOf(100.0), tpPrices = listOf(110.0), slPrice = 90.0,
                status = SignalStatus.CANCELLED.name,
                outcome = SignalOutcome.CANCELLED_NO_RESULT.name
            ),
            // Beta: 2 WIN, 2 LOSS -> Win Rate = 2 / 4 = 50% (More completed signals than Alpha -> ranks higher on tie-break!)
            TradingSignalEntity(
                id = "s4", userId = "u1", mentorId = "m_beta", mentorName = "Beta Scalper",
                pair = "BTC/USDT", direction = "LONG", signalTimestampMillis = now - 1000,
                entryPrices = listOf(60000.0), tpPrices = listOf(61000.0), slPrice = 59000.0,
                reachedTpIndices = listOf(0), status = SignalStatus.COMPLETED.name,
                outcome = SignalOutcome.WIN.name, outcomeSource = PriceSourceType.BINANCE_FUTURES.name
            ),
            TradingSignalEntity(
                id = "s5", userId = "u1", mentorId = "m_beta", mentorName = "Beta Scalper",
                pair = "BTC/USDT", direction = "LONG", signalTimestampMillis = now - 1000,
                entryPrices = listOf(60000.0), tpPrices = listOf(61000.0), slPrice = 59000.0,
                reachedTpIndices = listOf(0), status = SignalStatus.COMPLETED.name,
                outcome = SignalOutcome.WIN.name, outcomeSource = PriceSourceType.COINGECKO.name
            ),
            TradingSignalEntity(
                id = "s6", userId = "u1", mentorId = "m_beta", mentorName = "Beta Scalper",
                pair = "BTC/USDT", direction = "LONG", signalTimestampMillis = now - 1000,
                entryPrices = listOf(60000.0), tpPrices = listOf(61000.0), slPrice = 59000.0,
                slHit = true, status = SignalStatus.COMPLETED.name,
                outcome = SignalOutcome.LOSS.name, outcomeSource = PriceSourceType.BINANCE_FUTURES.name
            ),
            TradingSignalEntity(
                id = "s7", userId = "u1", mentorId = "m_beta", mentorName = "Beta Scalper",
                pair = "BTC/USDT", direction = "LONG", signalTimestampMillis = now - 1000,
                entryPrices = listOf(60000.0), tpPrices = listOf(61000.0), slPrice = 59000.0,
                slHit = true, status = SignalStatus.COMPLETED.name,
                outcome = SignalOutcome.LOSS.name, outcomeSource = PriceSourceType.BINANCE_FUTURES.name
            )
        )

        val rankings = StatisticsCalculator.calculateMentorRankings(mentors, signals, now, periodDays = 30)
        // Both Beta and Alpha have 50.0% Win Rate, but Beta has 4 completed signals vs Alpha's 2 -> Beta is Rank #1
        assertEquals("m_beta", rankings[0].mentorId)
        assertEquals(1, rankings[0].rank)
        assertEquals(50.0, rankings[0].winRatePercent, 0.01)

        assertEquals("m_alpha", rankings[1].mentorId)
        assertEquals(2, rankings[1].rank)
        assertEquals(50.0, rankings[1].winRatePercent, 0.01)

        // Dashboard summary: no realized trades yet -> currentRealizedBalance & totalRealizedPnl are null
        val summaryWithoutTrades = StatisticsCalculator.calculateDashboardSummary(
            initialCapital = 1000.0,
            signals = signals,
            realizedTrades = emptyList(),
            nowMillis = now,
            periodDays = 30
        )
        assertNull(summaryWithoutTrades.currentRealizedBalance)
        assertNull(summaryWithoutTrades.totalRealizedPnl)
        assertEquals(6, summaryWithoutTrades.combinedSampleCount) // 3 wins + 2 losses + 1 breakeven
        assertEquals(50.0, summaryWithoutTrades.combinedWinRate, 0.01)

        // With a realized trade -> balance & P/L reflect actual closed trade
        val summaryWithTrades = StatisticsCalculator.calculateDashboardSummary(
            initialCapital = 1000.0,
            signals = signals,
            realizedTrades = listOf(
                RealizedTradeEntity(
                    id = "t1", userId = "u1", signalId = "s1", pair = "BTC/USDT", direction = "LONG",
                    entryPrice = 60000.0, exitPrice = 63000.0, positionSizeUsdt = 500.0,
                    feeUsdt = 1.0, realizedPnlUsdt = 24.0, closedAtMillis = now - 500
                )
            ),
            nowMillis = now,
            periodDays = 30
        )
        assertEquals(1024.0, summaryWithTrades.currentRealizedBalance ?: 0.0, 0.01)
        assertEquals(24.0, summaryWithTrades.totalRealizedPnl ?: 0.0, 0.01)
    }

    @Test
    fun `tradingView symbol mapper and cross-source CoinGecko conflict detection`() {
        assertTrue(com.example.ui.components.TradingViewSymbolMapper.isSymbolSupported("BTC/USDT"))
        assertFalse(com.example.ui.components.TradingViewSymbolMapper.isSymbolSupported("INVALID_PAIR_NO_SLASH"))
        assertEquals(
            "BINANCE:BTCUSDT.P",
            com.example.ui.components.TradingViewSymbolMapper.mapToTradingViewSymbol("BTC/USDT", PriceSourceType.BINANCE_FUTURES.name)
        )
        assertEquals(
            "BINANCE:ETHUSDT",
            com.example.ui.components.TradingViewSymbolMapper.mapToTradingViewSymbol("ETH/USDT", PriceSourceType.BINANCE_SPOT.name)
        )
        val html = com.example.ui.components.TradingViewSymbolMapper.buildWidgetHtml("BINANCE:BTCUSDT.P", "15")
        assertTrue(html.contains("BINANCE:BTCUSDT.P"))
        assertTrue(html.contains("Volume@tv-basicstudies"))

        // Cross-source conflict: CoinGecko hits TP1 (62050) while Binance Futures is at 61800
        val now = 1_700_000_000_000L
        val sig = TradingSignalEntity(
            id = "sig_conflict",
            userId = "u1",
            mentorId = "m1",
            mentorName = "Mentor Conflict",
            pair = "BTC/USDT",
            direction = SignalDirection.LONG.name,
            signalTimestampMillis = now,
            entryPrices = listOf(60000.0),
            reachedEntryIndices = listOf(0),
            tpPrices = listOf(62000.0),
            slPrice = 58000.0,
            status = SignalStatus.ACTIVE.name,
            outcome = SignalOutcome.PENDING.name
        )
        val binanceQuote = PriceQuote("BTC/USDT", 61800.0, PriceSourceType.BINANCE_FUTURES, now)
        val cgQuote = PriceQuote("BTC/USDT", 62050.0, PriceSourceType.COINGECKO, now)

        val eval = PriceMonitoringEngine.evaluateCrossSourceConflict(sig, binanceQuote, cgQuote, now, 60)
        assertTrue(eval.updatedSignal.hasSourceConflict)
        // Verified Binance state has NOT hit TP1 yet
        assertTrue(eval.updatedSignal.reachedTpIndices.isEmpty())
        assertTrue(eval.newEvents.any { it.eventType == SignalEventType.COINGECKO_CONFLICT.name })
    }
}
