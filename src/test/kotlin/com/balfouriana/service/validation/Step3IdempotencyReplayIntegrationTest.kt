package com.balfouriana.service.validation

import com.balfouriana.domain.CanonicalRecordMappedEvent
import com.balfouriana.domain.CanonicalRecordType
import com.balfouriana.domain.DomainEvent
import com.balfouriana.domain.EventMetadata
import com.balfouriana.domain.FilingReadyRecordEvent
import com.balfouriana.domain.IngestionChannel
import com.balfouriana.domain.IngestionFileFormat
import com.balfouriana.domain.RegulatoryRegime
import com.balfouriana.domain.SourceRecordEnvelope
import com.balfouriana.repository.EventStoreRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.util.UUID

@SpringBootTest
@ActiveProfiles("local")
class Step3IdempotencyReplayIntegrationTest {
    @Autowired
    lateinit var validationAndMappingService: ValidationAndMappingService

    @Autowired
    lateinit var eventStoreRepository: EventStoreRepository

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Test
    fun `replaying same canonical input keeps deterministic step3 fingerprints and trace`() {
        val correlationId = UUID.randomUUID()
        val event = mappedEvent(correlationId)
        eventStoreRepository.append(event)

        val first = validationAndMappingService.process(event)
        val second = validationAndMappingService.process(event)
        assertEquals(first.outputFingerprint, second.outputFingerprint)

        val chain = eventStoreRepository.findByCorrelationId(correlationId)
        val filingReadyEvents = chain
            .asSequence()
            .filter { it.eventType == "FilingReadyRecordEvent" }
            .map { objectMapper.readValue<DomainEvent>(it.payload) as FilingReadyRecordEvent }
            .toList()

        assertEquals(2, filingReadyEvents.size)
        assertEquals(filingReadyEvents[0].outputFingerprint, filingReadyEvents[1].outputFingerprint)
        assertEquals(filingReadyEvents[0].rulePackVersion, filingReadyEvents[1].rulePackVersion)
        assertEquals(
            filingReadyEvents[0].traceMetadata["step3_applied_calculation_ids"],
            filingReadyEvents[1].traceMetadata["step3_applied_calculation_ids"]
        )
        assertTrue(filingReadyEvents.all { it.traceMetadata["step3_rule_pack_id"] == "step3-mifid-transaction-rules" })
    }

    private fun mappedEvent(correlationId: UUID): CanonicalRecordMappedEvent {
        val now = Instant.parse("2026-04-30T12:00:00Z")
        return CanonicalRecordMappedEvent(
            metadata = EventMetadata(
                eventId = UUID.randomUUID(),
                correlationId = correlationId,
                sourceSystem = "idempotency-test",
                occurredAt = now,
                schemaVersion = "ingestion.parse.v1",
                regimes = setOf(RegulatoryRegime.MIFID_II)
            ),
            artifactId = UUID.randomUUID(),
            envelope = SourceRecordEnvelope(
                sourceId = "idempotency-test",
                sourceSystem = "idempotency-test",
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
            canonicalFields = mapOf(
                "record_type" to "TRADE",
                "instrument_id" to "GB00B03MLX29",
                "trade_date" to "2026-04-20",
                "quantity" to "10",
                "price" to "2.5",
                "currency" to "GBP",
                "venue" to "xlon",
                "counterparty_lei" to "5493001KJTIIGC8Y1R12",
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
                "price_currency" to "GBP"
            )
        )
    }
}
