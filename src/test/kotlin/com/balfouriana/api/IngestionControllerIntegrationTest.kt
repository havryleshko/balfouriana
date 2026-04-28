package com.balfouriana.api

import org.hamcrest.Matchers
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertEquals
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
    fun `post ingest csv emits mapped and rejected events`() {
        val csv = """
            record_type,trade_id,instrument_id,trade_date,quantity,price
            TRADE,T-1,GB00B03MLX29,2026-04-22,100,10.3
            TRADE,T-2,GB00B03MLX29,22-04-2026,100,10.3
        """.trimIndent()
        val file = MockMultipartFile("file", "trades.csv", MediaType.TEXT_PLAIN_VALUE, csv.toByteArray())
        val beforeMapped = countEvents("CanonicalRecordMappedEvent")
        val beforeRejected = countEvents("ParseRecordRejectedEvent")
        val beforeDecision = countEvents("ValidationDecisionEvent")
        val beforeValidationException = countEvents("ValidationExceptionRaisedEvent")
        val beforeValidated = countEvents("CanonicalRecordValidatedEvent")
        mockMvc.perform(multipart("/ingest").file(file))
            .andExpect(status().isOk)
        assertEquals(beforeMapped + 1, countEvents("CanonicalRecordMappedEvent"))
        assertEquals(beforeRejected + 1, countEvents("ParseRecordRejectedEvent"))
        assertEquals(beforeDecision + 6, countEvents("ValidationDecisionEvent"))
        assertTrue(countEvents("ValidationExceptionRaisedEvent") >= beforeValidationException + 1)
        assertEquals(beforeValidated, countEvents("CanonicalRecordValidatedEvent"))
    }

    @Test
    fun `post ingest json fix xml smoke through full pipeline`() {
        val beforeMapped = countEvents("CanonicalRecordMappedEvent")
        val beforeRejected = countEvents("ParseRecordRejectedEvent")

        mockMvc.perform(
            multipart("/ingest").file(
                MockMultipartFile(
                    "file",
                    "trade.json",
                    MediaType.APPLICATION_JSON_VALUE,
                    """{"record_type":"TRADE","trade_id":"T-1","instrument_id":"GB00B03MLX29"}""".toByteArray()
                )
            )
        ).andExpect(status().isOk)

        mockMvc.perform(
            multipart("/ingest").file(
                MockMultipartFile(
                    "file",
                    "bad.fix",
                    MediaType.TEXT_PLAIN_VALUE,
                    "malformed-line".toByteArray()
                )
            )
        ).andExpect(status().isOk)

        mockMvc.perform(
            multipart("/ingest").file(
                MockMultipartFile(
                    "file",
                    "trade.xml",
                    MediaType.APPLICATION_XML_VALUE,
                    "<records><record><record_type>TRADE</record_type><trade_id>T-1</trade_id></record></records>".toByteArray()
                )
            )
        ).andExpect(status().isOk)

        assertTrue(countEvents("CanonicalRecordMappedEvent") >= beforeMapped + 2)
        assertTrue(countEvents("ParseRecordRejectedEvent") >= beforeRejected + 1)
    }

    @Test
    fun `post ingest rejects empty file`() {
        val file = MockMultipartFile("file", "empty.txt", MediaType.TEXT_PLAIN_VALUE, ByteArray(0))
        mockMvc.perform(multipart("/ingest").file(file))
            .andExpect(status().isBadRequest)
    }

    private fun countEvents(eventType: String): Int {
        return jdbcTemplate.queryForObject(
            "select count(*) from event_store where event_type = ?",
            Int::class.java,
            eventType
        ) ?: 0
    }
}
