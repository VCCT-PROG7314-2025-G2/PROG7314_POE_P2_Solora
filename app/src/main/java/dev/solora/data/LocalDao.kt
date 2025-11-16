package dev.solora.data

import androidx.room.*

@Dao
interface LocalDao {

    // Quotes
    @Query("SELECT * FROM quotes ORDER BY id DESC")
    suspend fun getAllQuotes(): List<LocalQuote>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuote(quote: LocalQuote)

    @Query("SELECT * FROM quotes WHERE synced = 0")
    suspend fun getUnsyncedQuotes(): List<LocalQuote>

    @Query("DELETE FROM quotes WHERE id = :id")
    suspend fun deleteQuote(id: String)

    // Leads
    @Query("SELECT * FROM leads ORDER BY createdAt DESC")
    suspend fun getAllLeads(): List<LocalLead>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLead(lead: LocalLead)

    @Query("SELECT * FROM leads WHERE synced = 0")
    suspend fun getUnsyncedLeads(): List<LocalLead>

    @Query("SELECT * FROM leads WHERE synced = :synced")
    suspend fun getLeadsWhereSynced(synced: Boolean): List<LocalLead>

    @Query("UPDATE leads SET synced = :synced WHERE id = :id")
    suspend fun updateLeadSynced(id: String, synced: Boolean)


    @Query("DELETE FROM leads WHERE id = :id")
    suspend fun deleteLead(id: String)
}