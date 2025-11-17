package dev.solora.data

import com.google.firebase.Timestamp
import java.util.Date
import java.util.UUID

/**
 * Extension functions to convert between local and Firebase entities
 */

// ============================================
// QUOTE CONVERTERS
// ============================================

fun LocalQuote.toFirebaseQuote(): FirebaseQuote {
    return FirebaseQuote(
        id = this.id,
        reference = this.reference,
        clientName = this.clientName,
        address = this.address,
        usageKwh = this.usageKwh,
        billRands = this.billRands,
        tariff = this.tariff,
        panelWatt = this.panelWatt,
        latitude = this.latitude,
        longitude = this.longitude,
        averageAnnualIrradiance = this.averageAnnualIrradiance,
        averageAnnualSunHours = this.averageAnnualSunHours,
        systemKwp = this.systemKwp,
        estimatedGeneration = this.estimatedGeneration,
        monthlySavings = this.monthlySavings,
        paybackMonths = this.paybackMonths,
        companyName = this.companyName,
        companyPhone = this.companyPhone,
        companyEmail = this.companyEmail,
        consultantName = this.consultantName,
        consultantPhone = this.consultantPhone,
        consultantEmail = this.consultantEmail,
        userId = this.userId,
        createdAt = Timestamp(Date(this.createdAt)),
        updatedAt = Timestamp(Date(this.updatedAt))
    )
}

fun FirebaseQuote.toLocalQuote(synced: Boolean = true): LocalQuote {
    return LocalQuote(
        id = this.id ?: UUID.randomUUID().toString(),
        reference = this.reference,
        clientName = this.clientName,
        address = this.address,
        usageKwh = this.usageKwh,
        billRands = this.billRands,
        tariff = this.tariff,
        panelWatt = this.panelWatt,
        latitude = this.latitude,
        longitude = this.longitude,
        averageAnnualIrradiance = this.averageAnnualIrradiance,
        averageAnnualSunHours = this.averageAnnualSunHours,
        systemKwp = this.systemKwp,
        estimatedGeneration = this.estimatedGeneration,
        monthlySavings = this.monthlySavings,
        paybackMonths = this.paybackMonths,
        companyName = this.companyName,
        companyPhone = this.companyPhone,
        companyEmail = this.companyEmail,
        consultantName = this.consultantName,
        consultantPhone = this.consultantPhone,
        consultantEmail = this.consultantEmail,
        userId = this.userId,
        createdAt = this.createdAt?.toDate()?.time ?: System.currentTimeMillis(),
        updatedAt = this.updatedAt?.toDate()?.time ?: System.currentTimeMillis(),
        synced = synced
    )
}

// ============================================
// LEAD CONVERTERS
// ============================================

fun LocalLead.toFirebaseLead(): FirebaseLead {
    return FirebaseLead(
        id = this.id,
        name = this.name,
        email = this.email,
        phone = this.phone,
        status = this.status,
        notes = this.notes,
        quoteId = this.quoteId,
        userId = this.userId,
        followUpDate = this.followUpDate?.let { Timestamp(Date(it)) },
        createdAt = Timestamp(Date(this.createdAt)),
        updatedAt = Timestamp(Date(this.updatedAt))
    )
}

fun FirebaseLead.toLocalLead(synced: Boolean = true): LocalLead {
    return LocalLead(
        id = this.id ?: UUID.randomUUID().toString(),
        name = this.name,
        email = this.email,
        phone = this.phone,
        status = this.status,
        notes = this.notes,
        quoteId = this.quoteId,
        userId = this.userId,
        followUpDate = this.followUpDate?.toDate()?.time,
        createdAt = this.createdAt?.toDate()?.time ?: System.currentTimeMillis(),
        updatedAt = this.updatedAt?.toDate()?.time ?: System.currentTimeMillis(),
        synced = synced
    )
}

