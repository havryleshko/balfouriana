package com.balfouriana.domain

import java.time.Instant
import java.util.UUID

enum class ExceptionSourceStep {
    VALIDATION,
    RULES,
    FILING_GENERATION,
    FILING_SUBMISSION,
    ACKNOWLEDGEMENT
}

enum class ExceptionQueueSeverity {
    BLOCKING,
    NEEDS_REVIEW,
    OPS
}

enum class ExceptionQueueStatus {
    OPEN,
    RESOLVED
}

data class ExceptionQueueItem(
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
