package com.balfouriana.service.ops

import com.balfouriana.domain.CanonicalRecordType
import com.balfouriana.domain.EventMetadata
import com.balfouriana.domain.ExceptionQueueSeverity
import com.balfouriana.domain.ExceptionQueueStatus
import com.balfouriana.domain.ExceptionResolutionType
import com.balfouriana.domain.ExceptionResolvedEvent
import com.balfouriana.domain.ExceptionSourceStep
import com.balfouriana.domain.FilingAcknowledgementReceivedEvent
import com.balfouriana.domain.FilingAcknowledgementStatus
import com.balfouriana.domain.FilingGenerationFailedEvent
import com.balfouriana.domain.FilingSubmissionFailedEvent
import com.balfouriana.domain.RegulatoryRegime
import com.balfouriana.domain.RuleExceptionEnvelope
import com.balfouriana.domain.RuleExceptionRaisedEvent
import com.balfouriana.domain.RulePackVersion
import com.balfouriana.domain.RuleSeverity
import com.balfouriana.domain.UNLINKED_ACK_CORRELATION_ID
import com.balfouriana.domain.ValidationExceptionEnvelope
import com.balfouriana.domain.ValidationExceptionRaisedEvent
import com.balfouriana.domain.ValidationPackVersion
import com.balfouriana.domain.ValidationSeverity
import com.balfouriana.repository.PersistedEventRecord
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class ExceptionQueueMapperTest {
    private val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build()).findAndRegisterModules()
    private val mapper = ExceptionQueueMapper(objectMapper)

    @Test
    fun `maps validation warning to needs review`() {
        val exceptionId = UUID.randomUUID()
        val item = mapper.map(recordFor(validationException(exceptionId, ValidationSeverity.WARNING)))!!
        assertEquals(ExceptionSourceStep.VALIDATION, item.sourceStep)
        assertEquals(ExceptionQueueSeverity.NEEDS_REVIEW, item.severity)
        assertEquals(exceptionId, item.queueItemId)
    }

    @Test
    fun `maps rule error to blocking`() {
        val exceptionId = UUID.randomUUID()
        val item = mapper.map(recordFor(ruleException(exceptionId, RuleSeverity.ERROR)))!!
        assertEquals(ExceptionSourceStep.RULES, item.sourceStep)
        assertEquals(ExceptionQueueSeverity.BLOCKING, item.severity)
    }

    @Test
    fun `maps filing generation failed to blocking`() {
        val eventId = UUID.randomUUID()
        val item = mapper.map(recordFor(filingGenerationFailed(eventId)))!!
        assertEquals(ExceptionSourceStep.FILING_GENERATION, item.sourceStep)
        assertEquals(ExceptionQueueSeverity.BLOCKING, item.severity)
        assertEquals("FILING_SCHEMA_VALIDATION_FAILED", item.reasonCode)
    }

    @Test
    fun `maps filing submission failed to blocking`() {
        val eventId = UUID.randomUUID()
        val item = mapper.map(recordFor(filingSubmissionFailed(eventId)))!!
        assertEquals(ExceptionSourceStep.FILING_SUBMISSION, item.sourceStep)
        assertEquals(ExceptionQueueSeverity.BLOCKING, item.severity)
    }

    @Test
    fun `maps linked nack to blocking`() {
        val item = mapper.map(recordFor(acknowledgement(FilingAcknowledgementStatus.NACK, correlationLinked = true)))!!
        assertEquals(ExceptionSourceStep.ACKNOWLEDGEMENT, item.sourceStep)
        assertEquals(ExceptionQueueSeverity.BLOCKING, item.severity)
    }

    @Test
    fun `maps orphan acknowledgement to ops`() {
        val item = mapper.map(
            recordFor(
                acknowledgement(
                    FilingAcknowledgementStatus.NACK,
                    correlationLinked = false,
                    correlationId = UNLINKED_ACK_CORRELATION_ID
                )
            )
        )!!
        assertEquals(ExceptionQueueSeverity.OPS, item.severity)
    }

    @Test
    fun `skips linked ack`() {
        assertNull(mapper.map(recordFor(acknowledgement(FilingAcknowledgementStatus.ACK, correlationLinked = true))))
    }

    @Test
    fun `enrich with resolution marks item resolved`() {
        val exceptionId = UUID.randomUUID()
        val item = mapper.map(recordFor(ruleException(exceptionId, RuleSeverity.ERROR)))!!
        val resolution = ExceptionResolvedEvent(
            metadata = metadata(item.correlationId, "ops.exception.resolve.v1"),
            queueItemId = exceptionId,
            sourceEventId = item.sourceEventId,
            sourceEventType = item.sourceEventType,
            correlationId = item.correlationId,
            sourceStep = ExceptionSourceStep.RULES,
            resolutionType = ExceptionResolutionType.DISMISSED,
            resolvedBy = "operator",
            note = "reviewed",
            resubmitSubmissionId = null
        )
        val resolved = mapper.enrichWithResolution(item, resolution)
        assertEquals(ExceptionQueueStatus.RESOLVED, resolved.status)
        assertEquals(ExceptionResolutionType.DISMISSED, resolved.resolutionType)
        assertEquals("operator", resolved.resolvedBy)
    }

    private fun recordFor(event: com.balfouriana.domain.DomainEvent): PersistedEventRecord {
        return PersistedEventRecord(
            eventId = event.metadata.eventId,
            correlationId = event.metadata.correlationId,
            eventType = event.javaClass.simpleName,
            sourceSystem = event.metadata.sourceSystem,
            schemaVersion = event.metadata.schemaVersion,
            regimes = event.metadata.regimes,
            occurredAt = event.metadata.occurredAt,
            payload = objectMapper.writeValueAsString(event),
            createdAt = Instant.now()
        )
    }

    private fun validationException(exceptionId: UUID, severity: ValidationSeverity): ValidationExceptionRaisedEvent {
        val correlationId = UUID.randomUUID()
        return ValidationExceptionRaisedEvent(
            metadata = metadata(correlationId, "ValidationExceptionRaisedEvent"),
            artifactId = UUID.randomUUID(),
            recordType = CanonicalRecordType.TRADE,
            recordIndex = 1,
            validationPack = ValidationPackVersion("step2-core", "2026.04.19", Instant.parse("2026-04-19T00:00:00Z")),
            exception = ValidationExceptionEnvelope(
                exceptionId = exceptionId,
                correlationId = correlationId,
                eventId = UUID.randomUUID(),
                regime = RegulatoryRegime.MIFID_II,
                ruleId = "validation.rule",
                severity = severity,
                rejectionCategory = "BUSINESS",
                reasonCode = "VALIDATION_ISSUE",
                message = "validation failed",
                remediationHint = "fix field"
            )
        )
    }

    private fun ruleException(exceptionId: UUID, severity: RuleSeverity): RuleExceptionRaisedEvent {
        val correlationId = UUID.randomUUID()
        return RuleExceptionRaisedEvent(
            metadata = metadata(correlationId, "RuleExceptionRaisedEvent"),
            artifactId = UUID.randomUUID(),
            recordType = CanonicalRecordType.POSITION,
            recordIndex = 1,
            rulePackVersion = RulePackVersion("step3-aifmd", "2026.05.01", Instant.parse("2026-05-01T00:00:00Z")),
            exception = RuleExceptionEnvelope(
                exceptionId = exceptionId,
                correlationId = correlationId,
                eventId = UUID.randomUUID(),
                regime = RegulatoryRegime.AIFMD_II,
                ruleId = "aifmd.rule",
                severity = severity,
                rejectionCategory = "CALCULATION",
                reasonCode = "AIFMD_LOAN_CONCENTRATION_BREACH",
                message = "loan concentration breach",
                remediationHint = "reduce exposure"
            )
        )
    }

    private fun filingGenerationFailed(eventId: UUID): FilingGenerationFailedEvent {
        val correlationId = UUID.randomUUID()
        return FilingGenerationFailedEvent(
            metadata = metadata(correlationId, "FilingGenerationFailedEvent").copy(eventId = eventId),
            artifactId = UUID.randomUUID(),
            sourceFilingReadyEventId = UUID.randomUUID(),
            sourceFilingReadyFingerprint = "fp",
            recordType = CanonicalRecordType.TRADE,
            filingTemplateId = "step4-mifid-xml",
            filingTemplateVersion = "2026.05.18",
            reasonCode = "FILING_SCHEMA_VALIDATION_FAILED",
            message = "invalid xml"
        )
    }

    private fun filingSubmissionFailed(eventId: UUID): FilingSubmissionFailedEvent {
        val correlationId = UUID.randomUUID()
        return FilingSubmissionFailedEvent(
            metadata = metadata(correlationId, "FilingSubmissionFailedEvent").copy(eventId = eventId),
            artifactId = UUID.randomUUID(),
            submissionId = UUID.randomUUID(),
            sourceFilingGeneratedEventId = UUID.randomUUID(),
            recordType = CanonicalRecordType.TRADE,
            channel = "LOCAL_OUTBOX",
            outputFileName = "x.xml",
            reasonCode = "FILING_SUBMISSION_FAILED",
            message = "upload failed"
        )
    }

    private fun acknowledgement(
        status: FilingAcknowledgementStatus,
        correlationLinked: Boolean,
        correlationId: UUID = UUID.randomUUID()
    ): FilingAcknowledgementReceivedEvent {
        return FilingAcknowledgementReceivedEvent(
            metadata = metadata(correlationId, "FilingAcknowledgementReceivedEvent"),
            submissionId = UUID.randomUUID(),
            correlationLinked = correlationLinked,
            linkedCorrelationId = if (correlationLinked) correlationId else null,
            channel = "SFTP",
            acknowledgementStatus = status,
            externalReference = UUID.randomUUID().toString(),
            reasonCode = "REJECTED",
            message = "bad data"
        )
    }

    private fun metadata(correlationId: UUID, schemaVersion: String): EventMetadata {
        return EventMetadata(
            eventId = UUID.randomUUID(),
            correlationId = correlationId,
            sourceSystem = "test",
            occurredAt = Instant.now(),
            schemaVersion = schemaVersion,
            regimes = setOf(RegulatoryRegime.MIFID_II)
        )
    }
}
