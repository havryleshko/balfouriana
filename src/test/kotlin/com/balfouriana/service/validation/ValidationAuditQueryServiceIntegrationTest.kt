package com.balfouriana.service.validation

import com.balfouriana.domain.CanonicalRecordMappedEvent
import com.balfouriana.domain.CanonicalRecordType
import com.balfouriana.domain.EventMetadata
import com.balfouriana.domain.IngestionChannel
import com.balfouriana.domain.IngestionFileFormat
import com.balfouriana.domain.RegulatoryRegime
import com.balfouriana.domain.SourceRecordEnvelope
import com.balfouriana.repository.EventStoreRepository
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.util.UUID

@SpringBootTest
@ActiveProfiles("local")
class ValidationAuditQueryServiceIntegrationTest {
    @Autowired
    lateinit var validationAndMappingService: ValidationAndMappingService

    @Autowired
    lateinit var validationAuditQueryService: ValidationAuditQueryService

    @Autowired
    lateinit var eventStoreRepository: EventStoreRepository

    @Test
    fun `returns decision chain by correlation id`() {
        val correlationId = UUID.randomUUID()
        val event = canonicalEvent(correlationId)
        eventStoreRepository.append(event)

        validationAndMappingService.process(event)
        val chain = validationAuditQueryService.decisionChainByCorrelationId(correlationId)

        assertTrue(chain.any { it.eventType == "ValidationDecisionEvent" })
        assertTrue(chain.any { it.eventType == "CanonicalRecordValidatedEvent" })
        assertTrue(chain.any { it.eventType == "RuleDecisionEvent" })
        assertTrue(chain.any { it.eventType == "CalculationAppliedEvent" })
        assertTrue(chain.any { it.eventType == "FilingReadyRecordEvent" })
    }

    @Test
    fun `aifmd regime audit chain includes step3 events`() {
        val correlationId = UUID.randomUUID()
        val event = canonicalAifmdEvent(correlationId)
        eventStoreRepository.append(event)

        validationAndMappingService.process(event)
        val chain = validationAuditQueryService.decisionChainByCorrelationId(correlationId)

        assertTrue(chain.any { it.eventType == "RuleDecisionEvent" })
        assertTrue(chain.any { it.eventType == "CalculationAppliedEvent" })
        assertTrue(chain.any { it.eventType == "FilingReadyRecordEvent" })
    }

    private fun canonicalEvent(correlationId: UUID): CanonicalRecordMappedEvent {
        val now = Instant.parse("2026-04-25T12:00:00Z")
        return CanonicalRecordMappedEvent(
            metadata = EventMetadata(
                eventId = UUID.randomUUID(),
                correlationId = correlationId,
                sourceSystem = "integration-test",
                occurredAt = now,
                schemaVersion = "ingestion.parse.v1",
                regimes = setOf(RegulatoryRegime.MIFID_II)
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
            canonicalFields = mapOf(
                "record_type" to "TRADE",
                "instrument_id" to "GB00B03MLX29",
                "trade_date" to "2026-04-20",
                "quantity" to "100",
                "price" to "12.45",
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

    private fun canonicalAifmdEvent(correlationId: UUID): CanonicalRecordMappedEvent {
        val base = canonicalEvent(correlationId)
        return base.copy(
            metadata = base.metadata.copy(regimes = setOf(RegulatoryRegime.AIFMD_II)),
            canonicalFields = base.canonicalFields + mapOf(
                "aifmd_commitment_exposure" to "150",
                "aifmd_nav" to "100",
                "aifmd_gross_exposure" to "120",
                "aifmd_fund_structure" to "OPEN_ENDED",
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
    }
}
