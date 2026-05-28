package com.balfouriana.domain

import java.util.UUID

enum class ExceptionResolutionType {
    DISMISSED,
    ACKNOWLEDGED,
    RESUBMITTED,
    SUPERSEDED
}

data class ExceptionResolvedEvent(
    override val metadata: EventMetadata,
    val queueItemId: UUID,
    val sourceEventId: UUID,
    val sourceEventType: String,
    val correlationId: UUID,
    val sourceStep: ExceptionSourceStep,
    val resolutionType: ExceptionResolutionType,
    val resolvedBy: String,
    val note: String?,
    val resubmitSubmissionId: UUID?
) : DomainEvent

data class ExceptionResubmitRequestedEvent(
    override val metadata: EventMetadata,
    val queueItemId: UUID,
    val sourceEventId: UUID,
    val correlationId: UUID,
    val filingReadyEventId: UUID,
    val forceResubmit: Boolean
) : DomainEvent
