package dev.solora.leads

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import dev.solora.SoloraApp
import dev.solora.data.FirebaseLead
import dev.solora.data.FirebaseRepository
import dev.solora.data.LocalLead
import dev.solora.data.OfflineRepository
import dev.solora.data.toFirebaseLead
import dev.solora.data.toLocalLead
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID

class LeadsViewModel(app: Application) : AndroidViewModel(app) {

    private val firebaseRepository = FirebaseRepository()
    private val offlineRepo: OfflineRepository = (getApplication() as SoloraApp).offlineRepo
    private var leadsForSelection: List<FirebaseLead> = emptyList()

    // Local state
    private val _leads = MutableStateFlow<List<FirebaseLead>>(emptyList())
    val leads: StateFlow<List<FirebaseLead>> get() = _leads

    init {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Log.w("LeadsViewModel", "No user logged in! Leads will be empty.")
        } else {
            refreshLeads()
        }
    }

    // Refresh leads
    fun refreshLeads() {
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch

            if (isOnline(context)) {

                syncOfflineLeads()

                // Fetch fresh leads from firebase
                val result = firebaseRepository.getLeadsForUser(userId)
                val firebaseLeads = result.getOrNull() ?: emptyList()

                // Update room db and mark as synced
                firebaseLeads.forEach { lead ->
                    offlineRepo.saveLeadOffline(lead.toLocalLead().copy(synced = true))
                }

                _leads.value = firebaseLeads
            } else {

                val localLeads = offlineRepo.getLocalLeads()
                _leads.value = localLeads.map { it.toFirebaseLead() }
            }
        }
    }

    // Offline Add
    fun addLeadOffline(localLead: LocalLead) {
        viewModelScope.launch {
            try {
                offlineRepo.saveLeadOffline(localLead)
                refreshLeads()
            } catch (e: Exception) {
                Log.e("LeadsViewModel", "Failed to save lead offline", e)
            }
        }
    }

    fun addLeadOnline(lead: FirebaseLead) {
        viewModelScope.launch {
            try {
                val result = firebaseRepository.saveLead(lead)
                if (result.isSuccess) {
                    // Save in Room as synced
                    offlineRepo.saveLeadOffline(lead.toLocalLead().copy(synced = true))
                } else {
                    // fallback: save offline for retry
                    offlineRepo.saveLeadOffline(lead.toLocalLead())
                }
            } catch (e: Exception) {
                Log.e("LeadsViewModel", "Failed to save lead online", e)
                // fallback offline
                offlineRepo.saveLeadOffline(lead.toLocalLead())
            }
        }
    }

    fun syncOfflineLeads() {
        viewModelScope.launch {
            val unsyncedLeads = offlineRepo.getUnsyncedLeads()
            unsyncedLeads.forEach { localLead ->
                val firebaseLead = localLead.toFirebaseLead()
                try {
                    val result = firebaseRepository.saveLead(firebaseLead) // use your existing saveLead()
                    if (result.isSuccess) {
                        // Update local lead as synced
                        offlineRepo.markLeadAsSynced(localLead.id)
                    } else {
                        Log.e("LeadsViewModel", "Failed to sync lead: ${result.exceptionOrNull()?.message}")
                    }
                } catch (e: Exception) {
                    Log.e("LeadsViewModel", "Exception syncing lead: ${e.message}", e)
                }
            }
        }
    }

    /*fun addLeadOffline(
        firstName: String,
        lastName: String,
        email: String,
        phone: String,
        notes: String = "",
        quoteId: String? = null
    ) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUserId == null) {
            Log.w("LeadsViewModel", "User not logged in, cannot save lead")
            return
        }

        val fullName = "$firstName $lastName"

        val newLead = FirebaseLead(
            id = null,
            name = fullName,
            email = email,
            phone = phone,
            status = "New",
            notes = notes,
            quoteId = quoteId,
            createdAt = null,
            updatedAt = null,
            userId = currentUserId
        )

        viewModelScope.launch {
            try {
                offlineRepo.saveLeadOffline(newLead)
                refreshLeads()
            } catch (e: Exception) {
                Log.e("LeadsViewModel", "Failed to save lead offline", e)
            }
        }
    }*/

    suspend fun addLeadToFirebase(lead: FirebaseLead): Boolean {
        return try {
            val result = firebaseRepository.saveLead(lead)
            result.isSuccess
        } catch (e: Exception) {
            Log.e("LeadsViewModel", "Failed to save lead to Firebase", e)
            false
        }
    }

    private fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    val firebaseLeadsFlow = flow {
        try {
            val apiResult = firebaseRepository.getLeadsViaApi()
            if (apiResult.isSuccess) {
                emit(apiResult.getOrNull() ?: emptyList())
            } else {
                emitAll(firebaseRepository.getLeads())
            }
        } catch (e: Exception) {
            emitAll(firebaseRepository.getLeads())
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    fun addLead(name: String, email: String, phone: String, notes: String = "") {
        viewModelScope.launch {
            val lead = FirebaseLead(
                name = name,
                email = email,
                phone = phone,
                status = "new",
                notes = notes
            )
            firebaseRepository.saveLead(lead)
        }
    }

    fun updateLeadStatus(leadId: String, status: String, notes: String = "") {
        viewModelScope.launch {
            val result = firebaseRepository.getLeadById(leadId)
            val currentLead = result.getOrNull() ?: return@launch
            val updated = currentLead.copy(
                status = status,
                notes = notes.ifEmpty { currentLead.notes }
            )
            firebaseRepository.updateLead(leadId, updated)
        }
    }

    fun deleteLead(leadId: String) {
        viewModelScope.launch {
            firebaseRepository.deleteLead(leadId)
        }
    }

    fun setLeadsForSelection(leads: List<FirebaseLead>) {
        leadsForSelection = leads
    }

    fun getLeadsForSelection(): List<FirebaseLead> = leadsForSelection

    fun createLeadFromQuote(
        quoteId: String,
        clientName: String,
        address: String,
        email: String = "",
        phone: String = "",
        notes: String = ""
    ) {
        viewModelScope.launch {
            val lead = FirebaseLead(
                name = clientName,
                email = email,
                phone = phone,
                status = "qualified",
                notes = notes.ifEmpty { "Lead created from quote. Address: $address" },
                quoteId = quoteId
            )
            firebaseRepository.saveLead(lead)
        }
    }

    fun linkQuoteToLead(leadId: String, quoteId: String) {
        viewModelScope.launch {
            val result = firebaseRepository.getLeadById(leadId)
            val currentLead = result.getOrNull() ?: return@launch
            val updated = currentLead.copy(quoteId = quoteId)
            firebaseRepository.updateLead(leadId, updated)
        }
    }

    suspend fun linkQuoteToLeadSync(leadId: String, quoteId: String): Boolean {
        try {
            val result = firebaseRepository.getLeadById(leadId)
            val lead = result.getOrNull() ?: return false

            val updated = lead.copy(quoteId = quoteId)
            val updateResult = firebaseRepository.updateLead(leadId, updated)

            return updateResult.isSuccess
        } catch (_: Exception) {
            return false
        }
    }

    suspend fun createLeadFromQuoteSync(
        quoteId: String,
        clientName: String,
        address: String,
        email: String = "",
        phone: String = "",
        notes: String = ""
    ): Boolean {
        try {
            val lead = FirebaseLead(
                name = clientName,
                email = email,
                phone = phone,
                status = "qualified",
                notes = notes.ifEmpty { "Lead created from quote. Address: $address" },
                quoteId = quoteId
            )
            val result = firebaseRepository.saveLead(lead)
            return result.isSuccess
        } catch (_: Exception) {
            return false
        }
    }
}

