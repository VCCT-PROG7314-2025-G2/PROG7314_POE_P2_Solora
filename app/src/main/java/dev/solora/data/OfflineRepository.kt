package dev.solora.data

import java.util.UUID

class OfflineRepository(
    private val localDb: LocalDatabase,
    private val firebase: FirebaseRepository
) {

    private val dao = localDb.dao()

    // Quotes
    suspend fun saveQuoteOffline(quote: FirebaseQuote) {
        val localId = quote.id ?: UUID.randomUUID().toString()

        val localQuote = LocalQuote(
            id = localId,
            reference = quote.reference,
            clientName = quote.clientName,
            address = quote.address,
            usageKwh = quote.usageKwh,
            billRands = quote.billRands,
            tariff = quote.tariff,
            panelWatt = quote.panelWatt,
            latitude = quote.latitude,
            longitude = quote.longitude,
            averageAnnualIrradiance = quote.averageAnnualIrradiance,
            averageAnnualSunHours = quote.averageAnnualSunHours,
            systemKwp = quote.systemKwp,
            estimatedGeneration = quote.estimatedGeneration,
            monthlySavings = quote.monthlySavings,
            paybackMonths = quote.paybackMonths,
            userId = quote.userId,
            synced = false
        )

        dao.insertQuote(localQuote)
    }

    suspend fun getUnsyncedLeads(): List<LocalLead> {
        return dao.getLeadsWhereSynced(false)
    }

    suspend fun markLeadAsSynced(id: String) {
        dao.updateLeadSynced(id, true)
    }

    suspend fun getLocalQuotes(): List<LocalQuote> = dao.getAllQuotes()

    // Leads
    suspend fun saveLeadOffline(lead: LocalLead) {
        dao.insertLead(lead)
    }

    suspend fun getLocalLeads(): List<LocalLead> = dao.getAllLeads()
}