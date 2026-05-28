package com.balfouriana.service.audit

import com.balfouriana.config.OpsProperties
import com.balfouriana.domain.AuditExportFilters
import com.balfouriana.domain.CanonicalRecordType
import com.balfouriana.domain.DomainEvent
import com.balfouriana.domain.EventMetadata
import com.balfouriana.domain.FileReceivedEvent
import com.balfouriana.domain.IngestionChannel
import com.balfouriana.domain.RegulatoryRegime
import com.balfouriana.domain.RuleExceptionEnvelope
import com.balfouriana.domain.RuleExceptionRaisedEvent
import com.balfouriana.domain.RulePackVersion
import com.balfouriana.domain.RuleSeverity
import com.balfouriana.repository.EventStoreRepository
import com.balfouriana.repository.PersistedEventRecord
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class AuditExportGoldenTest {
    private val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build()).findAndRegisterModules()
    private val correlationId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val artifactId = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val exceptionId = UUID.fromString("33333333-3333-3333-3333-333333333333")

    @Test
    fun `export shape matches golden structure`() {
        val repository = GoldenAuditEventStoreRepository(objectMapper, correlationId, artifactId, exceptionId)
        val service = AuditExportService(
            eventStoreRepository = repository,
            objectMapper = objectMapper,
            opsProperties = OpsProperties(
                auditExport = OpsProperties.AuditExport(defaultLimit = 500, maxLimit = 5000, defaultLookbackHours = 168)
            )
        )

        val bundle = service.export(AuditExportFilters(correlationId = correlationId))
        val normalized = normalize(objectMapper.valueToTree(bundle))

        assertEquals("11111111-1111-1111-1111-111111111111", normalized.path("summary").path("correlationId").asText())
        assertEquals(2, normalized.path("timeline").size())
        assertEquals("FileReceivedEvent", normalized.path("timeline").get(0).path("eventType").asText())
        assertEquals("RuleExceptionRaisedEvent", normalized.path("timeline").get(1).path("eventType").asText())
        assertEquals("INGEST", normalized.path("timeline").get(0).path("pipelineStep").asText())
        assertEquals(artifactId.toString(), normalized.path("summary").path("artifactId").asText())
    }

    private fun normalize(root: com.fasterxml.jackson.databind.JsonNode): ObjectNode {
        val copy = root.deepCopy<ObjectNode>()
        copy.remove("exportId")
        copy.remove("generatedAt")
        val filters = copy.path("filters") as ObjectNode
        filters.remove("limit")
        val timeline = copy.path("timeline") as ArrayNode
        for (node in timeline) {
            val entry = node as ObjectNode
            entry.remove("eventId")
            entry.remove("occurredAt")
            val payload = entry.path("payload") as ObjectNode
            payload.path("metadata").let { metadata ->
                if (metadata is ObjectNode) {
                    metadata.remove("eventId")
                    metadata.remove("occurredAt")
                }
            }
        }
        return copy
    }
}

private class GoldenAuditEventStoreRepository(
    private val objectMapper: ObjectMapper,
    private val correlationId: UUID,
    private val artifactId: UUID,
    private val exceptionId: UUID
) : EventStoreRepository {
    private val records = mutableListOf<PersistedEventRecord>()

    init {
        append(
            FileReceivedEvent(
                metadata = EventMetadata(
                    eventId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                    correlationId = correlationId,
                    sourceSystem = "test",
                    occurredAt = Instant.parse("2026-05-01T10:00:00Z"),
                    schemaVersion = "ingestion.receive.v1",
                    regimes = setOf(RegulatoryRegime.AIFMD_II)
                ),
                channel = IngestionChannel.REST,
                artifactId = artifactId,
                originalFilename = "positions.csv",
                storedRelativePath = "received/positions.csv",
                byteSize = 100,
                payloadChecksumSha256 = "abc"
            )
        )
        append(
            RuleExceptionRaisedEvent(
                metadata = EventMetadata(
                    eventId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                    correlationId = correlationId,
                    sourceSystem = "test",
                    occurredAt = Instant.parse("2026-05-01T10:05:00Z"),
                    schemaVersion = "rules.step3.exception.v1",
                    regimes = setOf(RegulatoryRegime.AIFMD_II)
                ),
                artifactId = artifactId,
                recordType = CanonicalRecordType.POSITION,
                recordIndex = 1,
                rulePackVersion = RulePackVersion("step3-aifmd", "2026.05.01", Instant.parse("2026-05-01T00:00:00Z")),
                exception = RuleExceptionEnvelope(
                    exceptionId = exceptionId,
                    correlationId = correlationId,
                    eventId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                    regime = RegulatoryRegime.AIFMD_II,
                    ruleId = "aifmd.rule",
                    severity = RuleSeverity.ERROR,
                    rejectionCategory = "CALCULATION",
                    reasonCode = "AIFMD_LOAN_CONCENTRATION_BREACH",
                    message = "breach",
                    remediationHint = "fix"
                )
            )
        )
    }

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

    override fun findByOccurredAtBetween(startInclusive: Instant, endExclusive: Instant): List<PersistedEventRecord> = emptyList()

    override fun findByEventType(eventType: String): List<PersistedEventRecord> = emptyList()

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
    ): List<PersistedEventRecord> = emptyList()

    override fun findLatestResolutionByQueueItemId(queueItemId: UUID): com.balfouriana.domain.ExceptionResolvedEvent? = null

    override fun findResolvedQueueItemIds(queueItemIds: Collection<UUID>): Set<UUID> = emptySet()

    override fun hasResolutionForQueueItem(queueItemId: UUID): Boolean = false

    override fun findCorrelationIdBySubmissionId(submissionId: UUID): UUID? = null

    override fun findCorrelationIdsByArtifactId(artifactId: UUID): List<UUID> = emptyList()
}
