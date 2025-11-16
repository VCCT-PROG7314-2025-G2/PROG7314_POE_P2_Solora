package dev.solora.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.tasks.await

// This class handles all the data operations
// It talks to Firebase and our API to save and get quotes, leads, and settings
class FirebaseRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val apiService = dev.solora.api.FirebaseFunctionsApi()
    
    private fun getCurrentUserId(): String? = auth.currentUser?.uid

    // Quote Operations - saving and getting quotes
    // This saves a quote to the database
    suspend fun saveQuote(quote: FirebaseQuote): Result<String> {
        return try {
            val userId = getCurrentUserId() ?: throw Exception("User not authenticated")
            
            val quoteWithUser = quote.copy(userId = userId)
            
            // Try API first, fallback to direct Firestore
            val apiResult = saveQuoteViaApi(quoteWithUser)
            if (apiResult.isSuccess) {
                apiResult
            } else {
                val docRef = firestore.collection("quotes").add(quoteWithUser).await()
                Result.success(docRef.id)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // This gets all quotes for the current user
    // It updates in real-time when quotes change
    suspend fun getQuotes(): Flow<List<FirebaseQuote>> = callbackFlow {
        val userId = getCurrentUserId()
        if (userId == null) {
            if (!isClosedForSend) {
                trySend(emptyList())
            }
            awaitClose { }
        } else {
            
            val listener = firestore.collection("quotes")
                .whereEqualTo("userId", userId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        
                        // Check for index requirement error
                        if (error.message?.contains("index", ignoreCase = true) == true) {
                        }
                        
                        if (!isClosedForSend) {
                            trySend(emptyList())
                        }
                        return@addSnapshotListener
                    }
                    
                    if (snapshot != null) {
                        val quotes = snapshot.documents.mapNotNull { doc ->
                            doc.toObject(FirebaseQuote::class.java)?.copy(id = doc.id)
                        }
                        if (!isClosedForSend) {
                            trySend(quotes)
                        }
                    } else {
                        if (!isClosedForSend) {
                            trySend(emptyList())
                        }
                    }
                }
            
            awaitClose {
                listener.remove()
            }
        }
    }

    suspend fun getQuoteById(quoteId: String): Result<FirebaseQuote?> {
        return try {
            val userId = getCurrentUserId() ?: throw Exception("User not authenticated")
            
            // Try API first, fallback to direct Firestore
            val apiResult = apiService.getQuoteById(quoteId)
            if (apiResult.isSuccess) {
                val quoteData = apiResult.getOrNull()
                if (quoteData != null) {
                    // Convert Map to FirebaseQuote
                    val quote = FirebaseQuote(
                        id = quoteData["id"] as? String,
                        reference = quoteData["reference"] as? String ?: "",
                        clientName = quoteData["clientName"] as? String ?: "",
                        address = quoteData["address"] as? String ?: "",
                        usageKwh = (quoteData["usageKwh"] as? Number)?.toDouble(),
                        billRands = (quoteData["billRands"] as? Number)?.toDouble(),
                        tariff = (quoteData["tariff"] as? Number)?.toDouble() ?: 0.0,
                        panelWatt = (quoteData["panelWatt"] as? Number)?.toInt() ?: 0,
                        latitude = (quoteData["latitude"] as? Number)?.toDouble(),
                        longitude = (quoteData["longitude"] as? Number)?.toDouble(),
                        averageAnnualIrradiance = (quoteData["averageAnnualIrradiance"] as? Number)?.toDouble(),
                        averageAnnualSunHours = (quoteData["averageAnnualSunHours"] as? Number)?.toDouble(),
                        systemKwp = (quoteData["systemKwp"] as? Number)?.toDouble() ?: 0.0,
                        estimatedGeneration = (quoteData["estimatedGeneration"] as? Number)?.toDouble() ?: 0.0,
                        monthlySavings = (quoteData["monthlySavings"] as? Number)?.toDouble() ?: 0.0,
                        paybackMonths = (quoteData["paybackMonths"] as? Number)?.toInt() ?: 0,
                        companyName = quoteData["companyName"] as? String ?: "",
                        companyPhone = quoteData["companyPhone"] as? String ?: "",
                        companyEmail = quoteData["companyEmail"] as? String ?: "",
                        consultantName = quoteData["consultantName"] as? String ?: "",
                        consultantPhone = quoteData["consultantPhone"] as? String ?: "",
                        consultantEmail = quoteData["consultantEmail"] as? String ?: "",
                        userId = quoteData["userId"] as? String ?: "",
                        createdAt = quoteData["createdAt"] as? com.google.firebase.Timestamp,
                        updatedAt = quoteData["updatedAt"] as? com.google.firebase.Timestamp
                    )
                    Result.success(quote)
                } else {
                    Result.success(null)
                }
            } else {
                
                // Fallback to direct Firestore
                val doc = firestore.collection("quotes")
                    .document(quoteId)
                    .get()
                    .await()
                
                if (doc.exists()) {
                    val quote = doc.toObject(FirebaseQuote::class.java)?.copy(id = doc.id)
                    if (quote?.userId == userId) {
                        Result.success(quote)
                    } else {
                        Result.failure(Exception("Quote not found or access denied"))
                    }
                } else {
                    Result.success(null)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateQuote(quoteId: String, quote: FirebaseQuote): Result<Unit> {
        return try {
            val userId = getCurrentUserId() ?: throw Exception("User not authenticated")
            
            firestore.collection("quotes")
                .document(quoteId)
                .set(quote.copy(id = quoteId, userId = userId))
                .await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteQuote(quoteId: String): Result<Unit> {
        return try {
            val userId = getCurrentUserId() ?: throw Exception("User not authenticated")
            
            // Verify ownership before deletion
            val doc = firestore.collection("quotes").document(quoteId).get().await()
            val quote = doc.toObject(FirebaseQuote::class.java)
            
            if (quote?.userId == userId) {
                firestore.collection("quotes").document(quoteId).delete().await()
                Result.success(Unit)
            } else {
                Result.failure(Exception("Quote not found or access denied"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Lead Operations
    suspend fun saveLead(lead: FirebaseLead): Result<String> {
        return try {
            val userId = getCurrentUserId() ?: throw Exception("User not authenticated")
            val leadWithUser = lead.copy(userId = userId)
            
            // Try API first, fallback to direct Firestore
            val apiResult = saveLeadViaApi(leadWithUser)
            if (apiResult.isSuccess) {
                apiResult
            } else {
                val docRef = firestore.collection("leads").add(leadWithUser).await()
                Result.success(docRef.id)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getLeads(): Flow<List<FirebaseLead>> = callbackFlow {
        val userId = getCurrentUserId()
        if (userId == null) {
            if (!isClosedForSend) {
                trySend(emptyList())
            }
            awaitClose { }
        } else {
            
            val listener = firestore.collection("leads")
                .whereEqualTo("userId", userId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        
                        // Check for index requirement error
                        if (error.message?.contains("index", ignoreCase = true) == true) {
                        }
                        
                        if (!isClosedForSend) {
                            trySend(emptyList())
                        }
                        return@addSnapshotListener
                    }
                    
                    if (snapshot != null) {
                        val leads = snapshot.documents.mapNotNull { doc ->
                            doc.toObject(FirebaseLead::class.java)?.copy(id = doc.id)
                        }
                        if (!isClosedForSend) {
                            trySend(leads)
                        }
                    } else {
                        if (!isClosedForSend) {
                            trySend(emptyList())
                        }
                    }
                }
            
            awaitClose {
                listener.remove()
            }
        }
    }

    suspend fun updateLead(leadId: String, lead: FirebaseLead): Result<Unit> {
        return try {
            val userId = getCurrentUserId() ?: throw Exception("User not authenticated")
            
            // Update all lead fields
            val updateData = mutableMapOf<String, Any?>(
                "name" to lead.name,
                "email" to lead.email,
                "phone" to lead.phone,
                "status" to lead.status,
                "notes" to lead.notes,
                "quoteId" to lead.quoteId,
                "followUpDate" to lead.followUpDate,
                "updatedAt" to com.google.firebase.Timestamp.now()
            )
            
            // Remove null values
            updateData.entries.removeIf { it.value == null }
            
            firestore.collection("leads")
                .document(leadId)
                .update(updateData)
                .await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getLeadById(leadId: String): Result<FirebaseLead?> {
        return try {
            val userId = getCurrentUserId() ?: throw Exception("User not authenticated")
            
            // Note: Using Firestore directly - no API endpoint exists for getLeadById
            val doc = firestore.collection("leads")
                .document(leadId)
                .get()
                .await()
            
            if (doc.exists()) {
                val lead = doc.toObject(FirebaseLead::class.java)?.copy(id = doc.id)
                if (lead?.userId == userId) {
                    Result.success(lead)
                } else {
                    Result.failure(Exception("Lead not found or access denied"))
                }
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteLead(leadId: String): Result<Unit> {
        return try {
            val userId = getCurrentUserId() ?: throw Exception("User not authenticated")
            
            // Verify ownership before deletion
            val doc = firestore.collection("leads").document(leadId).get().await()
            val lead = doc.toObject(FirebaseLead::class.java)
            
            if (lead?.userId == userId) {
                firestore.collection("leads").document(leadId).delete().await()
                Result.success(Unit)
            } else {
                Result.failure(Exception("Lead not found or access denied"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // User Profile Operations
    suspend fun saveUserProfile(user: FirebaseUser): Result<String> {
        return try {
            val userId = getCurrentUserId() ?: throw Exception("User not authenticated")
            
            // Try API first, fallback to direct Firestore
            val apiResult = apiService.updateUserProfile(user.copy(id = userId))
            if (apiResult.isSuccess) {
                Result.success(userId)
            } else {
                
                // Fallback to direct Firestore
                firestore.collection("users")
                    .document(userId)
                    .set(user.copy(id = userId))
                    .await()
                
                Result.success(userId)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUserProfile(): Result<FirebaseUser?> {
        return try {
            val userId = getCurrentUserId() ?: throw Exception("User not authenticated")
            
            // Try API first, fallback to direct Firestore
            val apiResult = apiService.getUserProfile()
            if (apiResult.isSuccess) {
                val userData = apiResult.getOrNull()
                if (userData != null) {
                    val user = convertMapToFirebaseUser(userData)
                    Result.success(user)
                } else {
                    Result.success(null)
                }
            } else {
                
                // Fallback to direct Firestore
                val doc = firestore.collection("users")
                    .document(userId)
                    .get()
                    .await()
                
                if (doc.exists()) {
                    val user = doc.toObject(FirebaseUser::class.java)?.copy(id = doc.id)
                    Result.success(user)
                } else {
                    Result.success(null)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun convertMapToFirebaseUser(data: Map<String, Any>): FirebaseUser? {
        return try {
            FirebaseUser(
                id = data["id"] as? String,
                name = data["name"] as? String ?: "",
                surname = data["surname"] as? String ?: "",
                email = data["email"] as? String ?: "",
                phone = data["phone"] as? String,
                company = data["company"] as? String,
                role = data["role"] as? String ?: "sales_consultant",
                createdAt = data["createdAt"] as? com.google.firebase.Timestamp,
                updatedAt = data["updatedAt"] as? com.google.firebase.Timestamp
            )
        } catch (e: Exception) {
            null
        }
    }

    // Configuration Operations
    suspend fun getConfiguration(): Result<FirebaseConfiguration?> {
        return try {
            val doc = firestore.collection("configurations")
                .document("app_config")
                .get()
                .await()
            
            if (doc.exists()) {
                val config = doc.toObject(FirebaseConfiguration::class.java)?.copy(id = doc.id)
                Result.success(config)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveConfiguration(config: FirebaseConfiguration): Result<Unit> {
        return try {
            firestore.collection("configurations")
                .document("app_config")
                .set(config)
                .await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // ============================================
    // API-BASED METHODS (Using REST API endpoints)
    // ============================================
    
    /**
     * Get leads via API with search and filter support
     */
    suspend fun getLeadsViaApi(
        search: String? = null,
        status: String? = null,
        limit: Int = 50
    ): Result<List<FirebaseLead>> {
        return try {
            val result = apiService.getLeads(search, status, limit)
            if (result.isSuccess) {
                val leadsData = result.getOrNull() ?: emptyList()
                val leads = leadsData.mapNotNull { data: Map<String, Any> ->
                    try {
                        FirebaseLead(
                            id = data["id"] as? String,
                            name = data["name"] as? String ?: "",
                            email = data["email"] as? String ?: "",
                            phone = data["phone"] as? String ?: "",
                            status = data["status"] as? String ?: "NEW",
                            notes = data["notes"] as? String,
                            quoteId = data["quoteId"] as? String,
                            userId = data["userId"] as? String ?: "",
                            createdAt = data["createdAt"] as? Timestamp,
                            updatedAt = data["updatedAt"] as? Timestamp
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
                Result.success(leads)
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Unknown error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get quotes via API with search support
     */
    suspend fun getQuotesViaApi(
        search: String? = null,
        limit: Int = 50
    ): Result<List<FirebaseQuote>> {
        return try {
            val result = apiService.getQuotes(search, limit)
            if (result.isSuccess) {
                val quotesData = result.getOrNull() ?: emptyList()
                val quotes = quotesData.mapNotNull { data: Map<String, Any> ->
                    try {
                        FirebaseQuote(
                            id = data["id"] as? String,
                            reference = data["reference"] as? String ?: "",
                            clientName = data["clientName"] as? String ?: "",
                            address = data["address"] as? String ?: "",
                            usageKwh = (data["usageKwh"] as? Number)?.toDouble(),
                            billRands = (data["billRands"] as? Number)?.toDouble(),
                            tariff = (data["tariff"] as? Number)?.toDouble() ?: 0.0,
                            panelWatt = (data["panelWatt"] as? Number)?.toInt() ?: 0,
                            latitude = (data["latitude"] as? Number)?.toDouble(),
                            longitude = (data["longitude"] as? Number)?.toDouble(),
                            averageAnnualIrradiance = (data["averageAnnualIrradiance"] as? Number)?.toDouble(),
                            averageAnnualSunHours = (data["averageAnnualSunHours"] as? Number)?.toDouble(),
                            systemKwp = (data["systemKwp"] as? Number)?.toDouble() ?: 0.0,
                            estimatedGeneration = (data["estimatedGeneration"] as? Number)?.toDouble() ?: 0.0,
                            monthlySavings = (data["monthlySavings"] as? Number)?.toDouble() ?: 0.0,
                            paybackMonths = (data["paybackMonths"] as? Number)?.toInt() ?: 0,
                            companyName = data["companyName"] as? String ?: "",
                            companyPhone = data["companyPhone"] as? String ?: "",
                            companyEmail = data["companyEmail"] as? String ?: "",
                            consultantName = data["consultantName"] as? String ?: "",
                            consultantPhone = data["consultantPhone"] as? String ?: "",
                            consultantEmail = data["consultantEmail"] as? String ?: "",
                            userId = data["userId"] as? String ?: "",
                            createdAt = data["createdAt"] as? Timestamp,
                            updatedAt = data["updatedAt"] as? Timestamp
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
                Result.success(quotes)
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Unknown error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Update settings via API
     */
    suspend fun updateSettingsViaApi(settings: Map<String, Any>): Result<String> {
        return try {
            val result = apiService.updateSettings(settings)
            if (result.isSuccess) {
                Result.success(result.getOrNull() ?: "Settings updated")
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Unknown error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Sync data via API
     */
    suspend fun syncDataViaApi(offlineData: Map<String, Any>): Result<Map<String, Any>> {
        return try {
            val result = apiService.syncData(offlineData)
            if (result.isSuccess) {
                Result.success(result.getOrNull() ?: emptyMap())
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Unknown error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Health check via API
     */
    suspend fun healthCheckViaApi(): Result<Map<String, Any>> {
        return try {
            val result = apiService.healthCheck()
            if (result.isSuccess) {
                Result.success(result.getOrNull() ?: emptyMap())
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Unknown error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Calculate quote via API with NASA integration
     */
    suspend fun calculateQuoteViaApi(
        address: String,
        usageKwh: Double?,
        billRands: Double?,
        tariff: Double,
        panelWatt: Int,
        latitude: Double?,
        longitude: Double?
    ): Result<dev.solora.quote.QuoteOutputs> {
        return try {
            val result = apiService.calculateQuote(address, usageKwh, billRands, tariff, panelWatt, latitude, longitude)
            if (result.isSuccess) {
                val calculationData = result.getOrNull()
                if (calculationData != null) {
                    // Convert API response to QuoteOutputs
                    val quoteOutputs = dev.solora.quote.QuoteOutputs(
                        panels = calculationData.panels,
                        systemKw = calculationData.systemKwp,
                        inverterKw = calculationData.inverterKw,
                        estimatedMonthlySavingsR = calculationData.monthlySavings,
                        monthlyUsageKwh = usageKwh ?: 0.0,
                        monthlyBillRands = billRands ?: 0.0,
                        tariffRPerKwh = tariff,
                        panelWatt = panelWatt,
                        estimatedMonthlyGeneration = calculationData.estimatedGeneration,
                        monthlySavingsRands = calculationData.monthlySavings,
                        paybackMonths = calculationData.paybackMonths,
                        detailedAnalysis = null // API doesn't return detailed analysis
                    )
                    Result.success(quoteOutputs)
                } else {
                    Result.failure(Exception("No calculation data returned from API"))
                }
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Unknown error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Save quote via API
     */
    suspend fun saveQuoteViaApi(quote: FirebaseQuote): Result<String> {
        return try {
            val quoteData = mapOf(
                "reference" to quote.reference,
                "clientName" to quote.clientName,
                "address" to quote.address,
                "usageKwh" to quote.usageKwh,
                "billRands" to quote.billRands,
                "tariff" to quote.tariff,
                "panelWatt" to quote.panelWatt,
                "latitude" to quote.latitude,
                "longitude" to quote.longitude,
                "averageAnnualIrradiance" to quote.averageAnnualIrradiance,
                "averageAnnualSunHours" to quote.averageAnnualSunHours,
                "systemKwp" to quote.systemKwp,
                "estimatedGeneration" to quote.estimatedGeneration,
                "monthlySavings" to quote.monthlySavings,
                "paybackMonths" to quote.paybackMonths,
                "companyName" to quote.companyName,
                "companyPhone" to quote.companyPhone,
                "companyEmail" to quote.companyEmail,
                "consultantName" to quote.consultantName,
                "consultantPhone" to quote.consultantPhone,
                "consultantEmail" to quote.consultantEmail,
                "userId" to quote.userId
            )
            
            val result = apiService.saveQuote(quoteData)
            if (result.isSuccess) {
                val quoteId = result.getOrNull()
                Result.success(quoteId ?: "")
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Unknown error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get quote by ID via API
     */
    suspend fun getQuoteByIdViaApi(quoteId: String): Result<FirebaseQuote?> {
        return try {
            val result = apiService.getQuoteById(quoteId)
            if (result.isSuccess) {
                val quoteData = result.getOrNull()
                if (quoteData != null) {
                    val quote = FirebaseQuote(
                        id = quoteData.get("id") as? String,
                        reference = quoteData.get("reference") as? String ?: "",
                        clientName = quoteData.get("clientName") as? String ?: "",
                        address = quoteData.get("address") as? String ?: "",
                        usageKwh = (quoteData.get("usageKwh") as? Number)?.toDouble(),
                        billRands = (quoteData.get("billRands") as? Number)?.toDouble(),
                        tariff = (quoteData.get("tariff") as? Number)?.toDouble() ?: 0.0,
                        panelWatt = (quoteData.get("panelWatt") as? Number)?.toInt() ?: 0,
                        latitude = (quoteData.get("latitude") as? Number)?.toDouble(),
                        longitude = (quoteData.get("longitude") as? Number)?.toDouble(),
                        averageAnnualIrradiance = (quoteData.get("averageAnnualIrradiance") as? Number)?.toDouble(),
                        averageAnnualSunHours = (quoteData.get("averageAnnualSunHours") as? Number)?.toDouble(),
                        systemKwp = (quoteData.get("systemKwp") as? Number)?.toDouble() ?: 0.0,
                        estimatedGeneration = (quoteData.get("estimatedGeneration") as? Number)?.toDouble() ?: 0.0,
                        monthlySavings = (quoteData.get("monthlySavings") as? Number)?.toDouble() ?: 0.0,
                        paybackMonths = (quoteData.get("paybackMonths") as? Number)?.toInt() ?: 0,
                        companyName = quoteData.get("companyName") as? String ?: "",
                        companyPhone = quoteData.get("companyPhone") as? String ?: "",
                        companyEmail = quoteData.get("companyEmail") as? String ?: "",
                        consultantName = quoteData.get("consultantName") as? String ?: "",
                        consultantPhone = quoteData.get("consultantPhone") as? String ?: "",
                        consultantEmail = quoteData.get("consultantEmail") as? String ?: "",
                        userId = quoteData.get("userId") as? String ?: "",
                        createdAt = quoteData.get("createdAt") as? Timestamp,
                        updatedAt = quoteData.get("updatedAt") as? Timestamp
                    )
                    Result.success(quote)
                } else {
                    Result.success(null)
                }
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Unknown error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Save lead via API
     */
    suspend fun saveLeadViaApi(lead: FirebaseLead): Result<String> {
        return try {
            val leadData = mapOf(
                "name" to lead.name,
                "email" to lead.email,
                "phone" to lead.phone,
                "status" to lead.status,
                "notes" to lead.notes,
                "quoteId" to lead.quoteId,
                "userId" to lead.userId
            )
            
            val result = apiService.saveLead(leadData)
            if (result.isSuccess) {
                val leadId = result.getOrNull()
                Result.success(leadId ?: "")
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Unknown error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get settings via API
     */
    suspend fun getSettingsViaApi(): Result<Map<String, Any>?> {
        return try {
            val result = apiService.getSettings()
            if (result.isSuccess) {
                val settings = result.getOrNull()
                Result.success(settings)
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Unknown error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
