package com.balfouriana.service.rules

import com.balfouriana.domain.CanonicalRecordType
import com.balfouriana.domain.CanonicalRecordValidatedEvent
import com.balfouriana.domain.EventMetadata
import com.balfouriana.domain.IngestionChannel
import com.balfouriana.domain.IngestionFileFormat
import com.balfouriana.domain.RegulatoryRegime
import com.balfouriana.domain.RuleOutcome
import com.balfouriana.domain.SourceRecordEnvelope
import com.balfouriana.domain.ValidationPackVersion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class Step3RuleBoundaryTest {
    @Test
    fun `aifmd lmt rule returns needs review for missing indicator`() {
        val outcome = AifmdLmtUsageRule().evaluate(
            validatedEvent(
                setOf(RegulatoryRegime.AIFMD_II),
                mapOf("aifmd_lmt_used" to "")
            )
        )
        assertEquals(RuleOutcome.NEEDS_REVIEW, outcome.outcome)
        assertEquals("AIFMD_LMT_INDICATOR_MISSING", outcome.reasonCode)
    }

    @Test
    fun `aifmd lof cap rule fails exactly above open ended threshold`() {
        val outcome = AifmdLofLeverageCapRule().evaluate(
            validatedEvent(
                setOf(RegulatoryRegime.AIFMD_II),
                mapOf(
                    "aifmd_fund_structure" to "OPEN_ENDED",
                    "aifmd_commitment_exposure" to "175.0001",
                    "aifmd_nav" to "100"
                )
            )
        )
        assertEquals(RuleOutcome.FAIL, outcome.outcome)
        assertEquals("AIFMD_LOF_CAP_BREACH", outcome.reasonCode)
    }

    @Test
    fun `emir margin rule accepts zero values and rejects negatives`() {
        val rule = EmirMarginConsistencyRule()
        val zeroOutcome = rule.evaluate(
            validatedEvent(
                setOf(RegulatoryRegime.EMIR),
                mapOf(
                    "initial_margin_posted" to "0",
                    "variation_margin_posted" to "0"
                )
            )
        )
        assertEquals(RuleOutcome.PASS, zeroOutcome.outcome)
        val negativeOutcome = rule.evaluate(
            validatedEvent(
                setOf(RegulatoryRegime.EMIR),
                mapOf(
                    "initial_margin_posted" to "-0.0001",
                    "variation_margin_posted" to "0"
                )
            )
        )
        assertEquals(RuleOutcome.FAIL, negativeOutcome.outcome)
        assertEquals("EMIR_INVALID_MARGIN_FIELDS", negativeOutcome.reasonCode)
    }

    @Test
    fun `notional calculation applies half up rounding at 8 decimals`() {
        val output = NotionalCalculation().apply(
            validatedEvent(
                setOf(RegulatoryRegime.MIFID_II),
                mapOf(
                    "quantity" to "1.23456789",
                    "price" to "2.34567891"
                )
            )
        )
        assertEquals("2.89589986", output.calculatedFields["calculated_notional"])
    }

    private fun validatedEvent(
        regimes: Set<RegulatoryRegime>,
        fields: Map<String, String>
    ): CanonicalRecordValidatedEvent {
        val now = Instant.parse("2026-04-30T11:30:00Z")
        return CanonicalRecordValidatedEvent(
            metadata = EventMetadata(
                eventId = UUID.randomUUID(),
                correlationId = UUID.randomUUID(),
                sourceSystem = "rules-boundary-test",
                occurredAt = now,
                schemaVersion = "validation.step2.validated.v1",
                regimes = regimes
            ),
            artifactId = UUID.randomUUID(),
            envelope = SourceRecordEnvelope(
                sourceId = "rules-boundary-test",
                sourceSystem = "rules-boundary-test",
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
            validatedFields = fields,
            enrichmentMetadata = emptyMap()
        )
    }
}
