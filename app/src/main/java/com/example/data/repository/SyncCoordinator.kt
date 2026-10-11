package com.example.data.repository

import com.example.data.local.JournalDao
import com.example.data.local.MentorEntity
import com.example.data.local.RealizedTradeEntity
import com.example.data.local.SignalEventEntity
import com.example.data.local.SyncConflictEntity
import com.example.data.local.TradingSignalEntity
import com.example.data.local.UserProfileEntity
import com.example.data.model.MentorDoc
import com.example.data.model.RealizedTradeDoc
import com.example.data.model.SignalEventDoc
import com.example.data.model.TradingSignalDoc
import com.example.domain.model.SyncStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class SyncCoordinator(
    private val dao: JournalDao,
    private val firestoreRepository: JournalFirestoreRepository,
    private val isNetworkOnline: () -> Boolean
) {
    private val _syncStatus = MutableStateFlow(SyncStatus.TERSINKRONISASI)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private val _lastSyncError = MutableStateFlow<String?>(null)
    val lastSyncError: StateFlow<String?> = _lastSyncError.asStateFlow()

    fun setOfflineStatus() {
        _syncStatus.value = SyncStatus.OFFLINE
    }

    /**
     * Pushes pending local mutations to Cloud Firestore and confirms server acknowledgment
     * before clearing `isPendingSync`.
     */
    suspend fun pushPendingChangesToCloud(userId: String): SyncStatus {
        if (!isNetworkOnline()) {
            _syncStatus.value = SyncStatus.OFFLINE
            return SyncStatus.OFFLINE
        }

        _syncStatus.value = SyncStatus.SINKRONISASI
        var encounteredError = false

        // 1. UserProfile
        dao.getUserProfile(userId)?.let { profile ->
            if (profile.isPendingSync) {
                val res = firestoreRepository.saveUserProfile(profile.toFirestoreDoc())
                if (res.isSuccess) {
                    dao.upsertUserProfile(profile.copy(isPendingSync = false))
                } else {
                    encounteredError = true
                    _lastSyncError.value = res.exceptionOrNull()?.localizedMessage
                }
            }
        }

        // 2. Mentors
        for (mentor in dao.getPendingSyncMentors(userId)) {
            val res = firestoreRepository.createOrUpdateMentor(mentor.toFirestoreDoc())
            if (res.isSuccess) {
                dao.upsertMentor(mentor.copy(isPendingSync = false))
            } else {
                encounteredError = true
                _lastSyncError.value = res.exceptionOrNull()?.localizedMessage
            }
        }

        // 3. Signals (check for server conflict before overwriting)
        for (localSignal in dao.getPendingSyncSignals(userId)) {
            val remoteRes = firestoreRepository.getSignalById(localSignal.id, userId)
            if (remoteRes.isSuccess) {
                val remoteDoc = remoteRes.getOrNull()
                if (remoteDoc != null && detectSignalConflict(localSignal, remoteDoc)) {
                    recordSignalConflict(userId, localSignal, remoteDoc)
                    continue
                }
                val writeRes = if (remoteDoc == null) {
                    firestoreRepository.createSignal(localSignal.toFirestoreDoc())
                } else {
                    firestoreRepository.updateSignal(localSignal.toFirestoreDoc()).map { localSignal.id }
                }
                if (writeRes.isSuccess) {
                    // Only mark synced AFTER server confirms write success
                    dao.upsertSignal(localSignal.copy(isPendingSync = false))
                } else {
                    encounteredError = true
                    _lastSyncError.value = writeRes.exceptionOrNull()?.localizedMessage
                }
            } else {
                encounteredError = true
                _lastSyncError.value = remoteRes.exceptionOrNull()?.localizedMessage
            }
        }

        // 4. Signal Events
        for (event in dao.getPendingSyncEvents(userId)) {
            val res = firestoreRepository.createSignalEvent(event.toFirestoreDoc())
            if (res.isSuccess) {
                dao.upsertEvent(event.copy(isPendingSync = false))
            } else {
                encounteredError = true
                _lastSyncError.value = res.exceptionOrNull()?.localizedMessage
            }
        }

        // 5. Realized Trades
        for (trade in dao.getPendingSyncTrades(userId)) {
            val res = firestoreRepository.createRealizedTrade(trade.toFirestoreDoc())
            if (res.isSuccess) {
                dao.upsertRealizedTrade(trade.copy(isPendingSync = false))
            } else {
                encounteredError = true
                _lastSyncError.value = res.exceptionOrNull()?.localizedMessage
            }
        }

        val activeConflicts = dao.getSyncConflicts(userId)
        val finalStatus = when {
            activeConflicts.isNotEmpty() -> SyncStatus.MEMBUTUHKAN_PENINJAUAN
            encounteredError -> SyncStatus.KESALAHAN
            else -> {
                _lastSyncError.value = null
                SyncStatus.TERSINKRONISASI
            }
        }
        _syncStatus.value = finalStatus
        return finalStatus
    }

    /**
     * Reconciles incoming remote signals with local Room storage, respecting idempotency keys,
     * tombstones (`isDeleted`), and detecting concurrent edits from multiple devices.
     */
    suspend fun reconcileRemoteSignals(userId: String, remoteSignals: List<TradingSignalDoc>) {
        var conflictDetected = false
        for (remote in remoteSignals) {
            val local = dao.getSignalById(userId, remote.id)
            if (local == null) {
                dao.upsertSignal(remote.toEntity())
            } else if (local.lastOperationId == remote.lastOperationId) {
                // Same operation idempotency key — already in sync
                if (local.isPendingSync) {
                    dao.upsertSignal(local.copy(isPendingSync = false))
                }
            } else if (local.isPendingSync && detectSignalConflict(local, remote)) {
                conflictDetected = true
                recordSignalConflict(userId, local, remote)
            } else if (!local.isPendingSync && remote.revision >= local.revision) {
                dao.upsertSignal(remote.toEntity())
            }
        }

        val conflicts = dao.getSyncConflicts(userId)
        if (conflictDetected || conflicts.isNotEmpty()) {
            _syncStatus.value = SyncStatus.KONFLIK
        }
    }

    suspend fun reconcileRemoteMentors(userId: String, remoteMentors: List<MentorDoc>) {
        for (remote in remoteMentors) {
            val local = dao.getMentorById(userId, remote.id)
            if (local == null || (!local.isPendingSync && remote.revision >= local.revision)) {
                dao.upsertMentor(
                    MentorEntity(
                        id = remote.id,
                        userId = remote.userId,
                        displayName = remote.displayName,
                        handleOrChannel = remote.handleOrChannel,
                        specialty = remote.specialty,
                        isDeleted = remote.isDeleted,
                        revision = remote.revision,
                        isPendingSync = false
                    )
                )
            }
        }
    }

    suspend fun reconcileRemoteEvents(userId: String, remoteEvents: List<SignalEventDoc>) {
        for (remote in remoteEvents) {
            val existing = dao.getEventByIdempotencyKey(remote.idempotencyKey)
            if (existing == null) {
                dao.upsertEvent(
                    SignalEventEntity(
                        id = remote.id,
                        userId = remote.userId,
                        signalId = remote.signalId,
                        pair = remote.pair,
                        direction = remote.direction,
                        eventType = remote.eventType,
                        levelIndex = remote.levelIndex,
                        price = remote.price,
                        priceSource = remote.priceSource,
                        message = remote.message,
                        eventTimestampMillis = remote.eventTimestampMillis,
                        idempotencyKey = remote.idempotencyKey,
                        notificationStatus = remote.notificationStatus,
                        isPendingSync = false
                    )
                )
            }
        }
    }

    suspend fun reconcileRemoteTrades(userId: String, remoteTrades: List<RealizedTradeDoc>) {
        for (remote in remoteTrades) {
            dao.upsertRealizedTrade(
                RealizedTradeEntity(
                    id = remote.id,
                    userId = remote.userId,
                    signalId = remote.signalId,
                    pair = remote.pair,
                    direction = remote.direction,
                    entryPrice = remote.entryPrice,
                    exitPrice = remote.exitPrice,
                    positionSizeUsdt = remote.positionSizeUsdt,
                    feeUsdt = remote.feeUsdt,
                    realizedPnlUsdt = remote.realizedPnlUsdt,
                    closedAtMillis = remote.closedAtMillis,
                    isDeleted = remote.isDeleted,
                    revision = remote.revision,
                    isPendingSync = false
                )
            )
        }
    }

    fun detectSignalConflict(local: TradingSignalEntity, remote: TradingSignalDoc): Boolean {
        if (!local.isPendingSync) return false
        if (local.lastOperationId == remote.lastOperationId) return false
        val structuralDiff = local.entryPrices != remote.entryPrices ||
            local.tpPrices != remote.tpPrices ||
            local.slPrice != remote.slPrice ||
            local.status != remote.status ||
            local.outcome != remote.outcome ||
            local.isDeleted != remote.isDeleted
        return structuralDiff && remote.revision >= local.revision
    }

    private suspend fun recordSignalConflict(
        userId: String,
        local: TradingSignalEntity,
        remote: TradingSignalDoc
    ) {
        val localSummary = "${local.pair} [${local.direction}] • Status: ${local.status} (${local.outcome}) | Entry: ${local.entryPrices.joinToString("/")} | TP: ${local.tpPrices.joinToString("/")} | SL: ${local.slPrice} (Rev ${local.revision})"
        val remoteSummary = "${remote.pair} [${remote.direction}] • Status: ${remote.status} (${remote.outcome}) | Entry: ${remote.entryPrices.joinToString("/")} | TP: ${remote.tpPrices.joinToString("/")} | SL: ${remote.slPrice} (Rev ${remote.revision})"

        val remoteJson = JSONObject().apply {
            put("id", remote.id)
            put("userId", remote.userId)
            put("mentorId", remote.mentorId)
            put("mentorName", remote.mentorName)
            put("pair", remote.pair)
            put("direction", remote.direction)
            put("signalTimestampMillis", remote.signalTimestampMillis)
            put("entryPrices", JSONArray(remote.entryPrices))
            put("reachedEntryIndices", JSONArray(remote.reachedEntryIndices))
            put("missedEntryIndices", JSONArray(remote.missedEntryIndices))
            put("tpPrices", JSONArray(remote.tpPrices))
            put("reachedTpIndices", JSONArray(remote.reachedTpIndices))
            put("slPrice", remote.slPrice)
            put("slHit", remote.slHit)
            put("returnedToEntry", remote.returnedToEntry)
            put("status", remote.status)
            put("outcome", remote.outcome)
            put("isConservativeEstimation", remote.isConservativeEstimation)
            put("priceSource", remote.priceSource)
            put("outcomeSource", remote.outcomeSource)
            put("hasSourceConflict", remote.hasSourceConflict)
            put("lastCheckedPrice", remote.lastCheckedPrice)
            put("lastCheckedAtMillis", remote.lastCheckedAtMillis)
            put("completedAtMillis", remote.completedAtMillis)
            put("isDeleted", remote.isDeleted)
            put("revision", remote.revision)
            put("lastOperationId", remote.lastOperationId)
        }.toString()

        dao.upsertSyncConflict(
            SyncConflictEntity(
                id = "conflict_${local.id}",
                userId = userId,
                entityType = "SIGNAL",
                entityId = local.id,
                title = "Konflik Sinyal ${local.pair} (${local.mentorName})",
                localRevision = local.revision,
                remoteRevision = remote.revision,
                localSummary = localSummary,
                remoteSummary = remoteSummary,
                remoteJsonPayload = remoteJson
            )
        )
        _syncStatus.value = SyncStatus.MEMBUTUHKAN_PENINJAUAN
    }

    suspend fun resolveConflict(
        userId: String,
        conflict: SyncConflictEntity,
        keepLocal: Boolean
    ) {
        if (keepLocal) {
            val local = dao.getSignalById(userId, conflict.entityId)
            if (local != null) {
                val bumped = local.copy(
                    revision = maxOf(local.revision, conflict.remoteRevision) + 1,
                    lastOperationId = "op_resolve_local_${System.currentTimeMillis()}",
                    isPendingSync = true
                )
                dao.upsertSignal(bumped)
                if (isNetworkOnline()) {
                    val res = firestoreRepository.updateSignal(bumped.toFirestoreDoc())
                    if (res.isSuccess) {
                        dao.upsertSignal(bumped.copy(isPendingSync = false))
                    }
                }
            }
        } else {
            val s = JSONObject(conflict.remoteJsonPayload)
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

            val remoteEntity = TradingSignalEntity(
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
                isDeleted = s.optBoolean("isDeleted", false),
                revision = s.optInt("revision", 1),
                lastOperationId = s.optString("lastOperationId", "op_cloud"),
                isPendingSync = false
            )
            dao.upsertSignal(remoteEntity)
        }
        dao.deleteSyncConflict(userId, conflict.id)
        val remaining = dao.getSyncConflicts(userId)
        _syncStatus.value = if (remaining.isEmpty()) SyncStatus.TERSINKRONISASI else SyncStatus.MEMBUTUHKAN_PENINJAUAN
    }

    private fun TradingSignalDoc.toEntity(): TradingSignalEntity = TradingSignalEntity(
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
        lastOperationId = lastOperationId,
        isPendingSync = false
    )
}
