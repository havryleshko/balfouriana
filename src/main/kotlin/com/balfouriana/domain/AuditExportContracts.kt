package com.balfouriana.domain

import com.fasterxml.jackson.databind.JsonNode
import java.time.Instant
import java.util.UUID

enum class AuditPipelineStep {
    INGEST,
    VALIDATION,
    RULES,
    FILING,
    OPS
}

data class AuditExportFilters(
    val correlationId: UUID? = null,
    val submissionId: UUID? = null,
    val artifactId: UUID? = null,
    val regime: RegulatoryRegime? = null,
    val startInclusive: Instant? = null,
    val endExclusive: Instant? = null,
    val limit: Int? = null
)

data class AuditEventLinks(
    val artifactId: UUID? = null,
    val submissionId: UUID? = null,
    val queueItemId: UUID? = null,
    val sourceEventId: UUID? = null
)

data class AuditTimelineEntry(
    val eventId: UUID,
    val correlationId: UUID,
    val eventType: String,
    val schemaVersion: String,
    val regimes: Set<RegulatoryRegime>,
    val pipelineStep: AuditPipelineStep,
    val occurredAt: Instant,
    val payload: JsonNode,
    val links: AuditEventLinks
)

data class Step4Summary(
    val generated: Boolean,
    val submitted: Boolean,
    val ackStatus: String?
)

data class AuditExportSummary(
    val correlationId: UUID,
    val artifactId: String?,
    val sourceFilename: String?,
    val regimes: Set<RegulatoryRegime>,
    val submissionIds: List<UUID>,
    val ackStatuses: List<String>,
    val reasonCodes: List<String>,
    val exceptionCount: Int,
    val resolutionCount: Int,
    val step4: Step4Summary
)

data class AuditExportBundle(
    val exportId: UUID,
    val generatedAt: Instant,
    val filters: AuditExportFilters,
    val summary: AuditExportSummary?,
    val timeline: List<AuditTimelineEntry>
)
