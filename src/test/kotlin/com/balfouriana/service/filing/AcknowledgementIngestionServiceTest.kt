package com.balfouriana.service.filing

import com.balfouriana.config.FilingStep4Properties
import com.balfouriana.domain.CanonicalRecordType
import com.balfouriana.domain.DomainEvent
import com.balfouriana.domain.EventMetadata
import com.balfouriana.domain.FilingSubmittedEvent
import com.balfouriana.domain.RegulatoryRegime
import com.balfouriana.repository.EventStoreRepository
import com.balfouriana.repository.PersistedEventRecord
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class AcknowledgementIngestionServiceTest {
    private val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build()).findAndRegisterModules()

    @Test
    fun `links ack to submitted filing by external reference`() {
        val repository = InMemoryAckEventStoreRepository(objectMapper)
        val submissionId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        repository.append(
            FilingSubmittedEvent(
                metadata = EventMetadata(
                    eventId = UUID.randomUUID(),
                    correlationId = correlationId,
                    sourceSystem = "step4",
                    occurredAt = Instant.parse("2026-05-01T12:00:00Z"),
                    schemaVersion = "filing.step4.submitted.v1",
                    regimes = setOf(RegulatoryRegime.MIFID_II)
                ),
                artifactId = UUID.randomUUID(),
                submissionId = submissionId,
                sourceFilingGeneratedEventId = UUID.randomUUID(),
                recordType = CanonicalRecordType.TRADE,
                channel = "SFTP",
                outputFileName = "x.xml",
                outputChecksumSha256 = "abc",
                remotePath = "sftp://out/x.xml"
            )
        )
        val service = AcknowledgementIngestionService(repository, FilingStep4Properties())
        val events = service.ingestCsv(
            "external_reference,status,reason_code,message\n$submissionId,ACK,,accepted"
        )
        assertEquals(1, events.size)
        assertTrue(events[0].correlationLinked)
        assertEquals(correlationId, events[0].linkedCorrelationId)
    }

    @Test
    fun `marks unresolved when external reference does not match submission`() {
        val repository = InMemoryAckEventStoreRepository(objectMapper)
        val service = AcknowledgementIngestionService(repository, FilingStep4Properties())
        val events = service.ingestCsv(
            "external_reference,status,reason_code,message\nUNKNOWN,NACK,REJECTED,bad data"
        )
        assertEquals(1, events.size)
        assertTrue(!events[0].correlationLinked)
    }
}

private class InMemoryAckEventStoreRepository(
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
}
