package com.balfouriana.api

import com.balfouriana.config.IngestionApiKeyFilter
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.nio.file.Files
import java.nio.file.Path

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class IngestionControllerApiKeyIntegrationTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    companion object {
        private val ingestRoot: Path = Files.createTempDirectory("ingest-rest-apikey")

        @JvmStatic
        @DynamicPropertySource
        fun registerProps(registry: DynamicPropertyRegistry) {
            registry.add("balfouriana.ingestion.root") { ingestRoot.toString() }
            registry.add("balfouriana.ingestion.rest.api-key") { "secret-key" }
        }
    }

    @Test
    fun `post ingest rejects missing api key`() {
        val file = MockMultipartFile("file", "upload.csv", MediaType.TEXT_PLAIN_VALUE, "a,b,c".toByteArray())
        mockMvc.perform(multipart("/ingest").file(file))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `post ingest rejects invalid api key`() {
        val file = MockMultipartFile("file", "upload.csv", MediaType.TEXT_PLAIN_VALUE, "a,b,c".toByteArray())
        mockMvc.perform(
            multipart("/ingest")
                .file(file)
                .header(IngestionApiKeyFilter.INGESTION_API_KEY_HEADER, "wrong-key")
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `post ingest accepts valid api key`() {
        val file = MockMultipartFile("file", "upload.csv", MediaType.TEXT_PLAIN_VALUE, "a,b,c".toByteArray())
        mockMvc.perform(
            multipart("/ingest")
                .file(file)
                .header(IngestionApiKeyFilter.INGESTION_API_KEY_HEADER, "secret-key")
        ).andExpect(status().isOk)
    }
}
