package com.balfouriana.repository

import com.balfouriana.domain.CanonicalRecordType
import com.balfouriana.domain.EventMetadata
import com.balfouriana.domain.FilingAcknowledgementReceivedEvent
import com.balfouriana.domain.FilingAcknowledgementStatus
import com.balfouriana.domain.FilingSubmittedEvent
import com.balfouriana.domain.RegulatoryRegime
import com.balfouriana.domain.UNLINKED_ACK_CORRELATION_ID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.util.UUID

@SpringBootTest
@ActiveProfiles("local")
class EventStoreRepositoryAckIntegrationTest {
    @Autowired
    lateinit var repository: EventStoreRepository

    @Test
    fun `findFilingSubmittedBySubmissionId returns matching event`() {
        val submissionId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val submitted = FilingSubmittedEvent(
            metadata = EventMetadata(
                eventId = UUID.randomUUID(),
                correlationId = correlationId,
                sourceSystem = "test",
                occurredAt = Instant.parse("2026-05-01T12:00:00Z"),
                schemaVersion = "filing.step4.submitted.v1",
                regimes = setOf(RegulatoryRegime.MIFID_II)
            ),
            artifactId = UUID.randomUUID(),
            submissionId = submissionId,
            sourceFilingGeneratedEventId = UUID.randomUUID(),
            recordType = CanonicalRecordType.TRADE,
            channel = "LOCAL_OUTBOX",
            outputFileName = "x.xml",
            outputChecksumSha256 = "abc",
            remotePath = "file:///out/x.xml"
        )
        repository.append(submitted)
        val found = repository.findFilingSubmittedBySubmissionId(submissionId)
        assertNotNull(found)
        assertEquals(submissionId, found!!.submissionId)
        assertEquals(correlationId, found.metadata.correlationId)
    }

    @Test
    fun `findUnresolvedAcknowledgements returns unlinked ack events`() {
        val linked = FilingAcknowledgementReceivedEvent(
            metadata = EventMetadata(
                eventId = UUID.randomUUID(),
                correlationId = UUID.randomUUID(),
                sourceSystem = "test",
                occurredAt = Instant.parse("2026-05-01T12:00:00Z"),
                schemaVersion = "filing.step4.ack.rcv.v1",
                regimes = setOf(RegulatoryRegime.MIFID_II)
            ),
            submissionId = UUID.randomUUID(),
            correlationLinked = true,
            linkedCorrelationId = UUID.randomUUID(),
            channel = "SFTP",
            acknowledgementStatus = FilingAcknowledgementStatus.ACK,
            externalReference = UUID.randomUUID().toString(),
            reasonCode = null,
            message = null
        )
        val orphan = FilingAcknowledgementReceivedEvent(
            metadata = EventMetadata(
                eventId = UUID.randomUUID(),
                correlationId = UNLINKED_ACK_CORRELATION_ID,
                sourceSystem = "test",
                occurredAt = Instant.parse("2026-05-01T12:01:00Z"),
                schemaVersion = "filing.step4.ack.rcv.v1",
                regimes = emptySet()
            ),
            submissionId = null,
            correlationLinked = false,
            linkedCorrelationId = null,
            channel = "SFTP",
            acknowledgementStatus = FilingAcknowledgementStatus.NACK,
            externalReference = "UNKNOWN",
            reasonCode = "REJECTED",
            message = "bad data"
        )
        repository.append(linked)
        repository.append(orphan)
        val unresolved = repository.findUnresolvedAcknowledgements()
        assertEquals(1, unresolved.size)
        assertTrue(unresolved[0].payload.contains("UNKNOWN"))
    }
}
