package com.balfouriana.service

import org.junit.jupiter.api.Assertions.assertTrue
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
class DropZonePollerIntegrationTest {

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    companion object {
        private val ingestRoot: Path = run {
            val p = Files.createTempDirectory("ingest-drop")
            Files.createDirectories(p.resolve("incoming"))
            p
        }

        @JvmStatic
        @DynamicPropertySource
        fun registerProps(registry: DynamicPropertyRegistry) {
            registry.add("balfouriana.ingestion.root") { ingestRoot.toString() }
            registry.add("balfouriana.ingestion.drop-zone.enabled") { "true" }
            registry.add("balfouriana.ingestion.drop-zone.poll-interval-ms") { "100" }
            registry.add("balfouriana.ingestion.drop-zone.stability-check-ms") { "0" }
        }
    }

    @Test
    fun `drop zone picks up stable file`() {
        val incoming = ingestRoot.resolve("incoming").resolve("from-custodian-${UUID.randomUUID()}.csv")
        Files.writeString(incoming, "col1,col2\n1,2")
        val before = jdbcTemplate.queryForObject(
            "select count(*) from event_store where event_type = 'FileReceivedEvent'",
            Int::class.java
        ) ?: 0
        var after = before
        var waited = 0
        while (waited < 15_000 && after <= before) {
            Thread.sleep(200)
            waited += 200
            after = jdbcTemplate.queryForObject(
                "select count(*) from event_store where event_type = 'FileReceivedEvent'",
                Int::class.java
            ) ?: 0
        }
        assertTrue(after > before, "expected FileReceivedEvent within timeout")
    }
}
