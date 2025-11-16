package dev.solora.data

import com.google.firebase.Timestamp
import java.util.Date
import java.util.UUID

// Offline to Online
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
        createdAt = Timestamp(Date(this.createdAt)),
        updatedAt = Timestamp(Date(this.updatedAt))
    )
}

// Online to Offline
fun FirebaseLead.toLocalLead(): LocalLead {
    return LocalLead(
        id = this.id ?: UUID.randomUUID().toString(),
        name = this.name,
        email = this.email,
        phone = this.phone,
        status = this.status,
        notes = this.notes,
        quoteId = this.quoteId,
        userId = this.userId,
        createdAt = this.createdAt?.toDate()?.time ?: System.currentTimeMillis(),
        updatedAt = this.updatedAt?.toDate()?.time ?: System.currentTimeMillis(),
        synced = false
    )
}