package com.balfouriana.service.ops

import com.balfouriana.config.OpsProperties
import com.balfouriana.domain.CanonicalRecordType
import com.balfouriana.domain.DomainEvent
import com.balfouriana.domain.EventMetadata
import com.balfouriana.domain.ExceptionQueueSeverity
import com.balfouriana.domain.ExceptionResolutionType
import com.balfouriana.domain.ExceptionResolvedEvent
import com.balfouriana.domain.ExceptionSourceStep
import com.balfouriana.domain.RegulatoryRegime
import com.balfouriana.domain.RuleExceptionEnvelope
import com.balfouriana.domain.RuleExceptionRaisedEvent
import com.balfouriana.domain.RulePackVersion
import com.balfouriana.domain.RuleSeverity
import com.balfouriana.repository.EventStoreRepository
import com.balfouriana.repository.PersistedEventRecord
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class ExceptionQueueQueryServiceTest {
    private val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build()).findAndRegisterModules()
    private val repository = InMemoryExceptionQueueEventStoreRepository(objectMapper)
    private val service = ExceptionQueueQueryService(
        eventStoreRepository = repository,
        exceptionQueueMapper = ExceptionQueueMapper(objectMapper),
        opsProperties = OpsProperties(exceptionQueue = OpsProperties.ExceptionQueue(lookbackHours = 168, defaultLimit = 100))
    )

    @Test
    fun `filters by severity and source step`() {
        val blocking = ruleException(RuleSeverity.ERROR, "AIFMD_LOAN_CONCENTRATION_BREACH")
        val review = ruleException(RuleSeverity.WARNING, "AIFMD_FUND_STRUCTURE_UNKNOWN")
        repository.append(blocking)
        repository.append(review)

        val blockingItems = service.openExceptions(severity = ExceptionQueueSeverity.BLOCKING)
        assertEquals(1, blockingItems.size)
        assertEquals(ExceptionSourceStep.RULES, blockingItems[0].sourceStep)

        val reviewItems = service.openExceptions(severity = ExceptionQueueSeverity.NEEDS_REVIEW)
        assertEquals(1, reviewItems.size)
        assertEquals("AIFMD_FUND_STRUCTURE_UNKNOWN", reviewItems[0].reasonCode)
    }

    @Test
    fun `filters by correlation id`() {
        val targetCorrelation = UUID.randomUUID()
        repository.append(ruleException(RuleSeverity.ERROR, "AIFMD_LOAN_CONCENTRATION_BREACH", targetCorrelation))
        repository.append(ruleException(RuleSeverity.ERROR, "OTHER", UUID.randomUUID()))

        val items = service.openExceptions(correlationId = targetCorrelation)
        assertEquals(1, items.size)
        assertEquals(targetCorrelation, items[0].correlationId)
    }

    @Test
    fun `excludes resolved items by default`() {
        val event = ruleException(RuleSeverity.ERROR, "AIFMD_LOAN_CONCENTRATION_BREACH")
        repository.append(event)
        repository.append(
            ExceptionResolvedEvent(
                metadata = EventMetadata(
                    eventId = UUID.randomUUID(),
                    correlationId = event.metadata.correlationId,
                    sourceSystem = "test",
                    occurredAt = Instant.now(),
                    schemaVersion = "ops.exception.resolve.v1",
                    regimes = setOf(RegulatoryRegime.AIFMD_II)
                ),
                queueItemId = event.exception.exceptionId,
                sourceEventId = event.metadata.eventId,
                sourceEventType = "RuleExceptionRaisedEvent",
                correlationId = event.metadata.correlationId,
                sourceStep = ExceptionSourceStep.RULES,
                resolutionType = ExceptionResolutionType.DISMISSED,
                resolvedBy = "operator",
                note = null,
                resubmitSubmissionId = null
            )
        )

        assertEquals(0, service.openExceptions().size)
        assertEquals(1, service.openExceptions(includeResolved = true).size)
    }

    @Test
    fun `deduplicates by queue item id`() {
        val event = ruleException(RuleSeverity.ERROR, "AIFMD_LOAN_CONCENTRATION_BREACH")
        repository.append(event)
        repository.append(event)

        val items = service.openExceptions()
        assertEquals(1, items.size)
    }

    private fun ruleException(
        severity: RuleSeverity,
        reasonCode: String,
        correlationId: UUID = UUID.randomUUID()
    ): RuleExceptionRaisedEvent {
        return RuleExceptionRaisedEvent(
            metadata = EventMetadata(
                eventId = UUID.randomUUID(),
                correlationId = correlationId,
                sourceSystem = "test",
                occurredAt = Instant.now(),
                schemaVersion = "rules.step3.exception.v1",
                regimes = setOf(RegulatoryRegime.AIFMD_II)
            ),
            artifactId = UUID.randomUUID(),
            recordType = CanonicalRecordType.POSITION,
            recordIndex = 1,
            rulePackVersion = RulePackVersion("step3-aifmd", "2026.05.01", Instant.parse("2026-05-01T00:00:00Z")),
            exception = RuleExceptionEnvelope(
                exceptionId = UUID.randomUUID(),
                correlationId = correlationId,
                eventId = UUID.randomUUID(),
                regime = RegulatoryRegime.AIFMD_II,
                ruleId = "aifmd.rule",
                severity = severity,
                rejectionCategory = "CALCULATION",
                reasonCode = reasonCode,
                message = reasonCode,
                remediationHint = "fix"
            )
        )
    }
}

