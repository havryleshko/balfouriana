package com.balfouriana.service.validation

import com.balfouriana.domain.CanonicalRecordMappedEvent
import com.balfouriana.domain.CanonicalRecordType
import com.balfouriana.domain.CanonicalRecordValidatedEvent
import com.balfouriana.domain.ConfidenceLevel
import com.balfouriana.domain.ConfidenceSourceStage
import com.balfouriana.domain.EscalationPriority
import com.balfouriana.domain.EventMetadata
import com.balfouriana.domain.IngestionChannel
import com.balfouriana.domain.IngestionFileFormat
import com.balfouriana.domain.RegulatoryRegime
import com.balfouriana.domain.RuleExceptionEnvelope
import com.balfouriana.domain.RuleSeverity
import com.balfouriana.domain.RulePackVersion
import com.balfouriana.domain.SourceRecordEnvelope
import com.balfouriana.domain.ValidationExceptionEnvelope
import com.balfouriana.domain.ValidationPackVersion
import com.balfouriana.domain.ValidationSeverity
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class ConfidenceEscalationServiceTest {
    private val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build()).findAndRegisterModules()
    private val service = ConfidenceEscalationService(mapper)

    @Test
    fun `step2 blocking exceptions produce low confidence and p1 priority`() {
        val event = mappedEvent(setOf(RegulatoryRegime.MIFID_II))
        val result = service.forValidation(
            event = event,
            validationPack = ValidationPackVersion("step2-core", "2026.04.19", Instant.parse("2026-04-19T00:00:00Z")),
            exceptions = listOf(
                ValidationExceptionEnvelope(
                    exceptionId = UUID.randomUUID(),
                    correlationId = event.metadata.correlationId,
                    eventId = event.metadata.eventId,
                    regime = RegulatoryRegime.MIFID_II,
                    ruleId = "rule",
                    severity = ValidationSeverity.ERROR,
                    rejectionCategory = "SCHEMA",
                    reasonCode = "MISSING_REQUIRED_FIELD",
                    message = "missing",
                    remediationHint = "fix"
                )
            ),
            inputFingerprint = "in",
            outputFingerprint = "out"
        )
        assertEquals(ConfidenceSourceStage.STEP2_VALIDATION, result.sourceStage)
        assertEquals(ConfidenceLevel.LOW, result.confidenceLevel)
        assertEquals(EscalationPriority.P1_BLOCKING, result.escalationPriority)
    }

    @Test
    fun `step3 review exceptions produce medium confidence and p2 priority`() {
        val event = validatedEvent(setOf(RegulatoryRegime.AIFMD_II))
        val result = service.forRules(
            event = event,
            rulePack = RulePackVersion("step3-aifmd-annex-iv-calcs", "2026.04.30", Instant.parse("2026-04-28T00:00:00Z")),
            exceptions = listOf(
                RuleExceptionEnvelope(
                    exceptionId = UUID.randomUUID(),
                    correlationId = event.metadata.correlationId,
                    eventId = event.metadata.eventId,
                    regime = RegulatoryRegime.AIFMD_II,
                    ruleId = "rule",
                    severity = RuleSeverity.WARNING,
                    rejectionCategory = "CALCULATION",
                    reasonCode = "AIFMD_FUND_STRUCTURE_UNKNOWN",
                    message = "review",
                    remediationHint = "fix"
                )
            ),
            inputFingerprint = "in",
            outputFingerprint = "out"
        )
        assertEquals(ConfidenceSourceStage.STEP3_RULES, result.sourceStage)
        assertEquals(ConfidenceLevel.MEDIUM, result.confidenceLevel)
        assertEquals(EscalationPriority.P2_REVIEW, result.escalationPriority)
    }

    @Test
    fun `no exceptions produce high confidence and p3 priority`() {
        val event = validatedEvent(setOf(RegulatoryRegime.EMIR))
        val result = service.forRules(
            event = event,
            rulePack = RulePackVersion("step3-emir-transaction-rules", "2026.04.30", Instant.parse("2026-04-28T00:00:00Z")),
            exceptions = emptyList(),
            inputFingerprint = "in",
            outputFingerprint = "out"
        )
        assertEquals(ConfidenceLevel.HIGH, result.confidenceLevel)
        assertEquals(EscalationPriority.P3_MONITOR, result.escalationPriority)
        assertEquals("EMIR.NONE.P3_MONITOR", result.routingKey)
    }

    private fun mappedEvent(regimes: Set<RegulatoryRegime>): CanonicalRecordMappedEvent {
        val now = Instant.parse("2026-04-30T12:10:00Z")
        return CanonicalRecordMappedEvent(
            metadata = EventMetadata(
                eventId = UUID.randomUUID(),
                correlationId = UUID.randomUUID(),
                sourceSystem = "test",
                occurredAt = now,
                schemaVersion = "ingestion.parse.v1",
                regimes = regimes
            ),
            artifactId = UUID.randomUUID(),
            envelope = SourceRecordEnvelope(
                sourceId = "test",
                sourceSystem = "test",
                ingestionChannel = IngestionChannel.REST,
                fileId = UUID.randomUUID(),
                originalFileName = "x.csv",
                recordIndex = 1,
                receivedAt = now,
                format = IngestionFileFormat.CSV,
                contentType = "text/csv",
                fileSizeBytes = 100,
                checksumSha256 = "abc",
                schemaHint = null
            ),
            recordType = CanonicalRecordType.TRADE,
            canonicalFields = emptyMap()
        )
    }

    private fun validatedEvent(regimes: Set<RegulatoryRegime>): CanonicalRecordValidatedEvent {
        val base = mappedEvent(regimes)
        return CanonicalRecordValidatedEvent(
            metadata = base.metadata.copy(schemaVersion = "validation.step2.validated.v1"),
            artifactId = base.artifactId,
            envelope = base.envelope,
            recordType = base.recordType,
            validationPack = ValidationPackVersion("step2-core", "2026.04.19", Instant.parse("2026-04-19T00:00:00Z")),
            validatedFields = mapOf("record_type" to "TRADE"),
            enrichmentMetadata = emptyMap()
        )
    }
}
