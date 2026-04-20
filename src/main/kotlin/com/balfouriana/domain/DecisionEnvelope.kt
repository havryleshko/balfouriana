package com.balfouriana.domain

enum class DecisionType {
    VALIDATION,
    MAPPING,
    CALCULATION
}

enum class DecisionOutcome {
    ACCEPTED,
    REJECTED,
    FLAGGED
}

data class DecisionEnvelope(
    val metadata: EventMetadata,
    val decisionType: DecisionType,
    val outcome: DecisionOutcome,
    val code: String,
    val message: String
)
