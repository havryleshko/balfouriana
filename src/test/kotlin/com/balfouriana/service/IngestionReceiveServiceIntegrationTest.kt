package com.balfouriana.service

import com.balfouriana.domain.IngestionChannel
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

@SpringBootTest
@ActiveProfiles("local")
class IngestionReceiveServiceIntegrationTest {

    @Autowired
    lateinit var ingestionReceiveService: IngestionReceiveService

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    companion object {
        private val ingestRoot: Path = Files.createTempDirectory("ingest-svc")

        @JvmStatic
        @DynamicPropertySource
        fun registerProps(registry: DynamicPropertyRegistry) {
            registry.add("balfouriana.ingestion.root") { ingestRoot.toString() }
        }
    }

    @Test
    fun `receive persists artifact and event`() {
        val correlationId = UUID.randomUUID()
        val before = jdbcTemplate.queryForObject(
            "select count(*) from ingestion_artifact",
            Int::class.java
        ) ?: 0
        val artifact = ingestionReceiveService.receive(
            bytes = "hello".toByteArray(),
            originalFilename = "x.txt",
            channel = IngestionChannel.REST,
            correlationId = correlationId
        )
        assertTrue(artifact.storedRelativePath.startsWith("received/"))
        val after = jdbcTemplate.queryForObject(
            "select count(*) from ingestion_artifact",
            Int::class.java
        ) ?: 0
        assertTrue(after > before)
        assertEquals(64, artifact.payloadChecksumSha256.length)
    }

    @Test
    fun `receive maps valid csv rows and rejects invalid rows`() {
        val correlationId = UUID.randomUUID()
        val payload = """
            record_type,trade_id,instrument_id,trade_date,quantity,price
            TRADE,T-1,GB00B03MLX29,2026-04-22,100,10.3
            TRADE,T-2,GB00B03MLX29,22-04-2026,100,10.3
        """.trimIndent().toByteArray()
        ingestionReceiveService.receive(
            bytes = payload,
            originalFilename = "trades.csv",
            channel = IngestionChannel.REST,
            correlationId = correlationId
        )

        val mappedCount = jdbcTemplate.queryForObject(
            "select count(*) from event_store where correlation_id = ? and event_type = 'CanonicalRecordMappedEvent'",
            Int::class.java,
            correlationId
        ) ?: 0
        val rejectedCount = jdbcTemplate.queryForObject(
            "select count(*) from event_store where correlation_id = ? and event_type = 'ParseRecordRejectedEvent'",
            Int::class.java,
            correlationId
        ) ?: 0

        assertEquals(1, mappedCount)
        assertEquals(1, rejectedCount)

        val mappedPayload = jdbcTemplate.queryForObject(
            "select payload from event_store where correlation_id = ? and event_type = 'CanonicalRecordMappedEvent' limit 1",
            String::class.java,
            correlationId
        )
        val rejectedPayload = jdbcTemplate.queryForObject(
            "select payload from event_store where correlation_id = ? and event_type = 'ParseRecordRejectedEvent' limit 1",
            String::class.java,
            correlationId
        )
        assertNotNull(mappedPayload)
        assertNotNull(rejectedPayload)
        assertTrue(mappedPayload!!.contains("\"checksumSha256\""))
        assertTrue(mappedPayload.contains("\"ingestionChannel\":\"REST\""))
        assertTrue(rejectedPayload!!.contains("\"checksumSha256\""))
    }

    @Test
    fun `receive generates stable checksum for identical bytes`() {
        val payload = "record_type,trade_id\nTRADE,T-1".toByteArray()
        val first = ingestionReceiveService.receive(
            bytes = payload,
            originalFilename = "first.csv",
            channel = IngestionChannel.REST,
            correlationId = UUID.randomUUID()
        )
        val second = ingestionReceiveService.receive(
            bytes = payload,
            originalFilename = "second.csv",
            channel = IngestionChannel.REST,
            correlationId = UUID.randomUUID()
        )
        assertEquals(first.payloadChecksumSha256, second.payloadChecksumSha256)
    }
}
