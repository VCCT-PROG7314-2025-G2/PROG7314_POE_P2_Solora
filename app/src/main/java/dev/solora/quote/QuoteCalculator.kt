package dev.solora.quote

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import dev.solora.quote.NasaPowerClient.LocationData

data class QuoteInputs(
    val monthlyUsageKwh: Double?,
    val monthlyBillRands: Double?,
    val tariffRPerKwh: Double,
    val panelWatt: Int,
    val sunHoursPerDay: Double,
    val location: LocationInputs? = null,
    val preferences: CustomerPreferences? = null
)

data class LocationInputs(
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val roofArea: Double? = null,
    val roofTilt: Double? = null,
    val roofAzimuth: Double? = null // degrees from south
)

data class CustomerPreferences(
    val maxBudget: Double? = null,
    val targetPaybackYears: Int? = null,
    val environmentalPriority: Boolean = false,
    val preferredBrands: List<String> = emptyList()
)

data class QuoteOutputs(
    val panels: Int,
    val systemKw: Double,
    val inverterKw: Double,
    val estimatedMonthlySavingsR: Double,
    val detailedAnalysis: DetailedAnalysis? = null,
    // Additional properties for Firebase storage
    val monthlyUsageKwh: Double? = null,
    val monthlyBillRands: Double? = null,
    val tariffRPerKwh: Double = 0.0,
    val panelWatt: Int = 0,
    val sunHoursPerDay: Double = 0.0,
    val estimatedMonthlyGeneration: Double = 0.0,
    val paybackMonths: Int = 0,
    val monthlySavingsRands: Double = 0.0
) : java.io.Serializable

data class DetailedAnalysis(
    val monthlyGeneration: Map<Int, Double>, // kWh per month
    val seasonalPerformance: Map<String, Double>, // percentage of annual generation
    val financialProjection: FinancialProjection,
    val environmentalImpact: EnvironmentalImpact,
    val systemOptimization: SystemOptimization,
    // NASA API data
    val locationData: LocationData? = null,
    val optimalMonth: Int? = null,
    val optimalMonthIrradiance: Double? = null,
    val averageTemperature: Double? = null,
    val averageWindSpeed: Double? = null,
    val averageHumidity: Double? = null
) : java.io.Serializable

data class FinancialProjection(
    val installationCost: Double,
    val annualSavings: Double,
    val paybackPeriodYears: Double,
    val totalLifetimeSavings: Double, // 25 years
    val yearlyProjections: List<YearlyProjection>
) : java.io.Serializable

data class YearlyProjection(
    val year: Int,
    val generation: Double,
    val savings: Double,
    val cumulativeSavings: Double
) : java.io.Serializable

data class EnvironmentalImpact(
    val co2ReductionKgPerYear: Double,
    val treesEquivalent: Int,
    val coalEquivalentKg: Double
) : java.io.Serializable

data class SystemOptimization(
    val efficiencyRating: String, // A+, A, B+, B, C
    val performanceRatio: Double, // 0-1
    val recommendations: List<String>
) : java.io.Serializable

/**
 * Solar system calculator with basic and advanced modes
 * Calculates panel requirements, costs, payback period, and environmental impact
 */
object QuoteCalculator {
    
    private const val PANEL_DEGRADATION_RATE = 0.005 // 0.5% per year
    private const val SYSTEM_LIFETIME_YEARS = 25
    private const val CO2_PER_KWH = 0.928 // kg CO2 per kWh in South Africa
    private const val INSTALLATION_COST_PER_KW = 15000.0 // R15,000 per kW estimated
    
