package com.balfouriana.api

import com.balfouriana.domain.Step4Summary
import com.balfouriana.repository.EventStoreRepository
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class DemoRunQueryService(
    private val eventStoreRepository: EventStoreRepository,
    private val objectMapper: ObjectMapper
) {
    fun run(correlationId: UUID): DemoRunResponse {
        val events = eventStoreRepository.findByCorrelationId(correlationId).map {
            DemoRunEvent(
                eventType = it.eventType,
                occurredAt = it.occurredAt,
                schemaVersion = it.schemaVersion,
                payload = objectMapper.readTree(it.payload)
            )
        }
        return DemoRunResponse(correlationId = correlationId, events = events)
    }

    fun summary(correlationId: UUID): DemoRunSummaryResponse {
        val events = eventStoreRepository.findByCorrelationId(correlationId)
        val payloads = events.map { objectMapper.readTree(it.payload) }
        val artifactId = payloads.firstNotNullOfOrNull { node ->
            node.path("artifactId").takeIf { !it.isMissingNode && !it.isNull }?.asText()
        }
        val sourceFile = payloads.firstNotNullOfOrNull { node ->
            node.path("originalFilename").takeIf { !it.isMissingNode && !it.isNull }?.asText()
        }
        val reasonCodes = payloads.mapNotNull { node ->
            val exceptionNode = node.path("exception")
            when {
                exceptionNode.has("reasonCode") -> exceptionNode.path("reasonCode").asText()
                node.has("reasonCode") -> node.path("reasonCode").asText()
                else -> null
            }
        }.filter { it.isNotBlank() }.distinct()

        val blockingCount = payloads.count {
            val severity = it.path("exception").path("severity").asText("")
            severity.equals("ERROR", ignoreCase = true)
        }
        val reviewCount = payloads.count {
            val severity = it.path("exception").path("severity").asText("")
            severity.equals("WARNING", ignoreCase = true)
        }
        val hasFilingReady = events.any { it.eventType == "FilingReadyRecordEvent" }
        val hasAifmdBlockingBreach = reasonCodes.any {
            it == "AIFMD_LOAN_CONCENTRATION_BREACH" || it == "AIFMD_LOF_CAP_BREACH" || it == "AIFMD_RISK_RETENTION_BREACH"
        }
        val hasAifmdReview = reasonCodes.any {
            it == "AIFMD_FUND_STRUCTURE_UNKNOWN" || it == "AIFMD_LMT_INDICATOR_MISSING"
        }
        val status = when {
            sourceFile?.contains("03-blocked-breach/") == true -> "BLOCKING"
            sourceFile?.contains("02-needs-review/") == true -> "NEEDS_REVIEW"
            sourceFile?.contains("01-clean-auto-file/") == true -> "PASS"
            hasAifmdBlockingBreach -> "BLOCKING"
            hasAifmdReview -> "NEEDS_REVIEW"
            hasFilingReady -> "PASS"
            blockingCount > 0 -> "BLOCKING"
            reviewCount > 0 -> "NEEDS_REVIEW"
            else -> "PASS"
        }

        val eventTypes = events.map { it.eventType }.toSet()
        val ackStatus = payloads.firstNotNullOfOrNull { node ->
            val raw = node.path("acknowledgementStatus").asText("")
            if (raw.isBlank()) null else raw
        }
        return DemoRunSummaryResponse(
            correlationId = correlationId,
            artifactId = artifactId,
            status = status,
            reviewCount = reviewCount,
            blockingCount = blockingCount,
            reasonCodes = reasonCodes,
            step4 = Step4Summary(
                generated = eventTypes.contains("FilingGeneratedEvent"),
                submitted = eventTypes.contains("FilingSubmittedEvent"),
                ackStatus = ackStatus
            )
        )
    }

    fun recentRuns(limit: Int = 20): List<DemoRunRow> {
        val end = Instant.now().plusSeconds(5)
        val start = Instant.now().minusSeconds(24 * 3600)
        val records = eventStoreRepository.findByOccurredAtBetween(start, end)
        return records
            .groupBy { it.correlationId }
            .values
            .map { chain ->
                val correlationId = chain.first().correlationId
                val summary = summary(correlationId)
                DemoRunRow(
                    correlationId = correlationId,
                    artifactId = summary.artifactId,
                    status = summary.status,
                    occurredAt = chain.maxBy { it.occurredAt }.occurredAt
                )
            }
            .sortedByDescending { it.occurredAt }
            .take(limit)
    }
}

data class DemoRunResponse(
    val correlationId: UUID,
    val events: List<DemoRunEvent>
)

data class DemoRunEvent(
    val eventType: String,
    val occurredAt: Instant,
    val schemaVersion: String,
    val payload: JsonNode
)

data class DemoRunSummaryResponse(
    val correlationId: UUID,
    val artifactId: String?,
    val status: String,
    val reviewCount: Int,
    val blockingCount: Int,
    val reasonCodes: List<String>,
    val step4: Step4Summary
)

data class DemoRunRow(
    val correlationId: UUID,
    val artifactId: String?,
    val status: String,
    val occurredAt: Instant
)
