package dev.solora

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import dev.solora.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Application class for Solora
 * Sets up Firebase, Room database, and automatic offline sync
 */
class SoloraApp : Application() {
    
    // Global offline components accessible to ViewModels
    lateinit var localDatabase: LocalDatabase
        private set
    lateinit var offlineRepository: OfflineRepository
        private set
    lateinit var syncManager: SyncManager
        private set
    lateinit var networkMonitor: NetworkMonitor
        private set
    
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    companion object {
        private const val TAG = "SoloraApp"
    }

    override fun onCreate() {
        super.onCreate()
        
        Log.d(TAG, "Initializing Solora App")
        
        FirebaseApp.initializeApp(this)
        
        // Enable Firestore offline persistence
        FirebaseFirestore.getInstance().firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .build()
        
        initializeOfflineMode()
        startNetworkMonitoring()
    }
    
    // Set up Room database and sync components
    private fun initializeOfflineMode() {
        Log.d(TAG, "Initializing offline mode components")
        
        localDatabase = LocalDatabase.getDatabase(this)
        
        val firebaseRepository = FirebaseRepository()
        offlineRepository = OfflineRepository(localDatabase)
        syncManager = SyncManager(offlineRepository, firebaseRepository)
        networkMonitor = NetworkMonitor(this)
        
        Log.d(TAG, "Offline mode initialized successfully")
    }
    
    // Auto sync when network becomes available
    private fun startNetworkMonitoring() {
        applicationScope.launch {
            networkMonitor.isConnected
                .distinctUntilChanged()
                .collect { isConnected ->
                    Log.d(TAG, "Network status changed: ${if (isConnected) "CONNECTED" else "DISCONNECTED"}")
                    
                    if (isConnected) {
                        Log.d(TAG, "Network available - starting sync")
                        try {
                            syncManager.syncAll()
                            Log.d(TAG, "Sync completed successfully")
                        } catch (e: Exception) {
                            Log.e(TAG, "Sync failed: ${e.message}", e)
                        }
                    } else {
                        Log.d(TAG, "Network unavailable - offline mode active")
                    }
                }
        }
    }
}
