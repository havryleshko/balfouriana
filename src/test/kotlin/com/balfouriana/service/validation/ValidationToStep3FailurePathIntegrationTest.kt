package com.balfouriana.service.validation

import com.balfouriana.domain.CanonicalRecordMappedEvent
import com.balfouriana.domain.CanonicalRecordType
import com.balfouriana.domain.DomainEvent
import com.balfouriana.domain.EventMetadata
import com.balfouriana.domain.IngestionChannel
import com.balfouriana.domain.IngestionFileFormat
import com.balfouriana.domain.RegulatoryRegime
import com.balfouriana.domain.RuleExceptionRaisedEvent
import com.balfouriana.domain.SourceRecordEnvelope
import com.balfouriana.repository.EventStoreRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.util.UUID

@SpringBootTest
@ActiveProfiles("local")
class ValidationToStep3FailurePathIntegrationTest {
    @Autowired
    lateinit var validationAndMappingService: ValidationAndMappingService

    @Autowired
    lateinit var eventStoreRepository: EventStoreRepository

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Test
    fun `emir blocking path emits rule exceptions and suppresses filing ready`() {
        val correlationId = UUID.randomUUID()
        val event = mappedEvent(
            correlationId = correlationId,
            regimes = setOf(RegulatoryRegime.EMIR),
            fields = mapOf(
                "record_type" to "TRADE",
                "instrument_id" to "GB00B03MLX29",
                "trade_date" to "2026-04-20",
                "quantity" to "10",
                "price" to "2.5",
                "currency" to "GBP",
                "venue" to "xlon",
                "counterparty_lei" to "5493001KJTIIGC8Y1R12",
                "cleared_status" to "Y",
                "clearing_member_lei" to "",
                "collateral_portfolio_code" to "",
                "initial_margin_posted" to "-1",
                "variation_margin_posted" to "1",
                "lifecycle_event_type" to "",
                "lifecycle_sequence" to "0",
                "valuation_amount" to "",
                "valuation_timestamp" to "",
                "reconciliation_reference" to ""
            )
        )
        eventStoreRepository.append(event)

        val result = validationAndMappingService.process(event)
        assertTrue(result.emittedValidatedEvent)

        val chain = eventStoreRepository.findByCorrelationId(correlationId)
        assertTrue(chain.any { it.eventType == "RuleExceptionRaisedEvent" })
        assertFalse(chain.any { it.eventType == "FilingReadyRecordEvent" })

        val exceptionEvent = chain
            .asSequence()
            .filter { it.eventType == "RuleExceptionRaisedEvent" }
            .map { objectMapper.readValue<DomainEvent>(it.payload) as RuleExceptionRaisedEvent }
            .first()
        assertEquals("EMIR", exceptionEvent.exception.regime?.name)
        assertTrue(exceptionEvent.exception.reasonCode.startsWith("EMIR_"))
        assertTrue(exceptionEvent.exception.remediationHint.isNotBlank())
    }

    @Test
    fun `aifmd warning path keeps filing ready and records review exception`() {
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

        val result = validationAndMappingService.process(event)
        assertTrue(result.emittedValidatedEvent)

        val chain = eventStoreRepository.findByCorrelationId(correlationId)
        assertTrue(chain.any { it.eventType == "RuleExceptionRaisedEvent" })
        assertTrue(chain.any { it.eventType == "FilingReadyRecordEvent" })
    }

    private fun mappedEvent(
        correlationId: UUID,
        regimes: Set<RegulatoryRegime>,
        fields: Map<String, String>
    ): CanonicalRecordMappedEvent {
        val now = Instant.parse("2026-04-30T10:00:00Z")
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
