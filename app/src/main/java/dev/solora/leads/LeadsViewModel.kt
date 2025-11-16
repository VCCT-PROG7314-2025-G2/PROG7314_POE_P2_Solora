package dev.solora.leads

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.solora.data.FirebaseLead
import dev.solora.data.FirebaseRepository
import dev.solora.notifications.MotivationalNotificationManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.launch
import com.google.firebase.auth.FirebaseAuth

class LeadsViewModel(app: Application) : AndroidViewModel(app) {
    private val firebaseRepository = FirebaseRepository()
    private val notificationManager = MotivationalNotificationManager(app.applicationContext)
    private var leadsForSelection: List<FirebaseLead> = emptyList()

    init {
        val currentUser = FirebaseAuth.getInstance().currentUser
        // LeadsViewModel initialized for user: ${currentUser?.uid ?: "NOT LOGGED IN"}
        if (currentUser == null) {
            // WARNING: No user logged in! Leads will be empty.
        }
    }

    // Firebase leads flow - filtered by logged-in user's ID
    // Using real-time Firestore listener for automatic updates
    val leads = flow {
        // Starting leads flow for user: ${FirebaseAuth.getInstance().currentUser?.uid}
        // Use direct Firestore with real-time listener for automatic updates
        emitAll(firebaseRepository.getLeads())
    }.stateIn(
        viewModelScope, 
        SharingStarted.WhileSubscribed(5000), 
        emptyList<FirebaseLead>()
    )

    fun addLead(name: String, email: String, phone: String, notes: String = "", status: String = "new", followUpDate: com.google.firebase.Timestamp? = null) {
        viewModelScope.launch { 
            val lead = FirebaseLead(
                name = name,
                email = email,
                phone = phone,
                status = status,
                notes = notes,
                followUpDate = followUpDate
            )
            
            val result = firebaseRepository.saveLead(lead)
            if (result.isSuccess) {
                notificationManager.checkAndSendLeadMessage()
            } else {
                // Failed to save lead to Firebase: ${result.exceptionOrNull()?.message}
            }
        }
    }
    
    fun updateLeadStatus(leadId: String, status: String, notes: String = "") {
        viewModelScope.launch {
            try {
                // Get current lead
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
                            // Lead status updated in Firebase
                        } else {
                            // Failed to update lead status in Firebase: ${updateResult.exceptionOrNull()?.message}
                        }
                    }
                } else {
                    // Failed to get lead: ${result.exceptionOrNull()?.message}
                }
            } catch (e: Exception) {
                // Error updating lead status: ${e.message}
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