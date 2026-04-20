package com.balfouriana.repository

import com.balfouriana.domain.DomainEvent
import java.time.Instant
import java.util.UUID

interface EventStoreRepository {
    fun append(event: DomainEvent)
    fun findByCorrelationId(correlationId: UUID): List<PersistedEventRecord>
    fun findByOccurredAtBetween(startInclusive: Instant, endExclusive: Instant): List<PersistedEventRecord>
}
