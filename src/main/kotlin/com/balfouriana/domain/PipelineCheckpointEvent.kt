package com.balfouriana.domain

enum class PipelineStage {
    INGESTION,
    VALIDATION,
    RULE_APPLICATION,
    FILING,
    AUDIT
}

data class PipelineCheckpointEvent(
    override val metadata: EventMetadata,
    val stage: PipelineStage,
    val status: String
) : DomainEvent
