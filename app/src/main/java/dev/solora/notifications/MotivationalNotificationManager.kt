package dev.solora.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import dev.solora.MainActivity
import dev.solora.R
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

private val Context.motivationalDataStore by preferencesDataStore(name = "motivational_notifications")

class MotivationalNotificationManager(private val context: Context) {
    
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    companion object {
        private val KEY_NOTIFICATIONS_ENABLED = booleanPreferencesKey("motivational_enabled")
        private const val CHANNEL_ID = "solora_motivational"
        private const val CHANNEL_NAME = "Motivational Messages"
        private const val CHANNEL_DESCRIPTION = "Encouraging messages for your solar sales journey"
    }

    suspend fun enableMotivationalNotifications(enabled: Boolean) {
        context.motivationalDataStore.edit { prefs ->
            prefs[KEY_NOTIFICATIONS_ENABLED] = enabled
        }
        
        // Save to Firebase user_settings collection
        saveToUserSettings("notificationsEnabled", enabled)
        
        if (enabled) {
            try {
                val token = FirebaseMessaging.getInstance().token.await()
                saveFCMToken(token)
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    suspend fun isNotificationsEnabled(): Boolean {
        // Use local DataStore during session (fast)
        return context.motivationalDataStore.data.first()[KEY_NOTIFICATIONS_ENABLED] ?: true
    }
    
    suspend fun syncNotificationPreference() {
        val firebasePreference = getFromUserSettings("notificationsEnabled") as? Boolean
        
        if (firebasePreference != null) {
            context.motivationalDataStore.edit { prefs ->
                prefs[KEY_NOTIFICATIONS_ENABLED] = firebasePreference
            }
        } else {
            // Initialize Firebase with default value if it doesn't exist
            val currentLocalValue = context.motivationalDataStore.data.first()[KEY_NOTIFICATIONS_ENABLED] ?: true
            saveToUserSettings("notificationsEnabled", currentLocalValue)
        }
    }

    suspend fun checkAndSendMotivationalMessage() {
        android.util.Log.d("NotificationDebug", "=== checkAndSendMotivationalMessage called ===")
        
        val notificationsEnabled = isNotificationsEnabled()
        android.util.Log.d("NotificationDebug", "Notifications enabled: $notificationsEnabled")
        if (!notificationsEnabled) return
        
        val userId = auth.currentUser?.uid
        android.util.Log.d("NotificationDebug", "User ID: $userId")
        if (userId == null) return
        
        try {
            val quotesSnapshot = firestore.collection("quotes")
                .whereEqualTo("userId", userId)
                .get()
                .await()
            
            val quoteCount = quotesSnapshot.size()
            android.util.Log.d("NotificationDebug", "Total quote count: $quoteCount")
            
            val shouldSend = shouldSendNotificationForCount(quoteCount, "quotes")
            android.util.Log.d("NotificationDebug", "Should send notification: $shouldSend")
            
            if (shouldSend) {
                val message = generateMotivationalMessage(quoteCount)
                android.util.Log.d("NotificationDebug", "Generated message: ${message?.first}")
                
                if (message != null) {
                    showLocalNotification(message.first, message.second)
                    markMilestoneAsNotified(quoteCount, "quotes")
                    android.util.Log.d("NotificationDebug", "Notification shown and milestone marked")
                }
            } else {
                // Log which milestones have already been notified
                val key = "notifiedQuoteMilestones"
                val notifiedMilestones = getFromUserSettings(key) as? List<*>
                val milestonesList = notifiedMilestones?.filterIsInstance<Long>()?.map { it.toInt() }
                android.util.Log.d("NotificationDebug", "Already notified milestones: $milestonesList")
            }
            
        } catch (e: Exception) {
            android.util.Log.e("NotificationDebug", "Error in checkAndSendMotivationalMessage: ${e.message}", e)
            if (shouldSendFallbackNotification()) {
                showLocalNotification("Great job!", "You've created a new quote!")
                markFallbackNotificationSent()
            }
        }
    }

    suspend fun checkAndSendLeadMessage() {
        android.util.Log.d("NotificationDebug", "=== checkAndSendLeadMessage called ===")
        
        val notificationsEnabled = isNotificationsEnabled()
        android.util.Log.d("NotificationDebug", "Notifications enabled: $notificationsEnabled")
        if (!notificationsEnabled) return
        
        val userId = auth.currentUser?.uid
        android.util.Log.d("NotificationDebug", "User ID: $userId")
        if (userId == null) return
        
        try {
            val leadsSnapshot = firestore.collection("leads")
                .whereEqualTo("userId", userId)
                .get()
                .await()
            
            val leadCount = leadsSnapshot.size()
            android.util.Log.d("NotificationDebug", "Total lead count: $leadCount")
            
            val shouldSend = shouldSendNotificationForCount(leadCount, "leads")
            android.util.Log.d("NotificationDebug", "Should send notification: $shouldSend")
            
            if (shouldSend) {
                val message = generateLeadMotivationalMessage(leadCount)
                android.util.Log.d("NotificationDebug", "Generated message: ${message?.first}")
                
                if (message != null) {
                    showLocalNotification(message.first, message.second)
                    markMilestoneAsNotified(leadCount, "leads")
                    android.util.Log.d("NotificationDebug", "Notification shown and milestone marked")
                }
            } else {
                // Log which milestones have already been notified
                val key = "notifiedLeadMilestones"
                val notifiedMilestones = getFromUserSettings(key) as? List<*>
                val milestonesList = notifiedMilestones?.filterIsInstance<Long>()?.map { it.toInt() }
                android.util.Log.d("NotificationDebug", "Already notified milestones: $milestonesList")
            }
            
        } catch (e: Exception) {
            android.util.Log.e("NotificationDebug", "Error in checkAndSendLeadMessage: ${e.message}", e)
        }
    }


    private fun generateMotivationalMessage(quoteCount: Int): Pair<String, String>? {
        return when {
            quoteCount == 1 -> "Congratulations!" to "You've created your first quote! You're on your way to solar success!"
            quoteCount in 2..4 -> "Great progress!" to "You have $quoteCount quotes created. Keep up the excellent work!"
            quoteCount in 5..9 -> "You're on fire!" to "Wow! $quoteCount quotes completed. You're becoming a solar expert!"
            quoteCount in 10..14 -> "Amazing work!" to "You've reached $quoteCount quotes! You're a true solar professional!"
            quoteCount in 15..19 -> "Incredible!" to "$quoteCount quotes! Your expertise is really showing!"
            quoteCount in 20..24 -> "Outstanding!" to "$quoteCount quotes completed! You're unstoppable!"
            quoteCount in 25..29 -> "Phenomenal!" to "$quoteCount quotes! You're a solar powerhouse!"
            quoteCount in 30..34 -> "Exceptional!" to "$quoteCount quotes! Your dedication is inspiring!"
            quoteCount in 35..39 -> "Remarkable!" to "$quoteCount quotes! You're setting new standards!"
            quoteCount in 40..44 -> "Extraordinary!" to "$quoteCount quotes! You're a true master!"
            quoteCount in 45..49 -> "Legendary!" to "$quoteCount quotes! You're breaking all records!"
            quoteCount >= 50 -> "ELITE STATUS!" to "$quoteCount quotes! You're in the top tier of solar professionals!"
            else -> "Great job!" to "You've created a new quote!"
        }
    }

    private fun generateLeadMotivationalMessage(leadCount: Int): Pair<String, String>? {
        return when {
            leadCount == 1 -> "First lead!" to "You've added your first lead! Your pipeline is growing!"
            leadCount in 2..4 -> "Building momentum!" to "You have $leadCount leads in your pipeline. Keep prospecting!"
            leadCount in 5..9 -> "Excellent work!" to "$leadCount leads and counting! Your pipeline is looking strong!"
            leadCount in 10..14 -> "Sales superstar!" to "You've reached $leadCount leads! Your pipeline is thriving!"
            leadCount in 15..19 -> "Pipeline pro!" to "$leadCount leads! You're building an impressive network!"
            leadCount in 20..24 -> "Lead generation master!" to "$leadCount leads! Your prospecting skills are exceptional!"
            leadCount in 25..29 -> "Networking champion!" to "$leadCount leads! Your pipeline is booming!"
            leadCount in 30..34 -> "Business builder!" to "$leadCount leads! You're creating real momentum!"
            leadCount in 35..39 -> "Sales machine!" to "$leadCount leads! Your dedication is paying off!"
            leadCount in 40..44 -> "Pipeline powerhouse!" to "$leadCount leads! You're unstoppable!"
            leadCount in 45..49 -> "Lead generation legend!" to "$leadCount leads! You're at the top of your game!"
            leadCount >= 50 -> "ELITE PROSPECTOR!" to "$leadCount leads! You're a true sales professional!"
            else -> "New lead!" to "You've added a new lead to your pipeline!"
        }
    }

    fun sendTestNotification() {
        showLocalNotification(
            "Solora Notifications Enabled! 🎉",
            "You'll receive motivational messages as you add quotes and leads!"
        )
    }
    
    private fun showLocalNotification(title: String, body: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Create notification channel for Android 8.0+
        createNotificationChannel(notificationManager)
        
        // Create intent to open app when notification is tapped
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 
            0, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.solora_logo)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
    
    private fun createNotificationChannel(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = CHANNEL_DESCRIPTION
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private suspend fun saveFCMToken(token: String) {
        val userId = auth.currentUser?.uid ?: return
        
        try {
            firestore.collection("users").document(userId)
                .update("fcmToken", token)
        } catch (e: Exception) {
            // Handle error silently
        }
    }
    
    private suspend fun saveToUserSettings(key: String, value: Any) {
        val userId = auth.currentUser?.uid ?: return
        
        try {
            firestore.collection("user_settings").document(userId)
                .set(mapOf(key to value), com.google.firebase.firestore.SetOptions.merge())
                .await()
        } catch (e: Exception) {
            // Handle error silently
        }
    }
    
    private suspend fun getFromUserSettings(key: String): Any? {
        val userId = auth.currentUser?.uid ?: return null
        
        return try {
            val document = firestore.collection("user_settings").document(userId)
                .get()
                .await()
            document.get(key)
        } catch (e: Exception) {
            // Handle error silently, return null to fall back to local storage
            null
        }
    }
    
    private suspend fun shouldSendNotificationForCount(count: Int, type: String): Boolean {
        val key = if (type == "quotes") "notifiedQuoteMilestones" else "notifiedLeadMilestones"
        val notifiedMilestones = getFromUserSettings(key) as? List<*>
        val milestonesList = notifiedMilestones?.filterIsInstance<Long>()?.map { it.toInt() } ?: emptyList()
        
        val currentMilestone = when {
            count == 1 -> 1
            count in 2..4 -> 2
            count in 5..9 -> 5
            count in 10..14 -> 10
            count in 15..19 -> 15
            count in 20..24 -> 20
            count in 25..29 -> 25
            count in 30..34 -> 30
            count in 35..39 -> 35
            count in 40..44 -> 40
            count in 45..49 -> 45
            count >= 50 -> 50
            else -> 0
        }
        
        return currentMilestone > 0 && !milestonesList.contains(currentMilestone)
    }
    
    private suspend fun markMilestoneAsNotified(count: Int, type: String) {
        val currentMilestone = when {
            count == 1 -> 1
            count in 2..4 -> 2
            count in 5..9 -> 5
            count in 10..14 -> 10
            count in 15..19 -> 15
            count in 20..24 -> 20
            count in 25..29 -> 25
            count in 30..34 -> 30
            count in 35..39 -> 35
            count in 40..44 -> 40
            count in 45..49 -> 45
            count >= 50 -> 50
            else -> return
        }
        
        val key = if (type == "quotes") "notifiedQuoteMilestones" else "notifiedLeadMilestones"
        val notifiedMilestones = getFromUserSettings(key) as? List<*>
        val milestonesList = notifiedMilestones?.filterIsInstance<Long>()?.map { it.toInt() }?.toMutableList() ?: mutableListOf()
        
        if (!milestonesList.contains(currentMilestone)) {
            milestonesList.add(currentMilestone)
            saveToUserSettings(key, milestonesList)
        }
    }
    
    private suspend fun shouldSendFallbackNotification(): Boolean {
        val lastFallback = getFromUserSettings("lastFallbackNotification") as? Long ?: 0L
        val oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000L)
        return lastFallback < oneDayAgo
    }
    
    private suspend fun markFallbackNotificationSent() {
        saveToUserSettings("lastFallbackNotification", System.currentTimeMillis())
    }

}
