package com.balfouriana.service.ops

import com.balfouriana.config.FilingStep4Properties
import com.balfouriana.config.OpsProperties
import com.balfouriana.domain.CanonicalRecordType
import com.balfouriana.domain.DomainEvent
import com.balfouriana.domain.EventMetadata
import com.balfouriana.domain.ExceptionResolutionType
import com.balfouriana.domain.ExceptionResolvedEvent
import com.balfouriana.domain.ExceptionResubmitRequestedEvent
import com.balfouriana.domain.FilingAcknowledgementReceivedEvent
import com.balfouriana.domain.FilingAcknowledgementStatus
import com.balfouriana.domain.FilingGeneratedEvent
import com.balfouriana.domain.FilingReadyRecordEvent
import com.balfouriana.domain.FilingSubmittedEvent
import com.balfouriana.domain.IngestionChannel
import com.balfouriana.domain.IngestionFileFormat
import com.balfouriana.domain.RegulatoryRegime
import com.balfouriana.domain.RuleExceptionEnvelope
import com.balfouriana.domain.RuleExceptionRaisedEvent
import com.balfouriana.domain.RulePackVersion
import com.balfouriana.domain.RuleSeverity
import com.balfouriana.domain.SourceRecordEnvelope
import com.balfouriana.repository.EventStoreRepository
import com.balfouriana.repository.PersistedEventRecord
import com.balfouriana.service.filing.FilingRendererRegistry
import com.balfouriana.service.filing.FilingSubmissionClient
import com.balfouriana.service.filing.Step4FilingInputService
import com.balfouriana.service.filing.SubmissionOrchestratorService
import com.balfouriana.service.filing.template.MifidXmlFilingRenderer
import com.balfouriana.service.filing.validation.FilingSchemaValidatorRegistry
import com.balfouriana.service.filing.validation.MifidFilingSchemaValidator
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class ExceptionResolutionServiceTest {
    private val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build()).findAndRegisterModules()

    @Test
    fun `dismiss marks queue item resolved`() {
        val repository = InMemoryResolutionEventStoreRepository(objectMapper)
        val ruleEvent = ruleException(RuleSeverity.WARNING, "AIFMD_FUND_STRUCTURE_UNKNOWN")
        repository.append(ruleEvent)
        val service = resolutionService(repository)

        val resolved = service.resolve(
            queueItemId = ruleEvent.exception.exceptionId,
            resolutionType = ExceptionResolutionType.ACKNOWLEDGED,
            note = "reviewed",
            resolvedBy = "operator"
        )

        assertEquals(ExceptionResolutionType.ACKNOWLEDGED, resolved.resolutionType)
        assertTrue(repository.hasResolutionForQueueItem(ruleEvent.exception.exceptionId))
        assertEquals(0, serviceQuery(repository).openExceptions().size)
    }

    @Test
    fun `double resolve is rejected`() {
        val repository = InMemoryResolutionEventStoreRepository(objectMapper)
        val ruleEvent = ruleException(RuleSeverity.ERROR, "AIFMD_LOAN_CONCENTRATION_BREACH")
        repository.append(ruleEvent)
        val service = resolutionService(repository)
        service.resolve(
            queueItemId = ruleEvent.exception.exceptionId,
            resolutionType = ExceptionResolutionType.DISMISSED,
            note = null,
            resolvedBy = "operator"
        )

        assertThrows(ExceptionResolutionConflictException::class.java) {
            service.resolve(
                queueItemId = ruleEvent.exception.exceptionId,
                resolutionType = ExceptionResolutionType.DISMISSED,
                note = null,
                resolvedBy = "operator"
            )
        }
    }

    @Test
    fun `resubmit calls orchestrator with force resubmit and resolves on success`() {
        val repository = InMemoryResolutionEventStoreRepository(objectMapper)
        val correlationId = UUID.randomUUID()
        val submissionId = UUID.randomUUID()
        val ackEventId = UUID.randomUUID()
        val filingReady = filingReady(correlationId)
        repository.append(filingReady)
        repository.append(
            FilingSubmittedEvent(
                metadata = EventMetadata(
                    eventId = UUID.randomUUID(),
                    correlationId = correlationId,
                    sourceSystem = "step4",
                    occurredAt = Instant.parse("2026-05-01T12:00:00Z"),
                    schemaVersion = "filing.step4.sub.ok.v1",
                    regimes = setOf(RegulatoryRegime.MIFID_II)
                ),
                artifactId = filingReady.artifactId,
                submissionId = submissionId,
                sourceFilingGeneratedEventId = UUID.randomUUID(),
                recordType = CanonicalRecordType.TRADE,
                channel = "LOCAL_OUTBOX",
                outputFileName = "x.xml",
                outputChecksumSha256 = "abc",
                remotePath = "file:///out/x.xml"
            )
        )
        repository.append(
            FilingAcknowledgementReceivedEvent(
                metadata = EventMetadata(
                    eventId = ackEventId,
                    correlationId = correlationId,
                    sourceSystem = "step4-ack",
                    occurredAt = Instant.parse("2026-05-01T12:05:00Z"),
                    schemaVersion = "filing.step4.ack.rcv.v1",
                    regimes = setOf(RegulatoryRegime.MIFID_II)
                ),
                submissionId = submissionId,
                correlationLinked = true,
                linkedCorrelationId = correlationId,
                channel = "SFTP",
                acknowledgementStatus = FilingAcknowledgementStatus.NACK,
                externalReference = submissionId.toString(),
                reasonCode = "REJECTED",
                message = "bad data"
            )
        )
        val service = resolutionService(repository)
        val result = service.resubmitFiling(ackEventId, "operator", "retry")

        assertTrue(result.resolved)
        assertEquals(2, repository.events().count { it is FilingSubmittedEvent })
        assertTrue(repository.events().any { it is ExceptionResubmitRequestedEvent })
        assertTrue(repository.hasResolutionForQueueItem(ackEventId))
    }

    @Test
    fun `resubmit without filing ready fails with actionable message`() {
        val repository = InMemoryResolutionEventStoreRepository(objectMapper)
        val submissionFailedEventId = UUID.randomUUID()
        repository.append(
            com.balfouriana.domain.FilingSubmissionFailedEvent(
                metadata = EventMetadata(
                    eventId = submissionFailedEventId,
                    correlationId = UUID.randomUUID(),
                    sourceSystem = "step4",
                    occurredAt = Instant.now(),
                    schemaVersion = "filing.step4.sub.fail.v1",
                    regimes = setOf(RegulatoryRegime.MIFID_II)
                ),
                artifactId = UUID.randomUUID(),
                submissionId = UUID.randomUUID(),
                sourceFilingGeneratedEventId = UUID.randomUUID(),
                recordType = CanonicalRecordType.TRADE,
                channel = "LOCAL_OUTBOX",
                outputFileName = "x.xml",
                reasonCode = "FILING_SUBMISSION_FAILED",
                message = "upload failed"
            )
        )
        val service = resolutionService(repository)

        val ex = assertThrows(ExceptionResolutionBadRequestException::class.java) {
            service.resubmitFiling(submissionFailedEventId, "operator", null)
        }
        assertTrue(ex.message!!.contains("re-ingest"))
    }

    private fun resolutionService(repository: InMemoryResolutionEventStoreRepository): ExceptionResolutionService {
        val orchestrator = SubmissionOrchestratorService(
            eventStoreRepository = repository,
            filingRendererRegistry = FilingRendererRegistry(listOf(MifidXmlFilingRenderer())),
            filingSchemaValidatorRegistry = FilingSchemaValidatorRegistry(
                validators = listOf(MifidFilingSchemaValidator()),
                filingStep4Properties = FilingStep4Properties()
            ),
            filingSubmissionClient = object : FilingSubmissionClient {
                override fun submit(fileName: String, payload: String): String = "file:///outbox/$fileName"
            },
            filingStep4Properties = FilingStep4Properties()
        )
        return ExceptionResolutionService(
            eventStoreRepository = repository,
            exceptionQueueQueryService = serviceQuery(repository),
            step4FilingInputService = Step4FilingInputService(repository, objectMapper),
            submissionOrchestratorService = orchestrator,
            objectMapper = objectMapper
        )
    }

    private fun serviceQuery(repository: EventStoreRepository): ExceptionQueueQueryService {
        return ExceptionQueueQueryService(
            eventStoreRepository = repository,
            exceptionQueueMapper = ExceptionQueueMapper(objectMapper),
            opsProperties = OpsProperties(exceptionQueue = OpsProperties.ExceptionQueue(lookbackHours = 168, defaultLimit = 100))
        )
    }

    private fun ruleException(severity: RuleSeverity, reasonCode: String): RuleExceptionRaisedEvent {
        val correlationId = UUID.randomUUID()
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

    private fun filingReady(correlationId: UUID): FilingReadyRecordEvent {
        val now = Instant.parse("2026-05-01T10:30:00Z")
        return FilingReadyRecordEvent(
            metadata = EventMetadata(
                eventId = UUID.randomUUID(),
                correlationId = correlationId,
                sourceSystem = "test",
                occurredAt = now,
                schemaVersion = "rules.step3.filing-ready.v1",
                regimes = setOf(RegulatoryRegime.MIFID_II)
            ),
            artifactId = UUID.randomUUID(),
            envelope = SourceRecordEnvelope(
                sourceId = "test",
                sourceSystem = "test",
                ingestionChannel = IngestionChannel.REST,
                fileId = UUID.randomUUID(),
                originalFileName = "x.csv",
                recordIndex = 1,
                receivedAt = now,
                format = IngestionFileFormat.CSV,
                contentType = "text/csv",
                fileSizeBytes = 100,
                checksumSha256 = "abc",
                schemaHint = null
            ),
            recordType = CanonicalRecordType.TRADE,
            rulePackVersion = RulePackVersion("pack", "v1", Instant.parse("2026-04-28T00:00:00Z")),
            filingReadyFields = mapOf(
                "record_type" to "TRADE",
                "trade_id" to "T-100",
                "instrument_id" to "GB00B03MLX29",
                "trade_date" to "2026-04-22",
                "quantity" to "100",
                "price" to "10.3",
                "currency" to "GBP",
                "buyer_lei" to "5493001KJTIIGC8Y1R12",
                "seller_lei" to "213800D1EI4B9WTWWD28",
                "decision_maker_lei" to "7245008N4E6Y7Z5RAA41",
                "execution_actor_type" to "HUMAN",
                "venue_code" to "XLON",
                "otc_indicator" to "N",
                "waiver_indicator" to "N",
                "short_selling_indicator" to "N",
                "commodity_derivative_indicator" to "N",
                "price_notation" to "MONETARY",
                "price_currency" to "GBP",
                "calculated_notional" to "1030.0",
                "mifid_transaction_side" to "BUYER_SIDE",
                "mifid_execution_mode" to "HUMAN"
            ),
            traceMetadata = emptyMap(),
            inputFingerprint = "in",
            outputFingerprint = "out"
        )
    }
}

