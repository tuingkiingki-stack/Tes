package com.example.service

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.remote.LiveMarketPriceProvider
import com.example.data.repository.JournalFirestoreRepository
import com.example.data.repository.SyncCoordinator
import com.example.domain.logic.PriceMonitoringEngine
import com.example.domain.model.PriceSourceType
import com.example.domain.model.SignalStatus
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Android background JobService that periodically monitors active crypto signals,
 * evaluates Entry / TP / SL touches against live market quotes, sends notifications,
 * and synchronizes pending local changes to Cloud Firestore when the app is in the background.
 */
class SignalMonitoringJobService : JobService() {

    private val jobScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartJob(params: JobParameters?): Boolean {
        jobScope.launch {
            try {
                runBackgroundMonitoringCycle(applicationContext)
            } catch (_: Exception) {
                // Handled gracefully without crashing background process
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        jobScope.cancel()
        return true
    }

    companion object {
        private const val JOB_ID = 94021
        private const val PERIODIC_INTERVAL_MS = 15 * 60 * 1000L // 15 minutes Android minimum periodic job interval

        fun schedulePeriodicMonitoring(context: Context) {
            try {
                val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
                val existing = scheduler.allPendingJobs.any { it.id == JOB_ID }
                if (existing) return

                val component = ComponentName(context, SignalMonitoringJobService::class.java)
                val jobInfo = JobInfo.Builder(JOB_ID, component)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(false)
                    .setPeriodic(PERIODIC_INTERVAL_MS)
                    .build()
                scheduler.schedule(jobInfo)
            } catch (_: Exception) {
                // Ignore if JobScheduler is restricted in test/headless environments
            }
        }

        suspend fun runBackgroundMonitoringCycle(context: Context) {
            val uid = try {
                FirebaseAuth.getInstance().currentUser?.uid
            } catch (_: Exception) {
                null
            } ?: return

            val db = AppDatabase.getInstance(context)
            val dao = db.journalDao()
            val priceProvider = LiveMarketPriceProvider(context)
            if (!priceProvider.isInternetAvailable()) return

            val profile = dao.getUserProfile(uid)
            val preferredSource = try {
                PriceSourceType.valueOf(profile?.preferredPriceSource ?: PriceSourceType.BINANCE_FUTURES.name)
            } catch (_: Exception) {
                PriceSourceType.BINANCE_FUTURES
            }
            val staleThresholdSec = profile?.priceStaleThresholdSec ?: 60
            val notificationsEnabled = profile?.notificationsEnabled ?: true

            val activeSignals = dao.getAllSignalsIncludingDeleted(uid)
                .filter { !it.isDeleted && it.status == SignalStatus.ACTIVE.name }

            val notifManager = SignalNotificationManager(context, dao)
            notifManager.retryFailedNotificationsOnConnectionRecovery(uid, notificationsEnabled)

            val distinctPairs = activeSignals.map { it.pair }.distinct()
            val quotesByPair = distinctPairs.associateWith { pair ->
                priceProvider.fetchPriceForPair(pair, preferredSource, staleThresholdSec)
            }

            val now = System.currentTimeMillis()
            for (signal in activeSignals) {
                val resp = quotesByPair[signal.pair] ?: continue
                val primaryQuote = resp.primaryQuote ?: continue
                val eval = PriceMonitoringEngine.evaluatePriceTick(
                    signal = signal,
                    quote = primaryQuote,
                    nowMillis = now,
                    staleThresholdSec = staleThresholdSec
                )
                if (!eval.rejectedBecauseExpired) {
                    var updated = eval.updatedSignal
                    val allEvents = eval.newEvents.toMutableList()
                    val cgQuote = resp.referenceCoinGeckoQuote
                    if (cgQuote != null && primaryQuote.source != PriceSourceType.COINGECKO) {
                        val conflictEval = PriceMonitoringEngine.evaluateCrossSourceConflict(
                            signal = updated,
                            binanceQuote = primaryQuote,
                            coingeckoQuote = cgQuote,
                            nowMillis = now,
                            staleThresholdSec = staleThresholdSec
                        )
                        updated = conflictEval.updatedSignal
                        allEvents.addAll(conflictEval.newEvents)
                    }
                    dao.upsertSignal(updated)
                    for (ev in allEvents) {
                        notifManager.persistAndNotifyEvent(ev, notificationsEnabled)
                    }
                }
            }

            val firestoreRepo = JournalFirestoreRepository(context)
            val syncCoordinator = SyncCoordinator(
                dao = dao,
                firestoreRepository = firestoreRepo,
                isNetworkOnline = { priceProvider.isInternetAvailable() }
            )
            syncCoordinator.pushPendingChangesToCloud(uid)
        }
    }
}
