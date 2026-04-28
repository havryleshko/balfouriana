package com.balfouriana.service.validation

import com.balfouriana.domain.CanonicalRecordMappedEvent
import com.balfouriana.domain.CanonicalRecordType
import com.balfouriana.domain.EventMetadata
import com.balfouriana.domain.IngestionChannel
import com.balfouriana.domain.IngestionFileFormat
import com.balfouriana.domain.RegulatoryRegime
import com.balfouriana.domain.SourceRecordEnvelope
import com.balfouriana.repository.EventStoreRepository
import com.balfouriana.repository.PersistedEventRecord
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class ValidationAndMappingServiceTest {
    private val repository = InMemoryEventStoreRepository()
    private val service = ValidationAndMappingService(
        validationPackRegistry = ValidationPackRegistry(),
        leiEnrichmentAdapter = LeiEnrichmentAdapter(),
        instrumentEnrichmentAdapter = InstrumentEnrichmentAdapter(),
        venueMicEnrichmentAdapter = VenueMicEnrichmentAdapter(),
        eventStoreRepository = repository,
        objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build()).findAndRegisterModules()
    )

    @Test
    fun `emits validated event for good canonical input`() {
        val event = canonicalEvent(
            mapOf(
                "record_type" to "TRADE",
                "instrument_id" to "GB00B03MLX29",
                "trade_date" to "2026-04-20",
                "quantity" to "100",
                "price" to "12.45",
                "currency" to "GBP",
                "venue" to "xlon",
                "counterparty_lei" to "5493001KJTIIGC8Y1R12"
            )
        )

        val result = service.process(event)

        assertTrue(result.emittedValidatedEvent)
        assertEquals(6, result.decisionCount)
        assertEquals(0, result.blockingExceptionIds.size)
    }

    @Test
    fun `does not emit validated event for blocking errors`() {
        val event = canonicalEvent(
            mapOf(
                "record_type" to "TRADE",
                "instrument_id" to "",
                "trade_date" to "20-04-2026",
                "quantity" to "0",
                "price" to "abc",
                "currency" to "gbp",
                "venue" to ""
            )
        )

        val result = service.process(event)

        assertFalse(result.emittedValidatedEvent)
        assertTrue(result.blockingExceptionIds.isNotEmpty())
        assertTrue(result.exceptionCount >= 1)
    }

    @Test
    fun `produces deterministic fingerprint for same input`() {
        val event = canonicalEvent(
            mapOf(
                "record_type" to "TRADE",
                "instrument_id" to "US0378331005",
                "trade_date" to "2026-04-20",
                "quantity" to "100",
                "price" to "12.45",
                "currency" to "USD",
                "venue" to "xoff",
                "counterparty_lei" to "5493001KJTIIGC8Y1R12"
            )
        )

        val first = service.process(event)
        val second = service.process(event)

        assertEquals(first.outputFingerprint, second.outputFingerprint)
    }

    private fun canonicalEvent(fields: Map<String, String>): CanonicalRecordMappedEvent {
        val now = Instant.parse("2026-04-25T12:00:00Z")
        return CanonicalRecordMappedEvent(
            metadata = EventMetadata(
                eventId = UUID.randomUUID(),
                correlationId = UUID.randomUUID(),
                sourceSystem = "test-source",
                occurredAt = now,
                schemaVersion = "ingestion.parse.v1",
                regimes = setOf(RegulatoryRegime.MIFID_II)
            ),
            artifactId = UUID.randomUUID(),
            envelope = SourceRecordEnvelope(
                sourceId = "test-source",
                sourceSystem = "test-source",
                ingestionChannel = IngestionChannel.REST,
                fileId = UUID.randomUUID(),
                originalFileName = "trades.csv",
                recordIndex = 1,
                receivedAt = now,
                format = IngestionFileFormat.CSV,
                contentType = "text/csv",
                fileSizeBytes = 100,
                checksumSha256 = "abc",
                schemaHint = null
            ),
            recordType = CanonicalRecordType.TRADE,
            canonicalFields = fields
        )
    }
}

private class InMemoryEventStoreRepository : EventStoreRepository {
    private val records = mutableListOf<PersistedEventRecord>()

    override fun append(event: com.balfouriana.domain.DomainEvent) {
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

    override fun findByOccurredAtBetween(
        startInclusive: Instant,
        endExclusive: Instant
    ): List<PersistedEventRecord> {
        return records.filter { it.occurredAt >= startInclusive && it.occurredAt < endExclusive }
    }
}
