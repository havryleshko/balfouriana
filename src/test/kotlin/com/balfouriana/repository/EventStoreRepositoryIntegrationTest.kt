package com.balfouriana.repository

import com.balfouriana.domain.EventMetadata
import com.balfouriana.domain.IngestionAcceptedEvent
import com.balfouriana.domain.RegulatoryRegime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DuplicateKeyException
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.util.UUID

@SpringBootTest
@ActiveProfiles("local")
class EventStoreRepositoryIntegrationTest {
    @Autowired
    lateinit var repository: EventStoreRepository

    @Test
    fun `appends and queries by correlation id`() {
        val correlationId = UUID.randomUUID()
        val event = IngestionAcceptedEvent(
            metadata = EventMetadata(
                eventId = UUID.randomUUID(),
                correlationId = correlationId,
                sourceSystem = "custodian-b",
                occurredAt = Instant.parse("2026-04-20T10:15:00Z"),
                schemaVersion = "1.0.0",
                regimes = setOf(RegulatoryRegime.AIFMD_II)
            ),
            fileName = "trades.csv",
            payloadChecksum = "checksum-1"
        )

        repository.append(event)
        val records = repository.findByCorrelationId(correlationId)

        assertEquals(1, records.size)
        assertEquals(event.metadata.eventId, records[0].eventId)
        assertEquals(event.metadata.correlationId, records[0].correlationId)
        assertEquals("IngestionAcceptedEvent", records[0].eventType)
    }

    @Test
    fun `duplicate event id is rejected`() {
        val eventId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val event = IngestionAcceptedEvent(
            metadata = EventMetadata(
                eventId = eventId,
                correlationId = correlationId,
                sourceSystem = "custodian-c",
                occurredAt = Instant.parse("2026-04-20T10:20:00Z"),
                schemaVersion = "1.0.0",
                regimes = setOf(RegulatoryRegime.EMIR)
            ),
            fileName = "cash.csv",
            payloadChecksum = "checksum-2"
        )

        repository.append(event)
        assertThrows(DuplicateKeyException::class.java) {
            repository.append(event)
        }
    }
}
