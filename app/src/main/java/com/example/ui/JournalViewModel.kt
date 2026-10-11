package com.example.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.JournalDao
import com.example.data.local.MentorEntity
import com.example.data.local.RealizedTradeEntity
import com.example.data.local.SignalEventEntity
import com.example.data.local.SyncConflictEntity
import com.example.data.local.TradingSignalEntity
import com.example.data.local.UserProfileEntity
import com.example.data.remote.PriceProvider
import com.example.data.repository.BackupRestoreManager
import com.example.data.repository.JournalFirestoreRepository
import com.example.data.repository.RestoreMode
import com.example.data.repository.SyncCoordinator
import com.example.domain.logic.PriceMonitoringEngine
import com.example.domain.logic.SignalValidator
import com.example.domain.logic.StatisticsCalculator
import com.example.domain.model.DashboardSummary
import com.example.domain.model.FeedConnectionStatus
import com.example.domain.model.MentorStats
import com.example.domain.model.NotificationDeliveryStatus
import com.example.domain.model.PriceQuote
import com.example.domain.model.PriceSourceType
import com.example.domain.model.SignalDirection
import com.example.domain.model.SignalEventType
import com.example.domain.model.SignalOutcome
import com.example.domain.model.SignalStatus
import com.example.domain.model.SyncStatus
import com.example.service.SignalNotificationManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class JournalViewModel(
    val currentUserId: String,
    val userEmail: String?,
    private val dao: JournalDao,
    private val firestoreRepository: JournalFirestoreRepository,
    private val priceProvider: PriceProvider,
    private val notificationManager: SignalNotificationManager,
    private val syncCoordinator: SyncCoordinator,
    private val backupRestoreManager: BackupRestoreManager,
    private val enableAutoMonitoringLoop: Boolean = true
) : ViewModel() {

    private val _selectedPeriodDays = MutableStateFlow(30)
    val selectedPeriodDays: StateFlow<Int> = _selectedPeriodDays.asStateFlow()

    private val _feedConnectionStatus = MutableStateFlow(FeedConnectionStatus.TERHUBUNG)
    val feedConnectionStatus: StateFlow<FeedConnectionStatus> = _feedConnectionStatus.asStateFlow()

    private val _latestPairQuotes = MutableStateFlow<Map<String, PriceQuote>>(emptyMap())
    val latestPairQuotes: StateFlow<Map<String, PriceQuote>> = _latestPairQuotes.asStateFlow()

    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

    private val _isMonitoringActive = MutableStateFlow(false)
    val isMonitoringActive: StateFlow<Boolean> = _isMonitoringActive.asStateFlow()

    val syncStatus: StateFlow<SyncStatus> = syncCoordinator.syncStatus

    val userProfile: StateFlow<UserProfileEntity> = dao.observeUserProfile(currentUserId)
        .mapOrDefaultProfile(currentUserId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UserProfileEntity(userId = currentUserId)
        )

    val signals: StateFlow<List<TradingSignalEntity>> = dao.observeSignals(currentUserId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val mentors: StateFlow<List<MentorEntity>> = dao.observeMentors(currentUserId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val allEvents: StateFlow<List<SignalEventEntity>> = dao.observeAllEvents(currentUserId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val realizedTrades: StateFlow<List<RealizedTradeEntity>> = dao.observeRealizedTrades(currentUserId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val syncConflicts: StateFlow<List<SyncConflictEntity>> = dao.observeSyncConflicts(currentUserId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val dashboardSummary: StateFlow<DashboardSummary> = combine(
        userProfile,
        signals,
        realizedTrades,
        _selectedPeriodDays
    ) { profile, sigList, tradeList, period ->
        StatisticsCalculator.calculateDashboardSummary(
            initialCapital = profile.initialCapital,
            signals = sigList,
            realizedTrades = tradeList,
            nowMillis = System.currentTimeMillis(),
            periodDays = period
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = DashboardSummary(
            initialCapital = 1000.0,
            currentRealizedBalance = null,
            totalRealizedPnl = null,
            realizedTradeCount = 0,
            estimatedSignalReturnPercent = 0.0,
            combinedWinRate = 0.0,
            combinedSampleCount = 0,
            binanceWinRate = 0.0,
            binanceSampleCount = 0,
            coingeckoWinRate = 0.0,
            coingeckoSampleCount = 0,
            totalSignals = 0,
            tpHitTotalCount = 0,
            slHitSignalCount = 0,
            activeSignalsCount = 0,
            completedSignalsCount = 0,
            winCount = 0,
            lossCount = 0,
            breakevenCount = 0,
            periodDays = 30
        )
    )

    val mentorRankings: StateFlow<List<MentorStats>> = combine(
        mentors,
        signals,
        _selectedPeriodDays
    ) { mentorList, sigList, period ->
        StatisticsCalculator.calculateMentorRankings(
            mentors = mentorList,
            signals = sigList,
            nowMillis = System.currentTimeMillis(),
            periodDays = period
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    private var monitoringJob: Job? = null
    private var wasOfflinePreviously = false

    init {
        if (enableAutoMonitoringLoop) {
            initializeUserAndCloudListeners()
            startAutomaticPriceMonitoringLoop()
        }
    }

    private fun kotlinx.coroutines.flow.Flow<UserProfileEntity?>.mapOrDefaultProfile(uid: String) =
        combine(this, MutableStateFlow(uid)) { profile, id ->
            profile ?: UserProfileEntity(userId = id)
        }

    private fun initializeUserAndCloudListeners() {
        viewModelScope.launch {
            val localProfile = dao.getUserProfile(currentUserId)
            if (localProfile == null) {
                val remoteProfile = firestoreRepository.getUserProfile(currentUserId).getOrNull()
                if (remoteProfile != null) {
                    dao.upsertUserProfile(
                        UserProfileEntity(
                            userId = currentUserId,
                            initialCapital = remoteProfile.initialCapital,
                            preferredPriceSource = remoteProfile.preferredPriceSource,
                            priceStaleThresholdSec = remoteProfile.priceStaleThresholdSec,
                            notificationsEnabled = remoteProfile.notificationsEnabled,
                            revision = remoteProfile.revision,
                            isPendingSync = false
                        )
                    )
                } else {
                    val fresh = UserProfileEntity(userId = currentUserId, isPendingSync = true)
                    dao.upsertUserProfile(fresh)
                }
            }
            syncCoordinator.pushPendingChangesToCloud(currentUserId)
        }

        // Observe remote Signals
        viewModelScope.launch {
            firestoreRepository.observeSignals(currentUserId)
                .catch { e -> Log.w("JournalVM", "Error observing cloud signals", e) }
                .collect { remoteList ->
                    syncCoordinator.reconcileRemoteSignals(currentUserId, remoteList)
                }
        }

        // Observe remote Mentors
        viewModelScope.launch {
            firestoreRepository.observeMentors(currentUserId)
                .catch { e -> Log.w("JournalVM", "Error observing cloud mentors", e) }
                .collect { remoteList ->
                    syncCoordinator.reconcileRemoteMentors(currentUserId, remoteList)
                }
        }

        // Observe remote Signal Events
        viewModelScope.launch {
            firestoreRepository.observeSignalEvents(currentUserId)
                .catch { e -> Log.w("JournalVM", "Error observing cloud events", e) }
                .collect { remoteList ->
                    syncCoordinator.reconcileRemoteEvents(currentUserId, remoteList)
                }
        }

        // Observe remote Realized Trades
        viewModelScope.launch {
            firestoreRepository.observeRealizedTrades(currentUserId)
                .catch { e -> Log.w("JournalVM", "Error observing cloud trades", e) }
                .collect { remoteList ->
                    syncCoordinator.reconcileRemoteTrades(currentUserId, remoteList)
                }
        }
    }

    private fun startAutomaticPriceMonitoringLoop() {
        monitoringJob?.cancel()
        monitoringJob = viewModelScope.launch {
            while (isActive) {
                runSingleMonitoringCycle()
                delay(15_000L)
            }
        }
    }

    fun triggerManualPriceCheck() {
        viewModelScope.launch {
            runSingleMonitoringCycle()
        }
    }

    suspend fun runSingleMonitoringCycle() {
        if (!priceProvider.isInternetAvailable()) {
            wasOfflinePreviously = true
            _feedConnectionStatus.value = FeedConnectionStatus.OFFLINE
            syncCoordinator.setOfflineStatus()
            return
        }

        val profile = userProfile.value
        if (wasOfflinePreviously) {
            wasOfflinePreviously = false
            notificationManager.sendConnectionRestoredNotification(profile.notificationsEnabled)
            notificationManager.retryFailedNotificationsOnConnectionRecovery(
                currentUserId,
                profile.notificationsEnabled
            )
            syncCoordinator.pushPendingChangesToCloud(currentUserId)
        }

        val activeList = dao.getActiveSignals(currentUserId)
        if (activeList.isEmpty()) {
            _feedConnectionStatus.value = FeedConnectionStatus.TERHUBUNG
            return
        }

        _isMonitoringActive.value = true
        val distinctPairs = activeList.map { it.pair }.distinct()
        val preferredSource = try {
            PriceSourceType.valueOf(profile.preferredPriceSource)
        } catch (_: Exception) {
            PriceSourceType.BINANCE_FUTURES
        }

        val fetchedByPair = mutableMapOf<String, com.example.data.remote.PriceFetchResponse>()
        for (pair in distinctPairs) {
            val response = priceProvider.fetchPriceForPair(
                pair = pair,
                preferredSource = preferredSource,
                staleThresholdSec = profile.priceStaleThresholdSec
            )
            fetchedByPair[pair] = response
            response.primaryQuote?.let { quote ->
                _latestPairQuotes.value = _latestPairQuotes.value + (pair to quote)
            }
        }

        // Update overall feed connection status
        val statuses = fetchedByPair.values.map { it.connectionStatus }
        _feedConnectionStatus.value = when {
            statuses.contains(FeedConnectionStatus.OFFLINE) -> FeedConnectionStatus.OFFLINE
            statuses.contains(FeedConnectionStatus.DATA_BINANCE_TIDAK_TERSEDIA) -> FeedConnectionStatus.DATA_BINANCE_TIDAK_TERSEDIA
            statuses.contains(FeedConnectionStatus.HARGA_KEDALUWARSA) -> FeedConnectionStatus.HARGA_KEDALUWARSA
            else -> FeedConnectionStatus.TERHUBUNG
        }

        val now = System.currentTimeMillis()
        var anySignalMutated = false

        for (sig in activeList) {
            val fetchResp = fetchedByPair[sig.pair] ?: continue
            val primaryQuote = fetchResp.primaryQuote ?: continue
            val cgQuote = fetchResp.referenceCoinGeckoQuote

            val evalResult = if (cgQuote != null && primaryQuote.source != PriceSourceType.COINGECKO) {
                PriceMonitoringEngine.evaluateCrossSourceConflict(
                    signal = sig,
                    binanceQuote = primaryQuote,
                    coingeckoQuote = cgQuote,
                    nowMillis = now,
                    staleThresholdSec = profile.priceStaleThresholdSec
                )
            } else {
                PriceMonitoringEngine.evaluatePriceTick(
                    signal = sig,
                    quote = primaryQuote,
                    nowMillis = now,
                    staleThresholdSec = profile.priceStaleThresholdSec
                )
            }

            if (!evalResult.rejectedBecauseExpired) {
                dao.upsertSignal(evalResult.updatedSignal)
                for (event in evalResult.newEvents) {
                    notificationManager.persistAndNotifyEvent(event, profile.notificationsEnabled)
                }
                if (evalResult.signalChanged) {
                    anySignalMutated = true
                }
            }
        }

        _isMonitoringActive.value = false
        if (anySignalMutated) {
            syncCoordinator.pushPendingChangesToCloud(currentUserId)
        }
    }

    fun setPeriodDays(days: Int) {
        _selectedPeriodDays.value = days
    }

    fun clearBannerMessage() {
        _statusBannerMessage.value = null
    }

    fun saveSignal(
        existingSignalId: String?,
        mentorIdInput: String?,
        mentorNameInput: String,
        pairInput: String,
        direction: SignalDirection,
        signalTimestampMillis: Long,
        entryPrices: List<Double>,
        tpPrices: List<Double>,
        slPrice: Double?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val normalizedPair = SignalValidator.normalizePair(pairInput)
        val validation = SignalValidator.validateSignalInput(
            mentorName = mentorNameInput,
            pair = normalizedPair,
            direction = direction,
            entryPrices = entryPrices,
            tpPrices = tpPrices,
            slPrice = slPrice
        )
        if (!validation.isValid) {
            onError(validation.errorMessage ?: "Data sinyal tidak valid.")
            return
        }

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val cleanMentorName = mentorNameInput.trim()

            // Find or create mentor with stable identity
            val existingMentor = mentors.value.firstOrNull {
                (mentorIdInput != null && it.id == mentorIdInput) ||
                    it.displayName.equals(cleanMentorName, ignoreCase = true)
            }
            val stableMentorId = existingMentor?.id ?: "mentor_${UUID.randomUUID().toString().replace("-", "").take(12)}"
            if (existingMentor == null) {
                dao.upsertMentor(
                    MentorEntity(
                        id = stableMentorId,
                        userId = currentUserId,
                        displayName = cleanMentorName,
                        isPendingSync = true
                    )
                )
            }

            val opId = "op_${UUID.randomUUID().toString().replace("-", "").take(16)}"

            if (existingSignalId == null) {
                val signalId = "sig_${UUID.randomUUID().toString().replace("-", "").take(16)}"
                val newSignal = TradingSignalEntity(
                    id = signalId,
                    userId = currentUserId,
                    mentorId = stableMentorId,
                    mentorName = cleanMentorName,
                    pair = normalizedPair,
                    direction = direction.name,
                    signalTimestampMillis = signalTimestampMillis,
                    entryPrices = entryPrices,
                    tpPrices = tpPrices,
                    slPrice = slPrice!!,
                    status = SignalStatus.ACTIVE.name,
                    outcome = SignalOutcome.PENDING.name,
                    priceSource = userProfile.value.preferredPriceSource,
                    revision = 1,
                    lastOperationId = opId,
                    isPendingSync = true,
                    createdAtMillis = now,
                    updatedAtMillis = now
                )
                dao.upsertSignal(newSignal)

                val createdEvent = SignalEventEntity(
                    id = "${signalId}_CREATED",
                    userId = currentUserId,
                    signalId = signalId,
                    pair = normalizedPair,
                    direction = direction.name,
                    eventType = SignalEventType.CREATED.name,
                    levelIndex = -1,
                    price = entryPrices.first(),
                    priceSource = PriceSourceType.USER.name,
                    message = "Sinyal ${direction.name} $normalizedPair dari mentor $cleanMentorName dibuat (Entry: ${entryPrices.joinToString("/")}, TP: ${tpPrices.joinToString("/")}, SL: $slPrice).",
                    eventTimestampMillis = now,
                    idempotencyKey = "${signalId}_CREATED",
                    notificationStatus = NotificationDeliveryStatus.NOT_REQUIRED.name,
                    isPendingSync = true
                )
                dao.upsertEvent(createdEvent)
            } else {
                val existing = dao.getSignalById(currentUserId, existingSignalId)
                if (existing == null) {
                    onError("Sinyal tidak ditemukan.")
                    return@launch
                }
                if (existing.status != SignalStatus.ACTIVE.name) {
                    onError("Hanya sinyal berstatus Aktif yang boleh diedit.")
                    return@launch
                }
                val nextRev = existing.revision + 1
                val updated = existing.copy(
                    mentorId = stableMentorId,
                    mentorName = cleanMentorName,
                    pair = normalizedPair,
                    direction = direction.name,
                    signalTimestampMillis = signalTimestampMillis,
                    entryPrices = entryPrices,
                    tpPrices = tpPrices,
                    slPrice = slPrice!!,
                    revision = nextRev,
                    lastOperationId = opId,
                    isPendingSync = true,
                    updatedAtMillis = now
                )
                dao.upsertSignal(updated)

                val editKey = "${existing.id}_EDITED_REV_$nextRev"
                val editedEvent = SignalEventEntity(
                    id = editKey,
                    userId = currentUserId,
                    signalId = existing.id,
                    pair = normalizedPair,
                    direction = direction.name,
                    eventType = SignalEventType.EDITED.name,
                    levelIndex = -1,
                    price = entryPrices.first(),
                    priceSource = PriceSourceType.USER.name,
                    message = "Revisi #$nextRev: Level sinyal diperbarui (Entry: ${entryPrices.joinToString("/")}, TP: ${tpPrices.joinToString("/")}, SL: $slPrice).",
                    eventTimestampMillis = now,
                    idempotencyKey = editKey,
                    notificationStatus = NotificationDeliveryStatus.NOT_REQUIRED.name,
                    isPendingSync = true
                )
                dao.upsertEvent(editedEvent)
            }

            onSuccess()
            syncCoordinator.pushPendingChangesToCloud(currentUserId)
            runSingleMonitoringCycle()
        }
    }

    /**
     * Cancels an active signal ONLY after server confirmation (Section 12: "Pembatalan hanya berhasil setelah konfirmasi server").
     */
    fun cancelSignalWithServerConfirmation(signal: TradingSignalEntity) {
        viewModelScope.launch {
            if (!priceProvider.isInternetAvailable()) {
                _statusBannerMessage.value = "Pembatalan sinyal membutuhkan konfirmasi server. Hubungkan internet terlebih dahulu."
                return@launch
            }
            val opId = "op_cancel_${UUID.randomUUID().toString().replace("-", "").take(12)}"
            // Ensure signal exists on server first if it was created offline
            syncCoordinator.pushPendingChangesToCloud(currentUserId)

            val serverResult = firestoreRepository.cancelSignalOnServer(signal.id, opId)
            if (serverResult.isSuccess) {
                val confirmedDoc = serverResult.getOrThrow()
                val now = System.currentTimeMillis()
                val updatedLocal = signal.copy(
                    status = confirmedDoc.status,
                    outcome = confirmedDoc.outcome,
                    completedAtMillis = confirmedDoc.completedAtMillis.takeIf { it > 0L } ?: now,
                    revision = confirmedDoc.revision,
                    lastOperationId = confirmedDoc.lastOperationId,
                    isPendingSync = false,
                    updatedAtMillis = now
                )
                dao.upsertSignal(updatedLocal)

                val cancelEvent = SignalEventEntity(
                    id = "${signal.id}_CANCELLED",
                    userId = currentUserId,
                    signalId = signal.id,
                    pair = signal.pair,
                    direction = signal.direction,
                    eventType = SignalEventType.CANCELLED.name,
                    levelIndex = -1,
                    price = signal.lastCheckedPrice.takeIf { it > 0.0 } ?: signal.entryPrices.first(),
                    priceSource = PriceSourceType.USER.name,
                    message = if (confirmedDoc.outcome == SignalOutcome.WIN.name) {
                        "Sinyal dibatalkan (Terkonfirmasi Server). Hasil MENANG tetap dipertahankan karena TP sudah tercapai sebelumnya."
                    } else {
                        "Sinyal dibatalkan oleh pengguna (Terkonfirmasi Server) tanpa hasil TP/SL."
                    },
                    eventTimestampMillis = now,
                    idempotencyKey = "${signal.id}_CANCELLED",
                    notificationStatus = NotificationDeliveryStatus.PENDING.name,
                    isPendingSync = true
                )
                notificationManager.persistAndNotifyEvent(cancelEvent, userProfile.value.notificationsEnabled)
                syncCoordinator.pushPendingChangesToCloud(currentUserId)
                _statusBannerMessage.value = "Sinyal ${signal.pair} berhasil dibatalkan setelah konfirmasi server."
            } else {
                _statusBannerMessage.value = "Gagal membatalkan sinyal di server: ${serverResult.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    /**
     * Closes an active signal at Entry as BREAKEVEN (IMPAS) when at least 1 Entry was touched and no TP/SL was hit yet.
     */
    fun closeSignalAsBreakeven(signal: TradingSignalEntity) {
        viewModelScope.launch {
            if (signal.status != SignalStatus.ACTIVE.name) return@launch
            if (signal.reachedTpIndices.isNotEmpty() || signal.slHit) {
                _statusBannerMessage.value = "Status IMPAS hanya berlaku jika belum ada TP atau SL yang tersentuh."
                return@launch
            }
            val now = System.currentTimeMillis()
            val opId = "op_be_${UUID.randomUUID().toString().replace("-", "").take(12)}"
            val updated = signal.copy(
                status = SignalStatus.COMPLETED.name,
                outcome = SignalOutcome.BREAKEVEN.name,
                outcomeSource = signal.priceSource,
                completedAtMillis = now,
                revision = signal.revision + 1,
                lastOperationId = opId,
                isPendingSync = true,
                updatedAtMillis = now
            )
            dao.upsertSignal(updated)

            val beEvent = SignalEventEntity(
                id = "${signal.id}_BREAKEVEN",
                userId = currentUserId,
                signalId = signal.id,
                pair = signal.pair,
                direction = signal.direction,
                eventType = SignalEventType.COMPLETED.name,
                levelIndex = 0,
                price = signal.entryPrices.first(),
                priceSource = PriceSourceType.USER.name,
                message = "Sinyal ditutup pada level Entry (IMPAS / Breakeven) tanpa TP/SL.",
                eventTimestampMillis = now,
                idempotencyKey = "${signal.id}_BREAKEVEN",
                notificationStatus = NotificationDeliveryStatus.NOT_REQUIRED.name,
                isPendingSync = true
            )
            dao.upsertEvent(beEvent)
            syncCoordinator.pushPendingChangesToCloud(currentUserId)
            _statusBannerMessage.value = "Sinyal ${signal.pair} ditutup sebagai IMPAS (Breakeven)."
        }
    }

    fun archiveSignal(signal: TradingSignalEntity) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val nextRev = signal.revision + 1
            val opId = "op_arch_${UUID.randomUUID().toString().replace("-", "").take(12)}"
            val updated = signal.copy(
                status = SignalStatus.ARCHIVED.name,
                revision = nextRev,
                lastOperationId = opId,
                isPendingSync = true,
                updatedAtMillis = now
            )
            dao.upsertSignal(updated)

            val archKey = "${signal.id}_ARCHIVED_$nextRev"
            dao.upsertEvent(
                SignalEventEntity(
                    id = archKey,
                    userId = currentUserId,
                    signalId = signal.id,
                    pair = signal.pair,
                    direction = signal.direction,
                    eventType = SignalEventType.ARCHIVED.name,
                    levelIndex = -1,
                    price = signal.lastCheckedPrice,
                    priceSource = PriceSourceType.USER.name,
                    message = "Sinyal diarsipkan secara aman. Pemantauan harga dihentikan untuk sinyal ini.",
                    eventTimestampMillis = now,
                    idempotencyKey = archKey,
                    notificationStatus = NotificationDeliveryStatus.NOT_REQUIRED.name,
                    isPendingSync = true
                )
            )
            syncCoordinator.pushPendingChangesToCloud(currentUserId)
            _statusBannerMessage.value = "Sinyal ${signal.pair} diarsipkan."
        }
    }

    /**
     * Restores an archived signal ONLY if it had not already reached a terminal outcome (WIN/LOSS/BREAKEVEN/CANCELLED),
     * starting monitoring from the current valid market price without fabricating historical events while archived.
     */
    fun restoreArchivedSignal(signal: TradingSignalEntity) {
        viewModelScope.launch {
            if (signal.outcome != SignalOutcome.PENDING.name && signal.reachedTpIndices.size == signal.tpPrices.size || signal.slHit) {
                _statusBannerMessage.value = "Sinyal yang sudah selesai atau dibatalkan tidak dapat diaktifkan kembali."
                return@launch
            }
            val profile = userProfile.value
            val preferredSource = try {
                PriceSourceType.valueOf(profile.preferredPriceSource)
            } catch (_: Exception) {
                PriceSourceType.BINANCE_FUTURES
            }
            val fetchResp = priceProvider.fetchPriceForPair(
                pair = signal.pair,
                preferredSource = preferredSource,
                staleThresholdSec = profile.priceStaleThresholdSec
            )
            val validQuote = fetchResp.primaryQuote
            if (validQuote == null || validQuote.isExpired(System.currentTimeMillis(), profile.priceStaleThresholdSec)) {
                _statusBannerMessage.value = "Pemulihan arsip membutuhkan harga pasar valid saat ini. Pastikan internet aktif."
                return@launch
            }

            val now = System.currentTimeMillis()
            val nextRev = signal.revision + 1
            val opId = "op_restore_${UUID.randomUUID().toString().replace("-", "").take(12)}"
            val restored = signal.copy(
                status = SignalStatus.ACTIVE.name,
                lastCheckedPrice = validQuote.price,
                lastCheckedAtMillis = validQuote.timestampMillis,
                revision = nextRev,
                lastOperationId = opId,
                isPendingSync = true,
                updatedAtMillis = now
            )
            dao.upsertSignal(restored)

            val restoreKey = "${signal.id}_RESTORED_$nextRev"
            dao.upsertEvent(
                SignalEventEntity(
                    id = restoreKey,
                    userId = currentUserId,
                    signalId = signal.id,
                    pair = signal.pair,
                    direction = signal.direction,
                    eventType = SignalEventType.RESTORED.name,
                    levelIndex = -1,
                    price = validQuote.price,
                    priceSource = validQuote.source.name,
                    message = "Sinyal dipulihkan dari arsip. Monitoring dimulai kembali dari harga valid saat ini (${validQuote.price} [${validQuote.source.label}]).",
                    eventTimestampMillis = now,
                    idempotencyKey = restoreKey,
                    notificationStatus = NotificationDeliveryStatus.NOT_REQUIRED.name,
                    isPendingSync = true
                )
            )
            syncCoordinator.pushPendingChangesToCloud(currentUserId)
            _statusBannerMessage.value = "Sinyal ${signal.pair} dipulihkan dan kembali dipantau dari harga ${validQuote.price}."
        }
    }

    fun saveMentor(displayName: String, handleOrChannel: String, specialty: String) {
        if (displayName.isBlank()) return
        viewModelScope.launch {
            val cleanName = displayName.trim()
            val existing = mentors.value.firstOrNull { it.displayName.equals(cleanName, ignoreCase = true) }
            val mentorId = existing?.id ?: "mentor_${UUID.randomUUID().toString().replace("-", "").take(12)}"
            val nextRev = (existing?.revision ?: 0) + 1
            dao.upsertMentor(
                MentorEntity(
                    id = mentorId,
                    userId = currentUserId,
                    displayName = cleanName,
                    handleOrChannel = handleOrChannel.trim(),
                    specialty = specialty.trim(),
                    revision = nextRev,
                    isPendingSync = true
                )
            )
            syncCoordinator.pushPendingChangesToCloud(currentUserId)
            _statusBannerMessage.value = "Data mentor $cleanName berhasil disimpan."
        }
    }

    fun recordRealizedTrade(
        signalId: String,
        pair: String,
        direction: SignalDirection,
        entryPrice: Double,
        exitPrice: Double,
        positionSizeUsdt: Double,
        feeUsdt: Double
    ) {
        if (entryPrice <= 0.0 || exitPrice <= 0.0 || positionSizeUsdt <= 0.0) return
        viewModelScope.launch {
            val rawReturnRatio = if (direction == SignalDirection.LONG) {
                (exitPrice - entryPrice) / entryPrice
            } else {
                (entryPrice - exitPrice) / entryPrice
            }
            val grossPnl = positionSizeUsdt * rawReturnRatio
            val netPnl = grossPnl - feeUsdt

            val trade = RealizedTradeEntity(
                id = "trd_${UUID.randomUUID().toString().replace("-", "").take(14)}",
                userId = currentUserId,
                signalId = signalId,
                pair = SignalValidator.normalizePair(pair),
                direction = direction.name,
                entryPrice = entryPrice,
                exitPrice = exitPrice,
                positionSizeUsdt = positionSizeUsdt,
                feeUsdt = feeUsdt,
                realizedPnlUsdt = netPnl,
                closedAtMillis = System.currentTimeMillis(),
                revision = 1,
                isPendingSync = true
            )
            dao.upsertRealizedTrade(trade)
            syncCoordinator.pushPendingChangesToCloud(currentUserId)
            _statusBannerMessage.value = "Transaksi terealisasi dicatat (Net P/L: ${String.format("%.2f", netPnl)} USDT)."
        }
    }

    fun updateSettings(
        initialCapital: Double,
        preferredSource: PriceSourceType,
        staleThresholdSec: Int,
        notificationsEnabled: Boolean
    ) {
        viewModelScope.launch {
            val current = userProfile.value
            val updated = current.copy(
                initialCapital = initialCapital.coerceAtLeast(0.0),
                preferredPriceSource = preferredSource.name,
                priceStaleThresholdSec = staleThresholdSec.coerceIn(10, 3600),
                notificationsEnabled = notificationsEnabled,
                revision = current.revision + 1,
                isPendingSync = true,
                updatedAtMillis = System.currentTimeMillis()
            )
            dao.upsertUserProfile(updated)
            syncCoordinator.pushPendingChangesToCloud(currentUserId)
            _statusBannerMessage.value = "Pengaturan berhasil disimpan dan disinkronkan."
        }
    }

    fun retryNotification(event: SignalEventEntity) {
        viewModelScope.launch {
            val updated = notificationManager.manualRetryNotification(event, userProfile.value.notificationsEnabled)
            if (updated.notificationStatus == NotificationDeliveryStatus.SENT.name) {
                _statusBannerMessage.value = "Notifikasi berhasil dikirim ulang."
            } else {
                _statusBannerMessage.value = "Gagal mengirim notifikasi. Periksa izin notifikasi Android."
            }
        }
    }

    fun resolveSyncConflict(conflict: SyncConflictEntity, keepLocal: Boolean) {
        viewModelScope.launch {
            syncCoordinator.resolveConflict(currentUserId, conflict, keepLocal)
            _statusBannerMessage.value = if (keepLocal) {
                "Konflik diselesaikan dengan mempertahankan versi perangkat ini."
            } else {
                "Konflik diselesaikan dengan menerapkan versi Cloud."
            }
        }
    }

    fun triggerManualCloudSync() {
        viewModelScope.launch {
            val status = syncCoordinator.pushPendingChangesToCloud(currentUserId)
            _statusBannerMessage.value = "Status sinkronisasi: ${status.label}"
        }
    }

    fun exportBackupJson(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val json = backupRestoreManager.exportToJson(currentUserId)
            onResult(json)
        }
    }

    fun importBackupJson(json: String, mode: RestoreMode) {
        viewModelScope.launch {
            val res = backupRestoreManager.importFromJson(currentUserId, json, mode)
            if (res.isSuccess) {
                val stats = res.getOrThrow()
                syncCoordinator.pushPendingChangesToCloud(currentUserId)
                _statusBannerMessage.value = "Pemulihan JSON (${mode.name}) berhasil: ${stats.signalCount} sinyal, ${stats.mentorCount} mentor."
            } else {
                _statusBannerMessage.value = "Gagal memulihkan JSON: ${res.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun exportBackupCsv(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val csv = backupRestoreManager.exportSignalsToCsv(currentUserId)
            onResult(csv)
        }
    }

    fun importBackupCsv(csv: String, mode: RestoreMode) {
        viewModelScope.launch {
            val res = backupRestoreManager.importSignalsFromCsv(currentUserId, csv, mode)
            if (res.isSuccess) {
                val count = res.getOrThrow()
                syncCoordinator.pushPendingChangesToCloud(currentUserId)
                _statusBannerMessage.value = "Pemulihan CSV (${mode.name}) berhasil: $count sinyal dipulihkan."
            } else {
                _statusBannerMessage.value = "Gagal memulihkan CSV: ${res.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun generatePdfReport(context: Context, onResult: (Result<File>) -> Unit) {
        viewModelScope.launch {
            val res = backupRestoreManager.exportReportToPdfFile(context, currentUserId)
            onResult(res)
        }
    }

    fun deleteAccountAndAllData(onComplete: () -> Unit) {
        viewModelScope.launch {
            firestoreRepository.deleteAllUserCloudData()
            dao.deleteAllSignalsForUser(currentUserId)
            dao.deleteAllMentorsForUser(currentUserId)
            dao.deleteAllEventsForUser(currentUserId)
            dao.deleteAllTradesForUser(currentUserId)
            dao.deleteAllConflictsForUser(currentUserId)
            dao.deleteUserProfile(currentUserId)
            onComplete()
        }
    }
}
