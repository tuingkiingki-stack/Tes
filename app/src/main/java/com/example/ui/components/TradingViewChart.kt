package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.local.TradingSignalEntity
import com.example.domain.model.PriceSourceType
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CardSurface
import com.example.ui.theme.CrimsonShort
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.ElevatedCardSurface
import com.example.ui.theme.EmeraldLong
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.TextSecondary

enum class ChartTimeframe(val label: String, val tvInterval: String) {
    M1("1m", "1"),
    M5("5m", "5"),
    M15("15m", "15"),
    H1("1h", "60"),
    H4("4h", "240"),
    D1("1D", "D")
}

object TradingViewSymbolMapper {
    private val VALID_PAIR_REGEX = Regex("^[A-Z0-9]{2,12}/[A-Z0-9]{2,10}$")

    fun isSymbolSupported(pair: String): Boolean {
        val clean = pair.trim().uppercase()
        return VALID_PAIR_REGEX.matches(clean)
    }

    fun mapToTradingViewSymbol(pair: String, priceSource: String): String {
        val clean = pair.trim().uppercase().replace("/", "").replace("-", "")
        return when (priceSource) {
            PriceSourceType.BINANCE_FUTURES.name -> "BINANCE:${clean}.P"
            PriceSourceType.BINANCE_SPOT.name -> "BINANCE:$clean"
            else -> "BINANCE:$clean"
        }
    }

    fun buildExternalTradingViewUrl(tvSymbol: String): String {
        return "https://www.tradingview.com/chart/?symbol=${Uri.encode(tvSymbol)}"
    }

