package com.balfouriana.service.audit

import com.balfouriana.config.OpsProperties
import com.balfouriana.domain.AuditExportFilters
import com.balfouriana.domain.AuditPipelineStep
import com.balfouriana.domain.CanonicalRecordType
import com.balfouriana.domain.DomainEvent
import com.balfouriana.domain.EventMetadata
import com.balfouriana.domain.ExceptionResolutionType
import com.balfouriana.domain.ExceptionResolvedEvent
import com.balfouriana.domain.ExceptionResubmitRequestedEvent
import com.balfouriana.domain.ExceptionSourceStep
import com.balfouriana.domain.FileReceivedEvent
import com.balfouriana.domain.FilingSubmittedEvent
import com.balfouriana.domain.IngestionChannel
import com.balfouriana.domain.IngestionFileFormat
import com.balfouriana.domain.RegulatoryRegime
import com.balfouriana.domain.RuleExceptionEnvelope
import com.balfouriana.domain.RuleExceptionRaisedEvent
import com.balfouriana.domain.RulePackVersion
import com.balfouriana.domain.RuleSeverity
import com.balfouriana.repository.EventStoreRepository
import com.balfouriana.repository.PersistedEventRecord
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class AuditExportServiceTest {
    private val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build()).findAndRegisterModules()
    private val repository = InMemoryAuditExportEventStoreRepository(objectMapper)
    private val service = AuditExportService(
        eventStoreRepository = repository,
        objectMapper = objectMapper,
        opsProperties = OpsProperties(
            auditExport = OpsProperties.AuditExport(defaultLimit = 500, maxLimit = 5000, defaultLookbackHours = 168)
        )
    )

    @Test
    fun `correlation export includes ingest step4 and ops events`() {
        val correlationId = UUID.randomUUID()
        val artifactId = UUID.randomUUID()
        val queueItemId = UUID.randomUUID()
        repository.append(fileReceived(correlationId, artifactId))
        repository.append(ruleException(correlationId, queueItemId))
        repository.append(
            ExceptionResolvedEvent(
                metadata = metadata(correlationId, "ops.exception.resolve.v1"),
                queueItemId = queueItemId,
                sourceEventId = UUID.randomUUID(),
                sourceEventType = "RuleExceptionRaisedEvent",
                correlationId = correlationId,
                sourceStep = ExceptionSourceStep.RULES,
                resolutionType = ExceptionResolutionType.DISMISSED,
                resolvedBy = "operator",
                note = null,
                resubmitSubmissionId = null
            )
        )
        repository.append(
            ExceptionResubmitRequestedEvent(
                metadata = metadata(correlationId, "ops.exception.resubmit.req.v1"),
                queueItemId = queueItemId,
                sourceEventId = UUID.randomUUID(),
                correlationId = correlationId,
                filingReadyEventId = UUID.randomUUID(),
                forceResubmit = true
            )
        )

        val bundle = service.export(AuditExportFilters(correlationId = correlationId))

        assertNotNull(bundle.summary)
        assertEquals(correlationId, bundle.summary!!.correlationId)
        assertTrue(bundle.timeline.any { it.pipelineStep == AuditPipelineStep.INGEST })
        assertTrue(bundle.timeline.any { it.eventType == "ExceptionResolvedEvent" })
        assertTrue(bundle.timeline.any { it.eventType == "ExceptionResubmitRequestedEvent" })
        assertEquals(2, bundle.summary!!.resolutionCount)
    }

    @Test
    fun `timeline sorted ascending by occurredAt`() {
        val correlationId = UUID.randomUUID()
        val early = fileReceived(correlationId, UUID.randomUUID()).copy(
            metadata = metadata(correlationId, "ingestion.receive.v1").copy(
                occurredAt = Instant.parse("2026-05-01T10:00:00Z")
            )
        )
        val late = ruleException(correlationId, UUID.randomUUID()).copy(
            metadata = metadata(correlationId, "rules.step3.exception.v1").copy(
                occurredAt = Instant.parse("2026-05-01T11:00:00Z")
            )
        )
        repository.append(late)
        repository.append(early)

        val timeline = service.export(AuditExportFilters(correlationId = correlationId)).timeline

        assertTrue(timeline.zipWithNext().all { (left, right) -> !left.occurredAt.isAfter(right.occurredAt) })
    }

    @Test
    fun `submission id filter resolves correlation`() {
        val correlationId = UUID.randomUUID()
        val submissionId = UUID.randomUUID()
        repository.append(
            FilingSubmittedEvent(
                metadata = metadata(correlationId, "filing.step4.sub.ok.v1"),
                artifactId = UUID.randomUUID(),
                submissionId = submissionId,
                sourceFilingGeneratedEventId = UUID.randomUUID(),
                recordType = CanonicalRecordType.TRADE,
                channel = "LOCAL_OUTBOX",
                outputFileName = "x.xml",
                outputChecksumSha256 = "abc",
                remotePath = "file:///out/x.xml"
            )
        )

        val bundle = service.export(AuditExportFilters(submissionId = submissionId))

        assertEquals(correlationId, bundle.summary!!.correlationId)
    }

    @Test
    fun `regime filter excludes non matching records`() {
        val correlationId = UUID.randomUUID()
        repository.append(
            ruleException(correlationId, UUID.randomUUID()).copy(
                metadata = metadata(correlationId, "rules.step3.exception.v1").copy(
                    regimes = setOf(RegulatoryRegime.AIFMD_II)
                )
            )
        )

        val bundle = service.export(
            AuditExportFilters(correlationId = correlationId, regime = RegulatoryRegime.MIFID_II)
        )

        assertTrue(bundle.timeline.isEmpty())
        assertEquals(correlationId, bundle.summary!!.correlationId)
    }

    @Test
    fun `bulk window export respects correlation limit`() {
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        repository.append(fileReceived(first, UUID.randomUUID()))
        repository.append(fileReceived(second, UUID.randomUUID()))

        val bundle = service.export(
            AuditExportFilters(
                startInclusive = Instant.parse("2026-05-01T00:00:00Z"),
                endExclusive = Instant.parse("2026-05-02T00:00:00Z"),
                limit = 2
            )
        )

        assertNull(bundle.summary)
        assertEquals(2, bundle.timeline.map { it.correlationId }.distinct().size)
    }

    @Test
    fun `missing filters returns bad request`() {
        assertThrows(AuditExportBadRequestException::class.java) {
            service.export(AuditExportFilters())
        }
    }

    @Test
    fun `decision chain excludes ingest events`() {
        val correlationId = UUID.randomUUID()
        repository.append(fileReceived(correlationId, UUID.randomUUID()))
        repository.append(ruleException(correlationId, UUID.randomUUID()))

        val chain = service.decisionChainByCorrelationId(correlationId)

        assertTrue(chain.none { it.eventType == "FileReceivedEvent" })
        assertTrue(chain.any { it.eventType == "RuleExceptionRaisedEvent" })
    }

    private fun fileReceived(correlationId: UUID, artifactId: UUID): FileReceivedEvent {
        return FileReceivedEvent(
            metadata = metadata(correlationId, "ingestion.receive.v1"),
            channel = IngestionChannel.REST,
            artifactId = artifactId,
            originalFilename = "trades.csv",
            storedRelativePath = "received/trades.csv",
            byteSize = 100,
            payloadChecksumSha256 = "abc"
        )
    }

    private fun ruleException(correlationId: UUID, exceptionId: UUID): RuleExceptionRaisedEvent {
        return RuleExceptionRaisedEvent(
            metadata = metadata(correlationId, "rules.step3.exception.v1"),
            artifactId = UUID.randomUUID(),
            recordType = CanonicalRecordType.POSITION,
            recordIndex = 1,
            rulePackVersion = RulePackVersion("step3-aifmd", "2026.05.01", Instant.parse("2026-05-01T00:00:00Z")),
            exception = RuleExceptionEnvelope(
                exceptionId = exceptionId,
                correlationId = correlationId,
                eventId = UUID.randomUUID(),
                regime = RegulatoryRegime.AIFMD_II,
                ruleId = "aifmd.rule",
                severity = RuleSeverity.ERROR,
                rejectionCategory = "CALCULATION",
                reasonCode = "AIFMD_LOAN_CONCENTRATION_BREACH",
                message = "breach",
                remediationHint = "fix"
            )
        )
    }

    private fun metadata(correlationId: UUID, schemaVersion: String): EventMetadata {
        return EventMetadata(
            eventId = UUID.randomUUID(),
            correlationId = correlationId,
            sourceSystem = "test",
            occurredAt = Instant.parse("2026-05-01T10:30:00Z"),
            schemaVersion = schemaVersion,
            regimes = setOf(RegulatoryRegime.AIFMD_II)
        )
    }
}

private class InMemoryAuditExportEventStoreRepository(
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

    override fun findFilingSubmittedBySubmissionId(submissionId: UUID): FilingSubmittedEvent? {
        return records.filter { it.eventType == "FilingSubmittedEvent" }
            .mapNotNull { record -> runCatching { objectMapper.readValue<FilingSubmittedEvent>(record.payload) }.getOrNull() }
            .lastOrNull { it.submissionId == submissionId }
    }

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
    ): List<PersistedEventRecord> = emptyList()

    override fun findLatestResolutionByQueueItemId(queueItemId: UUID): com.balfouriana.domain.ExceptionResolvedEvent? = null

    override fun findResolvedQueueItemIds(queueItemIds: Collection<UUID>): Set<UUID> = emptySet()

    override fun hasResolutionForQueueItem(queueItemId: UUID): Boolean = false

    override fun findCorrelationIdBySubmissionId(submissionId: UUID): UUID? {
        return findFilingSubmittedBySubmissionId(submissionId)?.metadata?.correlationId
    }

    override fun findCorrelationIdsByArtifactId(artifactId: UUID): List<UUID> {
        val needle = artifactId.toString()
        return records.filter { it.payload.contains(needle) }.map { it.correlationId }.distinct()
    }
}
