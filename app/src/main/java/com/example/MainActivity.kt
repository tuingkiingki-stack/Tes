package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import com.example.data.local.AppDatabase
import com.example.data.remote.LiveMarketPriceProvider
import com.example.data.repository.BackupRestoreManager
import com.example.data.repository.JournalFirestoreRepository
import com.example.data.repository.SyncCoordinator
import com.example.service.SignalNotificationManager
import com.example.ui.JournalAppContent
import com.example.ui.JournalViewModel
import com.example.ui.auth.GoogleAuthHelper
import com.example.ui.screens.AuthScreen
import com.example.ui.theme.MyApplicationTheme
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.example.service.SignalMonitoringJobService.schedulePeriodicMonitoring(applicationContext)
        setContent {
            MyApplicationTheme {
                JurnalSinyalRootApp()
            }
        }
    }
}

@Composable
fun JurnalSinyalRootApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val firebaseAuth = remember {
        try {
            FirebaseAuth.getInstance()
        } catch (_: Exception) {
            null
        }
    }

    var currentUser by remember { mutableStateOf(firebaseAuth?.currentUser) }

    DisposableEffect(firebaseAuth) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        firebaseAuth?.addAuthStateListener(listener)
        onDispose {
            firebaseAuth?.removeAuthStateListener(listener)
        }
    }

    val activeUser = currentUser
    if (activeUser == null) {
        AuthScreen(
            onAuthSuccess = {
                currentUser = firebaseAuth?.currentUser
            }
        )
    } else {
        val viewModel = remember(activeUser.uid) {
            val db = AppDatabase.getInstance(context)
            val dao = db.journalDao()
            val firestoreRepo = JournalFirestoreRepository(context)
            val priceProvider = LiveMarketPriceProvider(context)
            val notifManager = SignalNotificationManager(context, dao)
            val syncCoordinator = SyncCoordinator(
                dao = dao,
                firestoreRepository = firestoreRepo,
                isNetworkOnline = { priceProvider.isInternetAvailable() }
            )
            val backupManager = BackupRestoreManager(dao)
            JournalViewModel(
                currentUserId = activeUser.uid,
                userEmail = activeUser.email,
                dao = dao,
                firestoreRepository = firestoreRepo,
                priceProvider = priceProvider,
                notificationManager = notifManager,
                syncCoordinator = syncCoordinator,
                backupRestoreManager = backupManager
            )
        }

        JournalAppContent(
            viewModel = viewModel,
            onLogout = {
                GoogleAuthHelper.signOut(
                    credentialManager = credentialManager,
                    onSignOutComplete = {
                        currentUser = null
                    },
                    scope = scope
                )
            }
        )
    }
}
