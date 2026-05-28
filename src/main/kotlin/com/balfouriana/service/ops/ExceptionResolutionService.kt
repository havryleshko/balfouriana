package com.balfouriana.service.ops

import com.balfouriana.domain.EventMetadata
import com.balfouriana.domain.ExceptionQueueItem
import com.balfouriana.domain.ExceptionQueueSeverity
import com.balfouriana.domain.ExceptionQueueStatus
import com.balfouriana.domain.ExceptionResolutionType
import com.balfouriana.domain.ExceptionResolvedEvent
import com.balfouriana.domain.ExceptionResubmitRequestedEvent
import com.balfouriana.domain.ExceptionSourceStep
import com.balfouriana.domain.FilingReadyRecordEvent
import com.balfouriana.domain.FilingSubmittedEvent
import com.balfouriana.repository.EventStoreRepository
import com.balfouriana.service.filing.Step4FilingInputService
import com.balfouriana.service.filing.SubmissionOrchestratorService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

data class ExceptionResubmitResult(
    val correlationId: UUID,
    val submissionId: UUID?,
    val filingReadyEventId: UUID,
    val resolved: Boolean
)

@Service
class ExceptionResolutionService(
    private val eventStoreRepository: EventStoreRepository,
    private val exceptionQueueQueryService: ExceptionQueueQueryService,
    private val step4FilingInputService: Step4FilingInputService,
    private val submissionOrchestratorService: SubmissionOrchestratorService,
    private val objectMapper: ObjectMapper
) {
    fun resolve(
        queueItemId: UUID,
        resolutionType: ExceptionResolutionType,
        note: String?,
        resolvedBy: String
    ): ExceptionResolvedEvent {
        val item = loadOpenQueueItem(queueItemId)
        validateResolutionType(item, resolutionType)
        val event = ExceptionResolvedEvent(
            metadata = resolutionMetadata(item, "ops.exception.resolve.v1"),
            queueItemId = item.queueItemId,
            sourceEventId = item.sourceEventId,
            sourceEventType = item.sourceEventType,
            correlationId = item.correlationId,
            sourceStep = item.sourceStep,
            resolutionType = resolutionType,
            resolvedBy = resolvedBy,
            note = note,
            resubmitSubmissionId = null
        )
        eventStoreRepository.append(event)
        return event
    }

    fun resubmitFiling(
        queueItemId: UUID,
        resolvedBy: String,
        note: String?
    ): ExceptionResubmitResult {
        val item = loadOpenQueueItem(queueItemId)
        if (!canResubmit(item)) {
            throw ExceptionResolutionBadRequestException(
                "Resubmit is not allowed for this queue item; use dismiss for ops cleanup or re-ingest corrected source data"
            )
        }
        val filingReady = latestFilingReady(item.correlationId)
            ?: throw ExceptionResolutionBadRequestException(
                "No filing-ready record found for correlation ${item.correlationId}; re-ingest corrected source data first"
            )
        val priorSubmissionEventId = latestSubmittedEvent(item.correlationId)?.metadata?.eventId
        eventStoreRepository.append(
            ExceptionResubmitRequestedEvent(
                metadata = resolutionMetadata(item, "ops.exception.resubmit.req.v1"),
                queueItemId = item.queueItemId,
                sourceEventId = item.sourceEventId,
                correlationId = item.correlationId,
                filingReadyEventId = filingReady.metadata.eventId,
                forceResubmit = true
            )
        )
        submissionOrchestratorService.process(filingReady, forceResubmit = true)
        val newSubmission = latestSubmittedEvent(item.correlationId)
        val submitted = newSubmission != null &&
            newSubmission.metadata.eventId != priorSubmissionEventId
        if (submitted) {
            eventStoreRepository.append(
                ExceptionResolvedEvent(
                    metadata = resolutionMetadata(item, "ops.exception.resolve.v1"),
                    queueItemId = item.queueItemId,
                    sourceEventId = item.sourceEventId,
                    sourceEventType = item.sourceEventType,
                    correlationId = item.correlationId,
                    sourceStep = item.sourceStep,
                    resolutionType = ExceptionResolutionType.RESUBMITTED,
                    resolvedBy = resolvedBy,
                    note = note,
                    resubmitSubmissionId = newSubmission!!.submissionId
                )
            )
        }
        return ExceptionResubmitResult(
            correlationId = item.correlationId,
            submissionId = newSubmission?.submissionId,
            filingReadyEventId = filingReady.metadata.eventId,
            resolved = submitted
        )
    }

    private fun loadOpenQueueItem(queueItemId: UUID): ExceptionQueueItem {
        val item = exceptionQueueQueryService.findQueueItemById(queueItemId)
            ?: throw ExceptionResolutionNotFoundException("Exception queue item not found: $queueItemId")
        if (item.status == ExceptionQueueStatus.RESOLVED ||
            eventStoreRepository.hasResolutionForQueueItem(queueItemId)
        ) {
            throw ExceptionResolutionConflictException("Exception queue item already resolved: $queueItemId")
        }
        return item
    }

    private fun validateResolutionType(item: ExceptionQueueItem, resolutionType: ExceptionResolutionType) {
        when (item.sourceStep) {
            ExceptionSourceStep.VALIDATION, ExceptionSourceStep.RULES -> {
                if (resolutionType !in setOf(ExceptionResolutionType.DISMISSED, ExceptionResolutionType.ACKNOWLEDGED)) {
                    throw ExceptionResolutionBadRequestException("Resolution type $resolutionType is not allowed for ${item.sourceStep}")
                }
                if (item.severity == ExceptionQueueSeverity.BLOCKING &&
                    resolutionType == ExceptionResolutionType.ACKNOWLEDGED
                ) {
                    throw ExceptionResolutionBadRequestException("Blocking items can only be dismissed")
                }
            }
            ExceptionSourceStep.ACKNOWLEDGEMENT -> {
                if (item.severity == ExceptionQueueSeverity.OPS) {
                    if (resolutionType != ExceptionResolutionType.DISMISSED) {
                        throw ExceptionResolutionBadRequestException("Orphan acknowledgement items can only be dismissed")
                    }
                } else if (resolutionType !in setOf(ExceptionResolutionType.DISMISSED, ExceptionResolutionType.ACKNOWLEDGED)) {
                    throw ExceptionResolutionBadRequestException("Resolution type $resolutionType is not allowed for acknowledgement items")
                }
            }
            ExceptionSourceStep.FILING_GENERATION, ExceptionSourceStep.FILING_SUBMISSION -> {
                if (resolutionType != ExceptionResolutionType.DISMISSED) {
                    throw ExceptionResolutionBadRequestException("Step 4 failures can only be dismissed manually; use resubmit to retry submission")
                }
            }
        }
        if (resolutionType == ExceptionResolutionType.RESUBMITTED || resolutionType == ExceptionResolutionType.SUPERSEDED) {
            throw ExceptionResolutionBadRequestException("Resolution type $resolutionType cannot be requested manually")
        }
    }

    private fun canResubmit(item: ExceptionQueueItem): Boolean {
        return when (item.sourceStep) {
            ExceptionSourceStep.FILING_GENERATION, ExceptionSourceStep.FILING_SUBMISSION -> true
            ExceptionSourceStep.ACKNOWLEDGEMENT ->
                item.severity == ExceptionQueueSeverity.BLOCKING && item.correlationLinked == true
            else -> false
        }
    }

    private fun latestFilingReady(correlationId: UUID): FilingReadyRecordEvent? {
        return step4FilingInputService.filingReadyByCorrelationId(correlationId)
            .maxByOrNull { it.metadata.occurredAt }
    }

    private fun latestSubmittedEvent(correlationId: UUID): FilingSubmittedEvent? {
        return eventStoreRepository.findByCorrelationId(correlationId)
            .filter { it.eventType == "FilingSubmittedEvent" }
            .mapNotNull { record ->
                runCatching { objectMapper.readValue<FilingSubmittedEvent>(record.payload) }.getOrNull()
            }
            .maxByOrNull { it.metadata.occurredAt }
    }

    private fun resolutionMetadata(item: ExceptionQueueItem, schemaVersion: String): EventMetadata {
        return EventMetadata(
            eventId = UUID.randomUUID(),
            correlationId = item.correlationId,
            sourceSystem = "exception-resolution",
            occurredAt = Instant.now(),
            schemaVersion = schemaVersion,
            regimes = item.regime?.let { setOf(it) } ?: emptySet()
        )
    }
}

class ExceptionResolutionNotFoundException(message: String) : RuntimeException(message)

class ExceptionResolutionConflictException(message: String) : RuntimeException(message)

class ExceptionResolutionBadRequestException(message: String) : RuntimeException(message)
