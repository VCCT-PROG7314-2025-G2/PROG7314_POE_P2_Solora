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
        if (!isNotificationsEnabled()) return
        
        val userId = auth.currentUser?.uid ?: return
        
        try {
            val quotesSnapshot = firestore.collection("quotes")
                .whereEqualTo("userId", userId)
                .get()
                .await()
            
            val quoteCount = quotesSnapshot.size()
            
            if (shouldSendNotificationForCount(quoteCount, "quotes")) {
                val message = generateMotivationalMessage(quoteCount)
                
                if (message != null) {
                    showLocalNotification(message.first, message.second)
                    markMilestoneAsNotified(quoteCount, "quotes")
                }
            }
            
        } catch (e: Exception) {
            if (shouldSendFallbackNotification()) {
                showLocalNotification("Great job!", "You've created a new quote!")
                markFallbackNotificationSent()
            }
        }
    }

    suspend fun checkAndSendLeadMessage() {
        if (!isNotificationsEnabled()) return
        
        val userId = auth.currentUser?.uid ?: return
        
        try {
            val leadsSnapshot = firestore.collection("leads")
                .whereEqualTo("userId", userId)
                .get()
                .await()
            
            val leadCount = leadsSnapshot.size()
            
            if (shouldSendNotificationForCount(leadCount, "leads")) {
                val message = generateLeadMotivationalMessage(leadCount)
                
                if (message != null) {
                    showLocalNotification(message.first, message.second)
                    markMilestoneAsNotified(leadCount, "leads")
                }
            }
            
        } catch (e: Exception) {
            // Handle error silently
        }
    }


    private fun generateMotivationalMessage(quoteCount: Int): Pair<String, String>? {
        return when {
            quoteCount == 1 -> {
                "Congratulations!" to "You've created your first quote! You're on your way to solar success!"
            }
            quoteCount in 2..4 -> {
                "Great progress!" to "You have $quoteCount quotes created. Keep up the excellent work!"
            }
            quoteCount in 5..9 -> {
                "You're on fire!" to "Wow! $quoteCount quotes completed. You're becoming a solar expert!"
            }
            quoteCount >= 10 -> {
                "Amazing work!" to "You've reached $quoteCount quotes! You're a true solar professional!"
            }
            else -> {
                "Great job!" to "You've created a new quote!"
            }
        }
    }

    private fun generateLeadMotivationalMessage(leadCount: Int): Pair<String, String>? {
        return when {
            leadCount == 1 -> {
                "First lead!" to "You've added your first lead! Your pipeline is growing!"
            }
            leadCount in 2..4 -> {
                "Building momentum!" to "You have $leadCount leads in your pipeline. Keep prospecting!"
            }
            leadCount in 5..9 -> {
                "Excellent work!" to "$leadCount leads and counting! Your pipeline is looking strong!"
            }
            leadCount >= 10 -> {
                "Sales superstar!" to "You've reached $leadCount leads! Your pipeline is thriving!"
            }
            else -> {
                "New lead!" to "You've added a new lead to your pipeline!"
            }
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
            count >= 10 -> 10
            else -> 0
        }
        
        return currentMilestone > 0 && !milestonesList.contains(currentMilestone)
    }
    
    private suspend fun markMilestoneAsNotified(count: Int, type: String) {
        val currentMilestone = when {
            count == 1 -> 1
            count in 2..4 -> 2
            count in 5..9 -> 5
            count >= 10 -> 10
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
