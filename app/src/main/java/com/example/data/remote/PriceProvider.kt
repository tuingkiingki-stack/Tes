package com.example.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.domain.model.FeedConnectionStatus
import com.example.domain.model.PriceQuote
import com.example.domain.model.PriceSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class PriceFetchResponse(
    val primaryQuote: PriceQuote? = null,
    val referenceCoinGeckoQuote: PriceQuote? = null,
    val connectionStatus: FeedConnectionStatus = FeedConnectionStatus.TERHUBUNG,
    val errorDetail: String? = null
)

interface PriceProvider {
    suspend fun fetchPriceForPair(
        pair: String,
        preferredSource: PriceSourceType,
        staleThresholdSec: Int
    ): PriceFetchResponse

    fun isInternetAvailable(): Boolean
}

class LiveMarketPriceProvider(
    private val context: Context,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
) : PriceProvider {

    companion object {
        private val COINGECKO_ID_MAP = mapOf(
            "BTC" to "bitcoin",
            "ETH" to "ethereum",
            "SOL" to "solana",
            "BNB" to "binancecoin",
            "XRP" to "ripple",
            "ADA" to "cardano",
            "DOGE" to "dogecoin",
            "AVAX" to "avalanche-2",
            "DOT" to "polkadot",
            "LINK" to "chainlink",
            "MATIC" to "matic-network",
            "POL" to "polygon-ecosystem-token",
            "NEAR" to "near",
            "ARB" to "arbitrum",
            "OP" to "optimism",
            "SUI" to "sui",
            "APT" to "aptos",
            "INJ" to "injective-protocol",
            "LTC" to "litecoin",
            "ATOM" to "cosmos",
            "PEPE" to "pepe",
            "SHIB" to "shiba-inu",
            "TRX" to "tron",
            "TON" to "the-open-network"
        )

        fun toBinanceSymbol(pair: String): String {
            return pair.uppercase().replace("/", "").replace("-", "").trim()
        }

        fun toCoinGeckoId(pair: String): String? {
            val base = pair.uppercase().split("/", "-").firstOrNull()?.trim() ?: return null
            return COINGECKO_ID_MAP[base] ?: base.lowercase()
        }
    }

    override fun isInternetAvailable(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun fetchPriceForPair(
        pair: String,
        preferredSource: PriceSourceType,
        staleThresholdSec: Int
    ): PriceFetchResponse = withContext(Dispatchers.IO) {
        if (!isInternetAvailable()) {
            return@withContext PriceFetchResponse(
                primaryQuote = null,
                referenceCoinGeckoQuote = null,
                connectionStatus = FeedConnectionStatus.OFFLINE,
                errorDetail = "Tidak ada koneksi internet (OFFLINE)."
            )
        }

        val now = System.currentTimeMillis()
        val binanceSymbol = toBinanceSymbol(pair)

        // 1. Fetch primary source
        val binanceQuote: PriceQuote? = when (preferredSource) {
            PriceSourceType.BINANCE_FUTURES -> fetchBinanceFuturesMarkPrice(pair, binanceSymbol, now)
                ?: fetchBinanceSpotPrice(pair, binanceSymbol, now)
            PriceSourceType.BINANCE_SPOT -> fetchBinanceSpotPrice(pair, binanceSymbol, now)
            else -> null
        }

        // 2. Fetch CoinGecko reference (used when preferred is COINGECKO, or when Binance is unavailable, or for cross-source conflict detection)
        val cgQuote: PriceQuote? = fetchCoinGeckoPrice(pair, now)

        when {
            preferredSource == PriceSourceType.COINGECKO -> {
                if (cgQuote != null) {
                    val status = if (cgQuote.isExpired(now, staleThresholdSec)) {
                        FeedConnectionStatus.HARGA_KEDALUWARSA
                    } else {
                        FeedConnectionStatus.TERHUBUNG
                    }
                    PriceFetchResponse(
                        primaryQuote = cgQuote,
                        referenceCoinGeckoQuote = cgQuote,
                        connectionStatus = status
                    )
                } else {
                    PriceFetchResponse(
                        primaryQuote = binanceQuote,
                        referenceCoinGeckoQuote = null,
                        connectionStatus = if (binanceQuote != null) FeedConnectionStatus.TERHUBUNG else FeedConnectionStatus.HARGA_KEDALUWARSA,
                        errorDetail = "Data referensi CoinGecko tidak tersedia untuk $pair."
                    )
                }
            }

            binanceQuote != null -> {
                val status = if (binanceQuote.isExpired(now, staleThresholdSec)) {
                    FeedConnectionStatus.HARGA_KEDALUWARSA
                } else {
                    FeedConnectionStatus.TERHUBUNG
                }
                PriceFetchResponse(
                    primaryQuote = binanceQuote,
                    referenceCoinGeckoQuote = cgQuote,
                    connectionStatus = status
                )
            }

            else -> {
                // Internet is active, but Binance feed failed!
                // Never equate Binance feed failure with internet disconnection: display DATA BINANCE TIDAK TERSEDIA
                // and provide CoinGecko as clearly labeled alternative reference if available.
                PriceFetchResponse(
                    primaryQuote = cgQuote,
                    referenceCoinGeckoQuote = cgQuote,
                    connectionStatus = FeedConnectionStatus.DATA_BINANCE_TIDAK_TERSEDIA,
                    errorDetail = if (cgQuote != null) {
                        "DATA BINANCE TIDAK TERSEDIA — Menggunakan Referensi CoinGecko (tidak identik dengan Binance Mark Price)."
                    } else {
                        "DATA BINANCE TIDAK TERSEDIA untuk pair $pair."
                    }
                )
            }
        }
    }

    private fun fetchBinanceFuturesMarkPrice(pair: String, symbol: String, nowMillis: Long): PriceQuote? {
        val urls = listOf(
            "https://fapi.binance.com/fapi/v1/premiumIndex?symbol=$symbol",
            "https://fapi.binance.me/fapi/v1/premiumIndex?symbol=$symbol"
        )
        for (url in urls) {
            try {
                val req = Request.Builder().url(url).get().build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: return@use
                        val json = JSONObject(body)
                        val markPrice = json.optString("markPrice", "").toDoubleOrNull()
                        val serverTime = json.optLong("time", nowMillis).takeIf { it > 0L } ?: nowMillis
                        if (markPrice != null && markPrice > 0.0) {
                            return PriceQuote(
                                pair = pair,
                                price = markPrice,
                                source = PriceSourceType.BINANCE_FUTURES,
                                timestampMillis = serverTime,
                                isMarkPrice = true
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                // Try fallback mirror
            }
        }
        return null
    }

    private fun fetchBinanceSpotPrice(pair: String, symbol: String, nowMillis: Long): PriceQuote? {
        val urls = listOf(
            "https://api.binance.com/api/v3/ticker/price?symbol=$symbol",
            "https://api1.binance.com/api/v3/ticker/price?symbol=$symbol",
            "https://api.binance.me/api/v3/ticker/price?symbol=$symbol"
        )
        for (url in urls) {
            try {
                val req = Request.Builder().url(url).get().build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: return@use
                        val json = JSONObject(body)
                        val price = json.optString("price", "").toDoubleOrNull()
                        if (price != null && price > 0.0) {
                            return PriceQuote(
                                pair = pair,
                                price = price,
                                source = PriceSourceType.BINANCE_SPOT,
                                timestampMillis = nowMillis,
                                isMarkPrice = false
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                // Try fallback mirror
            }
        }
        return null
    }

    private fun fetchCoinGeckoPrice(pair: String, nowMillis: Long): PriceQuote? {
        val coinId = toCoinGeckoId(pair) ?: return null
        val url = "https://api.coingecko.com/api/v3/simple/price?ids=$coinId&vs_currencies=usd&include_last_updated_at=true"
        return try {
            val req = Request.Builder().url(url).get().build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val root = JSONObject(body)
                val coinObj = root.optJSONObject(coinId) ?: return null
                val usdPrice = coinObj.optDouble("usd", -1.0)
                if (usdPrice <= 0.0) return null
                // Note: CoinGecko's `last_updated_at` is an internal aggregation timestamp that can lag by several minutes;
                // we use the HTTP response receipt timestamp if within normal bounds or nowMillis for fresh quote delivery.
                PriceQuote(
                    pair = pair,
                    price = usdPrice,
                    source = PriceSourceType.COINGECKO,
                    timestampMillis = nowMillis,
                    isMarkPrice = false
                )
            }
        } catch (_: Exception) {
            null
        }
    }
}
