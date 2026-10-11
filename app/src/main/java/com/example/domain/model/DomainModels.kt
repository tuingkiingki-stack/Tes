package com.example.domain.model

enum class SignalDirection(val label: String) {
    LONG("LONG"),
    SHORT("SHORT")
}

enum class SignalStatus(val label: String) {
    ACTIVE("Aktif"),
    COMPLETED("Selesai"),
    CANCELLED("Dibatalkan"),
    ARCHIVED("Arsip")
}

enum class SignalOutcome(val label: String) {
    PENDING("Menunggu"),
    WIN("MENANG"),
    LOSS("KALAH"),
    BREAKEVEN("IMPAS"),
    CANCELLED_NO_RESULT("DIBATALKAN TANPA HASIL")
}

enum class PriceSourceType(val label: String) {
    BINANCE_FUTURES("Binance Futures (Mark Price)"),
    BINANCE_SPOT("Binance Spot"),
    COINGECKO("Referensi CoinGecko"),
    USER("Input Pengguna"),
    SYSTEM("Sistem")
}

enum class SyncStatus(val label: String, val badgeText: String) {
    TERSINKRONISASI("Data tersinkronisasi dengan Cloud", "TERSINKRONISASI"),
    SINKRONISASI("Sedang menyinkronkan ke Cloud...", "SINKRONISASI"),
    OFFLINE("Mode Offline — tersimpan di perangkat", "OFFLINE"),
    KESALAHAN("Gagal sinkronisasi ke Cloud", "KESALAHAN"),
    KONFLIK("Konflik perubahan terdeteksi", "KONFLIK"),
    MEMBUTUHKAN_PENINJAUAN("Membutuhkan peninjauan konflik", "MEMBUTUHKAN_PENINJAUAN")
}

enum class FeedConnectionStatus(val label: String) {
    TERHUBUNG("TERHUBUNG"),
    OFFLINE("OFFLINE"),
    DATA_BINANCE_TIDAK_TERSEDIA("DATA BINANCE TIDAK TERSEDIA"),
    HARGA_KEDALUWARSA("HARGA KEDALUWARSA")
}

enum class SignalEventType(val label: String) {
    CREATED("Sinyal Dibuat"),
    ENTRY_HIT("Entry Tersentuh"),
    TP_HIT("TP Tercapai"),
    SL_HIT("SL Tercapai"),
    RETURN_TO_ENTRY("KEMBALI KE ENTRY"),
    ENTRY_MISSED("Entry TIDAK TERCAPAI"),
    COMPLETED("Sinyal Selesai"),
    CANCELLED("Sinyal Dibatalkan"),
    ARCHIVED("Sinyal Diarsipkan"),
    RESTORED("Sinyal Dipulihkan"),
    EDITED("Level Sinyal Diedit"),
    COINGECKO_CONFLICT("REFERENSI COINGECKO — TIDAK TERVERIFIKASI BINANCE"),
    CONSERVATIVE_ESTIMATE("ESTIMASI KONSERVATIF (SL Didahulukan)")
}

enum class NotificationDeliveryStatus(val label: String) {
    PENDING("Menunggu"),
    SENT("Terkirim"),
    FAILED("Gagal (Perlu Coba Lagi)"),
    NOT_REQUIRED("Tidak Perlu")
}

data class PriceQuote(
    val pair: String,
    val price: Double,
    val source: PriceSourceType,
    val timestampMillis: Long,
    val isMarkPrice: Boolean = false
) {
    fun isExpired(nowMillis: Long, staleThresholdSec: Int): Boolean {
        val ageMs = nowMillis - timestampMillis
        return ageMs > staleThresholdSec * 1000L
    }
}

data class LevelDistanceInfo(
    val label: String,
    val targetPrice: Double,
    val currentPrice: Double,
    val priceDiff: Double,
    val percentDiff: Double,
    val isReached: Boolean,
    val isMissed: Boolean = false
)

data class MentorStats(
    val mentorId: String,
    val displayName: String,
    val handleOrChannel: String,
    val specialty: String,
    val totalSignals: Int,
    val completedSignals: Int,
    val activeSignals: Int,
    val winCount: Int,
    val lossCount: Int,
    val breakevenCount: Int,
    val winRatePercent: Double,
    val binanceWinRatePercent: Double,
    val binanceSampleCount: Int,
    val coingeckoWinRatePercent: Double,
    val coingeckoSampleCount: Int,
    val rank: Int
)

data class DashboardSummary(
    val initialCapital: Double,
    val currentRealizedBalance: Double?,
    val totalRealizedPnl: Double?,
    val realizedTradeCount: Int,
    val estimatedSignalReturnPercent: Double,
    val combinedWinRate: Double,
    val combinedSampleCount: Int,
    val binanceWinRate: Double,
    val binanceSampleCount: Int,
    val coingeckoWinRate: Double,
    val coingeckoSampleCount: Int,
    val totalSignals: Int,
    val tpHitTotalCount: Int,
    val slHitSignalCount: Int,
    val activeSignalsCount: Int,
    val completedSignalsCount: Int,
    val winCount: Int,
    val lossCount: Int,
    val breakevenCount: Int,
    val periodDays: Int
)
