package dev.solora.quotes

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dev.solora.SoloraApp
import dev.solora.data.*
import dev.solora.notifications.MotivationalNotificationManager
import dev.solora.quote.GeocodingService
import dev.solora.quote.NasaPowerClient
import dev.solora.quote.QuoteCalculator
import dev.solora.quote.QuoteInputs
import dev.solora.quote.QuoteOutputs
import dev.solora.settings.SettingsRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * ViewModel for quote calculations and management
 * Handles NASA API integration, geocoding, and quote persistence with offline support
 */
class QuotesViewModel(app: Application) : AndroidViewModel(app) {
    private val firebaseRepository = FirebaseRepository()
    private val offlineRepository = (app as SoloraApp).offlineRepository
    private val networkMonitor = (app as SoloraApp).networkMonitor
    private val nasa = NasaPowerClient()
    private val calculator = QuoteCalculator
    private val geocodingService = GeocodingService(app)
    private val settingsRepository = SettingsRepository()
    private val notificationManager = MotivationalNotificationManager(app)

    companion object {
        private const val TAG = "QuotesViewModel"
    }

    init {
        val currentUser = FirebaseAuth.getInstance().currentUser
        Log.d(TAG, "QuotesViewModel initialized for user: ${currentUser?.uid ?: "NOT LOGGED IN"}")
        if (currentUser == null) {
            Log.w(TAG, "WARNING: No user logged in! Quotes will be empty.")
        }
    }

    /**
     * Combined quotes flow - merges Firebase and local data
     * Shows local data immediately, then syncs with Firebase when online
     */
    val quotes = flow {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        Log.d(TAG, "Starting quotes flow for user: $userId")
        
        if (userId == null) {
            Log.w(TAG, "No user logged in")
            emit(emptyList())
            return@flow
        }
        
        // First emit local data for instant display
        val localQuotes = offlineRepository.getLocalQuotes()
        Log.d(TAG, "Loaded ${localQuotes.size} quotes from local database")
        emit(localQuotes.map { it.toFirebaseQuote() })
        
        // Then listen to Firebase for real-time updates
        firebaseRepository.getQuotes().collect { firebaseQuotes ->
            Log.d(TAG, "Received ${firebaseQuotes.size} quotes from Firebase")
            
            // Save Firebase quotes to local database for offline access
            val localQuotes = firebaseQuotes.map { it.toLocalQuote(synced = true) }
            offlineRepository.saveQuotesOffline(localQuotes)
            
            // Emit the Firebase quotes
            emit(firebaseQuotes)
        }
    }.stateIn(
        viewModelScope, 
        SharingStarted.WhileSubscribed(5000), 
        emptyList<FirebaseQuote>()
    )

    private val _lastQuote = MutableStateFlow<FirebaseQuote?>(null)
    val lastQuote: StateFlow<FirebaseQuote?> = _lastQuote.asStateFlow()

    private val _calculationState = MutableStateFlow<CalculationState>(CalculationState.Idle)
    val calculationState: StateFlow<CalculationState> = _calculationState.asStateFlow()
    
    private val _lastCalculation = MutableStateFlow<QuoteOutputs?>(null)
    val lastCalculation: StateFlow<QuoteOutputs?> = _lastCalculation.asStateFlow()

