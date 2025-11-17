package dev.solora.data

import androidx.room.*

/**
 * Data Access Object for local database operations
 * Provides methods to interact with Room database
 */
@Dao
interface LocalDao {

    // ============================================
    // QUOTES OPERATIONS
    // ============================================
    
    @Query("SELECT * FROM quotes WHERE userId = :userId ORDER BY createdAt DESC")
    suspend fun getAllQuotes(userId: String): List<LocalQuote>

    @Query("SELECT * FROM quotes WHERE id = :id")
    suspend fun getQuoteById(id: String): LocalQuote?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuote(quote: LocalQuote)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuotes(quotes: List<LocalQuote>)

    @Query("SELECT * FROM quotes WHERE synced = 0 AND userId = :userId")
    suspend fun getUnsyncedQuotes(userId: String): List<LocalQuote>

    @Query("UPDATE quotes SET synced = 1 WHERE id = :id")
    suspend fun markQuoteAsSynced(id: String)

    @Query("DELETE FROM quotes WHERE id = :id")
    suspend fun deleteQuote(id: String)

    @Query("DELETE FROM quotes WHERE userId = :userId")
    suspend fun deleteAllQuotes(userId: String)

    // ============================================
    // LEADS OPERATIONS
    // ============================================
    
    @Query("SELECT * FROM leads WHERE userId = :userId ORDER BY createdAt DESC")
    suspend fun getAllLeads(userId: String): List<LocalLead>

    @Query("SELECT * FROM leads WHERE id = :id")
    suspend fun getLeadById(id: String): LocalLead?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLead(lead: LocalLead)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLeads(leads: List<LocalLead>)

    @Query("SELECT * FROM leads WHERE synced = 0 AND userId = :userId")
    suspend fun getUnsyncedLeads(userId: String): List<LocalLead>

    @Query("UPDATE leads SET synced = 1 WHERE id = :id")
    suspend fun markLeadAsSynced(id: String)
    
    @Update
    suspend fun updateLead(lead: LocalLead)

    @Query("DELETE FROM leads WHERE id = :id")
    suspend fun deleteLead(id: String)

    @Query("DELETE FROM leads WHERE userId = :userId")
    suspend fun deleteAllLeads(userId: String)
}

