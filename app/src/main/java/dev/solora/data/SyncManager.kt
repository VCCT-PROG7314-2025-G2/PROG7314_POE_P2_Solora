package dev.solora.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages synchronization between local Room database and Firebase
 * Syncs unsynced data when network is available
 */
class SyncManager(
    private val offlineRepo: OfflineRepository,
    private val firebaseRepo: FirebaseRepository
) {
    companion object {
        private const val TAG = "SyncManager"
    }

    /**
     * Sync all unsynced data to Firebase
     * Called when network becomes available
     */
    suspend fun syncAll() = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting sync of all offline data")

        try {
            // Sync quotes first
            syncQuotes()
            
            // Then sync leads
            syncLeads()
            
            Log.d(TAG, "Sync completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error during sync: ${e.message}", e)
        }
    }

    /**
     * Sync unsynced quotes to Firebase
     */
    private suspend fun syncQuotes() {
        try {
            val unsyncedQuotes = offlineRepo.getUnsyncedQuotes()
            Log.d(TAG, "Found ${unsyncedQuotes.size} unsynced quotes")

            unsyncedQuotes.forEach { localQuote ->
                try {
                    val firebaseQuote = localQuote.toFirebaseQuote()
                    val result = firebaseRepo.saveQuote(firebaseQuote)

                    if (result.isSuccess) {
                        offlineRepo.markQuoteAsSynced(localQuote.id)
                        Log.d(TAG, "Synced quote: ${localQuote.id}")
                    } else {
                        Log.e(TAG, "Failed to sync quote ${localQuote.id}: ${result.exceptionOrNull()?.message}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception syncing quote ${localQuote.id}: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting unsynced quotes: ${e.message}", e)
        }
    }

    /**
     * Sync unsynced leads to Firebase
     */
    private suspend fun syncLeads() {
        try {
            val unsyncedLeads = offlineRepo.getUnsyncedLeads()
            Log.d(TAG, "Found ${unsyncedLeads.size} unsynced leads")

            unsyncedLeads.forEach { localLead ->
                try {
                    val firebaseLead = localLead.toFirebaseLead()
                    val result = firebaseRepo.saveLead(firebaseLead)

                    if (result.isSuccess) {
                        offlineRepo.markLeadAsSynced(localLead.id)
                        Log.d(TAG, "Synced lead: ${localLead.id}")
                    } else {
                        Log.e(TAG, "Failed to sync lead ${localLead.id}: ${result.exceptionOrNull()?.message}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception syncing lead ${localLead.id}: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting unsynced leads: ${e.message}", e)
        }
    }

    /**
     * Download Firebase data to local database
     * Used for initial sync or refresh
     */
    suspend fun downloadFromFirebase() = withContext(Dispatchers.IO) {
        Log.d(TAG, "Downloading data from Firebase")

        try {
            // Download quotes
            val quotesResult = firebaseRepo.getQuotesViaApi()
            if (quotesResult.isSuccess) {
                val firebaseQuotes = quotesResult.getOrNull() ?: emptyList()
                val localQuotes = firebaseQuotes.map { it.toLocalQuote(synced = true) }
                offlineRepo.saveQuotesOffline(localQuotes)
                Log.d(TAG, "Downloaded ${localQuotes.size} quotes from Firebase")
            }

            // Download leads
            val leadsResult = firebaseRepo.getLeadsViaApi()
            if (leadsResult.isSuccess) {
                val firebaseLeads = leadsResult.getOrNull() ?: emptyList()
                val localLeads = firebaseLeads.map { it.toLocalLead(synced = true) }
                offlineRepo.saveLeadsOffline(localLeads)
                Log.d(TAG, "Downloaded ${localLeads.size} leads from Firebase")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading from Firebase: ${e.message}", e)
        }
    }
}

