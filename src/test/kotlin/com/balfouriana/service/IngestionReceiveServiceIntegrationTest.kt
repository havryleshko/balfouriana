package com.balfouriana.service

import com.balfouriana.domain.IngestionChannel
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
    }
}