private class InMemoryResolutionEventStoreRepository(
    private val objectMapper: ObjectMapper
) : EventStoreRepository {
    private val records = mutableListOf<PersistedEventRecord>()
    private val rawEvents = mutableListOf<DomainEvent>()

    override fun append(event: DomainEvent) {
        rawEvents.add(event)
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
    ): Boolean {
        val correlationRecords = findByCorrelationId(correlationId)
        val generatedEventIds = correlationRecords.filter { it.eventType == "FilingGeneratedEvent" }
            .mapNotNull { record -> runCatching { objectMapper.readValue<FilingGeneratedEvent>(record.payload) }.getOrNull() }
            .filter {
                it.outputChecksumSha256 == outputChecksumSha256 &&
                    it.filingTemplateVersion == filingTemplateVersion
            }
            .map { it.metadata.eventId }
            .toSet()
        if (generatedEventIds.isEmpty()) {
            return false
        }
        return correlationRecords.filter { it.eventType == "FilingSubmittedEvent" }
            .mapNotNull { record -> runCatching { objectMapper.readValue<FilingSubmittedEvent>(record.payload) }.getOrNull() }
            .any {
                it.outputChecksumSha256 == outputChecksumSha256 &&
                    it.sourceFilingGeneratedEventId in generatedEventIds
            }
    }

    override fun findFilingSubmittedBySubmissionId(submissionId: UUID): FilingSubmittedEvent? = null

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
            .mapNotNull { record -> runCatching { objectMapper.readValue<ExceptionResolvedEvent>(record.payload) }.getOrNull() }
            .filter { it.queueItemId == queueItemId }
            .maxByOrNull { it.metadata.occurredAt }
    }

    override fun findResolvedQueueItemIds(queueItemIds: Collection<UUID>): Set<UUID> {
        if (queueItemIds.isEmpty()) {
            return emptySet()
        }
        val targets = queueItemIds.toSet()
        return records.filter { it.eventType == "ExceptionResolvedEvent" }
            .mapNotNull { record -> runCatching { objectMapper.readValue<ExceptionResolvedEvent>(record.payload) }.getOrNull() }
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

    fun events(): List<DomainEvent> = rawEvents.toList()
}
