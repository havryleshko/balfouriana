package com.balfouriana.service.validation

import com.balfouriana.domain.CanonicalRecordMappedEvent
import com.balfouriana.domain.CanonicalRecordType
import com.balfouriana.domain.ConfidenceEscalationEvaluatedEvent
import com.balfouriana.domain.ConfidenceLevel
import com.balfouriana.domain.DomainEvent
import com.balfouriana.domain.EventMetadata
import com.balfouriana.domain.EscalationPriority
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
class ValidationAuditQueryServiceIntegrationTest {
    @Autowired
    lateinit var validationAndMappingService: ValidationAndMappingService

    @Autowired
    lateinit var validationAuditQueryService: ValidationAuditQueryService

    @Autowired
    lateinit var eventStoreRepository: EventStoreRepository

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Test
    fun `returns decision chain by correlation id`() {
        val correlationId = UUID.randomUUID()
        val event = canonicalEvent(correlationId)
        eventStoreRepository.append(event)

        validationAndMappingService.process(event)
        val chain = validationAuditQueryService.decisionChainByCorrelationId(correlationId)

        assertEquals(correlationId, chain.first().correlationId)
        assertTrue(chain.zipWithNext().all { (left, right) -> !left.occurredAt.isAfter(right.occurredAt) })
        assertTrue(chain.any { it.eventType == "ValidationDecisionEvent" })
        assertTrue(chain.any { it.eventType == "CanonicalRecordValidatedEvent" })
        assertTrue(chain.any { it.eventType == "RuleDecisionEvent" })
        assertTrue(chain.any { it.eventType == "CalculationAppliedEvent" })
        assertTrue(chain.any { it.eventType == "FilingReadyRecordEvent" })
        assertTrue(chain.any { it.eventType == "ConfidenceEscalationEvaluatedEvent" })
        assertTrue(chain.any { it.eventType == "FilingGenerationRequestedEvent" })
        assertTrue(chain.any { it.eventType == "FilingGeneratedEvent" })
        assertTrue(chain.any { it.eventType == "FilingSubmissionRequestedEvent" })
        assertTrue(chain.any { it.eventType == "FilingSubmittedEvent" })
        assertTrue(chain.any { it.schemaVersion == "validation.step2.validated.v1" })
        assertTrue(chain.any { it.schemaVersion == "rules.step3.filing-ready.v1" })
        assertTrue(chain.all { it.regimes == setOf(RegulatoryRegime.MIFID_II) })

        val filingReadyPayload = chain.last { it.eventType == "FilingReadyRecordEvent" }.payload
        val filingReady = objectMapper.readValue<DomainEvent>(filingReadyPayload) as FilingReadyRecordEvent
        assertTrue(filingReady.traceMetadata.containsKey("step3_rule_pack_id"))
        assertTrue(filingReady.traceMetadata.containsKey("step3_rule_pack_version"))
        assertTrue(filingReady.traceMetadata.containsKey("step3_applied_calculation_ids"))
        val confidencePayload = chain.last { it.eventType == "ConfidenceEscalationEvaluatedEvent" }.payload
        val confidence = objectMapper.readValue<DomainEvent>(confidencePayload) as ConfidenceEscalationEvaluatedEvent
        assertEquals(ConfidenceLevel.HIGH, confidence.confidenceLevel)
        assertEquals(EscalationPriority.P3_MONITOR, confidence.escalationPriority)
    }

    @Test
    fun `aifmd regime audit chain includes step3 events`() {
        val correlationId = UUID.randomUUID()
        val event = canonicalAifmdEvent(correlationId)
        eventStoreRepository.append(event)

        validationAndMappingService.process(event)
        val chain = validationAuditQueryService.decisionChainByCorrelationId(correlationId)

        assertTrue(chain.zipWithNext().all { (left, right) -> !left.occurredAt.isAfter(right.occurredAt) })
        assertTrue(chain.all { it.regimes == setOf(RegulatoryRegime.AIFMD_II) })
        assertTrue(chain.any { it.eventType == "RuleDecisionEvent" })
        assertTrue(chain.any { it.eventType == "CalculationAppliedEvent" })
        assertTrue(chain.any { it.eventType == "FilingReadyRecordEvent" })
        assertTrue(chain.any { it.eventType == "ConfidenceEscalationEvaluatedEvent" })
        assertTrue(chain.any { it.eventType == "FilingGenerationRequestedEvent" })
        assertTrue(chain.any { it.eventType == "FilingGeneratedEvent" })
        assertTrue(chain.any { it.eventType == "FilingSubmissionRequestedEvent" })
        assertTrue(chain.any { it.eventType == "FilingSubmittedEvent" })
        val filingReadyPayload = chain.last { it.eventType == "FilingReadyRecordEvent" }.payload
        val filingReady = objectMapper.readValue<DomainEvent>(filingReadyPayload) as FilingReadyRecordEvent
        assertEquals("step3-aifmd-annex-iv-calcs", filingReady.traceMetadata["step3_rule_pack_id"])
        assertTrue(filingReady.traceMetadata.containsKey("aifmd_primary_metric"))
        val confidencePayload = chain.last { it.eventType == "ConfidenceEscalationEvaluatedEvent" }.payload
        val confidence = objectMapper.readValue<DomainEvent>(confidencePayload) as ConfidenceEscalationEvaluatedEvent
        assertEquals(ConfidenceLevel.HIGH, confidence.confidenceLevel)
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
                "trade_id" to "T-100",
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
