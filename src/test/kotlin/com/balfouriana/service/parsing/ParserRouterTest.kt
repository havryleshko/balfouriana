package com.balfouriana.service.parsing

import com.balfouriana.domain.IngestionFileFormat
import com.balfouriana.domain.IngestionChannel
import com.balfouriana.domain.ParseRejectionCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class ParserRouterTest {
    private val router = ParserRouter(
        fileFormatDetector = FileFormatDetector(),
        parsers = listOf(CsvIngestionFormatParser())
    )

    @Test
    fun `routes based on extension`() {
        val payload = """
            record_type,trade_id,instrument_id,trade_date,quantity,price
            TRADE,T-1,GB00B03MLX29,2026-04-22,100,1.23
        """.trimIndent()
        val result = router.route(
            ParseRequest(
                artifactId = UUID.randomUUID(),
                sourceId = "drop-zone",
                sourceSystem = "drop-zone",
                channel = IngestionChannel.DROP_ZONE,
                originalFilename = "positions.csv",
                receivedAt = java.time.Instant.parse("2026-04-22T10:00:00Z"),
                fileSizeBytes = payload.toByteArray().size.toLong(),
                payloadChecksumSha256 = "abc",
                bytes = payload.toByteArray()
            )
        )
        assertEquals(IngestionFileFormat.CSV, result.format)
        assertTrue(result.outcomes.first() is ParserRecordOutcome.Parsed)
    }

    @Test
    fun `returns rejection for unsupported format`() {
        val result = router.route(
            ParseRequest(
                artifactId = UUID.randomUUID(),
                sourceId = "drop-zone",
                sourceSystem = "drop-zone",
                channel = IngestionChannel.DROP_ZONE,
                originalFilename = "binary.dat",
                receivedAt = java.time.Instant.parse("2026-04-22T10:00:00Z"),
                fileSizeBytes = 3,
                payloadChecksumSha256 = "def",
                bytes = byteArrayOf(0x01, 0x02, 0x03)
            )
        )
        val rejected = result.outcomes.first() as ParserRecordOutcome.Rejected
        assertEquals(ParseRejectionCode.UNSUPPORTED_FORMAT, rejected.rejection.code)
    }

    @Test
    fun `returns empty payload rejection`() {
        val result = router.route(
            ParseRequest(
                artifactId = UUID.randomUUID(),
                sourceId = "drop-zone",
                sourceSystem = "drop-zone",
                channel = IngestionChannel.DROP_ZONE,
                originalFilename = "empty.csv",
                receivedAt = java.time.Instant.parse("2026-04-22T10:00:00Z"),
                fileSizeBytes = 0,
                payloadChecksumSha256 = "0",
                bytes = byteArrayOf()
            )
        )
        val rejected = result.outcomes.first() as ParserRecordOutcome.Rejected
        assertEquals(ParseRejectionCode.EMPTY_PAYLOAD, rejected.rejection.code)
    }

    @Test
    fun `maps parser exceptions to parser failure rejection`() {
        val failing = object : IngestionFormatParser {
            override fun supports(format: IngestionFileFormat): Boolean = format == IngestionFileFormat.CSV
            override fun parse(request: ParseRequest): ParserBatchResult = error("boom")
        }
        val localRouter = ParserRouter(FileFormatDetector(), listOf(failing))
        val result = localRouter.route(
            ParseRequest(
                artifactId = UUID.randomUUID(),
                sourceId = "drop-zone",
                sourceSystem = "drop-zone",
                channel = IngestionChannel.DROP_ZONE,
                originalFilename = "data.csv",
                receivedAt = java.time.Instant.parse("2026-04-22T10:00:00Z"),
                fileSizeBytes = 4,
                payloadChecksumSha256 = "1",
                bytes = "a,b".toByteArray()
            )
        )
        val rejected = result.outcomes.first() as ParserRecordOutcome.Rejected
        assertEquals(ParseRejectionCode.PARSER_FAILURE, rejected.rejection.code)
    }
}
