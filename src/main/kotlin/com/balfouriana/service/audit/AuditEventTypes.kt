package com.balfouriana.service.audit

import com.balfouriana.domain.AuditPipelineStep

object AuditEventTypes {
    val INGEST = listOf(
        "IngestionAcceptedEvent",
        "FileReceivedEvent",
        "CanonicalRecordMappedEvent",
        "ParseRecordRejectedEvent",
        "PipelineCheckpointEvent"
    )

    val VALIDATION = listOf(
        "ValidationDecisionEvent",
        "ValidationExceptionRaisedEvent",
        "CanonicalRecordValidatedEvent"
    )

    val RULES = listOf(
        "RuleDecisionEvent",
        "RuleExceptionRaisedEvent",
        "CalculationAppliedEvent",
        "FilingReadyRecordEvent",
        "ConfidenceEscalationEvaluatedEvent"
    )

    val FILING = listOf(
        "FilingGenerationRequestedEvent",
        "FilingGeneratedEvent",
        "FilingGenerationFailedEvent",
        "FilingSubmissionRequestedEvent",
        "FilingSubmittedEvent",
        "FilingSubmissionFailedEvent",
        "FilingAcknowledgementReceivedEvent"
    )

    val OPS = listOf(
        "ExceptionResolvedEvent",
        "ExceptionResubmitRequestedEvent"
    )

    val ALL = INGEST + VALIDATION + RULES + FILING + OPS

    val DECISION_CHAIN = VALIDATION + RULES + FILING + OPS

    private val stepByEventType: Map<String, AuditPipelineStep> =
        INGEST.associateWith { AuditPipelineStep.INGEST } +
            VALIDATION.associateWith { AuditPipelineStep.VALIDATION } +
            RULES.associateWith { AuditPipelineStep.RULES } +
            FILING.associateWith { AuditPipelineStep.FILING } +
            OPS.associateWith { AuditPipelineStep.OPS }

    fun pipelineStepFor(eventType: String): AuditPipelineStep? = stepByEventType[eventType]

    fun isAuditable(eventType: String): Boolean = eventType in stepByEventType
}
