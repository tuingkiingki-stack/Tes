package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.TradingSignalDoc
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class JournalFirestoreRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private fun sampleSignal(id: String = "sig_${UUID.randomUUID().toString().replace("-", "")}") = TradingSignalDoc(
        id = id,
        mentorId = "mentor_alpha",
        mentorName = "Mentor Alpha",
        pair = "BTC/USDT",
        direction = "LONG",
        signalTimestampMillis = 1720000000000L,
        entryPrices = listOf(60000.0, 59500.0),
        reachedEntryIndices = emptyList(),
        missedEntryIndices = emptyList(),
        tpPrices = listOf(61500.0, 63000.0),
        reachedTpIndices = emptyList(),
        slPrice = 58000.0,
        slHit = false,
        returnedToEntry = false,
        status = "ACTIVE",
        outcome = "PENDING",
        isConservativeEstimation = false,
        priceSource = "BINANCE_FUTURES",
        outcomeSource = "NONE",
        hasSourceConflict = false,
        lastCheckedPrice = 60050.0,
        lastCheckedAtMillis = 1720000005000L,
        completedAtMillis = 0L,
        isDeleted = false,
        revision = 1,
        lastOperationId = "op_${UUID.randomUUID().toString().replace("-", "")}"
    )

    @Test
    fun createSignal_validPayload_createsDocumentAndReturnsId() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = JournalFirestoreRepository(firestore, auth)

        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createSignal(sampleSignal())
        }
        assertTrue("Expected createSignal to succeed: ${result.exceptionOrNull()}", result.isSuccess)
        val signalId = result.getOrThrow()
        assertTrue(signalId.isNotEmpty())
    }

    @Test
    fun observeSignals_authenticatedOwner_emitsRealtimeUpdates() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = JournalFirestoreRepository(firestore, auth)
        val signalId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createSignal(sampleSignal()).getOrThrow()
        }

        val emitted = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeSignals(uid).first { list -> list.any { it.id == signalId } }
        }
        assertTrue(emitted.any { it.id == signalId })
    }

    @Test
    fun getSignalById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = JournalFirestoreRepository(firestore, auth)
        val signalId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createSignal(sampleSignal()).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = JournalFirestoreRepository(firestore, auth)
        val bobResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getSignalById(signalId, ownerUserId = aliceUid)
        }
        assertTrue(bobResult.isFailure)
        val ex = bobResult.exceptionOrNull() as? FirebaseFirestoreException
        assertNotNull(ex)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, ex?.code)
    }

    @Test
    fun observeSignals_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = JournalFirestoreRepository(firestore, auth)

        var caughtException: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeSignals().first()
            }
        } catch (e: Throwable) {
            caughtException = e
        }
        assertNotNull(caughtException)
        val firestoreEx = caughtException as? FirebaseFirestoreException
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_jurnal@test.com"
        const val BOB_EMAIL = "bob_jurnal@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