    /**
     * Calculate solar quote with NASA API integration
     * Geocodes address if coordinates not provided, fetches solar irradiance data,
     * and calculates system specifications
     */
    fun calculateAdvanced(
        reference: String,
        clientName: String,
        address: String,
        usageKwh: Double?,
        billRands: Double?,
        tariff: Double,
        panelWatt: Int,
        sunHours: Double = 5.0,
        latitude: Double? = null,
        longitude: Double? = null
    ) {
        viewModelScope.launch {
            _calculationState.value = CalculationState.Loading
            
            try {
                var finalLatitude = latitude
                var finalLongitude = longitude
                var finalAddress = address
                var finalSunHours = sunHours
                
                // Geocode address to get coordinates if not provided
                if (finalLatitude == null || finalLongitude == null) {
                    val geocodeResult = geocodingService.getCoordinatesFromAddress(address)
                    
                    if (geocodeResult.success) {
                        finalLatitude = geocodeResult.latitude
                        finalLongitude = geocodeResult.longitude
                        finalAddress = geocodeResult.address
                        // Geocoding successful: $finalLatitude, $finalLongitude
                    } else {
                        // Geocoding failed: ${geocodeResult.error}
                        // Continue with calculation without location data
                    }
                }
                
                // Fetch NASA solar irradiance data if coordinates available
                if (finalLatitude != null && finalLongitude != null) {
                    try {
                        val nasaDataResult = nasa.getSolarDataWithFallback(finalLatitude, finalLongitude)
                        if (nasaDataResult.isSuccess) {
                            val nasaData = nasaDataResult.getOrNull()
                            if (nasaData != null) {
                                finalSunHours = nasaData.averageAnnualSunHours
                                // NASA sun hours: $finalSunHours
                            }
                        } else {
                            // NASA API failed: ${nasaDataResult.exceptionOrNull()?.message}
                        }
                    } catch (e: Exception) {
                        // NASA API error: ${e.message}
                    }
                }
                
                val inputs = QuoteInputs(
                    monthlyUsageKwh = usageKwh,
                    monthlyBillRands = billRands,
                    tariffRPerKwh = tariff,
                    panelWatt = panelWatt,
                    sunHoursPerDay = finalSunHours,
                    location = if (finalLatitude != null && finalLongitude != null) {
                        dev.solora.quote.LocationInputs(
                            latitude = finalLatitude,
                            longitude = finalLongitude,
                            address = finalAddress
                        )
                    } else null
                )

                // Get calculation settings (tariffs, performance ratios, etc.)
                val settings = settingsRepository.settings.stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(5000),
                    dev.solora.settings.AppSettings()
                ).value.calculationSettings
                
                // Try server-side calculation first (includes NASA API), fallback to local
                val apiResult = firebaseRepository.calculateQuoteViaApi(
                    address = finalAddress,
                    usageKwh = usageKwh,
                    billRands = billRands,
                    tariff = tariff,
                    panelWatt = panelWatt,
                    latitude = finalLatitude,
                    longitude = finalLongitude
                )
                
                val result = if (apiResult.isSuccess) {
                    // Using API calculation result
                    apiResult
                } else {
                    // API calculation failed, using local calculation: ${apiResult.exceptionOrNull()?.message}
                    calculator.calculateAdvanced(inputs, nasa, settings)
                }
                result.fold(
                    onSuccess = { outputs ->
                        // Calculation successful: ${outputs.systemKw}kW system, ${outputs.panels} panels, R${outputs.monthlySavingsRands} savings
                        
                        // Debug NASA data
                        if (outputs.detailedAnalysis?.locationData != null) {
                            val nasaData = outputs.detailedAnalysis.locationData
                            // NASA data in calculation: irradiance=${nasaData.averageAnnualIrradiance}, sunHours=${nasaData.averageAnnualSunHours}
                        } else {
                            // WARNING: No NASA data in detailedAnalysis!
                        }
                        
                        _lastCalculation.value = outputs
                        _calculationState.value = CalculationState.Success(outputs)
                    },
                    onFailure = { error ->
                        // Calculation failed: ${error.message}
                        _calculationState.value = CalculationState.Error(error.message ?: "Calculation failed")
                    }
                )
                
            } catch (e: Exception) {
                // ("QuotesViewModel", "Exception during calculation: ${e.message}", e)
                _calculationState.value = CalculationState.Error(e.message ?: "Calculation failed")
            }
        }
    }

    // Save quote to Firebase (simplified version - prefer saveQuoteFromCalculation)
    fun saveQuote(
        reference: String,
        clientName: String,
        address: String,
        usageKwh: Double?,
        billRands: Double?,
        tariff: Double,
        panelWatt: Int,
        systemKwp: Double,
        estimatedGeneration: Double,
        paybackMonths: Int,
        monthlySavings: Double
    ) {
        viewModelScope.launch {
            try {
                val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                val quoteId = UUID.randomUUID().toString()
                
                val quote = FirebaseQuote(
                    id = quoteId,
                    reference = reference,
                    clientName = clientName,
                    address = address,
                    usageKwh = usageKwh,
                    billRands = billRands,
                    tariff = tariff,
                    panelWatt = panelWatt,
                    systemKwp = systemKwp,
                    estimatedGeneration = estimatedGeneration,
                    paybackMonths = paybackMonths,
                    monthlySavings = monthlySavings,
                    userId = userId
                )

                // Check if online
                val isOnline = networkMonitor.isCurrentlyConnected()
                Log.d(TAG, "Saving quote, online status: $isOnline")
                
                if (isOnline) {
                    // Try to save to Firebase first
                    val result = firebaseRepository.saveQuote(quote)
                    if (result.isSuccess) {
                        Log.d(TAG, "Quote saved to Firebase successfully")
                        _lastQuote.value = quote.copy(id = result.getOrNull())
                        // Also save to local database (marked as synced)
                        offlineRepository.saveQuoteOffline(quote.toLocalQuote(synced = true))
                        notificationManager.checkAndSendMotivationalMessage()
                    } else {
                        Log.e(TAG, "Failed to save quote to Firebase: ${result.exceptionOrNull()?.message}")
                        // Save locally as unsynced
                        offlineRepository.saveQuoteOffline(quote.toLocalQuote(synced = false))
                        _lastQuote.value = quote
                    }
                } else {
                    // Offline - save to local database only (unsynced)
                    Log.d(TAG, "Offline - saving quote to local database")
                    offlineRepository.saveQuoteOffline(quote.toLocalQuote(synced = false))
                    _lastQuote.value = quote
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving quote: ${e.message}", e)
            }
        }
    }

    /**
     * Save quote to database from calculation results
     * Includes NASA data, location info, and company settings snapshot
     */
    fun saveQuoteFromCalculation(
        reference: String,
        clientName: String,
        address: String,
        calculation: QuoteOutputs
    ) {
        // Clear previous quote to allow new save
        _lastQuote.value = null
        
        viewModelScope.launch {
            try {
                // Get company settings snapshot
                val companySettings = settingsRepository.settings.stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(5000),
                    dev.solora.settings.AppSettings()
                ).value.companySettings
                
                // Saving quote with panelWatt=${calculation.panelWatt}W (from calculation)
                
                // Debug NASA data before saving
                val nasaData = calculation.detailedAnalysis?.locationData
                // NASA data for saving: irradiance=${nasaData?.averageAnnualIrradiance}, sunHours=${nasaData?.averageAnnualSunHours}
                // detailedAnalysis is null: ${calculation.detailedAnalysis == null}
                // locationData is null: ${nasaData == null}
                
                val quote = FirebaseQuote(
            reference = reference,
            clientName = clientName,
            address = address,
                    // Input data
                    usageKwh = calculation.monthlyUsageKwh,
                    billRands = calculation.monthlyBillRands,
                    tariff = calculation.tariffRPerKwh,
                    panelWatt = calculation.panelWatt,
                    // Location data
                    latitude = calculation.detailedAnalysis?.locationData?.latitude,
                    longitude = calculation.detailedAnalysis?.locationData?.longitude,
                    // NASA API solar data
                    averageAnnualIrradiance = calculation.detailedAnalysis?.locationData?.averageAnnualIrradiance,
                    averageAnnualSunHours = calculation.detailedAnalysis?.locationData?.averageAnnualSunHours,
                    // Calculation results
                    systemKwp = calculation.systemKw,
                    estimatedGeneration = calculation.estimatedMonthlyGeneration,
                    monthlySavings = calculation.monthlySavingsRands,
                    paybackMonths = calculation.paybackMonths,
                    // Company information (snapshot at time of quote)
                    companyName = companySettings.companyName,
                    companyPhone = companySettings.companyPhone,
                    companyEmail = companySettings.companyEmail,
                    consultantName = companySettings.consultantName,
                    consultantPhone = companySettings.consultantPhone,
                    consultantEmail = companySettings.consultantEmail,
                    // Metadata
                    userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                )

                val result = firebaseRepository.saveQuote(quote)
                if (result.isSuccess) {
                    val savedQuote = quote.copy(id = result.getOrNull())
                    _lastQuote.value = savedQuote
                    viewModelScope.launch {
                        notificationManager.checkAndSendMotivationalMessage()
                    }
                }
            } catch (e: Exception) {
                // ("QuotesViewModel", "Exception saving quote: ${e.message}", e)
            }
        }
    }

    // Get quote by ID
    suspend fun getQuoteById(quoteId: String): dev.solora.data.FirebaseQuote? {
        return try {
            val result = firebaseRepository.getQuoteById(quoteId)
            if (result.isSuccess) {
                val quote = result.getOrNull()
                _lastQuote.value = quote
                quote
            } else {
                // Error getting quote by ID: ${result.exceptionOrNull()?.message}
                null
            }
        } catch (e: Exception) {
            // ("QuotesViewModel", "Exception getting quote by ID: ${e.message}", e)
            null
        }
    }

    // Update quote
    fun updateQuote(quoteId: String, quote: FirebaseQuote) {
        viewModelScope.launch {
            firebaseRepository.updateQuote(quoteId, quote)
        }
    }

    // Delete quote
    fun deleteQuote(quoteId: String) {
        viewModelScope.launch {
            firebaseRepository.deleteQuote(quoteId)
        }
    }

    // Clear calculation state
    fun clearCalculationState() {
        _calculationState.value = CalculationState.Idle
    }
    
    // Clear last quote
    fun clearLastQuote() {
        _lastQuote.value = null
    }
    
    fun clearLastCalculation() {
        _lastCalculation.value = null
    }
    
    // Synchronous version for immediate feedback
    suspend fun saveQuoteFromCalculationSync(
        reference: String,
        clientName: String,
        address: String,
        calculation: QuoteOutputs
    ): Result<FirebaseQuote> {
        return try {
            // Get company settings snapshot
            val companySettings = settingsRepository.settings.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                dev.solora.settings.AppSettings()
            ).value.companySettings
            
            val quote = FirebaseQuote(
                reference = reference,
                clientName = clientName,
                address = address,
                // Input data
                usageKwh = calculation.monthlyUsageKwh,
                billRands = calculation.monthlyBillRands,
                tariff = calculation.tariffRPerKwh,
                panelWatt = calculation.panelWatt,
                // Location data
                latitude = calculation.detailedAnalysis?.locationData?.latitude,
                longitude = calculation.detailedAnalysis?.locationData?.longitude,
                // NASA API solar data
                averageAnnualIrradiance = calculation.detailedAnalysis?.locationData?.averageAnnualIrradiance,
                averageAnnualSunHours = calculation.detailedAnalysis?.locationData?.averageAnnualSunHours,
                // Calculation results
                systemKwp = calculation.systemKw,
                estimatedGeneration = calculation.estimatedMonthlyGeneration,
                monthlySavings = calculation.monthlySavingsRands,
                paybackMonths = calculation.paybackMonths,
                // Company information (snapshot at time of quote)
                companyName = companySettings.companyName,
                companyPhone = companySettings.companyPhone,
                companyEmail = companySettings.companyEmail,
                consultantName = companySettings.consultantName,
                consultantPhone = companySettings.consultantPhone,
                consultantEmail = companySettings.consultantEmail,
                // Metadata
                userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
            )

            val result = firebaseRepository.saveQuote(quote)
            if (result.isSuccess) {
                val savedQuote = quote.copy(id = result.getOrNull())
                _lastQuote.value = savedQuote
                viewModelScope.launch {
                    notificationManager.checkAndSendMotivationalMessage()
                }
                Result.success(savedQuote)
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Failed to save quote"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

sealed class CalculationState {
    object Idle : CalculationState()
    object Loading : CalculationState()
    data class Success(val outputs: QuoteOutputs) : CalculationState()
    data class Error(val message: String) : CalculationState()
}