    fun buildWidgetHtml(tvSymbol: String, interval: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
                <style>
                    html, body {
                        margin: 0;
                        padding: 0;
                        width: 100%;
                        height: 100%;
                        background-color: #080D1A;
                        overflow: hidden;
                    }
                    #tv_chart_container {
                        width: 100%;
                        height: 100%;
                    }
                </style>
            </head>
            <body>
                <div class="tradingview-widget-container" id="tv_chart_container">
                    <div id="tradingview_advanced_chart" style="width:100%;height:100%;"></div>
                </div>
                <script type="text/javascript" src="https://s3.tradingview.com/tv.js"></script>
                <script type="text/javascript">
                    try {
                        new TradingView.widget({
                            "autosize": true,
                            "symbol": "$tvSymbol",
                            "interval": "$interval",
                            "timezone": "Etc/UTC",
                            "theme": "dark",
                            "style": "1",
                            "locale": "id",
                            "backgroundColor": "#080D1A",
                            "gridColor": "rgba(40, 53, 72, 0.35)",
                            "hide_top_toolbar": false,
                            "hide_legend": false,
                            "save_image": false,
                            "volume": true,
                            "studies": [
                                "Volume@tv-basicstudies"
                            ],
                            "container_id": "tradingview_advanced_chart"
                        });
                    } catch (e) {
                        document.body.innerHTML = '<div style="color:#94A3B8;padding:16px;font-family:sans-serif;">Grafik TradingView tidak dapat dimuat saat ini.</div>';
                    }
                </script>
            </body>
            </html>
        """.trimIndent()
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TradingViewSignalChartSection(
    signal: TradingSignalEntity,
    isOnline: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTimeframe by remember { mutableStateOf(ChartTimeframe.M15) }
    var webViewError by remember { mutableStateOf(false) }

    val symbolSupported = remember(signal.pair) {
        TradingViewSymbolMapper.isSymbolSupported(signal.pair)
    }
    val tvSymbol = remember(signal.pair, signal.priceSource) {
        TradingViewSymbolMapper.mapToTradingViewSymbol(signal.pair, signal.priceSource)
    }
    val externalUrl = remember(tvSymbol) {
        TradingViewSymbolMapper.buildExternalTradingViewUrl(tvSymbol)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardSurface)
            .border(1.dp, SubtleBorder, RoundedCornerShape(16.dp))
            .padding(14.dp)
            .testTag("tradingview_chart_section")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Grafik Candlestick TradingView",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Simbol: $tvSymbol • Visualisasi Pasar",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            OutlinedButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(externalUrl))
                    context.startActivity(intent)
                },
                contentPadding = ButtonDefaults.TextButtonContentPadding,
                modifier = Modifier.testTag("open_external_tradingview_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "Buka di situs TradingView"
                )
                Spacer(modifier = Modifier.padding(horizontal = 3.dp))
                Text("TradingView.com", style = MaterialTheme.typography.labelMedium)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Timeframe selector: 1m, 5m, 15m, 1h, 4h, 1D
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChartTimeframe.entries.forEach { tf ->
                val isSelected = selectedTimeframe == tf
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) CyanAccent.copy(alpha = 0.2f) else ElevatedCardSurface,
                    modifier = Modifier
                        .border(
                            width = 1.dp,
                            color = if (isSelected) CyanAccent else SubtleBorder,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable {
                            selectedTimeframe = tf
                            webViewError = false
                        }
                        .testTag("timeframe_chip_${tf.label}")
                ) {
                    Text(
                        text = tf.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) CyanAccent else TextSecondary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (!symbolSupported) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ElevatedCardSurface)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Simbol tidak tersedia",
                        tint = AmberWarning
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Simbol tidak tersedia untuk pair ${signal.pair}",
                        style = MaterialTheme.typography.titleSmall,
                        color = AmberWarning
                    )
                    Text(
                        text = "Silakan gunakan tautan TradingView.com di atas untuk mencari simbol secara manual.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        } else if (!isOnline || webViewError) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ElevatedCardSurface)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Grafik offline",
                        tint = AmberWarning
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Widget grafik membutuhkan koneksi internet aktif.",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "Panel level harga sinyal tetap tersedia secara offline di bawah ini.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        } else {
            val htmlContent = remember(tvSymbol, selectedTimeframe) {
                TradingViewSymbolMapper.buildWidgetHtml(tvSymbol, selectedTimeframe.tvInterval)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, SubtleBorder, RoundedCornerShape(12.dp))
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.cacheMode = WebSettings.LOAD_DEFAULT
                            webViewClient = object : WebViewClient() {
                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    if (request?.isForMainFrame == true) {
                                        webViewError = true
                                    }
                                }
                            }
                        }
                    },
                    update = { webView ->
                        webView.loadDataWithBaseURL(
                            "https://www.tradingview.com",
                            htmlContent,
                            "text/html",
                            "UTF-8",
                            null
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Synchronized Price Levels Panel near the chart (since free TradingView widget iframe does not support custom horizontal line injection)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(ElevatedCardSurface)
                .padding(12.dp)
                .testTag("synchronized_price_levels_panel")
        ) {
            Text(
                text = "Panel Level Harga Tersinkronisasi (${signal.pair} • ${signal.direction})",
                style = MaterialTheme.typography.labelLarge,
                color = CyanAccent
            )
            Text(
                text = "Catatan: Widget TradingView hanya untuk visualisasi. Deteksi Entry/TP/SL menggunakan feed harga terverifikasi (${signal.priceSource}).",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                signal.entryPrices.forEachIndexed { idx, price ->
                    val hit = idx in signal.reachedEntryIndices
                    val missed = idx in signal.missedEntryIndices
                    LevelPill(
                        title = "Entry ${idx + 1}",
                        price = price,
                        statusText = when {
                            hit -> "TERSENTUH"
                            missed -> "TIDAK TERCAPAI"
                            else -> "AKTIF"
                        },
                        accentColor = if (hit) CyanAccent else if (missed) AmberWarning else TextSecondary
                    )
                }
                signal.tpPrices.forEachIndexed { idx, price ->
                    val hit = idx in signal.reachedTpIndices
                    LevelPill(
                        title = "TP${idx + 1}",
                        price = price,
                        statusText = if (hit) "TERCAPAI" else "TARGET",
                        accentColor = EmeraldLong
                    )
                }
                LevelPill(
                    title = "SL",
                    price = signal.slPrice,
                    statusText = if (signal.slHit) "TERSENTUH" else "BATAS RISIKO",
                    accentColor = CrimsonShort
                )
            }
        }
    }
}

@Composable
private fun LevelPill(
    title: String,
    price: Double,
    statusText: String,
    accentColor: androidx.compose.ui.graphics.Color
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(CardSurface)
            .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = "$title • $statusText",
            style = MaterialTheme.typography.labelSmall,
            color = accentColor
        )
        Text(
            text = "$price",
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = JetBrainsMonoFamily,
            fontWeight = FontWeight.Bold
        )
    }
}
