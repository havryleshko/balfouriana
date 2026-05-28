package com.balfouriana.service.filing

import com.balfouriana.config.FilingStep4Properties
import com.balfouriana.domain.CanonicalRecordType
import com.balfouriana.domain.DomainEvent
import com.balfouriana.domain.EventMetadata
import com.balfouriana.domain.FilingGeneratedEvent
import com.balfouriana.domain.FilingReadyRecordEvent
import com.balfouriana.domain.FilingSubmissionRequestedEvent
import com.balfouriana.domain.FilingSubmittedEvent
import com.balfouriana.domain.IngestionChannel
import com.balfouriana.domain.IngestionFileFormat
import com.balfouriana.domain.RegulatoryRegime
import com.balfouriana.domain.RulePackVersion
import com.balfouriana.domain.SourceRecordEnvelope
import com.balfouriana.repository.EventStoreRepository
import com.balfouriana.repository.PersistedEventRecord
import com.balfouriana.service.filing.template.MifidXmlFilingRenderer
import com.balfouriana.service.filing.validation.FilingSchemaValidatorRegistry
import com.balfouriana.service.filing.validation.MifidFilingSchemaValidator
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class SubmissionOrchestratorServiceTest {
    private val objectMapper = ObjectMapper().findAndRegisterModules()

    @Test
    fun `emits generated and submitted events on success`() {
        val repository = InMemoryEventStoreRepository(objectMapper)
        val service = orchestrator(repository)
        service.process(filingReady(validMifidFields()))
        assertTrue(repository.eventTypes().contains("FilingGeneratedEvent"))
        assertTrue(repository.eventTypes().contains("FilingSubmittedEvent"))
    }

    @Test
    fun `links submission events to generated event id`() {
        val repository = InMemoryEventStoreRepository(objectMapper)
        val service = orchestrator(repository)
        service.process(filingReady(validMifidFields()))
        val generated = repository.events().filterIsInstance<FilingGeneratedEvent>().single()
        val requested = repository.events().filterIsInstance<FilingSubmissionRequestedEvent>().single()
        assertEquals(generated.metadata.eventId, requested.sourceFilingGeneratedEventId)
    }

    @Test
    fun `emits submission failed event when client fails`() {
        val repository = InMemoryEventStoreRepository(objectMapper)
        val service = SubmissionOrchestratorService(
            eventStoreRepository = repository,
            filingRendererRegistry = FilingRendererRegistry(listOf(MifidXmlFilingRenderer())),
            filingSchemaValidatorRegistry = schemaValidatorRegistry(),
            filingSubmissionClient = object : FilingSubmissionClient {
                override fun submit(fileName: String, payload: String): String {
                    error("network failure")
                }
            },
            filingStep4Properties = FilingStep4Properties()
        )
        service.process(filingReady(validMifidFields()))
        assertTrue(repository.eventTypes().contains("FilingSubmissionFailedEvent"))
    }

    @Test
    fun `does not submit when schema validation fails`() {
        val repository = InMemoryEventStoreRepository(objectMapper)
        val service = orchestrator(repository)
        service.process(filingReady(mapOf("quantity" to "1")))
        assertTrue(repository.eventTypes().contains("FilingGenerationFailedEvent"))
        assertFalse(repository.eventTypes().contains("FilingGeneratedEvent"))
        assertFalse(repository.eventTypes().contains("FilingSubmittedEvent"))
    }

    @Test
    fun `does not resubmit duplicate filing artifact`() {
        val repository = InMemoryEventStoreRepository(objectMapper)
        val service = orchestrator(repository)
        val event = filingReady(validMifidFields())
        service.process(event)
        service.process(event)
        assertEquals(1, repository.events().count { it is FilingSubmittedEvent })
        assertEquals(2, repository.events().count { it is FilingGeneratedEvent })
    }

    @Test
    fun `force resubmit submits despite prior successful submission`() {
        val repository = InMemoryEventStoreRepository(objectMapper)
        val service = orchestrator(repository)
        val event = filingReady(validMifidFields())
        service.process(event)
        service.process(event, forceResubmit = true)
        assertEquals(2, repository.events().count { it is FilingSubmittedEvent })
    }

    private fun orchestrator(repository: EventStoreRepository): SubmissionOrchestratorService {
        return SubmissionOrchestratorService(
            eventStoreRepository = repository,
            filingRendererRegistry = FilingRendererRegistry(listOf(MifidXmlFilingRenderer())),
            filingSchemaValidatorRegistry = schemaValidatorRegistry(),
            filingSubmissionClient = object : FilingSubmissionClient {
                override fun submit(fileName: String, payload: String): String = "file:///outbox/$fileName"
            },
            filingStep4Properties = FilingStep4Properties()
        )
    }

    private fun schemaValidatorRegistry(): FilingSchemaValidatorRegistry {
        return FilingSchemaValidatorRegistry(
            validators = listOf(MifidFilingSchemaValidator()),
            filingStep4Properties = FilingStep4Properties()
        )
    }

    private fun validMifidFields(): Map<String, String> = mapOf(
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
    )

    private fun filingReady(fields: Map<String, String>): FilingReadyRecordEvent {
        val now = Instant.parse("2026-05-01T10:30:00Z")
        return FilingReadyRecordEvent(
            metadata = EventMetadata(
                eventId = UUID.randomUUID(),
                correlationId = UUID.randomUUID(),
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
            filingReadyFields = fields,
            traceMetadata = emptyMap(),
            inputFingerprint = "in",
            outputFingerprint = "out"
        )
    }
}

private class InMemoryEventStoreRepository(
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

    override fun findLatestResolutionByQueueItemId(queueItemId: UUID): com.balfouriana.domain.ExceptionResolvedEvent? = null

    override fun findResolvedQueueItemIds(queueItemIds: Collection<UUID>): Set<UUID> = emptySet()

    override fun hasResolutionForQueueItem(queueItemId: UUID): Boolean = false

    override fun findCorrelationIdBySubmissionId(submissionId: UUID): UUID? = null

    override fun findCorrelationIdsByArtifactId(artifactId: UUID): List<UUID> = emptyList()

    fun eventTypes(): Set<String> = records.map { it.eventType }.toSet()

    fun events(): List<DomainEvent> = rawEvents.toList()
}
