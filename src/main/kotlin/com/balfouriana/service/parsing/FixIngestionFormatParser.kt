package com.balfouriana.service.parsing

import com.balfouriana.domain.CanonicalRecordType
import com.balfouriana.domain.IngestionFileFormat
import com.balfouriana.domain.ParseRejectionCode
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets

@Component
class FixIngestionFormatParser : IngestionFormatParser {
    override fun supports(format: IngestionFileFormat): Boolean = format == IngestionFileFormat.FIX

    override fun parse(request: ParseRequest): ParserBatchResult {
        val lines = request.bytes.toString(StandardCharsets.UTF_8)
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .split('\n')
            .filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            return ParserBatchResult(
                format = IngestionFileFormat.FIX,
                outcomes = listOf(
                    ParserRecordOutcome.Rejected(
                        ParserRejection(0, ParseRejectionCode.MALFORMED_RECORD, "FIX payload is empty", null)
                    )
                )
            )
        }
        val outcomes = lines.mapIndexed { index, line ->
            val fields = line.split('|')
                .mapNotNull { pair ->
                    val eqIndex = pair.indexOf('=')
                    if (eqIndex <= 0 || eqIndex == pair.lastIndex) null
                    else pair.substring(0, eqIndex) to pair.substring(eqIndex + 1)
                }
                .toMap()
            val rawType = fields["35"] ?: fields["record_type"]
            val type = when (rawType) {
                "D", "8", "TRADE" -> CanonicalRecordType.TRADE
                "AP", "POSITION" -> CanonicalRecordType.POSITION
                "CM", "CASH_MOVEMENT" -> CanonicalRecordType.CASH_MOVEMENT
                "CA", "CORPORATE_ACTION" -> CanonicalRecordType.CORPORATE_ACTION
                else -> null
            }
            if (fields.isEmpty() || type == null) {
                ParserRecordOutcome.Rejected(
                    ParserRejection(
                        recordIndex = index + 1,
                        code = ParseRejectionCode.MALFORMED_RECORD,
                        reason = "FIX row missing required tags or unsupported message type",
                        rawRecord = line
                    )
                )
            } else {
                ParserRecordOutcome.Parsed(
                    ParsedStructuredRecord(
                        recordIndex = index + 1,
                        typeHint = type,
                        fields = fields.mapKeys { it.key.lowercase() },
                        rawRecord = line
                    )
                )
            }
        }
        return ParserBatchResult(IngestionFileFormat.FIX, outcomes)
    }
}
