package com.balfouriana.api

import com.balfouriana.domain.ExceptionQueueItem
import com.balfouriana.domain.ExceptionQueueSeverity
import com.balfouriana.domain.ExceptionQueueStatus
import com.balfouriana.domain.ExceptionResolutionType
import com.balfouriana.domain.ExceptionSourceStep
import com.balfouriana.domain.RegulatoryRegime
import java.time.Instant
import java.util.UUID

data class ExceptionQueueItemResponse(
    val queueItemId: UUID,
    val sourceEventId: UUID,
    val sourceEventType: String,
    val correlationId: UUID,
    val artifactId: UUID?,
    val sourceStep: ExceptionSourceStep,
    val severity: ExceptionQueueSeverity,
    val status: ExceptionQueueStatus,
    val regime: RegulatoryRegime?,
    val reasonCode: String,
    val message: String,
    val remediationHint: String?,
    val occurredAt: Instant,
    val submissionId: UUID?,
    val correlationLinked: Boolean?,
    val resolvedAt: Instant? = null,
    val resolutionType: ExceptionResolutionType? = null,
    val resolvedBy: String? = null
)

fun ExceptionQueueItem.toResponse(): ExceptionQueueItemResponse {
    return ExceptionQueueItemResponse(
        queueItemId = queueItemId,
        sourceEventId = sourceEventId,
        sourceEventType = sourceEventType,
        correlationId = correlationId,
        artifactId = artifactId,
        sourceStep = sourceStep,
        severity = severity,
        status = status,
        regime = regime,
        reasonCode = reasonCode,
        message = message,
        remediationHint = remediationHint,
        occurredAt = occurredAt,
        submissionId = submissionId,
        correlationLinked = correlationLinked,
        resolvedAt = resolvedAt,
        resolutionType = resolutionType,
        resolvedBy = resolvedBy
    )
}

data class ExceptionResolveRequest(
    val resolutionType: ExceptionResolutionType,
    val note: String? = null,
    val resolvedBy: String = "operator"
)

data class ExceptionResolveResponse(
    val queueItemId: UUID,
    val resolutionType: ExceptionResolutionType,
    val resolvedAt: Instant,
    val resolvedBy: String
)

data class ExceptionResubmitRequest(
    val note: String? = null,
    val resolvedBy: String = "operator"
)

data class ExceptionResubmitResponse(
    val correlationId: UUID,
    val submissionId: UUID?,
    val filingReadyEventId: UUID,
    val resolved: Boolean
)
