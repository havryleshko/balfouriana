package com.balfouriana.service.audit

import com.balfouriana.config.OpsProperties
import com.balfouriana.domain.AuditEventLinks
import com.balfouriana.domain.AuditExportBundle
import com.balfouriana.domain.AuditExportFilters
import com.balfouriana.domain.AuditExportSummary
import com.balfouriana.domain.AuditTimelineEntry
import com.balfouriana.domain.RegulatoryRegime
import com.balfouriana.domain.Step4Summary
import com.balfouriana.repository.EventStoreRepository
import com.balfouriana.repository.PersistedEventRecord
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class AuditExportService(
    private val eventStoreRepository: EventStoreRepository,
    private val objectMapper: ObjectMapper,
    private val opsProperties: OpsProperties
) {
    fun export(filters: AuditExportFilters): AuditExportBundle {
        val effectiveLimit = (filters.limit ?: opsProperties.auditExport.defaultLimit)
            .coerceAtMost(opsProperties.auditExport.maxLimit)
        val resolvedFilters = filters.copy(limit = effectiveLimit)
        val correlationId = resolveCorrelationId(resolvedFilters)
        val records = when {
            correlationId != null -> auditableRecordsForCorrelation(correlationId, resolvedFilters.regime, includeIngest = true)
            else -> auditableRecordsForWindow(resolvedFilters, effectiveLimit)
        }
        val timeline = assembleTimeline(records)
        val summary = correlationId?.let { buildSummary(it, records, timeline) }
        return AuditExportBundle(
            exportId = UUID.randomUUID(),
            generatedAt = Instant.now(),
            filters = resolvedFilters,
            summary = summary,
            timeline = timeline
        )
    }

    fun decisionChainByCorrelationId(correlationId: UUID): List<PersistedEventRecord> {
        return auditableRecordsForCorrelation(correlationId, regime = null, includeIngest = false)
    }

    private fun resolveCorrelationId(filters: AuditExportFilters): UUID? {
        filters.correlationId?.let { return it }
        filters.submissionId?.let { submissionId ->
            return eventStoreRepository.findCorrelationIdBySubmissionId(submissionId)
                ?: throw AuditExportBadRequestException("No filing submission found for submissionId $submissionId")
        }
        filters.artifactId?.let { artifactId ->
            val correlations = eventStoreRepository.findCorrelationIdsByArtifactId(artifactId)
            return when {
                correlations.isEmpty() ->
                    throw AuditExportBadRequestException("No events found for artifactId $artifactId")
                correlations.size > 1 ->
                    throw AuditExportBadRequestException(
                        "artifactId $artifactId matches multiple correlations: ${correlations.joinToString()}; provide correlationId"
                    )
                else -> correlations.single()
            }
        }
        if (filters.startInclusive == null || filters.endExclusive == null) {
            throw AuditExportBadRequestException(
                "Provide correlationId, submissionId, artifactId, or both start and end for window export"
            )
        }
        return null
    }

    private fun auditableRecordsForWindow(filters: AuditExportFilters, limit: Int): List<PersistedEventRecord> {
        val start = filters.startInclusive!!
        val end = filters.endExclusive!!
        val records = eventStoreRepository.findByOccurredAtBetween(start, end)
            .filter { AuditEventTypes.isAuditable(it.eventType) }
            .filter { filters.regime == null || filters.regime in it.regimes }
        val correlationIds = records.map { it.correlationId }.distinct().take(limit)
        if (records.map { it.correlationId }.distinct().size > limit) {
            throw AuditExportBadRequestException(
                "Window matches more than $limit correlations; narrow the date range or provide correlationId"
            )
        }
        return records
            .filter { it.correlationId in correlationIds.toSet() }
            .sortedWith(compareBy({ it.occurredAt }, { it.eventId }))
    }

    private fun auditableRecordsForCorrelation(
        correlationId: UUID,
        regime: RegulatoryRegime?,
        includeIngest: Boolean
    ): List<PersistedEventRecord> {
        val allowedTypes = if (includeIngest) AuditEventTypes.ALL else AuditEventTypes.DECISION_CHAIN
        return eventStoreRepository.findByCorrelationId(correlationId)
            .filter { it.eventType in allowedTypes }
            .filter { regime == null || regime in it.regimes }
            .sortedWith(compareBy({ it.occurredAt }, { it.eventId }))
    }

    private fun assembleTimeline(records: List<PersistedEventRecord>): List<AuditTimelineEntry> {
        return records.mapNotNull { record ->
            val pipelineStep = AuditEventTypes.pipelineStepFor(record.eventType) ?: return@mapNotNull null
            val payload = objectMapper.readTree(record.payload)
            AuditTimelineEntry(
                eventId = record.eventId,
                correlationId = record.correlationId,
                eventType = record.eventType,
                schemaVersion = record.schemaVersion,
                regimes = record.regimes,
                pipelineStep = pipelineStep,
                occurredAt = record.occurredAt,
                payload = payload,
                links = extractLinks(record.eventType, payload)
            )
        }
    }

    private fun extractLinks(eventType: String, payload: JsonNode): AuditEventLinks {
        val artifactId = readUuid(payload, "artifactId")
        val submissionId = readUuid(payload, "submissionId")
            ?: readUuid(payload.path("metadata"), "submissionId")
        val queueItemId = when (eventType) {
            "ExceptionResolvedEvent", "ExceptionResubmitRequestedEvent" -> readUuid(payload, "queueItemId")
            "ValidationExceptionRaisedEvent", "RuleExceptionRaisedEvent" ->
                readUuid(payload.path("exception"), "exceptionId")
            else -> null
        }
        val sourceEventId = readUuid(payload, "sourceEventId")
        return AuditEventLinks(
            artifactId = artifactId,
            submissionId = submissionId,
            queueItemId = queueItemId,
            sourceEventId = sourceEventId
        )
    }

    private fun readUuid(node: JsonNode, field: String): UUID? {
        val text = node.path(field).takeIf { !it.isMissingNode && !it.isNull }?.asText()?.trim()
        if (text.isNullOrBlank()) {
            return null
        }
        return runCatching { UUID.fromString(text) }.getOrNull()
    }

    private fun buildSummary(
        correlationId: UUID,
        records: List<PersistedEventRecord>,
        timeline: List<AuditTimelineEntry>
    ): AuditExportSummary {
        val payloads = timeline.map { it.payload }
        val artifactId = payloads.firstNotNullOfOrNull { node ->
            node.path("artifactId").takeIf { !it.isMissingNode && !it.isNull }?.asText()
        }
        val sourceFilename = payloads.firstNotNullOfOrNull { node ->
            node.path("originalFilename").takeIf { !it.isMissingNode && !it.isNull }?.asText()
                ?: node.path("envelope").path("originalFileName").takeIf { !it.isMissingNode && !it.isNull }?.asText()
        }
        val reasonCodes = payloads.mapNotNull { node ->
            val exceptionNode = node.path("exception")
            when {
                exceptionNode.has("reasonCode") -> exceptionNode.path("reasonCode").asText()
                node.has("reasonCode") -> node.path("reasonCode").asText()
                else -> null
            }
        }.filter { it.isNotBlank() }.distinct()
        val submissionIds = timeline.mapNotNull { it.links.submissionId }.distinct()
        val ackStatuses = payloads.mapNotNull { node ->
            val raw = node.path("acknowledgementStatus").asText("")
            raw.takeIf { it.isNotBlank() }
        }.distinct()
        val exceptionCount = timeline.count {
            it.eventType.endsWith("ExceptionRaisedEvent") ||
                it.eventType.endsWith("FailedEvent") ||
                (it.eventType == "FilingAcknowledgementReceivedEvent" &&
                    it.payload.path("acknowledgementStatus").asText("") == "NACK")
        }
        val resolutionCount = timeline.count {
            it.eventType == "ExceptionResolvedEvent" || it.eventType == "ExceptionResubmitRequestedEvent"
        }
        val eventTypes = records.map { it.eventType }.toSet()
        val ackStatus = ackStatuses.firstOrNull()
        return AuditExportSummary(
            correlationId = correlationId,
            artifactId = artifactId,
            sourceFilename = sourceFilename,
            regimes = records.flatMap { it.regimes }.toSet(),
            submissionIds = submissionIds,
            ackStatuses = ackStatuses,
            reasonCodes = reasonCodes,
            exceptionCount = exceptionCount,
            resolutionCount = resolutionCount,
            step4 = Step4Summary(
                generated = eventTypes.contains("FilingGeneratedEvent"),
                submitted = eventTypes.contains("FilingSubmittedEvent"),
                ackStatus = ackStatus
            )
        )
    }

    fun defaultWindowEnd(): Instant = Instant.now().plusSeconds(5)

    fun defaultWindowStart(): Instant =
        Instant.now().minus(opsProperties.auditExport.defaultLookbackHours, ChronoUnit.HOURS)
}
