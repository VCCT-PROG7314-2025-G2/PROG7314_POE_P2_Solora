package dev.solora.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quotes")
data class LocalQuote(
    @PrimaryKey
    val id: String,
    val reference: String,
    val clientName: String,
    val address: String,
    val usageKwh: Double?,
    val billRands: Double?,
    val tariff: Double,
    val panelWatt: Int,
    val latitude: Double?,
    val longitude: Double?,
    val averageAnnualIrradiance: Double?,
    val averageAnnualSunHours: Double?,
    val systemKwp: Double,
    val estimatedGeneration: Double,
    val monthlySavings: Double,
    val paybackMonths: Int,
    val companyName: String = "",
    val companyPhone: String = "",
    val companyEmail: String = "",
    val consultantName: String = "",
    val consultantPhone: String = "",
    val consultantEmail: String = "",
    val userId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val synced: Boolean = false
)

@Entity(tableName = "leads")
data class LocalLead(
    @PrimaryKey
    val id: String,
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val status: String = "new",
    val notes: String? = null,
    val quoteId: String? = null,
    val userId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val synced: Boolean = false
)