package com.balfouriana.api

import com.balfouriana.domain.DomainEvent
import com.balfouriana.domain.FilingReadyRecordEvent
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class IngestionStep3PipelineIntegrationTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Test
    fun `csv ingest emits default step3 events and filing ready payload contract`() {
        val csv = """
            record_type,trade_id,instrument_id,trade_date,quantity,price,currency,buyer_lei,seller_lei,decision_maker_lei,execution_actor_type,venue_code,otc_indicator,waiver_indicator,short_selling_indicator,commodity_derivative_indicator,price_notation,price_currency
            TRADE,T-100,GB00B03MLX29,2026-04-22,100,10.3,GBP,5493001KJTIIGC8Y1R12,213800D1EI4B9WTWWD28,7245008N4E6Y7Z5RAA41,HUMAN,XLON,N,N,N,N,MONETARY,GBP
        """.trimIndent()
        val file = MockMultipartFile("file", "step3-mifid.csv", MediaType.TEXT_PLAIN_VALUE, csv.toByteArray())
        val response = mockMvc.perform(multipart("/ingest").file(file))
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString
        val artifactId = objectMapper.readTree(response).path("artifactId").asText()
        val correlationId = correlationIdForArtifact(UUID.fromString(artifactId))
        val eventTypes = waitForEventTypes(correlationId)

        assertTrue(eventTypes.contains("RuleDecisionEvent"), eventTypes.toString())
        assertTrue(eventTypes.contains("CalculationAppliedEvent"), eventTypes.toString())
        assertTrue(eventTypes.contains("FilingReadyRecordEvent"), eventTypes.toString())

        val payload = jdbcTemplate.queryForObject(
            "select payload from event_store where event_type = 'FilingReadyRecordEvent' and payload like ? order by created_at desc limit 1",
            String::class.java,
            "%$artifactId%"
        ) ?: error("missing FilingReadyRecordEvent payload")
        val event = objectMapper.readValue<DomainEvent>(payload) as FilingReadyRecordEvent
        assertEquals("step3-core-default", event.rulePackVersion.packId)
        assertEquals("rules.step3.filing-ready.v1", event.metadata.schemaVersion)
        assertTrue(event.filingReadyFields.containsKey("calculated_notional"))
        assertFalse(event.filingReadyFields.containsKey("mifid_execution_mode"))
        assertTrue(event.traceMetadata.containsKey("step3_rule_pack_version"))
        assertTrue(event.traceMetadata.containsKey("step3_applied_calculation_ids"))
    }

    @Test
    fun `csv ingest with blocking step2 input suppresses step3 emission`() {
        val csv = """
            record_type,trade_id,instrument_id,trade_date,quantity,price,currency
            TRADE,T-200,GB00B03MLX29,2026-04-22,100,-1,GBP
        """.trimIndent()
        val file = MockMultipartFile("file", "step3-block.csv", MediaType.TEXT_PLAIN_VALUE, csv.toByteArray())
        val response = mockMvc.perform(multipart("/ingest").file(file))
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString
        val artifactId = objectMapper.readTree(response).path("artifactId").asText()
        val correlationId = correlationIdForArtifact(UUID.fromString(artifactId))
        val eventTypes = waitForEventTypes(correlationId)

        assertTrue(eventTypes.contains("ValidationExceptionRaisedEvent"), eventTypes.toString())
        assertFalse(eventTypes.contains("RuleDecisionEvent"), eventTypes.toString())
        assertFalse(eventTypes.contains("FilingReadyRecordEvent"), eventTypes.toString())
    }

    private fun eventTypesForCorrelation(correlationId: UUID): Set<String> {
        return jdbcTemplate.query(
            "select distinct event_type from event_store where correlation_id = ?",
            { rs, _ -> rs.getString("event_type") },
            correlationId
        ).toSet()
    }

    private fun waitForEventTypes(correlationId: UUID): Set<String> {
        repeat(20) {
            val types = eventTypesForCorrelation(correlationId)
            if (types.isNotEmpty()) {
                return types
            }
            Thread.sleep(100)
        }
        return eventTypesForCorrelation(correlationId)
    }

    private fun correlationIdForArtifact(artifactId: UUID): UUID {
        val like = "%$artifactId%"
        repeat(20) {
            val value = jdbcTemplate.queryForObject(
                "select correlation_id from event_store where event_type = 'FileReceivedEvent' and payload like ? order by created_at desc limit 1",
                { rs, _ -> rs.getObject("correlation_id", UUID::class.java) },
                like
            )
            if (value != null) {
                return value
            }
            Thread.sleep(100)
        }
        return jdbcTemplate.queryForObject(
            "select correlation_id from event_store where event_type = 'FileReceivedEvent' and payload like ? order by created_at desc limit 1",
            { rs, _ -> rs.getObject("correlation_id", UUID::class.java) },
            like
        ) ?: error("missing correlation for artifact $artifactId")
    }
}
