package com.balfouriana.domain

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class DomainEventSerializationTest {
    private val mapper: ObjectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build()).findAndRegisterModules()

    @Test
    fun `serializes and deserializes ingestion accepted event`() {
        val event = IngestionAcceptedEvent(
            metadata = EventMetadata(
                eventId = UUID.randomUUID(),
                correlationId = UUID.randomUUID(),
                sourceSystem = "custodian-a",
                occurredAt = Instant.parse("2026-04-20T10:00:00Z"),
                schemaVersion = "1.0.0",
                regimes = setOf(RegulatoryRegime.MIFID_II, RegulatoryRegime.EMIR)
            ),
            fileName = "positions.csv",
            payloadChecksum = "abc123"
        )

        val payload = mapper.writeValueAsString(event)
        val decoded: DomainEvent = mapper.readValue(payload)

        assertEquals(event, decoded)
    }

    @Test
    fun `serializes and deserializes file received event`() {
        val event = FileReceivedEvent(
            metadata = EventMetadata(
                eventId = UUID.randomUUID(),
                correlationId = UUID.randomUUID(),
                sourceSystem = "rest-ingest",
                occurredAt = Instant.parse("2026-04-20T11:00:00Z"),
                schemaVersion = "ingestion.receive.v1",
                regimes = emptySet()
            ),
            channel = IngestionChannel.REST,
            artifactId = UUID.randomUUID(),
            originalFilename = "trades.csv",
            storedRelativePath = "received/artifact_trades.csv",
            byteSize = 42
        )
        val payload = mapper.writeValueAsString(event)
        val decoded: DomainEvent = mapper.readValue(payload)
        assertEquals(event, decoded)
    }
}
