package com.balfouriana.service.parsing

import com.balfouriana.domain.IngestionFileFormat
import com.balfouriana.domain.IngestionChannel
import com.balfouriana.domain.ParseRejectionCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class CsvIngestionFormatParserTest {
    private val parser = CsvIngestionFormatParser()

    @Test
    fun `parses valid trade csv row`() {
        val payload = """
            record_type,trade_id,instrument_id,trade_date,quantity,price,currency,venue
            TRADE,T-1,GB00B03MLX29,2026-04-22,100,12.33,GBP,XLON
        """.trimIndent()
        val result = parser.parse(
            ParseRequest(
                artifactId = UUID.randomUUID(),
                sourceId = "rest-ingest",
                sourceSystem = "rest-ingest",
                channel = IngestionChannel.REST,
                originalFilename = "trades.csv",
                receivedAt = java.time.Instant.parse("2026-04-22T10:00:00Z"),
                fileSizeBytes = payload.toByteArray().size.toLong(),
                payloadChecksumSha256 = "abc",
                bytes = payload.toByteArray(),
                declaredFormat = IngestionFileFormat.CSV
            )
        )
        assertEquals(IngestionFileFormat.CSV, result.format)
        assertEquals(1, result.outcomes.size)
        val outcome = result.outcomes.first()
        assertTrue(outcome is ParserRecordOutcome.Parsed)
    }

    @Test
    fun `rejects malformed trade date`() {
        val payload = """
            record_type,trade_id,instrument_id,trade_date,quantity,price
            TRADE,T-1,GB00B03MLX29,22/04/2026,100,12.33
        """.trimIndent()
        val result = parser.parse(
            ParseRequest(
                artifactId = UUID.randomUUID(),
                sourceId = "rest-ingest",
                sourceSystem = "rest-ingest",
                channel = IngestionChannel.REST,
                originalFilename = "trades.csv",
                receivedAt = java.time.Instant.parse("2026-04-22T10:00:00Z"),
                fileSizeBytes = payload.toByteArray().size.toLong(),
                payloadChecksumSha256 = "def",
                bytes = payload.toByteArray(),
                declaredFormat = IngestionFileFormat.CSV
            )
        )
        val outcome = result.outcomes.first() as ParserRecordOutcome.Rejected
        assertEquals(ParseRejectionCode.INVALID_DATE_FORMAT, outcome.rejection.code)
    }
}
