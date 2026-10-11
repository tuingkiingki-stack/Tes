package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.model.MentorDoc
import com.example.data.model.RealizedTradeDoc
import com.example.data.model.SignalEventDoc
import com.example.data.model.TradingSignalDoc
import com.example.data.model.UserProfileDoc

@Entity(tableName = "user_profiles")
data class UserProfileEntity(
    @PrimaryKey val userId: String,
    val initialCapital: Double = 1000.0,
    val preferredPriceSource: String = "BINANCE_FUTURES",
    val priceStaleThresholdSec: Int = 60,
    val notificationsEnabled: Boolean = true,
    val revision: Int = 1,
    val isPendingSync: Boolean = false,
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    fun toFirestoreDoc(): UserProfileDoc = UserProfileDoc(
        id = userId,
        userId = userId,
        initialCapital = initialCapital,
        preferredPriceSource = preferredPriceSource,
        priceStaleThresholdSec = priceStaleThresholdSec,
        notificationsEnabled = notificationsEnabled,
        revision = revision
    )
}

@Entity(
    tableName = "mentors",
    indices = [Index(value = ["userId"])]
)
data class MentorEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val displayName: String,
    val handleOrChannel: String = "",
    val specialty: String = "",
    val isDeleted: Boolean = false,
    val revision: Int = 1,
    val isPendingSync: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    fun toFirestoreDoc(): MentorDoc = MentorDoc(
        id = id,
        userId = userId,
        displayName = displayName,
        handleOrChannel = handleOrChannel,
        specialty = specialty,
        isDeleted = isDeleted,
        revision = revision
    )
}

@Entity(
    tableName = "trading_signals",
    indices = [Index(value = ["userId", "status"]), Index(value = ["userId", "mentorId"])]
)
data class TradingSignalEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val mentorId: String,
    val mentorName: String,
    val pair: String,
    val direction: String, // LONG or SHORT
    val signalTimestampMillis: Long,
    val entryPrices: List<Double>,
    val reachedEntryIndices: List<Int> = emptyList(),
    val missedEntryIndices: List<Int> = emptyList(),
    val tpPrices: List<Double>,
    val reachedTpIndices: List<Int> = emptyList(),
    val slPrice: Double,
    val slHit: Boolean = false,
    val returnedToEntry: Boolean = false,
    val status: String = "ACTIVE", // ACTIVE, COMPLETED, CANCELLED, ARCHIVED
    val outcome: String = "PENDING", // PENDING, WIN, LOSS, BREAKEVEN, CANCELLED_NO_RESULT
    val isConservativeEstimation: Boolean = false,
    val priceSource: String = "BINANCE_FUTURES",
    val outcomeSource: String = "NONE",
    val hasSourceConflict: Boolean = false,
    val lastCheckedPrice: Double = 0.0,
    val lastCheckedAtMillis: Long = 0L,
    val completedAtMillis: Long = 0L,
    val isDeleted: Boolean = false,
    val revision: Int = 1,
    val lastOperationId: String = "op_init",
    val isPendingSync: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    fun toFirestoreDoc(): TradingSignalDoc = TradingSignalDoc(
        id = id,
        userId = userId,
        mentorId = mentorId,
        mentorName = mentorName,
        pair = pair,
        direction = direction,
        signalTimestampMillis = signalTimestampMillis,
        entryPrices = entryPrices,
        reachedEntryIndices = reachedEntryIndices,
        missedEntryIndices = missedEntryIndices,
        tpPrices = tpPrices,
        reachedTpIndices = reachedTpIndices,
        slPrice = slPrice,
        slHit = slHit,
        returnedToEntry = returnedToEntry,
        status = status,
        outcome = outcome,
        isConservativeEstimation = isConservativeEstimation,
        priceSource = priceSource,
        outcomeSource = outcomeSource,
        hasSourceConflict = hasSourceConflict,
        lastCheckedPrice = lastCheckedPrice,
        lastCheckedAtMillis = lastCheckedAtMillis,
        completedAtMillis = completedAtMillis,
        isDeleted = isDeleted,
        revision = revision,
        lastOperationId = lastOperationId
    )
}

@Entity(
    tableName = "signal_events",
    indices = [
        Index(value = ["userId", "signalId"]),
        Index(value = ["idempotencyKey"], unique = true)
    ]
)
data class SignalEventEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val signalId: String,
    val pair: String,
    val direction: String,
    val eventType: String,
    val levelIndex: Int = -1,
    val price: Double = 0.0,
    val priceSource: String = "SYSTEM",
    val message: String,
    val eventTimestampMillis: Long,
    val idempotencyKey: String,
    val notificationStatus: String = "NOT_REQUIRED", // PENDING, SENT, FAILED, NOT_REQUIRED
    val notificationRetryCount: Int = 0,
    val isPendingSync: Boolean = false
) {
    fun toFirestoreDoc(): SignalEventDoc = SignalEventDoc(
        id = id,
        userId = userId,
        signalId = signalId,
        pair = pair,
        direction = direction,
        eventType = eventType,
        levelIndex = levelIndex,
        price = price,
        priceSource = priceSource,
        message = message,
        eventTimestampMillis = eventTimestampMillis,
        idempotencyKey = idempotencyKey,
        notificationStatus = notificationStatus
    )
}

@Entity(
    tableName = "realized_trades",
    indices = [Index(value = ["userId"])]
)
data class RealizedTradeEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val signalId: String = "",
    val pair: String,
    val direction: String,
    val entryPrice: Double,
    val exitPrice: Double,
    val positionSizeUsdt: Double,
    val feeUsdt: Double,
    val realizedPnlUsdt: Double,
    val closedAtMillis: Long,
    val isDeleted: Boolean = false,
    val revision: Int = 1,
    val isPendingSync: Boolean = false
) {
    fun toFirestoreDoc(): RealizedTradeDoc = RealizedTradeDoc(
        id = id,
        userId = userId,
        signalId = signalId,
        pair = pair,
        direction = direction,
        entryPrice = entryPrice,
        exitPrice = exitPrice,
        positionSizeUsdt = positionSizeUsdt,
        feeUsdt = feeUsdt,
        realizedPnlUsdt = realizedPnlUsdt,
        closedAtMillis = closedAtMillis,
        isDeleted = isDeleted,
        revision = revision
    )
}

@Entity(
    tableName = "sync_conflicts",
    indices = [Index(value = ["userId"])]
)
data class SyncConflictEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val entityType: String, // SIGNAL or MENTOR
    val entityId: String,
    val title: String,
    val localRevision: Int,
    val remoteRevision: Int,
    val localSummary: String,
    val remoteSummary: String,
    val remoteJsonPayload: String,
    val detectedAtMillis: Long = System.currentTimeMillis()
)
