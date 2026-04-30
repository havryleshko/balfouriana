package com.balfouriana.service.filing

import com.balfouriana.config.FilingStep4Properties
import com.balfouriana.domain.CanonicalRecordType
import com.balfouriana.domain.DomainEvent
import com.balfouriana.domain.EventMetadata
import com.balfouriana.domain.FilingReadyRecordEvent
import com.balfouriana.domain.IngestionChannel
import com.balfouriana.domain.IngestionFileFormat
import com.balfouriana.domain.RegulatoryRegime
import com.balfouriana.domain.RulePackVersion
import com.balfouriana.domain.SourceRecordEnvelope
import com.balfouriana.repository.EventStoreRepository
import com.balfouriana.repository.PersistedEventRecord
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class SubmissionOrchestratorServiceTest {
    @Test
    fun `emits generated and submitted events on success`() {
        val repository = InMemoryEventStoreRepository()
        val service = SubmissionOrchestratorService(
            eventStoreRepository = repository,
            filingRendererRegistry = FilingRendererRegistry(listOf(MifidXmlFilingRenderer())),
            filingSubmissionClient = object : FilingSubmissionClient {
                override fun submit(fileName: String, payload: String): String = "sftp://out/$fileName"
            },
            filingStep4Properties = FilingStep4Properties()
        )
        service.process(filingReady())
        assertTrue(repository.eventTypes().contains("FilingGeneratedEvent"))
        assertTrue(repository.eventTypes().contains("FilingSubmittedEvent"))
    }

    @Test
    fun `emits submission failed event when client fails`() {
        val repository = InMemoryEventStoreRepository()
        val service = SubmissionOrchestratorService(
            eventStoreRepository = repository,
            filingRendererRegistry = FilingRendererRegistry(listOf(MifidXmlFilingRenderer())),
            filingSubmissionClient = object : FilingSubmissionClient {
                override fun submit(fileName: String, payload: String): String {
                    error("network failure")
                }
            },
            filingStep4Properties = FilingStep4Properties()
        )
        service.process(filingReady())
        assertTrue(repository.eventTypes().contains("FilingSubmissionFailedEvent"))
    }

    private fun filingReady(): FilingReadyRecordEvent {
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
            filingReadyFields = mapOf("quantity" to "1"),
            traceMetadata = emptyMap(),
            inputFingerprint = "in",
            outputFingerprint = "out"
        )
    }
}

private class InMemoryEventStoreRepository : EventStoreRepository {
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
                payload = "",
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

    fun eventTypes(): Set<String> = records.map { it.eventType }.toSet()
}
