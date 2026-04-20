package com.balfouriana.domain

import java.time.Instant
import java.util.UUID

data class EventMetadata(
    val eventId: UUID,
    val correlationId: UUID,
    val sourceSystem: String,
    val occurredAt: Instant,
    val schemaVersion: String,
    val regimes: Set<RegulatoryRegime>
)
