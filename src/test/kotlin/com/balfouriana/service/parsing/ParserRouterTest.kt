package com.balfouriana.service.parsing

import com.balfouriana.domain.IngestionFileFormat
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
                originalFilename = "positions.csv",
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
                originalFilename = "binary.dat",
                bytes = byteArrayOf(0x01, 0x02, 0x03)
            )
        )
        val rejected = result.outcomes.first() as ParserRecordOutcome.Rejected
        assertEquals(ParseRejectionCode.UNSUPPORTED_FORMAT, rejected.rejection.code)
    }
}
