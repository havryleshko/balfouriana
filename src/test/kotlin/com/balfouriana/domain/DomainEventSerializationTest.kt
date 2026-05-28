package com.balfouriana.domain

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class DomainEventSerializationTest {
    private val mapper: ObjectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build()).findAndRegisterModules()

    @Test
    fun `serializes and deserializes ingestion accepted event`() {
        val event = IngestionAcceptedEvent(
            metadata = EventMetadata(
                eventId = UUID.randomUUID(),
                correlationId = UUID.randomUUID(),
                sourceSystem = "custodian-a",
                occurredAt = Instant.parse("2026-04-20T10:00:00Z"),
                schemaVersion = "1.0.0",
                regimes = setOf(RegulatoryRegime.MIFID_II, RegulatoryRegime.EMIR)
            ),
            fileName = "positions.csv",
            payloadChecksum = "abc123"
        )

        val payload = mapper.writeValueAsString(event)
        val decoded: DomainEvent = mapper.readValue(payload)

        assertEquals(event, decoded)
    }

    @Test
    fun `serializes and deserializes file received event`() {
        val event = FileReceivedEvent(
            metadata = EventMetadata(
                eventId = UUID.randomUUID(),
                correlationId = UUID.randomUUID(),
                sourceSystem = "rest-ingest",
                occurredAt = Instant.parse("2026-04-20T11:00:00Z"),
                schemaVersion = "ingestion.receive.v1",
                regimes = emptySet()
            ),
            channel = IngestionChannel.REST,
            artifactId = UUID.randomUUID(),
            originalFilename = "trades.csv",
            storedRelativePath = "received/artifact_trades.csv",
            byteSize = 42,
            payloadChecksumSha256 = "8f434346648f6b96df89dda901c5176b10a6d83961c116ea4a84f7df0cf2ce13"
        )
        val payload = mapper.writeValueAsString(event)
        val decoded: DomainEvent = mapper.readValue(payload)
        assertEquals(event, decoded)
    }

    @Test
    fun `serializes and deserializes canonical mapped event`() {
        val event = CanonicalRecordMappedEvent(
            metadata = EventMetadata(
                eventId = UUID.randomUUID(),
                correlationId = UUID.randomUUID(),
                sourceSystem = "rest-ingest",
                occurredAt = Instant.parse("2026-04-22T11:00:00Z"),
                schemaVersion = "ingestion.parse.v1",
                regimes = emptySet()
            ),
            artifactId = UUID.randomUUID(),
            envelope = SourceRecordEnvelope(
                sourceId = "rest-ingest",
                sourceSystem = "rest-ingest",
                ingestionChannel = IngestionChannel.REST,
                fileId = UUID.randomUUID(),
                originalFileName = "trades.csv",
                recordIndex = 1,
                receivedAt = Instant.parse("2026-04-22T10:59:59Z"),
                format = IngestionFileFormat.CSV,
                contentType = "text/csv",
                fileSizeBytes = 42,
                checksumSha256 = "8f434346648f6b96df89dda901c5176b10a6d83961c116ea4a84f7df0cf2ce13",
                schemaHint = null
            ),
            recordType = CanonicalRecordType.TRADE,
            canonicalFields = mapOf("trade_id" to "T-1")
        )
        val payload = mapper.writeValueAsString(event)
        val decoded: DomainEvent = mapper.readValue(payload)
        assertEquals(event, decoded)
    }

    @Test
    fun `serializes and deserializes validation decision event`() {
        val event = ValidationDecisionEvent(
            metadata = EventMetadata(
                eventId = UUID.randomUUID(),
                correlationId = UUID.randomUUID(),
                sourceSystem = "validation-service",
                occurredAt = Instant.parse("2026-04-25T11:00:00Z"),
                schemaVersion = "validation.step2.decision.v1",
                regimes = setOf(RegulatoryRegime.MIFID_II)
            ),
            artifactId = UUID.randomUUID(),
            recordType = CanonicalRecordType.TRADE,
            recordIndex = 1,
            validationPack = ValidationPackVersion(
                packId = "step2-core",
                version = "2026.04.19",
                effectiveFrom = Instant.parse("2026-04-19T00:00:00Z")
            ),
            ruleResult = ValidationRuleResult(
                ruleId = "required.instrument_id",
                layer = ValidationLayer.SCHEMA,
                outcome = ValidationOutcome.PASS,
                reasonCode = "OK",
                message = "Field present",
                severity = ValidationSeverity.WARNING
            ),
            inputFingerprint = "in",
            outputFingerprint = "out",
            exceptionId = null
        )
        val payload = mapper.writeValueAsString(event)
        val decoded: DomainEvent = mapper.readValue(payload)
        assertEquals(event, decoded)
    }

    @Test
    fun `serializes and deserializes rule decision event`() {
        val event = RuleDecisionEvent(
            metadata = EventMetadata(
                eventId = UUID.randomUUID(),
                correlationId = UUID.randomUUID(),
                sourceSystem = "rules-service",
                occurredAt = Instant.parse("2026-04-29T11:00:00Z"),
                schemaVersion = "rules.step3.decision.v1",
                regimes = setOf(RegulatoryRegime.MIFID_II)
            ),
            artifactId = UUID.randomUUID(),
            recordType = CanonicalRecordType.TRADE,
            recordIndex = 1,
            rulePackVersion = RulePackVersion(
                packId = "step3-mifid-foundation",
                version = "2026.04.28",
                effectiveFrom = Instant.parse("2026-04-28T00:00:00Z")
            ),
            ruleResult = RuleEvaluationResult(
                ruleId = "step3.price.positive_for_readiness",
                layer = RuleLayer.READINESS,
                outcome = RuleOutcome.PASS,
                reasonCode = "OK",
                message = "price is positive",
                severity = RuleSeverity.WARNING,
                sourceAuthority = "internal",
                sourceReference = "balfouriana step3 foundation",
                sourcePublishedAt = "2026-04-28T00:00:00Z"
            ),
            inputFingerprint = "in",
            outputFingerprint = "out",
            exceptionId = null
        )
        val payload = mapper.writeValueAsString(event)
        val decoded: DomainEvent = mapper.readValue(payload)
        assertEquals(event, decoded)
    }

    @Test
    fun `serializes and deserializes rule exception event`() {
        val event = RuleExceptionRaisedEvent(
            metadata = EventMetadata(
                eventId = UUID.randomUUID(),
                correlationId = UUID.randomUUID(),
                sourceSystem = "rules-service",
                occurredAt = Instant.parse("2026-04-29T11:01:00Z"),
                schemaVersion = "rules.step3.exception.v1",
                regimes = setOf(RegulatoryRegime.EMIR)
            ),
            artifactId = UUID.randomUUID(),
            recordType = CanonicalRecordType.TRADE,
            recordIndex = 3,
            rulePackVersion = RulePackVersion(
                packId = "step3-emir-foundation",
                version = "2026.04.28",
                effectiveFrom = Instant.parse("2026-04-28T00:00:00Z")
            ),
            exception = RuleExceptionEnvelope(
                exceptionId = UUID.randomUUID(),
                correlationId = UUID.randomUUID(),
                eventId = UUID.randomUUID(),
                regime = RegulatoryRegime.EMIR,
                ruleId = "step3.price.positive_for_readiness",
                severity = RuleSeverity.ERROR,
                rejectionCategory = "READINESS",
                reasonCode = "PRICE_MISSING_OR_NON_POSITIVE",
                message = "price must be positive",
                remediationHint = "Fix price"
            )
        )
        val payload = mapper.writeValueAsString(event)
        val decoded: DomainEvent = mapper.readValue(payload)
        assertEquals(event, decoded)
    }

    @Test
    fun `serializes and deserializes calculation applied event`() {
        val event = CalculationAppliedEvent(
            metadata = EventMetadata(
                eventId = UUID.randomUUID(),
                correlationId = UUID.randomUUID(),
                sourceSystem = "rules-service",
                occurredAt = Instant.parse("2026-04-29T11:02:00Z"),
                schemaVersion = "rules.step3.calculation.v1",
                regimes = setOf(RegulatoryRegime.AIFMD_II)
            ),
            artifactId = UUID.randomUUID(),
            recordType = CanonicalRecordType.POSITION,
            recordIndex = 2,
            rulePackVersion = RulePackVersion(
                packId = "step3-aifmd-annex-iv-calcs",
                version = "2026.04.30",
                effectiveFrom = Instant.parse("2026-04-28T00:00:00Z")
            ),
            calculationMethod = CalculationMethodRef(
                calculationId = "aifmd.commitment_leverage.calc",
                methodVersion = "2026.04.30"
            ),
            calculatedFields = mapOf("aifmd_commitment_leverage_ratio" to "175"),
            calculationMetadata = mapOf(
                "regulatory_source_authority" to "ESMA",
                "regulatory_source_reference" to "AIFMD II Annex IV leverage and reporting",
                "regulatory_source_published_at" to "2026-04-16T00:00:00Z"
            ),
            inputFingerprint = "in",
            outputFingerprint = "out"
        )
        val payload = mapper.writeValueAsString(event)
        val decoded: DomainEvent = mapper.readValue(payload)
        assertEquals(event, decoded)
    }

    @Test
    fun `serializes and deserializes filing ready event`() {
        val event = FilingReadyRecordEvent(
            metadata = EventMetadata(
                eventId = UUID.randomUUID(),
                correlationId = UUID.randomUUID(),
                sourceSystem = "rules-service",
                occurredAt = Instant.parse("2026-04-29T11:03:00Z"),
                schemaVersion = "rules.step3.filing-ready.v1",
                regimes = setOf(RegulatoryRegime.MIFID_II)
            ),
            artifactId = UUID.randomUUID(),
            envelope = SourceRecordEnvelope(
                sourceId = "test",
                sourceSystem = "test",
                ingestionChannel = IngestionChannel.REST,
                fileId = UUID.randomUUID(),
                originalFileName = "trades.csv",
                recordIndex = 1,
                receivedAt = Instant.parse("2026-04-29T10:59:59Z"),
                format = IngestionFileFormat.CSV,
                contentType = "text/csv",
                fileSizeBytes = 100,
                checksumSha256 = "abc",
                schemaHint = null
            ),
            recordType = CanonicalRecordType.TRADE,
            rulePackVersion = RulePackVersion(
                packId = "step3-mifid-foundation",
                version = "2026.04.28",
                effectiveFrom = Instant.parse("2026-04-28T00:00:00Z")
            ),
            filingReadyFields = mapOf("trade_id" to "T-1", "calculated_notional" to "1000"),
            traceMetadata = mapOf("sourceValidatedSchemaVersion" to "validation.step2.validated.v1"),
            inputFingerprint = "in",
            outputFingerprint = "out"
        )
        val payload = mapper.writeValueAsString(event)
        val decoded: DomainEvent = mapper.readValue(payload)
        assertEquals(event, decoded)
    }

    @Test
    fun `serializes and deserializes confidence escalation evaluated event`() {
        val event = ConfidenceEscalationEvaluatedEvent(
            metadata = EventMetadata(
                eventId = UUID.randomUUID(),
                correlationId = UUID.randomUUID(),
                sourceSystem = "rules-service",
                occurredAt = Instant.parse("2026-04-30T12:00:00Z"),
                schemaVersion = "confidence.escalation.v1",
                regimes = setOf(RegulatoryRegime.AIFMD_II)
            ),
            artifactId = UUID.randomUUID(),
            recordType = CanonicalRecordType.TRADE,
            recordIndex = 1,
            sourceStage = ConfidenceSourceStage.STEP3_RULES,
            confidenceLevel = ConfidenceLevel.MEDIUM,
            escalationPriority = EscalationPriority.P2_REVIEW,
            routingKey = "AIFMD_II.CALCULATION.P2_REVIEW",
            blockingCount = 0,
            reviewCount = 1,
            sourceReasonCodes = setOf("AIFMD_FUND_STRUCTURE_UNKNOWN"),
            sourceExceptionIds = setOf(UUID.randomUUID()),
            sourcePackId = "step3-aifmd-annex-iv-calcs",
            sourcePackVersion = "2026.04.30",
            sourceEventSchemaVersion = "rules.step3.exception.v1",
            inputFingerprint = "in",
            outputFingerprint = "out"
        )
        val payload = mapper.writeValueAsString(event)
        val decoded: DomainEvent = mapper.readValue(payload)
        assertEquals(event, decoded)
    }

    @Test
    fun `serializes and deserializes filing generated event`() {
        val event = FilingGeneratedEvent(
            metadata = EventMetadata(
                eventId = UUID.randomUUID(),
                correlationId = UUID.randomUUID(),
                sourceSystem = "step4-service",
                occurredAt = Instant.parse("2026-05-01T11:00:00Z"),
                schemaVersion = "filing.step4.gen.ok.v1",
                regimes = setOf(RegulatoryRegime.MIFID_II)
            ),
            artifactId = UUID.randomUUID(),
            sourceFilingReadyEventId = UUID.randomUUID(),
            sourceFilingReadyFingerprint = "fp",
            recordType = CanonicalRecordType.TRADE,
            filingTemplateId = "step4-mifid-xml",
            filingTemplateVersion = "2026.05.01",
            filingOutputFormat = FilingOutputFormat.XML,
            outputFileName = "x.xml",
            outputChecksumSha256 = "abc",
            outputSizeBytes = 100
        )
        val payload = mapper.writeValueAsString(event)
        val decoded: DomainEvent = mapper.readValue(payload)
        assertEquals(event, decoded)
    }

    @Test
    fun `serializes and deserializes filing acknowledgement event`() {
        val event = FilingAcknowledgementReceivedEvent(
            metadata = EventMetadata(
                eventId = UUID.randomUUID(),
                correlationId = UUID.randomUUID(),
                sourceSystem = "step4-ack",
                occurredAt = Instant.parse("2026-05-01T11:05:00Z"),
                schemaVersion = "filing.step4.ack.rcv.v1",
                regimes = setOf(RegulatoryRegime.EMIR)
            ),
            submissionId = UUID.randomUUID(),
            correlationLinked = true,
            linkedCorrelationId = UUID.randomUUID(),
            channel = "SFTP",
            acknowledgementStatus = FilingAcknowledgementStatus.ACK,
            externalReference = "ref",
            reasonCode = null,
            message = null
        )
        val payload = mapper.writeValueAsString(event)
        val decoded: DomainEvent = mapper.readValue(payload)
        assertEquals(event, decoded)
    }

    @Test
    fun `serializes and deserializes exception resolved event`() {
        val event = ExceptionResolvedEvent(
            metadata = EventMetadata(
                eventId = UUID.randomUUID(),
                correlationId = UUID.randomUUID(),
                sourceSystem = "exception-resolution",
                occurredAt = Instant.parse("2026-05-01T12:10:00Z"),
                schemaVersion = "ops.exception.resolve.v1",
                regimes = setOf(RegulatoryRegime.MIFID_II)
            ),
            queueItemId = UUID.randomUUID(),
            sourceEventId = UUID.randomUUID(),
            sourceEventType = "FilingAcknowledgementReceivedEvent",
            correlationId = UUID.randomUUID(),
            sourceStep = ExceptionSourceStep.ACKNOWLEDGEMENT,
            resolutionType = ExceptionResolutionType.RESUBMITTED,
            resolvedBy = "operator",
            note = "retry",
            resubmitSubmissionId = UUID.randomUUID()
        )
        val payload = mapper.writeValueAsString(event)
        val decoded: DomainEvent = mapper.readValue(payload)
        assertEquals(event, decoded)
    }

    @Test
    fun `serializes and deserializes exception resubmit requested event`() {
        val event = ExceptionResubmitRequestedEvent(
            metadata = EventMetadata(
                eventId = UUID.randomUUID(),
                correlationId = UUID.randomUUID(),
                sourceSystem = "exception-resolution",
                occurredAt = Instant.parse("2026-05-01T12:09:00Z"),
                schemaVersion = "ops.exception.resubmit.req.v1",
                regimes = setOf(RegulatoryRegime.MIFID_II)
            ),
            queueItemId = UUID.randomUUID(),
            sourceEventId = UUID.randomUUID(),
            correlationId = UUID.randomUUID(),
            filingReadyEventId = UUID.randomUUID(),
            forceResubmit = true
        )
        val payload = mapper.writeValueAsString(event)
        val decoded: DomainEvent = mapper.readValue(payload)
        assertEquals(event, decoded)
    }
}
