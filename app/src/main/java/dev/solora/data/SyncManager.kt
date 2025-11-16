package dev.solora.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncManager(
    private val localDb: LocalDatabase,
    private val firebase: FirebaseRepository
) {
    private val dao = localDb.dao()

    suspend fun syncAll() = withContext(Dispatchers.IO) {

        // Sync Quotes
        val unsyncedQuotes = dao.getUnsyncedQuotes()
        unsyncedQuotes.forEach { local ->

            val firebaseQuote = FirebaseQuote(
                id = local.id,
                reference = local.reference,
                clientName = local.clientName,
                address = local.address,
                usageKwh = local.usageKwh,
                billRands = local.billRands,
                tariff = local.tariff,
                panelWatt = local.panelWatt,
                latitude = local.latitude,
                longitude = local.longitude,
                averageAnnualIrradiance = local.averageAnnualIrradiance,
                averageAnnualSunHours = local.averageAnnualSunHours,
                systemKwp = local.systemKwp,
                estimatedGeneration = local.estimatedGeneration,
                monthlySavings = local.monthlySavings,
                paybackMonths = local.paybackMonths,
                userId = local.userId
            )

            val result = firebase.saveQuote(firebaseQuote)
            if (result.isSuccess) {
                dao.insertQuote(local.copy(synced = true))
            }
        }

        // Sync Leads
        val unsyncedLeads = dao.getUnsyncedLeads()
        unsyncedLeads.forEach { local ->

            val firebaseLead = FirebaseLead(
                id = local.id,
                quoteId = local.quoteId,
                userId = local.userId
            )

            val result = firebase.saveLead(firebaseLead)
            if (result.isSuccess) {
                dao.insertLead(local.copy(synced = true))
            }
        }
    }
}