    // This is the main calculation function that does everything
    // It can use NASA data to get accurate sun hours for the location
    suspend fun calculateAdvanced(
        inputs: QuoteInputs,
        nasaClient: NasaPowerClient? = null,
        settings: dev.solora.settings.CalculationSettings? = null
    ): Result<QuoteOutputs> {
        return try {
            // Basic calculation
            val basicOutputs = calculateBasic(inputs, settings)
            
            // Enhanced calculation with NASA data if location provided
            val detailedAnalysis = if (inputs.location != null && nasaClient != null) {
                // Calling calculateDetailedAnalysis for location: ${inputs.location.latitude}, ${inputs.location.longitude}
                val result = calculateDetailedAnalysis(inputs, basicOutputs, nasaClient, settings)
                // calculateDetailedAnalysis result: ${result != null}
                result
            } else {
                // Skipping calculateDetailedAnalysis: location=${inputs.location != null}, nasaClient=${nasaClient != null}
                null
            }
            
            Result.success(basicOutputs.copy(detailedAnalysis = detailedAnalysis))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // This does the basic calculation without NASA data
    // It just uses the sun hours you provide
    fun calculateBasic(inputs: QuoteInputs, settings: dev.solora.settings.CalculationSettings? = null): QuoteOutputs {
        val usageKwh = inputs.monthlyUsageKwh ?: run {
            val bill = inputs.monthlyBillRands ?: 0.0
            if (bill <= 0) 0.0 else bill / inputs.tariffRPerKwh
        }

        val averageDailyKwh = usageKwh / 30.0
        val systemKw = if (inputs.sunHoursPerDay <= 0) 0.0 else averageDailyKwh / inputs.sunHoursPerDay
        val panelKw = inputs.panelWatt / 1000.0
        val panels = if (panelKw <= 0) 0 else ceil(systemKw / panelKw).toInt()
        val inverterRatio = settings?.inverterSizingRatio ?: 0.8
        val inverterKw = (systemKw * inverterRatio).coerceAtLeast(1.0)
        val performanceRatio = settings?.performanceRatio ?: 0.8
        val savings = usageKwh * inputs.tariffRPerKwh * performanceRatio
        
        // Calculate additional metrics
        val estimatedMonthlyGeneration = systemKw * inputs.sunHoursPerDay * 30
        val installationCostPerKw = settings?.installationCostPerKw ?: 15000.0
        val installationCost = systemKw * installationCostPerKw
        val paybackMonths = if (savings > 0) (installationCost / savings).toInt() else 0
        
        return QuoteOutputs(
            panels = panels, 
            systemKw = round2(systemKw), 
            inverterKw = round2(inverterKw), 
            estimatedMonthlySavingsR = round2(savings),
            // Additional properties for Firebase storage
            monthlyUsageKwh = usageKwh,
            monthlyBillRands = inputs.monthlyBillRands,
            tariffRPerKwh = inputs.tariffRPerKwh,
            panelWatt = inputs.panelWatt,
            sunHoursPerDay = inputs.sunHoursPerDay,
            estimatedMonthlyGeneration = round2(estimatedMonthlyGeneration),
            paybackMonths = paybackMonths,
            monthlySavingsRands = round2(savings)
        )
    }
    
    private suspend fun calculateDetailedAnalysis(
        inputs: QuoteInputs,
        basicOutputs: QuoteOutputs,
        nasaClient: NasaPowerClient,
        settings: dev.solora.settings.CalculationSettings? = null
    ): DetailedAnalysis? {
        val location = inputs.location ?: return null
        
        // Getting NASA data for lat=${location.latitude}, lon=${location.longitude}
        
        // Get NASA solar data with fallback
        val nasaDataResult = nasaClient.getSolarDataWithFallback(location.latitude, location.longitude)
        if (nasaDataResult.isFailure) {
            // NASA API failed even with fallback: ${nasaDataResult.exceptionOrNull()?.message}
            return null
        }
        
        val nasaData = nasaDataResult.getOrNull() ?: return null
        // NASA data retrieved: irradiance=${nasaData.averageAnnualIrradiance}, sunHours=${nasaData.averageAnnualSunHours}
        
        // Calculate monthly generation
        val monthlyGeneration = mutableMapOf<Int, Double>()
        var totalAnnualGeneration = 0.0
        
        for (month in 1..12) {
            val monthData = nasaData.monthlyData[month] ?: continue
            val daysInMonth = getDaysInMonth(month)
            val monthlyGen = basicOutputs.systemKw * monthData.estimatedSunHours * daysInMonth * getPerformanceFactor(month, location)
            monthlyGeneration[month] = round2(monthlyGen)
            totalAnnualGeneration += monthlyGen
        }
        
        // Calculate seasonal performance
        val seasonalPerformance = mapOf(
            "Spring" to round2((monthlyGeneration[9]!! + monthlyGeneration[10]!! + monthlyGeneration[11]!!) / totalAnnualGeneration * 100),
            "Summer" to round2((monthlyGeneration[12]!! + monthlyGeneration[1]!! + monthlyGeneration[2]!!) / totalAnnualGeneration * 100),
            "Autumn" to round2((monthlyGeneration[3]!! + monthlyGeneration[4]!! + monthlyGeneration[5]!!) / totalAnnualGeneration * 100),
            "Winter" to round2((monthlyGeneration[6]!! + monthlyGeneration[7]!! + monthlyGeneration[8]!!) / totalAnnualGeneration * 100)
        )
        
        // Financial projection
        val installationCost = basicOutputs.systemKw * INSTALLATION_COST_PER_KW
        val firstYearSavings = totalAnnualGeneration * inputs.tariffRPerKwh
        val paybackPeriod = if (firstYearSavings > 0) installationCost / firstYearSavings else 0.0
        
        val yearlyProjections = mutableListOf<YearlyProjection>()
        var cumulativeSavings = 0.0
        
        for (year in 1..SYSTEM_LIFETIME_YEARS) {
            val degradationFactor = 1.0 - (PANEL_DEGRADATION_RATE * (year - 1))
            val yearGeneration = totalAnnualGeneration * degradationFactor
            val yearSavings = yearGeneration * inputs.tariffRPerKwh
            cumulativeSavings += yearSavings
            
            yearlyProjections.add(YearlyProjection(
                year = year,
                generation = round2(yearGeneration),
                savings = round2(yearSavings),
                cumulativeSavings = round2(cumulativeSavings)
            ))
        }
        
        val financialProjection = FinancialProjection(
            installationCost = round2(installationCost),
            annualSavings = round2(firstYearSavings),
            paybackPeriodYears = round2(paybackPeriod),
            totalLifetimeSavings = round2(cumulativeSavings - installationCost),
            yearlyProjections = yearlyProjections
        )
        
        // Environmental impact
        val annualCO2Reduction = totalAnnualGeneration * CO2_PER_KWH
        val environmentalImpact = EnvironmentalImpact(
            co2ReductionKgPerYear = round2(annualCO2Reduction),
            treesEquivalent = (annualCO2Reduction / 21.8).toInt(), // 21.8 kg CO2 per tree per year
            coalEquivalentKg = round2(annualCO2Reduction / 2.23) // 2.23 kg CO2 per kg coal
        )
        
        // System optimization
        val performanceRatio = calculatePerformanceRatio(basicOutputs, nasaData, location)
        val recommendations = generateRecommendations(performanceRatio, location, inputs.preferences)
        val efficiencyRating = getEfficiencyRating(performanceRatio)
        
        val systemOptimization = SystemOptimization(
            efficiencyRating = efficiencyRating,
            performanceRatio = round2(performanceRatio),
            recommendations = recommendations
        )
        
        // Calculate NASA data averages
        val averageTemperature = nasaData.monthlyData.values.mapNotNull { it.temperature }.average().let { if (it.isNaN()) null else it }
        val averageWindSpeed = nasaData.monthlyData.values.mapNotNull { it.windSpeed }.average().let { if (it.isNaN()) null else it }
        val averageHumidity = nasaData.monthlyData.values.mapNotNull { it.humidity }.average().let { if (it.isNaN()) null else it }
        
        // Find optimal month
        val optimalMonthEntry = nasaData.monthlyData.maxByOrNull { it.value.solarIrradiance }
        val optimalMonth = optimalMonthEntry?.key
        val optimalMonthIrradiance = optimalMonthEntry?.value?.solarIrradiance
        
        return DetailedAnalysis(
            monthlyGeneration = monthlyGeneration,
            seasonalPerformance = seasonalPerformance,
            financialProjection = financialProjection,
            environmentalImpact = environmentalImpact,
            systemOptimization = systemOptimization,
            // NASA API data
            locationData = nasaData,
            optimalMonth = optimalMonth,
            optimalMonthIrradiance = optimalMonthIrradiance,
            averageTemperature = averageTemperature,
            averageWindSpeed = averageWindSpeed,
            averageHumidity = averageHumidity
        )
    }
    
    private fun getDaysInMonth(month: Int): Int {
        return when (month) {
            2 -> 28 // Simplification, not accounting for leap years
            4, 6, 9, 11 -> 30
            else -> 31
        }
    }
    
    private fun getPerformanceFactor(month: Int, location: LocationInputs): Double {
        // Adjust for roof tilt and azimuth
        val tiltFactor = when {
            location.roofTilt == null -> 1.0
            location.roofTilt < 10 -> 0.92
            location.roofTilt in 10.0..35.0 -> 1.0
            location.roofTilt in 35.0..45.0 -> 0.98
            else -> 0.85
        }
        
        val azimuthFactor = when {
            location.roofAzimuth == null -> 1.0 // Assume optimal
            kotlin.math.abs(location.roofAzimuth) <= 15 -> 1.0 // Perfect south
            kotlin.math.abs(location.roofAzimuth) <= 45 -> 0.95
            kotlin.math.abs(location.roofAzimuth) <= 90 -> 0.85
            else -> 0.7
        }
        
        // Seasonal temperature derating (South African climate)
        val temperatureFactor = when (month) {
            12, 1, 2 -> 0.88 // Hot summer months
            3, 4, 5, 9, 10, 11 -> 0.95 // Mild months
            else -> 1.0 // Cool winter months
        }
        
        return tiltFactor * azimuthFactor * temperatureFactor * 0.85 // Overall system efficiency
    }
    
    private fun calculatePerformanceRatio(
        outputs: QuoteOutputs,
        nasaData: NasaPowerClient.LocationData,
        location: LocationInputs
    ): Double {
        val theoreticalGeneration = outputs.systemKw * nasaData.averageAnnualSunHours * 365
        val actualGeneration = theoreticalGeneration * getPerformanceFactor(6, location) // Use winter month as baseline
        return actualGeneration / theoreticalGeneration
    }
    
    private fun generateRecommendations(
        performanceRatio: Double,
        location: LocationInputs,
        preferences: CustomerPreferences?
    ): List<String> {
        val recommendations = mutableListOf<String>()
        
        if (performanceRatio < 0.75) {
            recommendations.add("Consider optimizing roof tilt angle to ${location.latitude.toInt()}° for better performance")
        }
        
        if (location.roofAzimuth != null && kotlin.math.abs(location.roofAzimuth) > 45) {
            recommendations.add("Roof orientation is not optimal. Consider adjusting panel placement if possible")
        }
        
        if (preferences?.environmentalPriority == true) {
            recommendations.add("Consider adding a battery system to maximize renewable energy usage")
        }
        
        if (preferences?.maxBudget != null) {
            recommendations.add("System sized within budget constraints. Consider phased installation for future expansion")
        }
        
        recommendations.add("Regular cleaning and maintenance will ensure optimal performance")
        recommendations.add("Monitor system performance monthly to detect any issues early")
        
        return recommendations
    }
    
    private fun getEfficiencyRating(performanceRatio: Double): String {
        return when {
            performanceRatio >= 0.85 -> "A+"
            performanceRatio >= 0.80 -> "A"
            performanceRatio >= 0.75 -> "B+"
            performanceRatio >= 0.70 -> "B"
            else -> "C"
        }
    }

    private fun round2(x: Double): Double = kotlin.math.round(x * 100.0) / 100.0
}


