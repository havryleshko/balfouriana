package com.balfouriana.domain

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = IngestionAcceptedEvent::class, name = "ingestionAccepted"),
    JsonSubTypes.Type(value = PipelineCheckpointEvent::class, name = "pipelineCheckpoint"),
    JsonSubTypes.Type(value = FileReceivedEvent::class, name = "fileReceived"),
    JsonSubTypes.Type(value = CanonicalRecordMappedEvent::class, name = "canonicalRecordMapped"),
    JsonSubTypes.Type(value = ParseRecordRejectedEvent::class, name = "parseRecordRejected"),
    JsonSubTypes.Type(value = ValidationDecisionEvent::class, name = "validationDecision"),
    JsonSubTypes.Type(value = ValidationExceptionRaisedEvent::class, name = "validationExceptionRaised"),
    JsonSubTypes.Type(value = CanonicalRecordValidatedEvent::class, name = "canonicalRecordValidated"),
    JsonSubTypes.Type(value = RuleDecisionEvent::class, name = "ruleDecision"),
    JsonSubTypes.Type(value = RuleExceptionRaisedEvent::class, name = "ruleExceptionRaised"),
    JsonSubTypes.Type(value = CalculationAppliedEvent::class, name = "calculationApplied"),
    JsonSubTypes.Type(value = FilingReadyRecordEvent::class, name = "filingReadyRecord"),
    JsonSubTypes.Type(value = ConfidenceEscalationEvaluatedEvent::class, name = "confidenceEscalationEvaluated"),
    JsonSubTypes.Type(value = FilingGenerationRequestedEvent::class, name = "filingGenerationRequested"),
    JsonSubTypes.Type(value = FilingGeneratedEvent::class, name = "filingGenerated"),
    JsonSubTypes.Type(value = FilingGenerationFailedEvent::class, name = "filingGenerationFailed"),
    JsonSubTypes.Type(value = FilingSubmissionRequestedEvent::class, name = "filingSubmissionRequested"),
    JsonSubTypes.Type(value = FilingSubmittedEvent::class, name = "filingSubmitted"),
    JsonSubTypes.Type(value = FilingSubmissionFailedEvent::class, name = "filingSubmissionFailed"),
    JsonSubTypes.Type(value = FilingAcknowledgementReceivedEvent::class, name = "filingAcknowledgementReceived"),
    JsonSubTypes.Type(value = ExceptionResolvedEvent::class, name = "exceptionResolved"),
    JsonSubTypes.Type(value = ExceptionResubmitRequestedEvent::class, name = "exceptionResubmitRequested")
)
sealed interface DomainEvent {
    val metadata: EventMetadata
}
