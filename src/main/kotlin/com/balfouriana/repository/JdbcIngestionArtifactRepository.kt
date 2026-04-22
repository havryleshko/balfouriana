package com.balfouriana.repository

import com.balfouriana.domain.IngestionChannel
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
class JdbcIngestionArtifactRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) : IngestionArtifactRepository {

    override fun insert(
        artifactId: UUID,
        correlationId: UUID,
        channel: IngestionChannel,
        originalFilename: String,
        storedPath: String,
        byteSize: Long,
        receivedAt: Instant
    ) {
        val sql = """
            insert into ingestion_artifact (
                artifact_id, correlation_id, channel, original_filename, stored_path, byte_size, received_at
            ) values (
                :artifactId, :correlationId, :channel, :originalFilename, :storedPath, :byteSize, :receivedAt
            )
        """.trimIndent()
        val params = MapSqlParameterSource()
            .addValue("artifactId", artifactId)
            .addValue("correlationId", correlationId)
            .addValue("channel", channel.name)
            .addValue("originalFilename", originalFilename)
            .addValue("storedPath", storedPath)
            .addValue("byteSize", byteSize)
            .addValue("receivedAt", receivedAt)
        jdbcTemplate.update(sql, params)
    }
}
