package com.balfouriana.service.validation

import com.balfouriana.domain.CanonicalRecordMappedEvent
import com.balfouriana.domain.CanonicalRecordType
import com.balfouriana.domain.ConfidenceEscalationEvaluatedEvent
import com.balfouriana.domain.ConfidenceLevel
import com.balfouriana.domain.DomainEvent
import com.balfouriana.domain.EscalationPriority
import com.balfouriana.domain.EventMetadata
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
class ConfidenceEscalationIntegrationTest {
    @Autowired
    lateinit var validationAndMappingService: ValidationAndMappingService

    @Autowired
    lateinit var eventStoreRepository: EventStoreRepository

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Test
    fun `step2 blocking path emits low confidence escalation event`() {
        val correlationId = UUID.randomUUID()
        val event = mappedEvent(
            correlationId = correlationId,
            regimes = setOf(RegulatoryRegime.MIFID_II),
            fields = mapOf(
                "record_type" to "TRADE",
                "instrument_id" to "",
                "trade_date" to "2026-04-22",
                "quantity" to "100",
                "price" to "10.3",
                "currency" to "GBP",
                "venue" to "xlon"
            )
        )
        eventStoreRepository.append(event)
        validationAndMappingService.process(event)
        val confidence = lastConfidenceEvent(correlationId)
        assertEquals(ConfidenceLevel.LOW, confidence.confidenceLevel)
        assertEquals(EscalationPriority.P1_BLOCKING, confidence.escalationPriority)
    }

    @Test
    fun `step3 review path emits medium confidence escalation event`() {
        val correlationId = UUID.randomUUID()
        val event = mappedEvent(
            correlationId = correlationId,
            regimes = setOf(RegulatoryRegime.AIFMD_II),
            fields = mapOf(
                "record_type" to "TRADE",
                "instrument_id" to "GB00B03MLX29",
                "trade_date" to "2026-04-20",
                "quantity" to "10",
                "price" to "2.5",
                "currency" to "GBP",
                "venue" to "xlon",
                "counterparty_lei" to "5493001KJTIIGC8Y1R12",
                "aifmd_commitment_exposure" to "150",
                "aifmd_nav" to "100",
                "aifmd_gross_exposure" to "120",
                "aifmd_fund_structure" to "UNKNOWN",
                "aifmd_delegated_nav" to "10",
                "aifmd_total_nav" to "100",
                "aifmd_internal_fte" to "4",
                "aifmd_delegated_fte" to "1",
                "aifmd_lmt_used" to "N",
                "aifmd_largest_borrower_exposure" to "15",
                "aifmd_total_loan_exposure" to "100",
                "aifmd_retained_amount" to "10",
                "aifmd_securitized_total" to "100"
            )
        )
        eventStoreRepository.append(event)
        validationAndMappingService.process(event)
        val confidence = lastConfidenceEvent(correlationId)
        assertEquals(ConfidenceLevel.MEDIUM, confidence.confidenceLevel)
        assertEquals(EscalationPriority.P2_REVIEW, confidence.escalationPriority)
    }

    @Test
    fun `replay keeps deterministic confidence mapping and routing`() {
        val correlationId = UUID.randomUUID()
        val event = mappedEvent(
            correlationId = correlationId,
            regimes = setOf(RegulatoryRegime.MIFID_II),
            fields = mapOf(
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
        eventStoreRepository.append(event)
        validationAndMappingService.process(event)
        validationAndMappingService.process(event)

        val confidenceEvents = eventStoreRepository.findByCorrelationId(correlationId)
            .filter { it.eventType == "ConfidenceEscalationEvaluatedEvent" }
            .map { objectMapper.readValue<DomainEvent>(it.payload) as ConfidenceEscalationEvaluatedEvent }
        assertTrue(confidenceEvents.size >= 2)
        val first = confidenceEvents[confidenceEvents.size - 2]
        val second = confidenceEvents.last()
        assertEquals(first.confidenceLevel, second.confidenceLevel)
        assertEquals(first.escalationPriority, second.escalationPriority)
        assertEquals(first.routingKey, second.routingKey)
        assertEquals(first.sourceReasonCodes, second.sourceReasonCodes)
    }

    private fun lastConfidenceEvent(correlationId: UUID): ConfidenceEscalationEvaluatedEvent {
        val payload = eventStoreRepository.findByCorrelationId(correlationId)
            .last { it.eventType == "ConfidenceEscalationEvaluatedEvent" }
            .payload
        return objectMapper.readValue<DomainEvent>(payload) as ConfidenceEscalationEvaluatedEvent
    }

    private fun mappedEvent(
        correlationId: UUID,
        regimes: Set<RegulatoryRegime>,
        fields: Map<String, String>
    ): CanonicalRecordMappedEvent {
        val now = Instant.parse("2026-04-30T12:20:00Z")
        return CanonicalRecordMappedEvent(
            metadata = EventMetadata(
                eventId = UUID.randomUUID(),
                correlationId = correlationId,
                sourceSystem = "integration-test",
                occurredAt = now,
                schemaVersion = "ingestion.parse.v1",
                regimes = regimes
            ),
            artifactId = UUID.randomUUID(),
            envelope = SourceRecordEnvelope(
                sourceId = "integration-test",
                sourceSystem = "integration-test",
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
