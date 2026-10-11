package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.MentorDoc
import com.example.data.model.RealizedTradeDoc
import com.example.data.model.SignalEventDoc
import com.example.data.model.TradingSignalDoc
import com.example.data.model.UserProfileDoc
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class JournalFirestoreRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        FirebaseAuth.getInstance()
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Pengguna harus login dengan Google sebelum mengakses Cloud Firestore.")
    }

    // --- UserProfile Operations ---

    suspend fun saveUserProfile(profile: UserProfileDoc): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docRef = db.collection("users").document(uid)
        return try {
            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                val existingCreatedAt = existingSnap.getTimestamp("createdAt")
                val payload = profile.copy(
                    id = uid,
                    userId = uid,
                    createdAt = existingCreatedAt,
                    updatedAt = null
                )
                docRef.set(payload).await()
            } else {
                val payload = profile.copy(
                    id = uid,
                    userId = uid,
                    createdAt = null,
                    updatedAt = null
                )
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun getUserProfile(targetUserId: String? = null): Result<UserProfileDoc?> {
        val uid = targetUserId ?: auth.currentUser?.uid ?: ""
        val docRef = db.collection("users").document(uid)
        return try {
            val snap = docRef.get().await()
            if (snap.exists()) {
                Result.success(snap.toObject(UserProfileDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE))
            } else {
                Result.success(null)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    // --- Mentor Operations ---

    suspend fun createOrUpdateMentor(mentor: MentorDoc): Result<String> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val mentorId = mentor.id.ifBlank { "mentor_${UUID.randomUUID().toString().replace("-", "")}" }
        val docRef = db.collection("users").document(uid).collection("mentors").document(mentorId)
        return try {
            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                val existingCreatedAt = existingSnap.getTimestamp("createdAt")
                docRef.set(
                    mentor.copy(
                        id = mentorId,
                        userId = uid,
                        createdAt = existingCreatedAt,
                        updatedAt = null
                    )
                ).await()
            } else {
                docRef.set(
                    mentor.copy(
                        id = mentorId,
                        userId = uid,
                        createdAt = null,
                        updatedAt = null
                    )
                ).await()
            }
            Result.success(mentorId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeMentors(targetUserId: String? = null): Flow<List<MentorDoc>> = callbackFlow {
        val uid = targetUserId ?: auth.currentUser?.uid ?: "unauthenticated"
        val path = "users/$uid/mentors"
        val registration = db.collection("users").document(uid).collection("mentors")
            .whereEqualTo("userId", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.LIST, path)
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    trySend(
                        snapshot.toObjects(
                            MentorDoc::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    )
                }
            }
        awaitClose { registration.remove() }
    }

    // --- TradingSignal Operations ---

    suspend fun createSignal(signal: TradingSignalDoc): Result<String> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val signalId = signal.id.ifBlank { "sig_${UUID.randomUUID().toString().replace("-", "")}" }
        val docRef = db.collection("users").document(uid).collection("signals").document(signalId)
        return try {
            val payload = signal.copy(
                id = signalId,
                userId = uid,
                createdAt = null,
                updatedAt = null
            )
            docRef.set(payload).await()
            Result.success(signalId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateSignal(signal: TradingSignalDoc): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        require(signal.id.isNotBlank()) { "Signal ID must not be blank for update" }
        val docRef = db.collection("users").document(uid).collection("signals").document(signal.id)
        return try {
            val existingSnap = docRef.get().await()
            val existingCreatedAt = existingSnap.getTimestamp("createdAt") ?: signal.createdAt
            requireNotNull(existingCreatedAt) { "Existing createdAt timestamp required for signal update" }
            val payload = signal.copy(
                userId = uid,
                createdAt = existingCreatedAt,
                updatedAt = null
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun getSignalById(signalId: String, ownerUserId: String? = null): Result<TradingSignalDoc?> {
        val uid = ownerUserId ?: auth.currentUser?.uid ?: "unauthenticated"
        val docRef = db.collection("users").document(uid).collection("signals").document(signalId)
        return try {
            val snap = docRef.get().await()
            if (snap.exists()) {
                Result.success(snap.toObject(TradingSignalDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE))
            } else {
                Result.success(null)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun getUserSignals(ownerUserId: String? = null): Result<List<TradingSignalDoc>> {
        val uid = ownerUserId ?: auth.currentUser?.uid ?: "unauthenticated"
        val path = "users/$uid/signals"
        return try {
            val snap = db.collection("users").document(uid).collection("signals")
                .whereEqualTo("userId", uid)
                .get()
                .await()
            Result.success(snap.toObjects(TradingSignalDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    fun observeSignals(ownerUserId: String? = null): Flow<List<TradingSignalDoc>> = callbackFlow {
        val uid = ownerUserId ?: auth.currentUser?.uid ?: "unauthenticated"
        val path = "users/$uid/signals"
        val registration = db.collection("users").document(uid).collection("signals")
            .whereEqualTo("userId", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.LIST, path)
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    trySend(
                        snapshot.toObjects(
                            TradingSignalDoc::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    )
                }
            }
        awaitClose { registration.remove() }
    }

    /**
     * Cancels a signal on the server using a Firestore Transaction so concurrent cancellations
     * or completions are resolved atomically by the first valid server confirmation.
     */
    suspend fun cancelSignalOnServer(signalId: String, operationId: String): Result<TradingSignalDoc> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docRef = db.collection("users").document(uid).collection("signals").document(signalId)
        return try {
            val updatedDoc = db.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                if (!snapshot.exists()) {
                    throw IllegalStateException("Sinyal tidak ditemukan di server.")
                }
                val current = snapshot.toObject(TradingSignalDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: throw IllegalStateException("Gagal membaca data sinyal.")
                if (current.userId != uid) {
                    throw IllegalAccessException("Bukan pemilik sinyal yang sah.")
                }
                if (current.status == "CANCELLED" || current.status == "COMPLETED") {
                    // Already finalized on server — return authoritative server state
                    return@runTransaction current
                }
                // If any TP was already reached before cancellation, preserve WIN outcome per Section 9
                val newOutcome = if (current.reachedTpIndices.isNotEmpty() || current.outcome == "WIN") {
                    "WIN"
                } else {
                    "CANCELLED_NO_RESULT"
                }
                val nextDoc = current.copy(
                    status = "CANCELLED",
                    outcome = newOutcome,
                    completedAtMillis = System.currentTimeMillis(),
                    revision = current.revision + 1,
                    lastOperationId = operationId,
                    createdAt = snapshot.getTimestamp("createdAt"),
                    updatedAt = null
                )
                transaction.set(docRef, nextDoc)
                nextDoc
            }.await()
            Result.success(updatedDoc)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    // --- SignalEvent Operations ---

    suspend fun createSignalEvent(event: SignalEventDoc): Result<String> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val eventId = event.id.ifBlank {
            event.idempotencyKey.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank {
                "evt_${UUID.randomUUID().toString().replace("-", "")}"
            }
        }
        val docRef = db.collection("users").document(uid).collection("signal_events").document(eventId)
        return try {
            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                return Result.success(eventId)
            }
            docRef.set(
                event.copy(
                    id = eventId,
                    userId = uid,
                    createdAt = null,
                    updatedAt = null
                )
            ).await()
            Result.success(eventId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeSignalEvents(ownerUserId: String? = null): Flow<List<SignalEventDoc>> = callbackFlow {
        val uid = ownerUserId ?: auth.currentUser?.uid ?: "unauthenticated"
        val path = "users/$uid/signal_events"
        val registration = db.collection("users").document(uid).collection("signal_events")
            .whereEqualTo("userId", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.LIST, path)
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    trySend(
                        snapshot.toObjects(
                            SignalEventDoc::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    )
                }
            }
        awaitClose { registration.remove() }
    }

    // --- RealizedTrade Operations ---

    suspend fun createRealizedTrade(trade: RealizedTradeDoc): Result<String> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val tradeId = trade.id.ifBlank { "trd_${UUID.randomUUID().toString().replace("-", "")}" }
        val docRef = db.collection("users").document(uid).collection("realized_trades").document(tradeId)
        return try {
            docRef.set(
                trade.copy(
                    id = tradeId,
                    userId = uid,
                    createdAt = null,
                    updatedAt = null
                )
            ).await()
            Result.success(tradeId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeRealizedTrades(ownerUserId: String? = null): Flow<List<RealizedTradeDoc>> = callbackFlow {
        val uid = ownerUserId ?: auth.currentUser?.uid ?: "unauthenticated"
        val path = "users/$uid/realized_trades"
        val registration = db.collection("users").document(uid).collection("realized_trades")
            .whereEqualTo("userId", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.LIST, path)
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    trySend(
                        snapshot.toObjects(
                            RealizedTradeDoc::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    )
                }
            }
        awaitClose { registration.remove() }
    }

    suspend fun deleteAllUserCloudData(): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val userRef = db.collection("users").document(uid)
        return try {
            val subcollections = listOf("mentors", "signals", "signal_events", "realized_trades")
            for (sub in subcollections) {
                val docs = userRef.collection(sub).whereEqualTo("userId", uid).get().await()
                for (doc in docs.documents) {
                    doc.reference.delete().await()
                }
            }
            userRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, userRef.path)
            Result.failure(e)
        }
    }
}
