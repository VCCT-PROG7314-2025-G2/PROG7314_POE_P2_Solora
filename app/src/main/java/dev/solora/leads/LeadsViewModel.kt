package dev.solora.leads

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dev.solora.SoloraApp
import dev.solora.data.*
import dev.solora.notifications.MotivationalNotificationManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class LeadsViewModel(app: Application) : AndroidViewModel(app) {
    private val firebaseRepository = FirebaseRepository()
    private val offlineRepository = (app as SoloraApp).offlineRepository
    private val networkMonitor = (app as SoloraApp).networkMonitor
    private val notificationManager = MotivationalNotificationManager(app.applicationContext)
    private var leadsForSelection: List<FirebaseLead> = emptyList()

    companion object {
        private const val TAG = "LeadsViewModel"
    }

    init {
        val currentUser = FirebaseAuth.getInstance().currentUser
        Log.d(TAG, "LeadsViewModel initialized for user: ${currentUser?.uid ?: "NOT LOGGED IN"}")
        if (currentUser == null) {
            Log.w(TAG, "WARNING: No user logged in! Leads will be empty.")
        }
    }

    // Combined leads flow - merges Firebase and local data
    // Shows local data immediately, then syncs with Firebase when online
    val leads = flow {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        Log.d(TAG, "Starting leads flow for user: $userId")
        
        if (userId == null) {
            Log.w(TAG, "No user logged in")
            emit(emptyList())
            return@flow
        }
        
        // First emit local data for instant display
        val localLeads = offlineRepository.getLocalLeads()
        Log.d(TAG, "Loaded ${localLeads.size} leads from local database")
        emit(localLeads.map { it.toFirebaseLead() })
        
        // Then listen to Firebase for real-time updates
        firebaseRepository.getLeads().collect { firebaseLeads ->
            Log.d(TAG, "Received ${firebaseLeads.size} leads from Firebase")
            
            // Save Firebase leads to local database for offline access
            val localLeads = firebaseLeads.map { it.toLocalLead(synced = true) }
            offlineRepository.saveLeadsOffline(localLeads)
            
            // Emit the Firebase leads
            emit(firebaseLeads)
        }
    }.stateIn(
        viewModelScope, 
        SharingStarted.WhileSubscribed(5000), 
        emptyList<FirebaseLead>()
    )

    fun addLead(name: String, email: String, phone: String, notes: String = "", status: String = "new", followUpDate: com.google.firebase.Timestamp? = null) {
        viewModelScope.launch {
            val userId = FirebaseAuth.getInstance().currentUser?.uid
            if (userId == null) {
                Log.e(TAG, "Cannot add lead: No user logged in")
                return@launch
            }
            
            val leadId = UUID.randomUUID().toString()
            val lead = FirebaseLead(
                id = leadId,
                name = name,
                email = email,
                phone = phone,
                status = status,
                notes = notes,
                followUpDate = followUpDate,
                userId = userId
            )
            
            // Check if online
            val isOnline = networkMonitor.isCurrentlyConnected()
            Log.d(TAG, "Adding lead, online status: $isOnline")
            
            if (isOnline) {
                // Try to save to Firebase first
                val result = firebaseRepository.saveLead(lead)
                if (result.isSuccess) {
                    Log.d(TAG, "Lead saved to Firebase successfully")
                    // Also save to local database (marked as synced)
                    offlineRepository.saveLeadOffline(lead.toLocalLead(synced = true))
                    notificationManager.checkAndSendLeadMessage()
                } else {
                    Log.e(TAG, "Failed to save lead to Firebase: ${result.exceptionOrNull()?.message}")
                    // Save locally as unsynced
                    offlineRepository.saveLeadOffline(lead.toLocalLead(synced = false))
                }
            } else {
                // Offline - save to local database only (unsynced)
                Log.d(TAG, "Offline - saving lead to local database")
                offlineRepository.saveLeadOffline(lead.toLocalLead(synced = false))
            }
        }
    }
    
    fun updateLeadStatus(leadId: String, status: String, notes: String = "") {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Updating lead status: $leadId to $status")
                
                // Check if online
                val isOnline = networkMonitor.isCurrentlyConnected()
                
                if (isOnline) {
                    // Get current lead from Firebase
                    val result = firebaseRepository.getLeadById(leadId)
                    if (result.isSuccess) {
                        val currentLead = result.getOrNull()
                        if (currentLead != null) {
                            val updatedLead = currentLead.copy(
                                status = status,
                                notes = notes.ifEmpty { currentLead.notes }
                            )
                            
                            val updateResult = firebaseRepository.updateLead(leadId, updatedLead)
                            if (updateResult.isSuccess) {
                                Log.d(TAG, "Lead status updated in Firebase")
                                // Update local database
                                offlineRepository.saveLeadOffline(updatedLead.toLocalLead(synced = true))
                            } else {
                                Log.e(TAG, "Failed to update lead status in Firebase: ${updateResult.exceptionOrNull()?.message}")
                            }
                        }
                    }
                } else {
                    // Offline - update local database only (mark as unsynced)
                    Log.d(TAG, "Offline - updating lead in local database")
                    // Get from local database
                    val localLeads = offlineRepository.getLocalLeads()
                    val localLead = localLeads.find { it.id == leadId }
                    if (localLead != null) {
                        val updatedLead = localLead.copy(
                            status = status,
                            notes = notes.ifEmpty { localLead.notes },
                            synced = false,
                            updatedAt = System.currentTimeMillis()
                        )
                        offlineRepository.updateLeadOffline(updatedLead)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating lead status: ${e.message}", e)
            }
        }
    }
    
    fun deleteLead(leadId: String) {
        viewModelScope.launch {
            try {
                val result = firebaseRepository.deleteLead(leadId)
                if (result.isSuccess) {
                    // Lead deleted from Firebase
                } else {
                    // Failed to delete lead from Firebase: ${result.exceptionOrNull()?.message}
                }
            } catch (e: Exception) {
                // Error deleting lead: ${e.message}
            }
        }
    }
    
    // Create a lead from a quote
    fun createLeadFromQuote(
        quoteId: String,
        clientName: String,
        address: String,
        email: String = "",
        phone: String = "",
        notes: String = "",
        status: String = "qualified"
    ) {
        viewModelScope.launch {
            try {
                val lead = FirebaseLead(
                    name = clientName,
                    email = email,
                    phone = phone,
                    status = status,
                    notes = notes.ifEmpty { "Lead created from quote. Address: $address" },
                    quoteId = quoteId
                )
                
                val result = firebaseRepository.saveLead(lead)
                if (result.isSuccess) {
                    notificationManager.checkAndSendLeadMessage()
                } else {
                    // Failed to save lead from quote to Firebase: ${result.exceptionOrNull()?.message}
                }
            } catch (e: Exception) {
                // Error creating lead from quote: ${e.message}
            }
        }
    }
    
    fun setLeadsForSelection(leads: List<FirebaseLead>) {
        leadsForSelection = leads
    }
    
    fun getLeadsForSelection(): List<FirebaseLead> {
        return leadsForSelection
    }
    
    // Link an existing quote to an existing lead
    fun linkQuoteToLead(leadId: String, quoteId: String) {
        viewModelScope.launch {
            try {
                // Get the current lead
                val result = firebaseRepository.getLeadById(leadId)
                if (result.isSuccess) {
                    val currentLead = result.getOrNull()
                    if (currentLead != null) {
                        // Update the lead with the quote ID
                        val updatedLead = currentLead.copy(quoteId = quoteId)
                        val updateResult = firebaseRepository.updateLead(leadId, updatedLead)
                        
                        if (updateResult.isSuccess) {
                            // Quote $quoteId linked to lead $leadId
                        } else {
                            // Failed to link quote to lead: ${updateResult.exceptionOrNull()?.message}
                        }
                    } else {
                        // Lead not found: $leadId
                    }
                } else {
                    // Error getting lead: ${result.exceptionOrNull()?.message}
                }
            } catch (e: Exception) {
                // ("LeadsViewModel", "Error linking quote to lead: ${e.message}", e)
            }
        }
    }
    
    // Synchronous version for immediate feedback
    suspend fun linkQuoteToLeadSync(leadId: String, quoteId: String): Boolean {
        return try {
            // Starting linkQuoteToLeadSync - LeadId: $leadId, QuoteId: $quoteId
            
            // Validate inputs
            if (leadId.isBlank() || quoteId.isBlank()) {
                // Invalid IDs - LeadId: '$leadId', QuoteId: '$quoteId'
                return false
            }
            
            // Get the current lead
            // Getting lead by ID: $leadId
            val result = firebaseRepository.getLeadById(leadId)
            
            if (result.isSuccess) {
                val currentLead = result.getOrNull()
                // Lead retrieved: ${currentLead?.id}, Name: ${currentLead?.name}
                
                if (currentLead != null) {
                    // Update the lead with the quote ID
                    val updatedLead = currentLead.copy(quoteId = quoteId)
                    // Updating lead with quote ID: $quoteId
                    
                    val updateResult = firebaseRepository.updateLead(leadId, updatedLead)
                    
                    if (updateResult.isSuccess) {
                        // Successfully linked quote $quoteId to lead $leadId
                        return true
                    } else {
                        val error = updateResult.exceptionOrNull()
                        // ("LeadsViewModel", "Failed to update lead in Firebase: ${error?.message}", error)
                        return false
                    }
                } else {
                    // Lead not found in database: $leadId
                    return false
                }
            } else {
                val error = result.exceptionOrNull()
                // ("LeadsViewModel", "Error getting lead from Firebase: ${error?.message}", error)
                return false
            }
        } catch (e: Exception) {
            // ("LeadsViewModel", "Unexpected error linking quote to lead: ${e.message}", e)
            return false
        }
    }
    
    // Synchronous version for immediate feedback
    suspend fun createLeadFromQuoteSync(
        quoteId: String,
        clientName: String,
        address: String,
        email: String = "",
        phone: String = "",
        notes: String = "",
        status: String = "qualified"
    ): Boolean {
        return try {
            if (quoteId.isBlank() || clientName.isBlank()) {
                return false
            }
            
            val lead = FirebaseLead(
                name = clientName,
                email = email,
                phone = phone,
                status = status,
                notes = notes.ifEmpty { "Lead created from quote. Address: $address" },
                quoteId = quoteId
            )
            
            val result = firebaseRepository.saveLead(lead)
            if (result.isSuccess) {
                val leadId = result.getOrNull()
                if (!leadId.isNullOrBlank()) {
                    notificationManager.checkAndSendLeadMessage()
                    true
                } else {
                    false
                }
            } else {
                val error = result.exceptionOrNull()
                false
            }
        } catch (e: Exception) {
            false
        }
    }
}