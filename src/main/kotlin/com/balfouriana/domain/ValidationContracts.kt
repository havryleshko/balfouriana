package com.balfouriana.domain

import java.time.Instant
import java.util.UUID

enum class ValidationLayer {
    SCHEMA,
    BUSINESS,
    ENRICHMENT_PRECONDITION
}

enum class ValidationOutcome {
    PASS,
    FAIL,
    NEEDS_REVIEW
}

enum class ValidationSeverity {
    ERROR,
    WARNING
}

data class ValidationRuleResult(
    val ruleId: String,
    val layer: ValidationLayer,
    val outcome: ValidationOutcome,
    val reasonCode: String,
    val message: String,
    val severity: ValidationSeverity
)

data class ValidationPackVersion(
    val packId: String,
    val version: String,
    val effectiveFrom: Instant
)

data class ValidationExceptionEnvelope(
    val exceptionId: UUID,
    val correlationId: UUID,
    val eventId: UUID,
    val regime: RegulatoryRegime?,
    val ruleId: String,
    val severity: ValidationSeverity,
    val rejectionCategory: String,
    val reasonCode: String,
    val message: String,
    val remediationHint: String
)
