package com.example.domain.logic

import com.example.domain.model.SignalDirection

data class SignalValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null
)

object SignalValidator {

    fun validateSignalInput(
        mentorName: String,
        pair: String,
        direction: SignalDirection,
        entryPrices: List<Double>,
        tpPrices: List<Double>,
        slPrice: Double?
    ): SignalValidationResult {
        if (mentorName.trim().isEmpty()) {
            return SignalValidationResult(false, "Nama mentor wajib diisi.")
        }
        if (mentorName.trim().length > 80) {
            return SignalValidationResult(false, "Nama mentor maksimal 80 karakter.")
        }

        val cleanPair = pair.trim().uppercase()
        if (cleanPair.isEmpty() || !cleanPair.matches(Regex("^[A-Z0-9]{2,15}(/[A-Z0-9]{2,10})?$"))) {
            return SignalValidationResult(false, "Format pair tidak valid (contoh yang benar: BTC/USDT atau ETH/USDT).")
        }

        if (entryPrices.isEmpty()) {
            return SignalValidationResult(false, "Minimal satu harga Entry (Entry 1) wajib diisi.")
        }
        if (entryPrices.size > 10) {
            return SignalValidationResult(false, "Maksimal 10 level Entry yang diperbolehkan.")
        }
        if (entryPrices.any { it <= 0.0 || it.isNaN() || it.isInfinite() }) {
            return SignalValidationResult(false, "Seluruh harga Entry harus lebih besar dari 0.")
        }
        if (entryPrices.distinct().size != entryPrices.size) {
            return SignalValidationResult(false, "Tidak boleh ada level harga Entry yang duplikat.")
        }

        if (tpPrices.isEmpty()) {
            return SignalValidationResult(false, "Minimal satu target Take Profit (TP1) wajib diisi.")
        }
        if (tpPrices.size > 10) {
            return SignalValidationResult(false, "Maksimal 10 level TP yang diperbolehkan.")
        }
        if (tpPrices.any { it <= 0.0 || it.isNaN() || it.isInfinite() }) {
            return SignalValidationResult(false, "Seluruh harga TP harus lebih besar dari 0.")
        }

        if (slPrice == null || slPrice <= 0.0 || slPrice.isNaN() || slPrice.isInfinite()) {
            return SignalValidationResult(false, "Harga Stop Loss (SL) wajib diisi dan lebih besar dari 0.")
        }

        val minEntry = entryPrices.minOrNull()!!
        val maxEntry = entryPrices.maxOrNull()!!
        val minTp = tpPrices.minOrNull()!!
        val maxTp = tpPrices.maxOrNull()!!

        when (direction) {
            SignalDirection.LONG -> {
                if (slPrice >= minEntry) {
                    return SignalValidationResult(
                        false,
                        "Sinyal LONG tidak valid: Harga SL ($slPrice) harus berada di bawah harga Entry terendah ($minEntry)."
                    )
                }
                if (minTp <= maxEntry) {
                    return SignalValidationResult(
                        false,
                        "Sinyal LONG tidak valid: Seluruh harga TP (terendah: $minTp) harus berada di atas harga Entry tertinggi ($maxEntry)."
                    )
                }
                for (i in 0 until tpPrices.size - 1) {
                    if (tpPrices[i] >= tpPrices[i + 1]) {
                        return SignalValidationResult(
                            false,
                            "Urutan TP LONG tidak valid: TP${i + 1} (${tpPrices[i]}) harus lebih kecil dari TP${i + 2} (${tpPrices[i + 1]})."
                        )
                    }
                }
            }

            SignalDirection.SHORT -> {
                if (slPrice <= maxEntry) {
                    return SignalValidationResult(
                        false,
                        "Sinyal SHORT tidak valid: Harga SL ($slPrice) harus berada di atas harga Entry tertinggi ($maxEntry)."
                    )
                }
                if (maxTp >= minEntry) {
                    return SignalValidationResult(
                        false,
                        "Sinyal SHORT tidak valid: Seluruh harga TP (tertinggi: $maxTp) harus berada di bawah harga Entry terendah ($minEntry)."
                    )
                }
                for (i in 0 until tpPrices.size - 1) {
                    if (tpPrices[i] <= tpPrices[i + 1]) {
                        return SignalValidationResult(
                            false,
                            "Urutan TP SHORT tidak valid: TP${i + 1} (${tpPrices[i]}) harus lebih besar dari TP${i + 2} (${tpPrices[i + 1]})."
                        )
                    }
                }
            }
        }

        return SignalValidationResult(true)
    }

    fun normalizePair(rawPair: String): String {
        val cleaned = rawPair.trim().uppercase().replace("-", "/").replace("_", "/").replace(" ", "")
        if (cleaned.contains("/")) return cleaned
        val knownQuotes = listOf("USDT", "USDC", "FDUSD", "BUSD", "BTC", "ETH", "USD")
        for (quote in knownQuotes) {
            if (cleaned.endsWith(quote) && cleaned.length > quote.length) {
                val base = cleaned.removeSuffix(quote)
                return "$base/$quote"
            }
        }
        return "$cleaned/USDT"
    }
}
