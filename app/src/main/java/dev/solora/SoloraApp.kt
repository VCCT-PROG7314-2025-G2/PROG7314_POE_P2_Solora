package dev.solora

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import dev.solora.data.FirebaseRepository
import dev.solora.data.LocalDatabase
import dev.solora.data.NetworkLiveData
import dev.solora.data.OfflineRepository
import dev.solora.data.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class SoloraApp : Application() {

    lateinit var offlineRepo: OfflineRepository
    lateinit var localDb: LocalDatabase
    lateinit var firebaseRepo: FirebaseRepository
    lateinit var syncManager: SyncManager

    override fun onCreate() {
        super.onCreate()

        // Initialize Firebase
        FirebaseApp.initializeApp(this)
        FirebaseFirestore.getInstance().firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .build()

        // Initialize RoomDB
        localDb = LocalDatabase.getDatabase(this)

        // Initialize repository and sync manager
        firebaseRepo = FirebaseRepository()
        syncManager = SyncManager(localDb, firebaseRepo)
        offlineRepo = OfflineRepository(localDb, firebaseRepo)

        // Observe network changes and sync when online
        NetworkLiveData(this).observeForever { connected ->
            if (connected == true) {
                CoroutineScope(Dispatchers.IO).launch {
                    syncManager.syncAll()
                }
            }
        }
    }
}
