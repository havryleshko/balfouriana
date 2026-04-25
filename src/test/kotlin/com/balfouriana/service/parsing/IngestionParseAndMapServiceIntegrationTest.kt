package com.balfouriana.service.parsing

import com.balfouriana.domain.IngestionChannel
import com.balfouriana.domain.RawIngestionArtifact
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.util.UUID

@SpringBootTest
@ActiveProfiles("local")
class IngestionParseAndMapServiceIntegrationTest {

    @Autowired
    lateinit var service: IngestionParseAndMapService

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `process emits deterministic mapped and rejected events for mixed csv`() {
        val correlationId = UUID.randomUUID()
        val artifactId = UUID.randomUUID()
        val payload = """
            record_type,trade_id,instrument_id,trade_date,quantity,price
            TRADE,T-1,GB00B03MLX29,2026-04-22,100,10.3
            TRADE,T-2,GB00B03MLX29,22-04-2026,100,10.3
        """.trimIndent().toByteArray()
        val artifact = RawIngestionArtifact(
            artifactId = artifactId,
            correlationId = correlationId,
            channel = IngestionChannel.REST,
            sourceSystem = "rest-ingest",
            originalFilename = "mixed.csv",
            storedRelativePath = "received/$artifactId.csv",
            byteSize = payload.size.toLong(),
            receivedAt = Instant.parse("2026-04-25T10:00:00Z"),
            payloadChecksumSha256 = "abc123"
        )
        service.process(artifact, payload)

        val mapped = count(correlationId, "CanonicalRecordMappedEvent")
        val rejected = count(correlationId, "ParseRecordRejectedEvent")
        assertEquals(1, mapped)
        assertEquals(1, rejected)
    }

    private fun count(correlationId: UUID, eventType: String): Int {
        return jdbcTemplate.queryForObject(
            "select count(*) from event_store where correlation_id = ? and event_type = ?",
            Int::class.java,
            correlationId,
            eventType
        ) ?: 0
    }
}
