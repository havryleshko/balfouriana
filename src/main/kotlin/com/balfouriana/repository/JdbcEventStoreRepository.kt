package com.balfouriana.repository

import com.balfouriana.domain.DomainEvent
import com.balfouriana.domain.ExceptionResolvedEvent
import com.balfouriana.domain.FilingAcknowledgementReceivedEvent
import com.balfouriana.domain.FilingAcknowledgementStatus
import com.balfouriana.domain.FilingGeneratedEvent
import com.balfouriana.domain.FilingSubmittedEvent
import com.balfouriana.domain.RegulatoryRegime
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

@Repository
class JdbcEventStoreRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper
) : EventStoreRepository {

    override fun append(event: DomainEvent) {
        val sql = """
            insert into event_store (
                event_id,
                correlation_id,
                event_type,
                source_system,
                schema_version,
                regimes,
                occurred_at,
                payload
            ) values (
                :eventId,
                :correlationId,
                :eventType,
                :sourceSystem,
                :schemaVersion,
                :regimes,
                :occurredAt,
                :payload
            )
        """.trimIndent()

        val metadata = event.metadata
        val params = MapSqlParameterSource()
            .addValue("eventId", metadata.eventId)
            .addValue("correlationId", metadata.correlationId)
            .addValue("eventType", event.javaClass.simpleName)
            .addValue("sourceSystem", metadata.sourceSystem)
            .addValue("schemaVersion", metadata.schemaVersion)
            .addValue("regimes", metadata.regimes.joinToString(","))
            .addValue("occurredAt", metadata.occurredAt)
            .addValue("payload", objectMapper.writeValueAsString(event))

        jdbcTemplate.update(sql, params)
    }

    override fun findByCorrelationId(correlationId: UUID): List<PersistedEventRecord> {
        val sql = """
            select event_id, correlation_id, event_type, source_system, schema_version, regimes, occurred_at, payload, created_at
            from event_store
            where correlation_id = :correlationId
            order by occurred_at asc
        """.trimIndent()
        return jdbcTemplate.query(sql, MapSqlParameterSource("correlationId", correlationId)) { rs, _ -> mapRecord(rs) }
    }

    override fun findByOccurredAtBetween(startInclusive: Instant, endExclusive: Instant): List<PersistedEventRecord> {
        val sql = """
            select event_id, correlation_id, event_type, source_system, schema_version, regimes, occurred_at, payload, created_at
            from event_store
            where occurred_at >= :startInclusive and occurred_at < :endExclusive
            order by occurred_at asc
        """.trimIndent()
        val params = MapSqlParameterSource()
            .addValue("startInclusive", startInclusive)
            .addValue("endExclusive", endExclusive)
        return jdbcTemplate.query(sql, params) { rs, _ -> mapRecord(rs) }
    }

    override fun findByEventType(eventType: String): List<PersistedEventRecord> {
        val sql = """
            select event_id, correlation_id, event_type, source_system, schema_version, regimes, occurred_at, payload, created_at
            from event_store
            where event_type = :eventType
            order by occurred_at asc
        """.trimIndent()
        return jdbcTemplate.query(sql, MapSqlParameterSource("eventType", eventType)) { rs, _ -> mapRecord(rs) }
    }

    override fun hasSuccessfulSubmission(
        correlationId: UUID,
        outputChecksumSha256: String,
        filingTemplateVersion: String
    ): Boolean {
        val records = findByCorrelationId(correlationId)
        val generatedEventIds = records.filter { it.eventType == "FilingGeneratedEvent" }
            .mapNotNull { record ->
                runCatching {
                    objectMapper.readValue(record.payload, FilingGeneratedEvent::class.java)
                }.getOrNull()
            }
            .filter {
                it.outputChecksumSha256 == outputChecksumSha256 &&
                    it.filingTemplateVersion == filingTemplateVersion
            }
            .map { it.metadata.eventId }
            .toSet()
        if (generatedEventIds.isEmpty()) {
            return false
        }
        return records.filter { it.eventType == "FilingSubmittedEvent" }
            .mapNotNull { record ->
                runCatching {
                    objectMapper.readValue(record.payload, FilingSubmittedEvent::class.java)
                }.getOrNull()
            }
            .any {
                it.outputChecksumSha256 == outputChecksumSha256 &&
                    it.sourceFilingGeneratedEventId in generatedEventIds
            }
    }

    override fun findFilingSubmittedBySubmissionId(submissionId: UUID): FilingSubmittedEvent? {
        return findByEventType("FilingSubmittedEvent")
            .mapNotNull { record ->
                runCatching {
                    objectMapper.readValue(record.payload, FilingSubmittedEvent::class.java)
                }.getOrNull()
            }
            .lastOrNull { it.submissionId == submissionId }
    }

    override fun hasAcknowledgementForSubmission(
        submissionId: UUID,
        acknowledgementStatus: FilingAcknowledgementStatus
    ): Boolean {
        return findByEventType("FilingAcknowledgementReceivedEvent")
            .mapNotNull { record ->
                runCatching {
                    objectMapper.readValue(record.payload, FilingAcknowledgementReceivedEvent::class.java)
                }.getOrNull()
            }
            .any {
                it.submissionId == submissionId && it.acknowledgementStatus == acknowledgementStatus
            }
    }

    override fun hasAcknowledgementForExternalReference(
        externalReference: String,
        acknowledgementStatus: FilingAcknowledgementStatus
    ): Boolean {
        return findByEventType("FilingAcknowledgementReceivedEvent")
            .mapNotNull { record ->
                runCatching {
                    objectMapper.readValue(record.payload, FilingAcknowledgementReceivedEvent::class.java)
                }.getOrNull()
            }
            .any {
                it.externalReference == externalReference && it.acknowledgementStatus == acknowledgementStatus
            }
    }

    override fun findUnresolvedAcknowledgements(limit: Int): List<PersistedEventRecord> {
        return findByEventType("FilingAcknowledgementReceivedEvent")
            .mapNotNull { record ->
                val event = runCatching {
                    objectMapper.readValue(record.payload, FilingAcknowledgementReceivedEvent::class.java)
                }.getOrNull()
                if (event != null && !event.correlationLinked) record else null
            }
            .sortedByDescending { it.occurredAt }
            .take(limit)
    }

    override fun findByEventTypesSince(
        eventTypes: List<String>,
        sinceInclusive: Instant,
        limit: Int
    ): List<PersistedEventRecord> {
        if (eventTypes.isEmpty()) {
            return emptyList()
        }
        val sql = """
            select event_id, correlation_id, event_type, source_system, schema_version, regimes, occurred_at, payload, created_at
            from event_store
            where event_type in (:eventTypes) and occurred_at >= :sinceInclusive
            order by occurred_at desc
            limit :limit
        """.trimIndent()
        val params = MapSqlParameterSource()
            .addValue("eventTypes", eventTypes)
            .addValue("sinceInclusive", sinceInclusive)
            .addValue("limit", limit)
        return jdbcTemplate.query(sql, params) { rs, _ -> mapRecord(rs) }
    }

    override fun findLatestResolutionByQueueItemId(queueItemId: UUID): ExceptionResolvedEvent? {
        return findByEventType("ExceptionResolvedEvent")
            .mapNotNull { record -> deserializeResolution(record) }
            .filter { it.queueItemId == queueItemId }
            .maxByOrNull { it.metadata.occurredAt }
    }

    override fun findResolvedQueueItemIds(queueItemIds: Collection<UUID>): Set<UUID> {
        if (queueItemIds.isEmpty()) {
            return emptySet()
        }
        val targetIds = queueItemIds.toSet()
        return findByEventType("ExceptionResolvedEvent")
            .mapNotNull { record -> deserializeResolution(record) }
            .filter { it.queueItemId in targetIds }
            .map { it.queueItemId }
            .toSet()
    }

    override fun hasResolutionForQueueItem(queueItemId: UUID): Boolean {
        return findLatestResolutionByQueueItemId(queueItemId) != null
    }

    override fun findCorrelationIdBySubmissionId(submissionId: UUID): UUID? {
        return findFilingSubmittedBySubmissionId(submissionId)?.metadata?.correlationId
    }

    override fun findCorrelationIdsByArtifactId(artifactId: UUID): List<UUID> {
        val needle = artifactId.toString()
        return findByEventType("FileReceivedEvent").plus(findByEventType("CanonicalRecordMappedEvent"))
            .plus(findByEventType("CanonicalRecordValidatedEvent"))
            .plus(findByEventType("FilingReadyRecordEvent"))
            .plus(findByEventType("FilingGeneratedEvent"))
            .plus(findByEventType("FilingSubmittedEvent"))
            .filter { record -> record.payload.contains(needle) }
            .map { it.correlationId }
            .distinct()
    }

    private fun deserializeResolution(record: PersistedEventRecord): ExceptionResolvedEvent? {
        return runCatching {
            objectMapper.readValue(record.payload, ExceptionResolvedEvent::class.java)
        }.getOrNull()
    }

    private fun mapRecord(rs: ResultSet): PersistedEventRecord {
        val regimesRaw = rs.getString("regimes").orEmpty()
        val regimes = if (regimesRaw.isBlank()) emptySet() else regimesRaw.split(",").map { RegulatoryRegime.valueOf(it) }.toSet()
        return PersistedEventRecord(
            eventId = rs.getObject("event_id", UUID::class.java),
            correlationId = rs.getObject("correlation_id", UUID::class.java),
            eventType = rs.getString("event_type"),
            sourceSystem = rs.getString("source_system"),
            schemaVersion = rs.getString("schema_version"),
            regimes = regimes,
            occurredAt = rs.getTimestamp("occurred_at").toInstant(),
            payload = rs.getString("payload"),
            createdAt = rs.getTimestamp("created_at").toInstant()
        )
    }
}
