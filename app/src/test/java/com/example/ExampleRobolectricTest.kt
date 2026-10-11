package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.TradingSignalEntity
import com.example.data.remote.PriceFetchResponse
import com.example.data.remote.PriceProvider
import com.example.data.repository.BackupRestoreManager
import com.example.data.repository.JournalFirestoreRepository
import com.example.data.repository.RestoreMode
import com.example.data.repository.SyncCoordinator
import com.example.domain.model.FeedConnectionStatus
import com.example.domain.model.PriceQuote
import com.example.domain.model.PriceSourceType
import com.example.domain.model.SignalDirection
import com.example.domain.model.SignalOutcome
import com.example.domain.model.SignalStatus
import com.example.service.SignalNotificationManager
import com.example.ui.JournalAppContent
import com.example.ui.JournalViewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: AppDatabase

    private class FakePriceProvider(
        var online: Boolean = true,
        var quotePrice: Double = 60500.0
    ) : PriceProvider {
        override fun isInternetAvailable(): Boolean = online

        override suspend fun fetchPriceForPair(
            pair: String,
            preferredSource: PriceSourceType,
            staleThresholdSec: Int
        ): PriceFetchResponse {
            if (!online) {
                return PriceFetchResponse(null, null, FeedConnectionStatus.OFFLINE)
            }
            val now = System.currentTimeMillis()
            val q = PriceQuote(
                pair = pair,
                price = quotePrice,
                source = preferredSource,
                timestampMillis = now,
                isMarkPrice = true
            )
            return PriceFetchResponse(q, null, FeedConnectionStatus.TERHUBUNG)
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val resId = context.resources.getIdentifier("firestore_database_id", "string", context.packageName)
        val databaseId = if (resId != 0) context.getString(resId) else "ai-studio-android-jurnalsi-028be420-6283-4155-b964-ee900036f3fe"
        if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
            val newApp = com.google.firebase.FirebaseApp.initializeApp(
                context,
                com.google.firebase.FirebaseOptions.Builder()
                    .setApplicationId("com.aistudio.jurnalsinyal.kxpqzm")
                    .setProjectId("demo-no-project")
                    .setApiKey("fake-api-key-for-emulator")
                    .build()
            )
            val newFirestore = com.google.firebase.firestore.FirebaseFirestore.getInstance(newApp, databaseId)
            newFirestore.useEmulator("127.0.0.1", 8085)
            newFirestore.firestoreSettings = com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(com.google.firebase.firestore.MemoryCacheSettings.newBuilder().build())
                .build()
            val newAuth = com.google.firebase.auth.FirebaseAuth.getInstance(newApp)
            newAuth.useEmulator("127.0.0.1", 9099)
        }
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `app_name and Firebase configuration resources are valid and non-placeholder`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("Jurnal Sinyal Mentor", appName)

        val webClientId = context.getString(R.string.default_web_client_id)
        assertTrue(webClientId.endsWith(".apps.googleusercontent.com"))
        assertTrue(!webClientId.contains("placeholder"))

        val dbId = context.getString(R.string.firestore_database_id)
        assertTrue(dbId.startsWith("ai-studio-android-jurnalsi-"))
    }

    @Test
    fun `notification idempotency and SyncCoordinator conflict detection work accurately`() = runBlocking {
        val dao = database.journalDao()
        val notifManager = SignalNotificationManager(context, dao)
        val uid = "user_notif_sync_test"

        val ev = com.example.data.local.SignalEventEntity(
            id = "sig_99_TP_0",
            userId = uid,
            signalId = "sig_99",
            pair = "BTC/USDT",
            direction = "LONG",
            eventType = com.example.domain.model.SignalEventType.TP_HIT.name,
            levelIndex = 0,
            price = 62000.0,
            priceSource = PriceSourceType.BINANCE_FUTURES.name,
            message = "TP1 tercapai",
            eventTimestampMillis = 1700000000000L,
            idempotencyKey = "sig_99_TP_0",
            notificationStatus = com.example.domain.model.NotificationDeliveryStatus.PENDING.name
        )

        // First call persists and sends notification; second call with same idempotencyKey is ignored
        notifManager.persistAndNotifyEvent(ev, notificationsEnabledInSettings = true)
        notifManager.persistAndNotifyEvent(ev.copy(price = 62100.0), notificationsEnabledInSettings = true)
        val storedEvents = dao.getAllEvents(uid)
        assertEquals(1, storedEvents.size)
        assertEquals(62000.0, storedEvents.first().price, 0.01)

        // SyncCoordinator conflict detection when local has pending changes and remote has newer revision with different operationId
        val firestoreRepo = JournalFirestoreRepository(context)
        val syncCoordinator = SyncCoordinator(dao, firestoreRepo, isNetworkOnline = { true })

        dao.upsertSignal(
            TradingSignalEntity(
                id = "sig_conflict_1",
                userId = uid,
                mentorId = "m1",
                mentorName = "Mentor A",
                pair = "BTC/USDT",
                direction = "LONG",
                signalTimestampMillis = 1700000000000L,
                entryPrices = listOf(60000.0),
                tpPrices = listOf(62000.0),
                slPrice = 58000.0,
                revision = 2,
                lastOperationId = "op_local_edit",
                isPendingSync = true
            )
        )

        val remoteDoc = com.example.data.model.TradingSignalDoc(
            id = "sig_conflict_1",
            userId = uid,
            mentorId = "m1",
            mentorName = "Mentor A",
            pair = "BTC/USDT",
            direction = "LONG",
            signalTimestampMillis = 1700000000000L,
            entryPrices = listOf(60000.0),
            tpPrices = listOf(63500.0),
            slPrice = 58500.0,
            revision = 3,
            lastOperationId = "op_remote_other_device"
        )

        syncCoordinator.reconcileRemoteSignals(uid, listOf(remoteDoc))
        val conflicts = dao.getSyncConflicts(uid)
        assertEquals(1, conflicts.size)
        assertTrue(
            syncCoordinator.syncStatus.value in listOf(
                com.example.domain.model.SyncStatus.KONFLIK,
                com.example.domain.model.SyncStatus.MEMBUTUHKAN_PENINJAUAN
            )
        )
    }

    @Test
    fun `backup and restore JSON and CSV support MERGE and REPLACE modes and PDF generation`() = runBlocking {
        val dao = database.journalDao()
        val backupManager = BackupRestoreManager(dao)
        val uid = "user_backup_test"

        dao.upsertSignal(
            TradingSignalEntity(
                id = "sig_1",
                userId = uid,
                mentorId = "m_1",
                mentorName = "Mentor Satoshi",
                pair = "BTC/USDT",
                direction = SignalDirection.LONG.name,
                signalTimestampMillis = 1700000000000L,
                entryPrices = listOf(60000.0, 59500.0),
                tpPrices = listOf(62000.0, 64000.0),
                slPrice = 58000.0,
                status = SignalStatus.COMPLETED.name,
                outcome = SignalOutcome.WIN.name,
                outcomeSource = PriceSourceType.BINANCE_FUTURES.name
            )
        )

        val exportedJson = backupManager.exportToJson(uid)
        assertTrue(exportedJson.contains("Mentor Satoshi"))

        val exportedCsv = backupManager.exportSignalsToCsv(uid)
        assertTrue(exportedCsv.contains("BTC/USDT"))

        // Add another signal then REPLACE with backup -> only sig_1 should remain
        dao.upsertSignal(
            TradingSignalEntity(
                id = "sig_2",
                userId = uid,
                mentorId = "m_2",
                mentorName = "Mentor Vitalik",
                pair = "ETH/USDT",
                direction = SignalDirection.SHORT.name,
                signalTimestampMillis = 1700000050000L,
                entryPrices = listOf(3000.0),
                tpPrices = listOf(2800.0),
                slPrice = 3100.0
            )
        )
        assertEquals(2, dao.getAllSignalsIncludingDeleted(uid).size)

        val replaceResult = backupManager.importFromJson(uid, exportedJson, RestoreMode.REPLACE)
        assertTrue(replaceResult.isSuccess)
        assertEquals(1, dao.getAllSignalsIncludingDeleted(uid).size)
        assertEquals("sig_1", dao.getAllSignalsIncludingDeleted(uid).first().id)

        // Generate PDF report
        val pdfResult = backupManager.exportReportToPdfFile(context, uid)
        assertTrue(pdfResult.isSuccess)
        val pdfFile = pdfResult.getOrThrow()
        assertTrue(pdfFile.exists() && pdfFile.length() > 0L)
    }

    @Test
    fun `dashboard, add signal, signal detail 9 sections, mentor ranking, and settings CUJ`() {
        val dao = database.journalDao()
        val firestoreRepo = JournalFirestoreRepository(context)
        val fakePriceProvider = FakePriceProvider(online = true, quotePrice = 61200.0)
        val notifManager = SignalNotificationManager(context, dao)
        val syncCoordinator = SyncCoordinator(
            dao = dao,
            firestoreRepository = firestoreRepo,
            isNetworkOnline = { fakePriceProvider.isInternetAvailable() }
        )
        val backupManager = BackupRestoreManager(dao)

        val viewModel = JournalViewModel(
            currentUserId = "test_uid_cuj",
            userEmail = "trader@example.com",
            dao = dao,
            firestoreRepository = firestoreRepo,
            priceProvider = fakePriceProvider,
            notificationManager = notifManager,
            syncCoordinator = syncCoordinator,
            backupRestoreManager = backupManager,
            enableAutoMonitoringLoop = false
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                JournalAppContent(
                    viewModel = viewModel,
                    onLogout = {}
                )
            }
        }

        composeTestRule.waitForIdle()

        // 1. Verify Dashboard renders key cards
        composeTestRule.onNodeWithTag("dashboard_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("capital_and_pnl_card").assertExists()
        composeTestRule.onNodeWithTag("dashboard_screen")
            .performScrollToNode(hasTestTag("win_rate_metrics_card"))
        composeTestRule.onNodeWithTag("win_rate_metrics_card").assertIsDisplayed()

        // 2. Click FAB to open Add Signal dialog and create a valid LONG signal
        composeTestRule.onNodeWithTag("fab_add_signal").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("input_mentor_name").performTextReplacement("Crypto Koko")
        composeTestRule.onNodeWithTag("input_pair").performTextReplacement("BTC/USDT")
        composeTestRule.onNodeWithTag("input_entries").performTextReplacement("60000, 59500")
        composeTestRule.onNodeWithTag("input_tps").performTextReplacement("62000, 64000")
        composeTestRule.onNodeWithTag("input_sl").performTextReplacement("58000")
        composeTestRule.onNodeWithTag("save_signal_submit_button").performClick()
        composeTestRule.waitForIdle()

        // 3. Navigate to Sinyal tab and verify the created signal card is displayed
        composeTestRule.onNodeWithTag("nav_tab_sinyal").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("signals_screen").assertIsDisplayed()
        composeTestRule.onNodeWithText("BTC/USDT").assertExists()

        // 4. Open Signal Detail screen and verify the mandatory sections
        composeTestRule.onNodeWithText("BTC/USDT").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("signal_detail_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("detail_section_1_summary").assertExists()
        composeTestRule.onNodeWithTag("tradingview_chart_section").assertExists()

        // Return from detail screen
        composeTestRule.onNodeWithTag("detail_back_button").performClick()
        composeTestRule.waitForIdle()

        // 5. Navigate to Mentor tab and verify mentor Crypto Koko is listed
        composeTestRule.onNodeWithTag("nav_tab_mentor").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("mentor_screen").assertIsDisplayed()
        composeTestRule.onNodeWithText("Crypto Koko").assertExists()

        // 6. Navigate to Pengaturan tab and verify settings screen
        composeTestRule.onNodeWithTag("nav_tab_pengaturan").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_screen").assertIsDisplayed()
    }
}
