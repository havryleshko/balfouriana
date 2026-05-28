package com.balfouriana.service.ops

import com.balfouriana.domain.ExceptionQueueItem
import com.balfouriana.domain.ExceptionQueueSeverity
import com.balfouriana.domain.ExceptionQueueStatus
import com.balfouriana.domain.ExceptionResolutionType
import com.balfouriana.domain.ExceptionResolvedEvent
import com.balfouriana.domain.ExceptionSourceStep
import com.balfouriana.domain.FilingAcknowledgementReceivedEvent
import com.balfouriana.domain.FilingAcknowledgementStatus
import com.balfouriana.domain.FilingGenerationFailedEvent
import com.balfouriana.domain.FilingSubmissionFailedEvent
import com.balfouriana.domain.RuleExceptionRaisedEvent
import com.balfouriana.domain.RuleSeverity
import com.balfouriana.domain.ValidationExceptionRaisedEvent
import com.balfouriana.domain.ValidationSeverity
import com.balfouriana.repository.PersistedEventRecord
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component

@Component
class ExceptionQueueMapper(
    private val objectMapper: ObjectMapper
) {
    fun enrichWithResolution(item: ExceptionQueueItem, resolution: ExceptionResolvedEvent?): ExceptionQueueItem {
        if (resolution == null) {
            return item
        }
        return item.copy(
            status = ExceptionQueueStatus.RESOLVED,
            resolvedAt = resolution.metadata.occurredAt,
            resolutionType = resolution.resolutionType,
            resolvedBy = resolution.resolvedBy
        )
    }

    fun map(record: PersistedEventRecord): ExceptionQueueItem? {
        return when (record.eventType) {
            "ValidationExceptionRaisedEvent" -> mapValidation(record)
            "RuleExceptionRaisedEvent" -> mapRule(record)
            "FilingGenerationFailedEvent" -> mapFilingGenerationFailed(record)
            "FilingSubmissionFailedEvent" -> mapFilingSubmissionFailed(record)
            "FilingAcknowledgementReceivedEvent" -> mapAcknowledgement(record)
            else -> null
        }
    }

    private fun mapValidation(record: PersistedEventRecord): ExceptionQueueItem? {
        val event = deserialize<ValidationExceptionRaisedEvent>(record) ?: return null
        val envelope = event.exception
        return ExceptionQueueItem(
            queueItemId = envelope.exceptionId,
            sourceEventId = record.eventId,
            sourceEventType = record.eventType,
            correlationId = record.correlationId,
            artifactId = event.artifactId,
            sourceStep = ExceptionSourceStep.VALIDATION,
            severity = envelope.severity.toQueueSeverity(),
            status = ExceptionQueueStatus.OPEN,
            regime = envelope.regime,
            reasonCode = envelope.reasonCode,
            message = envelope.message,
            remediationHint = envelope.remediationHint,
            occurredAt = record.occurredAt,
            submissionId = null,
            correlationLinked = null
        )
    }

    private fun mapRule(record: PersistedEventRecord): ExceptionQueueItem? {
        val event = deserialize<RuleExceptionRaisedEvent>(record) ?: return null
        val envelope = event.exception
        return ExceptionQueueItem(
            queueItemId = envelope.exceptionId,
            sourceEventId = record.eventId,
            sourceEventType = record.eventType,
            correlationId = record.correlationId,
            artifactId = event.artifactId,
            sourceStep = ExceptionSourceStep.RULES,
            severity = envelope.severity.toQueueSeverity(),
            status = ExceptionQueueStatus.OPEN,
            regime = envelope.regime,
            reasonCode = envelope.reasonCode,
            message = envelope.message,
            remediationHint = envelope.remediationHint,
            occurredAt = record.occurredAt,
            submissionId = null,
            correlationLinked = null
        )
    }

    private fun mapFilingGenerationFailed(record: PersistedEventRecord): ExceptionQueueItem? {
        val event = deserialize<FilingGenerationFailedEvent>(record) ?: return null
        return ExceptionQueueItem(
            queueItemId = record.eventId,
            sourceEventId = record.eventId,
            sourceEventType = record.eventType,
            correlationId = record.correlationId,
            artifactId = event.artifactId,
            sourceStep = ExceptionSourceStep.FILING_GENERATION,
            severity = ExceptionQueueSeverity.BLOCKING,
            status = ExceptionQueueStatus.OPEN,
            regime = record.regimes.firstOrNull(),
            reasonCode = event.reasonCode,
            message = event.message,
            remediationHint = null,
            occurredAt = record.occurredAt,
            submissionId = null,
            correlationLinked = null
        )
    }

    private fun mapFilingSubmissionFailed(record: PersistedEventRecord): ExceptionQueueItem? {
        val event = deserialize<FilingSubmissionFailedEvent>(record) ?: return null
        return ExceptionQueueItem(
            queueItemId = record.eventId,
            sourceEventId = record.eventId,
            sourceEventType = record.eventType,
            correlationId = record.correlationId,
            artifactId = event.artifactId,
            sourceStep = ExceptionSourceStep.FILING_SUBMISSION,
            severity = ExceptionQueueSeverity.BLOCKING,
            status = ExceptionQueueStatus.OPEN,
            regime = record.regimes.firstOrNull(),
            reasonCode = event.reasonCode,
            message = event.message,
            remediationHint = null,
            occurredAt = record.occurredAt,
            submissionId = event.submissionId,
            correlationLinked = null
        )
    }

    private fun mapAcknowledgement(record: PersistedEventRecord): ExceptionQueueItem? {
        val event = deserialize<FilingAcknowledgementReceivedEvent>(record) ?: return null
        if (!shouldIncludeAcknowledgement(event)) {
            return null
        }
        val severity = when {
            !event.correlationLinked || event.acknowledgementStatus == FilingAcknowledgementStatus.UNRESOLVED ->
                ExceptionQueueSeverity.OPS
            event.acknowledgementStatus == FilingAcknowledgementStatus.NACK -> ExceptionQueueSeverity.BLOCKING
            else -> return null
        }
        return ExceptionQueueItem(
            queueItemId = record.eventId,
            sourceEventId = record.eventId,
            sourceEventType = record.eventType,
            correlationId = record.correlationId,
            artifactId = null,
            sourceStep = ExceptionSourceStep.ACKNOWLEDGEMENT,
            severity = severity,
            status = ExceptionQueueStatus.OPEN,
            regime = record.regimes.firstOrNull(),
            reasonCode = event.reasonCode ?: event.acknowledgementStatus.name,
            message = event.message ?: event.externalReference,
            remediationHint = null,
            occurredAt = record.occurredAt,
            submissionId = event.submissionId,
            correlationLinked = event.correlationLinked
        )
    }

    private fun shouldIncludeAcknowledgement(event: FilingAcknowledgementReceivedEvent): Boolean {
        return event.acknowledgementStatus == FilingAcknowledgementStatus.NACK ||
            event.acknowledgementStatus == FilingAcknowledgementStatus.UNRESOLVED ||
            !event.correlationLinked
    }

    private inline fun <reified T> deserialize(record: PersistedEventRecord): T? {
        return runCatching {
            objectMapper.readValue(record.payload, T::class.java)
        }.getOrNull()
    }

    private fun ValidationSeverity.toQueueSeverity(): ExceptionQueueSeverity {
        return when (this) {
            ValidationSeverity.ERROR -> ExceptionQueueSeverity.BLOCKING
            ValidationSeverity.WARNING -> ExceptionQueueSeverity.NEEDS_REVIEW
        }
    }

    private fun RuleSeverity.toQueueSeverity(): ExceptionQueueSeverity {
        return when (this) {
            RuleSeverity.ERROR -> ExceptionQueueSeverity.BLOCKING
            RuleSeverity.WARNING -> ExceptionQueueSeverity.NEEDS_REVIEW
        }
    }
}
