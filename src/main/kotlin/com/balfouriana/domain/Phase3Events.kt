package com.balfouriana.domain

import java.util.UUID

data class ValidationDecisionEvent(
    override val metadata: EventMetadata,
    val artifactId: UUID,
    val recordType: CanonicalRecordType,
    val recordIndex: Int,
    val validationPack: ValidationPackVersion,
    val ruleResult: ValidationRuleResult,
    val inputFingerprint: String,
    val outputFingerprint: String?,
    val exceptionId: UUID?
) : DomainEvent

data class ValidationExceptionRaisedEvent(
    override val metadata: EventMetadata,
    val artifactId: UUID,
    val recordType: CanonicalRecordType,
    val recordIndex: Int,
    val validationPack: ValidationPackVersion,
    val exception: ValidationExceptionEnvelope
) : DomainEvent

data class CanonicalRecordValidatedEvent(
    override val metadata: EventMetadata,
    val artifactId: UUID,
    val envelope: SourceRecordEnvelope,
    val recordType: CanonicalRecordType,
    val validationPack: ValidationPackVersion,
    val validatedFields: Map<String, String>,
    val enrichmentMetadata: Map<String, String>
) : DomainEvent
