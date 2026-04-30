package com.balfouriana.domain

import java.util.UUID

data class RuleDecisionEvent(
    override val metadata: EventMetadata,
    val artifactId: UUID,
    val recordType: CanonicalRecordType,
    val recordIndex: Int,
    val rulePackVersion: RulePackVersion,
    val ruleResult: RuleEvaluationResult,
    val inputFingerprint: String,
    val outputFingerprint: String?,
    val exceptionId: UUID?
) : DomainEvent

data class RuleExceptionRaisedEvent(
    override val metadata: EventMetadata,
    val artifactId: UUID,
    val recordType: CanonicalRecordType,
    val recordIndex: Int,
    val rulePackVersion: RulePackVersion,
    val exception: RuleExceptionEnvelope
) : DomainEvent

data class CalculationAppliedEvent(
    override val metadata: EventMetadata,
    val artifactId: UUID,
    val recordType: CanonicalRecordType,
    val recordIndex: Int,
    val rulePackVersion: RulePackVersion,
    val calculationMethod: CalculationMethodRef,
    val calculatedFields: Map<String, String>,
    val calculationMetadata: Map<String, String>,
    val inputFingerprint: String,
    val outputFingerprint: String
) : DomainEvent

data class FilingReadyRecordEvent(
    override val metadata: EventMetadata,
    val artifactId: UUID,
    val envelope: SourceRecordEnvelope,
    val recordType: CanonicalRecordType,
    val rulePackVersion: RulePackVersion,
    val filingReadyFields: Map<String, String>,
    val traceMetadata: Map<String, String>,
    val inputFingerprint: String,
    val outputFingerprint: String
) : DomainEvent

enum class ConfidenceLevel {
    HIGH,
    MEDIUM,
    LOW
}

enum class EscalationPriority {
    P1_BLOCKING,
    P2_REVIEW,
    P3_MONITOR
}

enum class ConfidenceSourceStage {
    STEP2_VALIDATION,
    STEP3_RULES
}

data class ConfidenceEscalationEvaluatedEvent(
    override val metadata: EventMetadata,
    val artifactId: UUID,
    val recordType: CanonicalRecordType,
    val recordIndex: Int,
    val sourceStage: ConfidenceSourceStage,
    val confidenceLevel: ConfidenceLevel,
    val escalationPriority: EscalationPriority,
    val routingKey: String,
    val blockingCount: Int,
    val reviewCount: Int,
    val sourceReasonCodes: Set<String>,
    val sourceExceptionIds: Set<UUID>,
    val sourcePackId: String,
    val sourcePackVersion: String,
    val sourceEventSchemaVersion: String,
    val inputFingerprint: String,
    val outputFingerprint: String
) : DomainEvent
