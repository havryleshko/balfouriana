package com.balfouriana.service.rules

import com.balfouriana.domain.CanonicalRecordType
import com.balfouriana.domain.CanonicalRecordValidatedEvent
import com.balfouriana.domain.CalculationAppliedEvent
import com.balfouriana.domain.FilingReadyRecordEvent
import com.balfouriana.domain.DomainEvent
import com.balfouriana.domain.RuleDecisionEvent
import com.balfouriana.domain.EventMetadata
import com.balfouriana.domain.IngestionChannel
import com.balfouriana.domain.IngestionFileFormat
import com.balfouriana.domain.RegulatoryRegime
import com.balfouriana.domain.SourceRecordEnvelope
import com.balfouriana.domain.ValidationPackVersion
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

class RuleEngineServiceTest {
    private val repository = InMemoryEventStoreRepository()
    private val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build()).findAndRegisterModules()
    private val service = RuleEngineService(
        rulePackRegistry = RulePackRegistry(),
        eventStoreRepository = repository,
        objectMapper = mapper
    )

    @Test
    fun `emits filing ready for valid mifid input`() {
        val result = service.process(
            validatedEvent(
                fields = mapOf(
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
                regimes = setOf(RegulatoryRegime.MIFID_II)
            )
        )
        assertTrue(result.emittedFilingReadyEvent)
        assertTrue(result.decisionCount >= 8)
        assertEquals(2, result.calculationCount)
        assertTrue(repository.eventTypes().contains("FilingReadyRecordEvent"))
    }

    @Test
    fun `emits decision and exception only for blocking emir input`() {
        val result = service.process(
            validatedEvent(
                fields = mapOf(
                    "record_type" to "TRADE",
                    "quantity" to "10",
                    "price" to "5",
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
                ),
                regimes = setOf(RegulatoryRegime.EMIR)
            )
        )
        assertFalse(result.emittedFilingReadyEvent)
        assertTrue(repository.eventTypes().contains("RuleExceptionRaisedEvent"))
        assertFalse(repository.eventTypes().contains("FilingReadyRecordEvent"))
    }

    @Test
    fun `produces deterministic fingerprint for same input and pack`() {
        val event = validatedEvent(
            fields = mapOf(
                "record_type" to "TRADE",
                "quantity" to "3",
                "price" to "11",
                "buyer_lei" to "5493001KJTIIGC8Y1R12",
                "seller_lei" to "213800D1EI4B9WTWWD28",
                "decision_maker_lei" to "7245008N4E6Y7Z5RAA41",
                "execution_actor_type" to "ALGORITHM",
                "venue_code" to "XOFF",
                "otc_indicator" to "Y",
                "waiver_indicator" to "N",
                "short_selling_indicator" to "N",
                "commodity_derivative_indicator" to "N",
                "price_notation" to "MONETARY",
                "price_currency" to "USD"
            ),
            regimes = setOf(RegulatoryRegime.MIFID_II)
        )
        val first = service.process(event)
        val second = service.process(event)
        assertEquals(first.outputFingerprint, second.outputFingerprint)
    }

    @Test
    fun `uses emir precedence when both regimes are present`() {
        val result = service.process(
            validatedEvent(
                fields = mapOf(
                    "record_type" to "TRADE",
                    "quantity" to "1",
                    "price" to "2",
                    "cleared_status" to "N",
                    "collateral_portfolio_code" to "PORT-1",
                    "initial_margin_posted" to "2",
                    "variation_margin_posted" to "3",
                    "lifecycle_event_type" to "MODI",
                    "lifecycle_sequence" to "1",
                    "valuation_amount" to "100",
                    "valuation_timestamp" to "2026-04-29T12:00:00Z",
                    "reconciliation_reference" to "REC-1"
                ),
                regimes = setOf(RegulatoryRegime.MIFID_II, RegulatoryRegime.EMIR)
            )
        )
        assertTrue(result.emittedFilingReadyEvent)
        assertEquals(2, result.calculationCount)
    }

    @Test
    fun `filing ready contract includes trace metadata and derived fields`() {
        service.process(
            validatedEvent(
                fields = mapOf(
                    "record_type" to "TRADE",
                    "quantity" to "10",
                    "price" to "2",
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
                regimes = setOf(RegulatoryRegime.MIFID_II)
            )
        )
        val filingReady = repository.events().filterIsInstance<FilingReadyRecordEvent>().last()
        assertTrue(filingReady.traceMetadata.containsKey("sourceValidatedSchemaVersion"))
        assertEquals("step3-mifid-transaction-rules", filingReady.traceMetadata["step3_rule_pack_id"])
        assertTrue(filingReady.traceMetadata["step3_applied_calculation_ids"]!!.contains("step3.notional"))
        assertTrue(filingReady.filingReadyFields.containsKey("calculated_notional"))
        assertTrue(filingReady.filingReadyFields.containsKey("mifid_execution_mode"))
    }

    @Test
    fun `aifmd pack emits calculations regulatory trace and filing metadata`() {
        val result = service.process(validatedEvent(fields = aifmdValidFields(), regimes = setOf(RegulatoryRegime.AIFMD_II)))
        assertTrue(result.emittedFilingReadyEvent)
        assertEquals(5, result.calculationCount)
        val filingReady = repository.events().filterIsInstance<FilingReadyRecordEvent>().last()
        assertEquals("step3-aifmd-annex-iv-calcs", filingReady.traceMetadata["step3_rule_pack_id"])
        assertEquals("false", filingReady.traceMetadata["aifmd_review_required"])
        assertEquals("0", filingReady.traceMetadata["aifmd_blocking_error_count"])
        assertEquals("0", filingReady.traceMetadata["aifmd_needs_review_count"])
        assertEquals("aifmd_commitment_leverage_ratio", filingReady.traceMetadata["aifmd_primary_metric"])
        assertTrue(filingReady.filingReadyFields.containsKey("aifmd_commitment_leverage_ratio"))
        val calcs = repository.events().filterIsInstance<CalculationAppliedEvent>()
        assertTrue(calcs.all { it.calculationMetadata.containsKey("regulatory_source_authority") })
        val decisions = repository.events().filterIsInstance<RuleDecisionEvent>()
        assertTrue(decisions.any { it.ruleResult.ruleId.startsWith("aifmd.") && it.ruleResult.sourceAuthority.isNotBlank() })
    }

    @Test
    fun `aifmd lof breach blocks filing ready`() {
        val fields = aifmdValidFields().toMutableMap()
        fields["aifmd_commitment_exposure"] = "180"
        fields["aifmd_nav"] = "100"
        fields["aifmd_fund_structure"] = "OPEN_ENDED"
        val result = service.process(validatedEvent(fields = fields, regimes = setOf(RegulatoryRegime.AIFMD_II)))
        assertFalse(result.emittedFilingReadyEvent)
        assertFalse(repository.eventTypes().contains("FilingReadyRecordEvent"))
    }

    @Test
    fun `aifmd unknown fund structure still emits filing with review metadata`() {
        val fields = aifmdValidFields().toMutableMap()
        fields["aifmd_fund_structure"] = "UNKNOWN"
        val result = service.process(validatedEvent(fields = fields, regimes = setOf(RegulatoryRegime.AIFMD_II)))
        assertTrue(result.emittedFilingReadyEvent)
        val filingReady = repository.events().filterIsInstance<FilingReadyRecordEvent>().last()
        assertEquals("true", filingReady.traceMetadata["aifmd_review_required"])
        assertTrue(result.exceptionCount >= 1)
    }

    private fun aifmdValidFields(): Map<String, String> = mapOf(
        "record_type" to "TRADE",
        "quantity" to "10",
        "price" to "2",
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

    private fun validatedEvent(fields: Map<String, String>, regimes: Set<RegulatoryRegime>): CanonicalRecordValidatedEvent {
        val now = Instant.parse("2026-04-29T12:00:00Z")
        return CanonicalRecordValidatedEvent(
            metadata = EventMetadata(
                eventId = UUID.randomUUID(),
                correlationId = UUID.randomUUID(),
                sourceSystem = "test",
                occurredAt = now,
                schemaVersion = "validation.step2.validated.v1",
                regimes = regimes
            ),
            artifactId = UUID.randomUUID(),
            envelope = SourceRecordEnvelope(
                sourceId = "test",
                sourceSystem = "test",
                ingestionChannel = IngestionChannel.REST,
                fileId = UUID.randomUUID(),
                originalFileName = "test.csv",
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
            validatedFields = fields,
            enrichmentMetadata = emptyMap()
        )
    }
}

private class InMemoryEventStoreRepository : EventStoreRepository {
    private val records = mutableListOf<PersistedEventRecord>()

    override fun append(event: DomainEvent) {
        rawEvents.add(event)
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

    override fun findByOccurredAtBetween(startInclusive: Instant, endExclusive: Instant): List<PersistedEventRecord> {
        return records.filter { it.occurredAt >= startInclusive && it.occurredAt < endExclusive }
    }

    fun eventTypes(): Set<String> = records.map { it.eventType }.toSet()

    fun events(): List<DomainEvent> = rawEvents.toList()

    private val rawEvents = mutableListOf<DomainEvent>()
}
