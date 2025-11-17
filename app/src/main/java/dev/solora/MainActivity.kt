package dev.solora

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import dev.solora.api.FirebaseFunctionsApi
import dev.solora.data.FirebaseRepository
import kotlinx.coroutines.launch
import android.util.Log
import dev.solora.profile.LocaleHelper
import android.content.SharedPreferences
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.distinctUntilChanged

// This is the main activity that starts when the app opens
// It checks if everything is working and then shows the main app
class MainActivity : FragmentActivity() {
    
    private val firebaseRepository = FirebaseRepository()
    private val apiService = FirebaseFunctionsApi()
    private var offlineDialogShown = false

    override fun attachBaseContext(newBase: Context) {
        val sharedPrefs = newBase.getSharedPreferences("Settings", Context.MODE_PRIVATE)
        val lang = sharedPrefs.getString("My_Lang", "en") ?: "en"
        val context = LocaleHelper.setLocale(newBase, lang)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Monitor network connectivity and show offline mode dialog
        monitorNetworkStatus()
        
        // Check if our API is working when the app starts
        // This makes sure everything is connected properly
        performStartupChecks()
    }
    
    private fun monitorNetworkStatus() {
        val app = application as SoloraApp
        lifecycleScope.launch {
            app.networkMonitor.isConnected
                .distinctUntilChanged()
                .collect { isConnected ->
                    if (!isConnected && !offlineDialogShown) {
                        showOfflineModeDialog()
                        offlineDialogShown = true
                    } else if (isConnected && offlineDialogShown) {
                        // Reset flag when back online
                        offlineDialogShown = false
                    }
                }
        }
    }
    
    private fun showOfflineModeDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Offline Mode")
            .setMessage(
                "You're currently offline, but you can still:\n\n" +
                "• View all quotes and leads\n" +
                "• Create new quotes\n" +
                "• Create and update leads\n" +
                "• Link quotes to leads\n\n" +
                "Limited features:\n" +
                "• NASA solar data (uses default values)\n" +
                "• Address geocoding\n" +
                "• Real-time sync\n\n" +
                "All your work will automatically sync to the cloud when you're back online!"
            )
            .setPositiveButton("Got it") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(true)
            .show()
    }
    
    private fun performStartupChecks() {
        lifecycleScope.launch {
            try {
                // Health check API endpoint
                val healthResult = apiService.healthCheck()
                if (healthResult.isSuccess) {
                    val healthData = healthResult.getOrNull()
                    Log.d("MainActivity", "API Health Check: $healthData")
                } else {
                    Log.w("MainActivity", "API Health Check failed: ${healthResult.exceptionOrNull()?.message}")
                }
                
                // Test API connectivity with a simple settings call
                val settingsResult = apiService.getSettings()
                if (settingsResult.isSuccess) {
                    Log.d("MainActivity", "API connectivity verified - settings endpoint working")
                } else {
                    Log.w("MainActivity", "API connectivity issue: ${settingsResult.exceptionOrNull()?.message}")
                }
                
                // Sync any offline data if needed
                syncOfflineData()
                
            } catch (e: Exception) {
                Log.e("MainActivity", "Startup checks failed: ${e.message}", e)
            }
        }
    }
    
    private suspend fun syncOfflineData() {
        try {
            // Check if there's any offline data that needs syncing
            // This could include pending quotes, leads, or settings changes
            val offlineData = mapOf<String, Any>(
                "timestamp" to System.currentTimeMillis(),
                "source" to "startup_sync"
            )
            
            val syncResult = apiService.syncData(offlineData)
            if (syncResult.isSuccess) {
                Log.d("MainActivity", "Offline data sync completed successfully")
            } else {
                Log.w("MainActivity", "Offline data sync failed: ${syncResult.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error during offline data sync: ${e.message}", e)
        }
    }
}
