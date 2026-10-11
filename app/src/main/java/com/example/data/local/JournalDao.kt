package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface JournalDao {

    // --- UserProfile ---
    @Query("SELECT * FROM user_profiles WHERE userId = :userId LIMIT 1")
    fun observeUserProfile(userId: String): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profiles WHERE userId = :userId LIMIT 1")
    suspend fun getUserProfile(userId: String): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUserProfile(profile: UserProfileEntity)

    // --- Mentors ---
    @Query("SELECT * FROM mentors WHERE userId = :userId AND isDeleted = 0 ORDER BY displayName ASC")
    fun observeMentors(userId: String): Flow<List<MentorEntity>>

    @Query("SELECT * FROM mentors WHERE userId = :userId")
    suspend fun getAllMentorsIncludingDeleted(userId: String): List<MentorEntity>

    @Query("SELECT * FROM mentors WHERE id = :mentorId AND userId = :userId LIMIT 1")
    suspend fun getMentorById(userId: String, mentorId: String): MentorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMentor(mentor: MentorEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMentors(mentors: List<MentorEntity>)

    // --- TradingSignals ---
    @Query("SELECT * FROM trading_signals WHERE userId = :userId AND isDeleted = 0 ORDER BY signalTimestampMillis DESC")
    fun observeSignals(userId: String): Flow<List<TradingSignalEntity>>

    @Query("SELECT * FROM trading_signals WHERE userId = :userId")
    suspend fun getAllSignalsIncludingDeleted(userId: String): List<TradingSignalEntity>

    @Query("SELECT * FROM trading_signals WHERE userId = :userId AND status = 'ACTIVE' AND isDeleted = 0")
    suspend fun getActiveSignals(userId: String): List<TradingSignalEntity>

    @Query("SELECT * FROM trading_signals WHERE id = :signalId AND userId = :userId LIMIT 1")
    fun observeSignalById(userId: String, signalId: String): Flow<TradingSignalEntity?>

    @Query("SELECT * FROM trading_signals WHERE id = :signalId AND userId = :userId LIMIT 1")
    suspend fun getSignalById(userId: String, signalId: String): TradingSignalEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSignal(signal: TradingSignalEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSignals(signals: List<TradingSignalEntity>)

    // --- SignalEvents ---
    @Query("SELECT * FROM signal_events WHERE userId = :userId AND signalId = :signalId ORDER BY eventTimestampMillis DESC")
    fun observeEventsForSignal(userId: String, signalId: String): Flow<List<SignalEventEntity>>

    @Query("SELECT * FROM signal_events WHERE userId = :userId ORDER BY eventTimestampMillis DESC")
    fun observeAllEvents(userId: String): Flow<List<SignalEventEntity>>

    @Query("SELECT * FROM signal_events WHERE userId = :userId ORDER BY eventTimestampMillis DESC")
    suspend fun getAllEvents(userId: String): List<SignalEventEntity>

    @Query("SELECT * FROM signal_events WHERE idempotencyKey = :key LIMIT 1")
    suspend fun getEventByIdempotencyKey(key: String): SignalEventEntity?

    @Query("SELECT * FROM signal_events WHERE userId = :userId AND notificationStatus = 'FAILED'")
    suspend fun getFailedNotificationEvents(userId: String): List<SignalEventEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEventIfAbsent(event: SignalEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEvent(event: SignalEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEvents(events: List<SignalEventEntity>)

    // --- RealizedTrades ---
    @Query("SELECT * FROM realized_trades WHERE userId = :userId AND isDeleted = 0 ORDER BY closedAtMillis DESC")
    fun observeRealizedTrades(userId: String): Flow<List<RealizedTradeEntity>>

    @Query("SELECT * FROM realized_trades WHERE userId = :userId")
    suspend fun getAllRealizedTradesIncludingDeleted(userId: String): List<RealizedTradeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRealizedTrade(trade: RealizedTradeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRealizedTrades(trades: List<RealizedTradeEntity>)

    // --- SyncConflicts ---
    @Query("SELECT * FROM sync_conflicts WHERE userId = :userId ORDER BY detectedAtMillis DESC")
    fun observeSyncConflicts(userId: String): Flow<List<SyncConflictEntity>>

    @Query("SELECT * FROM sync_conflicts WHERE userId = :userId")
    suspend fun getSyncConflicts(userId: String): List<SyncConflictEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSyncConflict(conflict: SyncConflictEntity)

    @Query("DELETE FROM sync_conflicts WHERE id = :conflictId AND userId = :userId")
    suspend fun deleteSyncConflict(userId: String, conflictId: String)

    // --- Pending Sync Queries ---
    @Query("SELECT * FROM trading_signals WHERE userId = :userId AND isPendingSync = 1")
    suspend fun getPendingSyncSignals(userId: String): List<TradingSignalEntity>

    @Query("SELECT * FROM mentors WHERE userId = :userId AND isPendingSync = 1")
    suspend fun getPendingSyncMentors(userId: String): List<MentorEntity>

    @Query("SELECT * FROM signal_events WHERE userId = :userId AND isPendingSync = 1")
    suspend fun getPendingSyncEvents(userId: String): List<SignalEventEntity>

    @Query("SELECT * FROM realized_trades WHERE userId = :userId AND isPendingSync = 1")
    suspend fun getPendingSyncTrades(userId: String): List<RealizedTradeEntity>

    // --- Full Replace / Account Deletion for a specific user ---
    @Query("DELETE FROM trading_signals WHERE userId = :userId")
    suspend fun deleteAllSignalsForUser(userId: String)

    @Query("DELETE FROM mentors WHERE userId = :userId")
    suspend fun deleteAllMentorsForUser(userId: String)

    @Query("DELETE FROM signal_events WHERE userId = :userId")
    suspend fun deleteAllEventsForUser(userId: String)

    @Query("DELETE FROM realized_trades WHERE userId = :userId")
    suspend fun deleteAllTradesForUser(userId: String)

    @Query("DELETE FROM sync_conflicts WHERE userId = :userId")
    suspend fun deleteAllConflictsForUser(userId: String)

    @Query("DELETE FROM user_profiles WHERE userId = :userId")
    suspend fun deleteUserProfile(userId: String)
}
