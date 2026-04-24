package com.balfouriana.service.parsing

import com.balfouriana.domain.IngestionFileFormat
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class FollowOnParsersContractTest {
    private val jsonParser = JsonIngestionFormatParser(ObjectMapper().registerModule(KotlinModule.Builder().build()).findAndRegisterModules())
    private val fixParser = FixIngestionFormatParser()
    private val xmlParser = XmlIngestionFormatParser()

    @Test
    fun `json parser handles happy and malformed payload`() {
        val happy = """[{"record_type":"TRADE","trade_id":"T-1","instrument_id":"GB00B03MLX29"}]"""
        val malformed = """{"record_type":"TRADE""""
        assertTrue(parseSingle(jsonParser, "data.json", happy) is ParserRecordOutcome.Parsed)
        assertTrue(parseSingle(jsonParser, "data.json", malformed) is ParserRecordOutcome.Rejected)
    }

    @Test
    fun `fix parser handles happy and malformed payload`() {
        val happy = "35=D|trade_id=T-1|instrument_id=GB00B03MLX29"
        val malformed = "malformed-line-without-tags"
        assertTrue(parseSingle(fixParser, "data.fix", happy) is ParserRecordOutcome.Parsed)
        assertTrue(parseSingle(fixParser, "data.fix", malformed) is ParserRecordOutcome.Rejected)
    }

    @Test
    fun `xml parser handles happy and malformed payload`() {
        val happy = "<records><record><record_type>TRADE</record_type><trade_id>T-1</trade_id></record></records>"
        val malformed = "<records><record>"
        assertTrue(parseSingle(xmlParser, "data.xml", happy) is ParserRecordOutcome.Parsed)
        assertTrue(parseSingle(xmlParser, "data.xml", malformed) is ParserRecordOutcome.Rejected)
    }

    private fun parseSingle(parser: IngestionFormatParser, fileName: String, payload: String): ParserRecordOutcome {
        val format = when {
            fileName.endsWith(".json") -> IngestionFileFormat.JSON
            fileName.endsWith(".fix") -> IngestionFileFormat.FIX
            else -> IngestionFileFormat.XML
        }
        val result = parser.parse(
            ParseRequest(
                artifactId = UUID.randomUUID(),
                sourceId = "rest-ingest",
                originalFilename = fileName,
                bytes = payload.toByteArray(),
                declaredFormat = format
            )
        )
        assertEquals(1, result.outcomes.size)
        return result.outcomes.first()
    }
}
