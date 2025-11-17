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
 * Application class that initializes Firebase services and offline mode
 * Enables offline persistence for both Firestore and Room database
 */
class SoloraApp : Application() {
    
    // Offline components - accessible throughout the app
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
        
        // Initialize Firebase
        FirebaseApp.initializeApp(this)
        // Enable offline persistence so data is available without network
        FirebaseFirestore.getInstance().firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .build()
        
        // Initialize offline components
        initializeOfflineMode()
        
        // Start network monitoring and auto-sync
        startNetworkMonitoring()
    }
    
    private fun initializeOfflineMode() {
        Log.d(TAG, "Initializing offline mode components")
        
        // Initialize Room database
        localDatabase = LocalDatabase.getDatabase(this)
        
        // Initialize repositories
        val firebaseRepository = FirebaseRepository()
        offlineRepository = OfflineRepository(localDatabase)
        
        // Initialize sync manager
        syncManager = SyncManager(offlineRepository, firebaseRepository)
        
        // Initialize network monitor
        networkMonitor = NetworkMonitor(this)
        
        Log.d(TAG, "Offline mode initialized successfully")
    }
    
    private fun startNetworkMonitoring() {
        applicationScope.launch {
            networkMonitor.isConnected
                .distinctUntilChanged() // Only react to changes
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
