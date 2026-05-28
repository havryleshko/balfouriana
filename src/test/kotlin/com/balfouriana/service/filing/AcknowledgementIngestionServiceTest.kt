package com.balfouriana.service.filing

import com.balfouriana.config.FilingStep4Properties
import com.balfouriana.domain.CanonicalRecordType
import com.balfouriana.domain.DomainEvent
import com.balfouriana.domain.EventMetadata
import com.balfouriana.domain.FilingAcknowledgementReceivedEvent
import com.balfouriana.domain.FilingAcknowledgementStatus
import com.balfouriana.domain.FilingSubmittedEvent
import com.balfouriana.domain.RegulatoryRegime
import com.balfouriana.domain.UNLINKED_ACK_CORRELATION_ID
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
        assertEquals(correlationId, events[0].metadata.correlationId)
        assertEquals(setOf(RegulatoryRegime.MIFID_II), events[0].metadata.regimes)
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
        assertEquals(UNLINKED_ACK_CORRELATION_ID, events[0].metadata.correlationId)
        assertEquals(FilingAcknowledgementStatus.NACK, events[0].acknowledgementStatus)
    }

    @Test
    fun `uses sentinel correlation when submission id not found`() {
        val repository = InMemoryAckEventStoreRepository(objectMapper)
        val service = AcknowledgementIngestionService(repository, FilingStep4Properties())
        val orphanSubmissionId = UUID.randomUUID()
        val events = service.ingestCsv(
            "external_reference,status,reason_code,message\n$orphanSubmissionId,NACK,REJECTED,bad data"
        )
        assertEquals(1, events.size)
        assertTrue(!events[0].correlationLinked)
        assertEquals(UNLINKED_ACK_CORRELATION_ID, events[0].metadata.correlationId)
    }

    @Test
    fun `suppresses duplicate ack for same submission and status`() {
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
        val csv = "external_reference,status,reason_code,message\n$submissionId,ACK,,accepted"
        assertEquals(1, service.ingestCsv(csv).size)
        assertEquals(0, service.ingestCsv(csv).size)
        assertEquals(1, repository.findByEventType("FilingAcknowledgementReceivedEvent").size)
    }

    @Test
    fun `xml path links by external reference`() {
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
        val event = service.ingestXml(
            """<Ack externalReference="$submissionId" status="ACK" reasonCode="" message="ok"/>"""
        )
        requireNotNull(event)
        assertTrue(event.correlationLinked)
        assertEquals(correlationId, event.linkedCorrelationId)
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

    override fun hasSuccessfulSubmission(
        correlationId: UUID,
        outputChecksumSha256: String,
        filingTemplateVersion: String
    ): Boolean = false

    override fun findFilingSubmittedBySubmissionId(submissionId: UUID): FilingSubmittedEvent? {
        return findByEventType("FilingSubmittedEvent")
            .mapNotNull { record ->
                runCatching {
                    objectMapper.readValue(record.payload, FilingSubmittedEvent::class.java)
                }.getOrNull()
            }
            .lastOrNull { it.submissionId == submissionId }
    }

    override fun hasAcknowledgementForSubmission(
        submissionId: UUID,
        acknowledgementStatus: FilingAcknowledgementStatus
    ): Boolean {
        return findByEventType("FilingAcknowledgementReceivedEvent")
            .mapNotNull { record ->
                runCatching {
                    objectMapper.readValue(record.payload, FilingAcknowledgementReceivedEvent::class.java)
                }.getOrNull()
            }
            .any { it.submissionId == submissionId && it.acknowledgementStatus == acknowledgementStatus }
    }

    override fun hasAcknowledgementForExternalReference(
        externalReference: String,
        acknowledgementStatus: FilingAcknowledgementStatus
    ): Boolean {
        return findByEventType("FilingAcknowledgementReceivedEvent")
            .mapNotNull { record ->
                runCatching {
                    objectMapper.readValue(record.payload, FilingAcknowledgementReceivedEvent::class.java)
                }.getOrNull()
            }
            .any { it.externalReference == externalReference && it.acknowledgementStatus == acknowledgementStatus }
    }

    override fun findUnresolvedAcknowledgements(limit: Int): List<PersistedEventRecord> {
        return findByEventType("FilingAcknowledgementReceivedEvent")
            .mapNotNull { record ->
                val event = runCatching {
                    objectMapper.readValue(record.payload, FilingAcknowledgementReceivedEvent::class.java)
                }.getOrNull()
                if (event != null && !event.correlationLinked) record else null
            }
            .sortedByDescending { it.occurredAt }
            .take(limit)
    }

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
}
