package com.balfouriana.repository

import com.balfouriana.domain.RegulatoryRegime
import java.time.Instant
import java.util.UUID

data class PersistedEventRecord(
    val eventId: UUID,
    val correlationId: UUID,
    val eventType: String,
    val sourceSystem: String,
    val schemaVersion: String,
    val regimes: Set<RegulatoryRegime>,
    val occurredAt: Instant,
    val payload: String,
    val createdAt: Instant
)
