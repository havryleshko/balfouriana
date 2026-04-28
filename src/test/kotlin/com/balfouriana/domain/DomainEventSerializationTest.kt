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
            byteSize = 42,
            payloadChecksumSha256 = "8f434346648f6b96df89dda901c5176b10a6d83961c116ea4a84f7df0cf2ce13"
        )
        val payload = mapper.writeValueAsString(event)
        val decoded: DomainEvent = mapper.readValue(payload)
        assertEquals(event, decoded)
    }

    @Test
    fun `serializes and deserializes canonical mapped event`() {
        val event = CanonicalRecordMappedEvent(
            metadata = EventMetadata(
                eventId = UUID.randomUUID(),
                correlationId = UUID.randomUUID(),
                sourceSystem = "rest-ingest",
                occurredAt = Instant.parse("2026-04-22T11:00:00Z"),
                schemaVersion = "ingestion.parse.v1",
                regimes = emptySet()
            ),
            artifactId = UUID.randomUUID(),
            envelope = SourceRecordEnvelope(
                sourceId = "rest-ingest",
                sourceSystem = "rest-ingest",
                ingestionChannel = IngestionChannel.REST,
                fileId = UUID.randomUUID(),
                originalFileName = "trades.csv",
                recordIndex = 1,
                receivedAt = Instant.parse("2026-04-22T10:59:59Z"),
                format = IngestionFileFormat.CSV,
                contentType = "text/csv",
                fileSizeBytes = 42,
                checksumSha256 = "8f434346648f6b96df89dda901c5176b10a6d83961c116ea4a84f7df0cf2ce13",
                schemaHint = null
            ),
            recordType = CanonicalRecordType.TRADE,
            canonicalFields = mapOf("trade_id" to "T-1")
        )
        val payload = mapper.writeValueAsString(event)
        val decoded: DomainEvent = mapper.readValue(payload)
        assertEquals(event, decoded)
    }

    @Test
    fun `serializes and deserializes validation decision event`() {
        val event = ValidationDecisionEvent(
            metadata = EventMetadata(
                eventId = UUID.randomUUID(),
                correlationId = UUID.randomUUID(),
                sourceSystem = "validation-service",
                occurredAt = Instant.parse("2026-04-25T11:00:00Z"),
                schemaVersion = "validation.step2.decision.v1",
                regimes = setOf(RegulatoryRegime.MIFID_II)
            ),
            artifactId = UUID.randomUUID(),
            recordType = CanonicalRecordType.TRADE,
            recordIndex = 1,
            validationPack = ValidationPackVersion(
                packId = "step2-core",
                version = "2026.04.19",
                effectiveFrom = Instant.parse("2026-04-19T00:00:00Z")
            ),
            ruleResult = ValidationRuleResult(
                ruleId = "required.instrument_id",
                layer = ValidationLayer.SCHEMA,
                outcome = ValidationOutcome.PASS,
                reasonCode = "OK",
                message = "Field present",
                severity = ValidationSeverity.WARNING
            ),
            inputFingerprint = "in",
            outputFingerprint = "out",
            exceptionId = null
        )
        val payload = mapper.writeValueAsString(event)
        val decoded: DomainEvent = mapper.readValue(payload)
        assertEquals(event, decoded)
    }
}
