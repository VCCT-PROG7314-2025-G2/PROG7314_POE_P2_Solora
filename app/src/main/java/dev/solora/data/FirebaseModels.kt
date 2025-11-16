package dev.solora.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

// These are the data models that store information in Firebase
// They define what data we save for quotes, leads, and users

// This stores all the information about a solar quote
data class FirebaseQuote(
    @DocumentId
    val id: String? = null,
    val reference: String = "",
    val clientName: String = "",
    val address: String = "",
    // Input data
    val usageKwh: Double? = null,
    val billRands: Double? = null,
    val tariff: Double = 0.0,
    val panelWatt: Int = 0,
    // Location data
    val latitude: Double? = null,
    val longitude: Double? = null,
    // NASA API solar data
    val averageAnnualIrradiance: Double? = null,
    val averageAnnualSunHours: Double? = null,
    // Calculation results
    val systemKwp: Double = 0.0,
    val estimatedGeneration: Double = 0.0,
    val monthlySavings: Double = 0.0,
    val paybackMonths: Int = 0,
    // Company information (snapshot at time of quote creation)
    val companyName: String = "",
    val companyPhone: String = "",
    val companyEmail: String = "",
    val consultantName: String = "",
    val consultantPhone: String = "",
    val consultantEmail: String = "",
    // Metadata
    val userId: String = "", // Link to consultant
    @ServerTimestamp
    val createdAt: Timestamp? = null,
    @ServerTimestamp
    val updatedAt: Timestamp? = null
)

// This stores information about potential customers (leads)
data class FirebaseLead(
    @DocumentId
    val id: String? = null,
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val status: String = "new",
    val notes: String? = null,
    val quoteId: String? = null, // Link to quote if applicable
    val userId: String = "", // Link to Firebase Auth user
    val followUpDate: Timestamp? = null, // Optional follow-up date
    @ServerTimestamp
    val createdAt: Timestamp? = null,
    @ServerTimestamp
    val updatedAt: Timestamp? = null
)

// This stores information about the app users (consultants)
data class FirebaseUser(
    @DocumentId
    val id: String? = null,
    val name: String = "",
    val surname: String = "",
    val email: String = "",
    val phone: String? = null,
    val company: String? = null,
    val role: String = "sales_consultant",
    @ServerTimestamp
    val createdAt: Timestamp? = null,
    @ServerTimestamp
    val updatedAt: Timestamp? = null
)

data class FirebaseConfiguration(
    @DocumentId
    val id: String? = null,
    val nasaApiEnabled: Boolean = true,
    val nasaApiUrl: String = "https://power.larc.nasa.gov/api/",
    val defaultTariff: Double = 2.50,
    val defaultPanelWatt: Int = 450,
    val companyInfo: CompanyInfo = CompanyInfo(),
    @ServerTimestamp
    val updatedAt: Timestamp? = null
)

data class CompanyInfo(
    val name: String = "Solora Solar",
    val contactEmail: String = "info@solora.dev",
    val contactPhone: String = "+27 11 123 4567",
    val address: String = "Johannesburg, South Africa"
)
