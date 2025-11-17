package dev.solora.data

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for managing offline data operations
 * Handles local database interactions and provides data to ViewModels
 */
class OfflineRepository(
    private val localDb: LocalDatabase
) {
    private val dao = localDb.dao()
    private val auth = FirebaseAuth.getInstance()

    private fun getCurrentUserId(): String? = auth.currentUser?.uid

    // ============================================
    // QUOTES OPERATIONS
    // ============================================

    suspend fun saveQuoteOffline(quote: LocalQuote) = withContext(Dispatchers.IO) {
        dao.insertQuote(quote)
    }

    suspend fun getLocalQuotes(): List<LocalQuote> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId() ?: return@withContext emptyList()
        dao.getAllQuotes(userId)
    }

    suspend fun getUnsyncedQuotes(): List<LocalQuote> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId() ?: return@withContext emptyList()
        dao.getUnsyncedQuotes(userId)
    }

    suspend fun markQuoteAsSynced(id: String) = withContext(Dispatchers.IO) {
        dao.markQuoteAsSynced(id)
    }

    suspend fun deleteQuote(id: String) = withContext(Dispatchers.IO) {
        dao.deleteQuote(id)
    }

    // ============================================
    // LEADS OPERATIONS
    // ============================================

    suspend fun saveLeadOffline(lead: LocalLead) = withContext(Dispatchers.IO) {
        dao.insertLead(lead)
    }

    suspend fun getLocalLeads(): List<LocalLead> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId() ?: return@withContext emptyList()
        dao.getAllLeads(userId)
    }

    suspend fun getUnsyncedLeads(): List<LocalLead> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId() ?: return@withContext emptyList()
        dao.getUnsyncedLeads(userId)
    }

    suspend fun markLeadAsSynced(id: String) = withContext(Dispatchers.IO) {
        dao.markLeadAsSynced(id)
    }
    
    suspend fun updateLeadOffline(lead: LocalLead) = withContext(Dispatchers.IO) {
        dao.updateLead(lead)
    }

    suspend fun deleteLead(id: String) = withContext(Dispatchers.IO) {
        dao.deleteLead(id)
    }

    // ============================================
    // BULK OPERATIONS
    // ============================================

    suspend fun saveQuotesOffline(quotes: List<LocalQuote>) = withContext(Dispatchers.IO) {
        dao.insertQuotes(quotes)
    }

    suspend fun saveLeadsOffline(leads: List<LocalLead>) = withContext(Dispatchers.IO) {
        dao.insertLeads(leads)
    }

    // ============================================
    // CLEAR DATA (for logout)
    // ============================================

    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId() ?: return@withContext
        dao.deleteAllQuotes(userId)
        dao.deleteAllLeads(userId)
    }
}

