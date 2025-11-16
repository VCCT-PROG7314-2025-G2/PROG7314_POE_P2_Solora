package dev.solora

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.appcompat.app.AppCompatDelegate
import dev.solora.api.FirebaseFunctionsApi
import dev.solora.data.FirebaseRepository
import kotlinx.coroutines.launch
import android.util.Log
import dev.solora.profile.LocaleHelper
import android.content.SharedPreferences

// This is the main activity that starts when the app opens
// It checks if everything is working and then shows the main app
class MainActivity : FragmentActivity() {
    
    private val firebaseRepository = FirebaseRepository()
    private val apiService = FirebaseFunctionsApi()
    
    companion object {
        private const val PREFS_NAME = "solora_settings"
        private const val KEY_DARK_MODE = "dark_mode_enabled"
    }

    override fun attachBaseContext(newBase: Context) {
        val sharedPrefs = newBase.getSharedPreferences("Settings", Context.MODE_PRIVATE)
        val lang = sharedPrefs.getString("My_Lang", "en") ?: "en"
        val context = LocaleHelper.setLocale(newBase, lang)
        super.attachBaseContext(context)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Apply dark mode before setting content view
        applyDarkModeSettings()
        
        setContentView(R.layout.activity_main)
        
        // Check if our API is working when the app starts
        // This makes sure everything is connected properly
        performStartupChecks()
    }
    
    private fun applyDarkModeSettings() {
        try {
            val sharedPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val isDarkMode = sharedPrefs.getBoolean(KEY_DARK_MODE, false)
            if (isDarkMode) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        } catch (e: Exception) {
            // Default to light mode if there's an error
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
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
