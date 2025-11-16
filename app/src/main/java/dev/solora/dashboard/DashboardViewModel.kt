package dev.solora.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.solora.data.FirebaseRepository
import dev.solora.data.FirebaseQuote
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.Result

/**
 * ViewModel for dashboard statistics
 * Aggregates quote data to show totals, averages, and recent activity
 */
class DashboardViewModel(app: Application) : AndroidViewModel(app) {
    private val firebaseRepository = FirebaseRepository()

    private val _dashboardData = MutableStateFlow(DashboardData())
    val dashboardData: StateFlow<DashboardData> = _dashboardData.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadDashboardData()
    }

    fun loadDashboardData() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                // Try API first for better performance, fallback to direct Firestore
                val apiResult = firebaseRepository.getQuotesViaApi()
                val quotes = if (apiResult.isSuccess) {
                    apiResult.getOrNull() ?: emptyList()
                } else {
                    // Fallback to direct Firestore real-time listener
                    val directFlow = firebaseRepository.getQuotes()
                    directFlow.first() // Get the first emission from the flow
                }

                // Convert API response (Map) to FirebaseQuote objects if needed
                val firebaseQuotes = if (quotes.isNotEmpty() && quotes.first() is Map<*, *>) {
                    // Convert API response to FirebaseQuote objects
                    quotes.mapNotNull { quoteMap ->
                        try {
                            val map = quoteMap as Map<String, Any>
                            FirebaseQuote(
                                id = map["id"] as? String,
                                reference = map["reference"] as? String ?: "",
                                clientName = map["clientName"] as? String ?: "",
                                address = map["address"] as? String ?: "",
                                usageKwh = (map["usageKwh"] as? Number)?.toDouble(),
                                billRands = (map["billRands"] as? Number)?.toDouble(),
                                tariff = (map["tariff"] as? Number)?.toDouble() ?: 0.0,
                                panelWatt = (map["panelWatt"] as? Number)?.toInt() ?: 0,
                                latitude = (map["latitude"] as? Number)?.toDouble(),
                                longitude = (map["longitude"] as? Number)?.toDouble(),
                                averageAnnualIrradiance = (map["averageAnnualIrradiance"] as? Number)?.toDouble(),
                                averageAnnualSunHours = (map["averageAnnualSunHours"] as? Number)?.toDouble(),
                                systemKwp = (map["systemKwp"] as? Number)?.toDouble() ?: 0.0,
                                estimatedGeneration = (map["estimatedGeneration"] as? Number)?.toDouble() ?: 0.0,
                                monthlySavings = (map["monthlySavings"] as? Number)?.toDouble() ?: 0.0,
                                paybackMonths = (map["paybackMonths"] as? Number)?.toInt() ?: 0,
                                companyName = map["companyName"] as? String ?: "",
                                companyPhone = map["companyPhone"] as? String ?: "",
                                companyEmail = map["companyEmail"] as? String ?: "",
                                consultantName = map["consultantName"] as? String ?: "",
                                consultantPhone = map["consultantPhone"] as? String ?: "",
                                consultantEmail = map["consultantEmail"] as? String ?: "",
                                userId = map["userId"] as? String ?: "",
                                createdAt = map["createdAt"] as? com.google.firebase.Timestamp,
                                updatedAt = map["updatedAt"] as? com.google.firebase.Timestamp
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }
                } else {
                    quotes as List<FirebaseQuote>
                }

                // Calculate dashboard data
                val dashboardData = calculateDashboardData(firebaseQuotes)
                _dashboardData.value = dashboardData
                

            } catch (e: Exception) {
                _error.value = "Failed to load dashboard data: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refreshDashboard() {
        loadDashboardData()
    }

    fun loadDashboardDataWithDateFilter(fromDate: java.util.Date?, toDate: java.util.Date?) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                
                // Try API first, fallback to direct Firestore
                val apiResult = firebaseRepository.getQuotesViaApi()
                val allQuotes = if (apiResult.isSuccess) {
                    apiResult.getOrNull() ?: emptyList()
                } else {
                    // Fallback to direct Firestore - collect the flow
                    val directFlow = firebaseRepository.getQuotes()
                    directFlow.first() // Get the first emission from the flow
                }

                // Convert API response to FirebaseQuote objects if needed
                val firebaseQuotes = if (allQuotes.isNotEmpty() && allQuotes.first() is Map<*, *>) {
                    allQuotes.mapNotNull { quoteMap ->
                        try {
                            val map = quoteMap as Map<String, Any>
                            FirebaseQuote(
                                id = map["id"] as? String,
                                reference = map["reference"] as? String ?: "",
                                clientName = map["clientName"] as? String ?: "",
                                address = map["address"] as? String ?: "",
                                usageKwh = (map["usageKwh"] as? Number)?.toDouble(),
                                billRands = (map["billRands"] as? Number)?.toDouble(),
                                tariff = (map["tariff"] as? Number)?.toDouble() ?: 0.0,
                                panelWatt = (map["panelWatt"] as? Number)?.toInt() ?: 0,
                                latitude = (map["latitude"] as? Number)?.toDouble(),
                                longitude = (map["longitude"] as? Number)?.toDouble(),
                                averageAnnualIrradiance = (map["averageAnnualIrradiance"] as? Number)?.toDouble(),
                                averageAnnualSunHours = (map["averageAnnualSunHours"] as? Number)?.toDouble(),
                                systemKwp = (map["systemKwp"] as? Number)?.toDouble() ?: 0.0,
                                estimatedGeneration = (map["estimatedGeneration"] as? Number)?.toDouble() ?: 0.0,
                                monthlySavings = (map["monthlySavings"] as? Number)?.toDouble() ?: 0.0,
                                paybackMonths = (map["paybackMonths"] as? Number)?.toInt() ?: 0,
                                companyName = map["companyName"] as? String ?: "",
                                companyPhone = map["companyPhone"] as? String ?: "",
                                companyEmail = map["companyEmail"] as? String ?: "",
                                consultantName = map["consultantName"] as? String ?: "",
                                consultantPhone = map["consultantPhone"] as? String ?: "",
                                consultantEmail = map["consultantEmail"] as? String ?: "",
                                userId = map["userId"] as? String ?: "",
                                createdAt = map["createdAt"] as? com.google.firebase.Timestamp,
                                updatedAt = map["updatedAt"] as? com.google.firebase.Timestamp
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }
                } else {
                    allQuotes as List<FirebaseQuote>
                }

                // Apply date filter
                val filteredQuotes = if (fromDate != null || toDate != null) {
                    firebaseQuotes.filter { quote ->
                        try {
                            val quoteDate = quote.createdAt?.toDate()
                            if (quoteDate == null) {
                                return@filter true // Include quote if no date available
                            }

                            val isAfterFromDate = fromDate?.let {
                                val calendar = java.util.Calendar.getInstance()
                                calendar.time = it
                                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                                calendar.set(java.util.Calendar.MINUTE, 0)
                                calendar.set(java.util.Calendar.SECOND, 0)
                                calendar.set(java.util.Calendar.MILLISECOND, 0)
                                quoteDate.after(calendar.time) || quoteDate == calendar.time
                            } ?: true

                            val isBeforeToDate = toDate?.let {
                                val calendar = java.util.Calendar.getInstance()
                                calendar.time = it
                                calendar.set(java.util.Calendar.HOUR_OF_DAY, 23)
                                calendar.set(java.util.Calendar.MINUTE, 59)
                                calendar.set(java.util.Calendar.SECOND, 59)
                                calendar.set(java.util.Calendar.MILLISECOND, 999)
                                quoteDate.before(calendar.time) || quoteDate == calendar.time
                            } ?: true

                            isAfterFromDate && isBeforeToDate
                        } catch (e: Exception) {
                            true // Include quote if date filtering fails
                        }
                    }
                } else {
                    firebaseQuotes
                }

                
                // Calculate dashboard data with filtered quotes
                val dashboardData = calculateDashboardData(filteredQuotes)
                _dashboardData.value = dashboardData
                

            } catch (e: Exception) {
                _error.value = "Failed to load dashboard data: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