private class InMemoryExceptionQueueEventStoreRepository(
    private val objectMapper: ObjectMapper
) : EventStoreRepository {
    private val records = mutableListOf<PersistedEventRecord>()

    override fun append(event: DomainEvent) {
        records.add(
            PersistedEventRecord(
                eventId = event.metadata.eventId,
                correlationId = event.metadata.correlationId,
                eventType = event.javaClass.simpleName,
                sourceSystem = event.metadata.sourceSystem,
                schemaVersion = event.metadata.schemaVersion,
                regimes = event.metadata.regimes,
                occurredAt = event.metadata.occurredAt,
                payload = objectMapper.writeValueAsString(event),
                createdAt = Instant.now()
            )
        )
    }

    override fun findByCorrelationId(correlationId: UUID): List<PersistedEventRecord> {
        return records.filter { it.correlationId == correlationId }
    }

    override fun findByOccurredAtBetween(startInclusive: Instant, endExclusive: Instant): List<PersistedEventRecord> {
        return records.filter { it.occurredAt >= startInclusive && it.occurredAt < endExclusive }
    }

    override fun findByEventType(eventType: String): List<PersistedEventRecord> {
        return records.filter { it.eventType == eventType }
    }

    override fun hasSuccessfulSubmission(
        correlationId: UUID,
        outputChecksumSha256: String,
        filingTemplateVersion: String
    ): Boolean = false

    override fun findFilingSubmittedBySubmissionId(submissionId: UUID): com.balfouriana.domain.FilingSubmittedEvent? = null

    override fun hasAcknowledgementForSubmission(
        submissionId: UUID,
        acknowledgementStatus: com.balfouriana.domain.FilingAcknowledgementStatus
    ): Boolean = false

    override fun hasAcknowledgementForExternalReference(
        externalReference: String,
        acknowledgementStatus: com.balfouriana.domain.FilingAcknowledgementStatus
    ): Boolean = false

    override fun findUnresolvedAcknowledgements(limit: Int): List<PersistedEventRecord> = emptyList()

    override fun findByEventTypesSince(
        eventTypes: List<String>,
        sinceInclusive: Instant,
        limit: Int
    ): List<PersistedEventRecord> {
        return records.filter { it.eventType in eventTypes && it.occurredAt >= sinceInclusive }
            .sortedByDescending { it.occurredAt }
            .take(limit)
    }

    override fun findLatestResolutionByQueueItemId(queueItemId: UUID): ExceptionResolvedEvent? {
        return records.filter { it.eventType == "ExceptionResolvedEvent" }
            .mapNotNull { record ->
                runCatching { objectMapper.readValue(record.payload, ExceptionResolvedEvent::class.java) }.getOrNull()
            }
            .filter { it.queueItemId == queueItemId }
            .maxByOrNull { it.metadata.occurredAt }
    }

    override fun findResolvedQueueItemIds(queueItemIds: Collection<UUID>): Set<UUID> {
        if (queueItemIds.isEmpty()) {
            return emptySet()
        }
        val targets = queueItemIds.toSet()
        return records.filter { it.eventType == "ExceptionResolvedEvent" }
            .mapNotNull { record ->
                runCatching { objectMapper.readValue(record.payload, ExceptionResolvedEvent::class.java) }.getOrNull()
            }
            .filter { it.queueItemId in targets }
            .map { it.queueItemId }
            .toSet()
    }

    override fun hasResolutionForQueueItem(queueItemId: UUID): Boolean {
        return findLatestResolutionByQueueItemId(queueItemId) != null
    }

    override fun findCorrelationIdBySubmissionId(submissionId: UUID): UUID? {
        return findFilingSubmittedBySubmissionId(submissionId)?.metadata?.correlationId
    }

    override fun findCorrelationIdsByArtifactId(artifactId: UUID): List<UUID> {
        val needle = artifactId.toString()
        return records.filter { it.payload.contains(needle) }.map { it.correlationId }.distinct()
    }
}
