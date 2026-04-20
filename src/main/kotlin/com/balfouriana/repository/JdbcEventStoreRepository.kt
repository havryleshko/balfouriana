package com.balfouriana.repository

import com.balfouriana.domain.DomainEvent
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
