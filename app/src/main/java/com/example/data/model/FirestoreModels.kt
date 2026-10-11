package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfileDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val initialCapital: Double = 1000.0,
    val preferredPriceSource: String = "BINANCE_FUTURES",
    val priceStaleThresholdSec: Int = 60,
    val notificationsEnabled: Boolean = true,
    val revision: Int = 1,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class MentorDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val handleOrChannel: String = "",
    val specialty: String = "",
    @get:PropertyName("isDeleted") @set:PropertyName("isDeleted")
    var isDeleted: Boolean = false,
    val revision: Int = 1,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class TradingSignalDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val mentorId: String = "",
    val mentorName: String = "",
    val pair: String = "BTC/USDT",
    val direction: String = "LONG",
    val signalTimestampMillis: Long = 0L,
    val entryPrices: List<Double> = emptyList(),
    val reachedEntryIndices: List<Int> = emptyList(),
    val missedEntryIndices: List<Int> = emptyList(),
    val tpPrices: List<Double> = emptyList(),
    val reachedTpIndices: List<Int> = emptyList(),
    val slPrice: Double = 0.0,
    val slHit: Boolean = false,
    val returnedToEntry: Boolean = false,
    val status: String = "ACTIVE",
    val outcome: String = "PENDING",
    @get:PropertyName("isConservativeEstimation") @set:PropertyName("isConservativeEstimation")
    var isConservativeEstimation: Boolean = false,
    val priceSource: String = "BINANCE_FUTURES",
    val outcomeSource: String = "NONE",
    val hasSourceConflict: Boolean = false,
    val lastCheckedPrice: Double = 0.0,
    val lastCheckedAtMillis: Long = 0L,
    val completedAtMillis: Long = 0L,
    @get:PropertyName("isDeleted") @set:PropertyName("isDeleted")
    var isDeleted: Boolean = false,
    val revision: Int = 1,
    val lastOperationId: String = "op_init",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SignalEventDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val signalId: String = "",
    val pair: String = "BTC/USDT",
    val direction: String = "LONG",
    val eventType: String = "CREATED",
    val levelIndex: Int = -1,
    val price: Double = 0.0,
    val priceSource: String = "SYSTEM",
    val message: String = "",
    val eventTimestampMillis: Long = 0L,
    val idempotencyKey: String = "",
    val notificationStatus: String = "NOT_REQUIRED",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class RealizedTradeDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val signalId: String = "",
    val pair: String = "BTC/USDT",
    val direction: String = "LONG",
    val entryPrice: Double = 0.0,
    val exitPrice: Double = 0.0,
    val positionSizeUsdt: Double = 0.0,
    val feeUsdt: Double = 0.0,
    val realizedPnlUsdt: Double = 0.0,
    val closedAtMillis: Long = 0L,
    @get:PropertyName("isDeleted") @set:PropertyName("isDeleted")
    var isDeleted: Boolean = false,
    val revision: Int = 1,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
