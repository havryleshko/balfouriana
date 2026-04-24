package com.balfouriana.service.parsing

import com.balfouriana.domain.CanonicalRecordType
import com.balfouriana.domain.IngestionFileFormat
import com.balfouriana.domain.ParseRejectionCode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets

@Component
class JsonIngestionFormatParser(
    private val objectMapper: ObjectMapper
) : IngestionFormatParser {
    override fun supports(format: IngestionFileFormat): Boolean = format == IngestionFileFormat.JSON

    override fun parse(request: ParseRequest): ParserBatchResult {
        val raw = request.bytes.toString(StandardCharsets.UTF_8)
        val node = runCatching { objectMapper.readTree(raw) }.getOrElse {
            return ParserBatchResult(
                format = IngestionFileFormat.JSON,
                outcomes = listOf(
                    ParserRecordOutcome.Rejected(
                        ParserRejection(0, ParseRejectionCode.MALFORMED_RECORD, "Invalid JSON payload", raw)
                    )
                )
            )
        }

        val items = when {
            node.isArray -> node.toList()
            node.isObject -> listOf(node)
            else -> emptyList()
        }
        val outcomes = items.mapIndexed { index, item ->
            val recordType = resolveType(item.path("record_type").asText(null))
            if (recordType == null) {
                ParserRecordOutcome.Rejected(
                    ParserRejection(
                        recordIndex = index + 1,
                        code = ParseRejectionCode.MISSING_REQUIRED_FIELD,
                        reason = "record_type is required",
                        rawRecord = item.toString()
                    )
                )
            } else {
                val fields = item.fields().asSequence().associate { it.key.lowercase() to it.value.asText("") }
                ParserRecordOutcome.Parsed(
                    ParsedStructuredRecord(
                        recordIndex = index + 1,
                        typeHint = recordType,
                        fields = fields,
                        rawRecord = item.toString()
                    )
                )
            }
        }
        return ParserBatchResult(IngestionFileFormat.JSON, outcomes)
    }

    private fun resolveType(raw: String?): CanonicalRecordType? {
        return when (raw?.trim()?.uppercase()) {
            "TRADE" -> CanonicalRecordType.TRADE
            "POSITION" -> CanonicalRecordType.POSITION
            "CASH_MOVEMENT" -> CanonicalRecordType.CASH_MOVEMENT
            "CORPORATE_ACTION" -> CanonicalRecordType.CORPORATE_ACTION
            else -> null
        }
    }
}
