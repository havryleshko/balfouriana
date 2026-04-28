package com.balfouriana.domain

import java.time.Instant
import java.util.UUID

enum class RuleLayer {
    CLASSIFICATION,
    CALCULATION,
    READINESS
}

enum class RuleOutcome {
    PASS,
    FAIL,
    NEEDS_REVIEW
}

enum class RuleSeverity {
    ERROR,
    WARNING
}

data class RuleEvaluationResult(
    val ruleId: String,
    val layer: RuleLayer,
    val outcome: RuleOutcome,
    val reasonCode: String,
    val message: String,
    val severity: RuleSeverity
)

data class RulePackVersion(
    val packId: String,
    val version: String,
    val effectiveFrom: Instant
)

data class CalculationMethodRef(
    val calculationId: String,
    val methodVersion: String
)

data class RuleExceptionEnvelope(
    val exceptionId: UUID,
    val correlationId: UUID,
    val eventId: UUID,
    val regime: RegulatoryRegime?,
    val ruleId: String,
    val severity: RuleSeverity,
    val rejectionCategory: String,
    val reasonCode: String,
    val message: String,
    val remediationHint: String
)
