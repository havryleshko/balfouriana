package com.balfouriana.repository

import com.balfouriana.domain.CanonicalRecordType
import com.balfouriana.domain.CanonicalRecordValidatedEvent
import com.balfouriana.domain.DomainEvent
import com.balfouriana.domain.EventMetadata
import com.balfouriana.domain.IngestionChannel
import com.balfouriana.domain.IngestionFileFormat
import com.balfouriana.domain.RegulatoryRegime
import com.balfouriana.domain.SourceRecordEnvelope
import com.balfouriana.domain.ValidationPackVersion
import com.balfouriana.service.rules.RuleEngineService
import com.balfouriana.service.rules.RulePackRegistry
import com.balfouriana.service.validation.ConfidenceEscalationService
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
class JdbcEventStoreRepositoryStep3IntegrationTest {
    @Autowired
    lateinit var repository: EventStoreRepository

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Test
    fun `persists and reads ordered step3 chain with decodable payloads`() {
        val correlationId = UUID.randomUUID()
        val service = RuleEngineService(
            rulePackRegistry = RulePackRegistry(),
            eventStoreRepository = repository,
            objectMapper = objectMapper,
            confidenceEscalationService = ConfidenceEscalationService(objectMapper)
        )
        service.process(validatedEvent(correlationId))

        val records = repository.findByCorrelationId(correlationId)
        assertTrue(records.zipWithNext().all { (left, right) -> !left.occurredAt.isAfter(right.occurredAt) })
        assertTrue(records.any { it.eventType == "RuleDecisionEvent" && it.schemaVersion == "rules.step3.decision.v1" })
        assertTrue(records.any { it.eventType == "CalculationAppliedEvent" && it.schemaVersion == "rules.step3.calculation.v1" })
        assertTrue(records.any { it.eventType == "FilingReadyRecordEvent" && it.schemaVersion == "rules.step3.filing-ready.v1" })
        assertTrue(records.all { it.regimes == setOf(RegulatoryRegime.MIFID_II) })

        val filingPayload = records.last { it.eventType == "FilingReadyRecordEvent" }.payload
        val filingEvent = objectMapper.readValue<DomainEvent>(filingPayload)
        assertEquals("FilingReadyRecordEvent", filingEvent.javaClass.simpleName)
    }

    private fun validatedEvent(correlationId: UUID): CanonicalRecordValidatedEvent {
        val now = Instant.parse("2026-04-30T11:00:00Z")
        return CanonicalRecordValidatedEvent(
            metadata = EventMetadata(
                eventId = UUID.randomUUID(),
                correlationId = correlationId,
                sourceSystem = "repository-test",
                occurredAt = now,
                schemaVersion = "validation.step2.validated.v1",
                regimes = setOf(RegulatoryRegime.MIFID_II)
            ),
            artifactId = UUID.randomUUID(),
            envelope = SourceRecordEnvelope(
                sourceId = "repository-test",
                sourceSystem = "repository-test",
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
            validationPack = ValidationPackVersion("step2-core", "2026.04.19", Instant.parse("2026-04-19T00:00:00Z")),
            validatedFields = mapOf(
                "record_type" to "TRADE",
                "quantity" to "10",
                "price" to "2.5",
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
            ),
            enrichmentMetadata = emptyMap()
        )
    }
}
