package com.balfouriana.api

import org.hamcrest.Matchers
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.nio.file.Files
import java.nio.file.Path

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class IngestionControllerIntegrationTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    companion object {
        private val ingestRoot: Path = Files.createTempDirectory("ingest-rest")

        @JvmStatic
        @DynamicPropertySource
        fun registerProps(registry: DynamicPropertyRegistry) {
            registry.add("balfouriana.ingestion.root") { ingestRoot.toString() }
        }
    }

    @Test
    fun `post ingest stores file and event`() {
        val before = jdbcTemplate.queryForObject(
            "select count(*) from event_store where event_type = 'FileReceivedEvent'",
            Int::class.java
        ) ?: 0
        val file = MockMultipartFile(
            "file",
            "upload.csv",
            MediaType.TEXT_PLAIN_VALUE,
            "a,b,c".toByteArray()
        )
        mockMvc.perform(multipart("/ingest").file(file))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.storedPath").value(Matchers.startsWith("received/")))
            .andExpect(jsonPath("$.artifactId").exists())
        val after = jdbcTemplate.queryForObject(
            "select count(*) from event_store where event_type = 'FileReceivedEvent'",
            Int::class.java
        ) ?: 0
        assertTrue(after > before)
    }

    @Test
    fun `post ingest rejects empty file`() {
        val file = MockMultipartFile("file", "empty.txt", MediaType.TEXT_PLAIN_VALUE, ByteArray(0))
        mockMvc.perform(multipart("/ingest").file(file))
            .andExpect(status().isBadRequest)
    }
}
