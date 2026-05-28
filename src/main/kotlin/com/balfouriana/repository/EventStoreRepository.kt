package com.balfouriana.repository

import com.balfouriana.domain.DomainEvent
import com.balfouriana.domain.ExceptionResolvedEvent
import com.balfouriana.domain.FilingAcknowledgementStatus
import com.balfouriana.domain.FilingSubmittedEvent
import java.time.Instant
import java.util.UUID

interface EventStoreRepository {
    fun append(event: DomainEvent)
    fun findByCorrelationId(correlationId: UUID): List<PersistedEventRecord>
    fun findByOccurredAtBetween(startInclusive: Instant, endExclusive: Instant): List<PersistedEventRecord>
    fun findByEventType(eventType: String): List<PersistedEventRecord>
    fun hasSuccessfulSubmission(
        correlationId: UUID,
        outputChecksumSha256: String,
        filingTemplateVersion: String
    ): Boolean
    fun findFilingSubmittedBySubmissionId(submissionId: UUID): FilingSubmittedEvent?
    fun hasAcknowledgementForSubmission(
        submissionId: UUID,
        acknowledgementStatus: FilingAcknowledgementStatus
    ): Boolean
    fun hasAcknowledgementForExternalReference(
        externalReference: String,
        acknowledgementStatus: FilingAcknowledgementStatus
    ): Boolean
    fun findUnresolvedAcknowledgements(limit: Int = 100): List<PersistedEventRecord>
    fun findByEventTypesSince(
        eventTypes: List<String>,
        sinceInclusive: Instant,
        limit: Int
    ): List<PersistedEventRecord>
    fun findLatestResolutionByQueueItemId(queueItemId: UUID): ExceptionResolvedEvent?
    fun findResolvedQueueItemIds(queueItemIds: Collection<UUID>): Set<UUID>
    fun hasResolutionForQueueItem(queueItemId: UUID): Boolean
    fun findCorrelationIdBySubmissionId(submissionId: UUID): UUID?
    fun findCorrelationIdsByArtifactId(artifactId: UUID): List<UUID>
}
