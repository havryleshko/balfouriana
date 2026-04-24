package com.balfouriana.service.parsing

import com.balfouriana.domain.CanonicalRecordType
import com.balfouriana.domain.IngestionFileFormat
import com.balfouriana.domain.ParseRejectionCode
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.time.LocalDate

@Component
class CsvIngestionFormatParser : IngestionFormatParser {
    override fun supports(format: IngestionFileFormat): Boolean = format == IngestionFileFormat.CSV

    override fun parse(request: ParseRequest): ParserBatchResult {
        val text = request.bytes.toString(StandardCharsets.UTF_8)
        val lines = text
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .split('\n')
            .filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            return ParserBatchResult(
                format = IngestionFileFormat.CSV,
                outcomes = listOf(
                    ParserRecordOutcome.Rejected(
                        ParserRejection(0, ParseRejectionCode.MALFORMED_RECORD, "CSV file is empty", null)
                    )
                )
            )
        }

        val headers = parseCsvLine(lines.first()).map { normalizeHeader(it) }
        if (headers.isEmpty()) {
            return ParserBatchResult(
                format = IngestionFileFormat.CSV,
                outcomes = listOf(
                    ParserRecordOutcome.Rejected(
                        ParserRejection(0, ParseRejectionCode.MALFORMED_RECORD, "CSV header row is invalid", lines.first())
                    )
                )
            )
        }

        val outcomes = mutableListOf<ParserRecordOutcome>()
        lines.drop(1).forEachIndexed { index, rawLine ->
            val recordIndex = index + 1
            val values = parseCsvLine(rawLine)
            if (values.size != headers.size) {
                outcomes += ParserRecordOutcome.Rejected(
                    ParserRejection(
                        recordIndex,
                        ParseRejectionCode.MALFORMED_RECORD,
                        "CSV row column count ${values.size} does not match header ${headers.size}",
                        rawLine
                    )
                )
                return@forEachIndexed
            }

            val fields = headers.zip(values).associate { (h, v) -> h to v.trim() }
            val type = resolveType(fields["record_type"])
            if (type == null) {
                outcomes += ParserRecordOutcome.Rejected(
                    ParserRejection(
                        recordIndex,
                        ParseRejectionCode.MISSING_REQUIRED_FIELD,
                        "record_type is required and must be one of TRADE, POSITION, CASH_MOVEMENT, CORPORATE_ACTION",
                        rawLine
                    )
                )
                return@forEachIndexed
            }

            val typeCheck = validateForType(type, fields)
            if (typeCheck != null) {
                outcomes += ParserRecordOutcome.Rejected(
                    ParserRejection(recordIndex, typeCheck.first, typeCheck.second, rawLine)
                )
                return@forEachIndexed
            }

            outcomes += ParserRecordOutcome.Parsed(
                ParsedStructuredRecord(
                    recordIndex = recordIndex,
                    typeHint = type,
                    fields = fields,
                    rawRecord = rawLine
                )
            )
        }

        return ParserBatchResult(format = IngestionFileFormat.CSV, outcomes = outcomes)
    }

    private fun validateForType(type: CanonicalRecordType, fields: Map<String, String>): Pair<ParseRejectionCode, String>? {
        val required = when (type) {
            CanonicalRecordType.TRADE -> listOf("trade_id", "instrument_id", "trade_date", "quantity", "price")
            CanonicalRecordType.POSITION -> listOf("position_id", "instrument_id", "position_date", "quantity")
            CanonicalRecordType.CASH_MOVEMENT -> listOf("cash_id", "currency", "amount", "value_date")
            CanonicalRecordType.CORPORATE_ACTION -> listOf("action_id", "instrument_id", "action_type", "effective_date")
        }
        val missing = required.firstOrNull { fields[it].isNullOrBlank() }
        if (missing != null) {
            return ParseRejectionCode.MISSING_REQUIRED_FIELD to "Missing required field: $missing"
        }

        if (type == CanonicalRecordType.TRADE || type == CanonicalRecordType.POSITION) {
            val quantity = fields["quantity"].orEmpty()
            if (quantity.toBigDecimalOrNull() == null) {
                return ParseRejectionCode.INVALID_NUMBER_FORMAT to "Invalid quantity value: $quantity"
            }
        }
        if (type == CanonicalRecordType.TRADE) {
            val price = fields["price"].orEmpty()
            if (price.toBigDecimalOrNull() == null) {
                return ParseRejectionCode.INVALID_NUMBER_FORMAT to "Invalid price value: $price"
            }
            if (!isIsoDate(fields["trade_date"].orEmpty())) {
                return ParseRejectionCode.INVALID_DATE_FORMAT to "trade_date must be ISO-8601 yyyy-MM-dd"
            }
        }
        if (type == CanonicalRecordType.POSITION && !isIsoDate(fields["position_date"].orEmpty())) {
            return ParseRejectionCode.INVALID_DATE_FORMAT to "position_date must be ISO-8601 yyyy-MM-dd"
        }
        if (type == CanonicalRecordType.CASH_MOVEMENT) {
            if (fields["amount"].orEmpty().toBigDecimalOrNull() == null) {
                return ParseRejectionCode.INVALID_NUMBER_FORMAT to "Invalid amount value: ${fields["amount"]}"
            }
            if (!isIsoDate(fields["value_date"].orEmpty())) {
                return ParseRejectionCode.INVALID_DATE_FORMAT to "value_date must be ISO-8601 yyyy-MM-dd"
            }
        }
        if (type == CanonicalRecordType.CORPORATE_ACTION && !isIsoDate(fields["effective_date"].orEmpty())) {
            return ParseRejectionCode.INVALID_DATE_FORMAT to "effective_date must be ISO-8601 yyyy-MM-dd"
        }
        return null
    }

    private fun isIsoDate(value: String): Boolean = runCatching { LocalDate.parse(value) }.isSuccess

    private fun resolveType(raw: String?): CanonicalRecordType? {
        return when (raw?.trim()?.uppercase()) {
            "TRADE" -> CanonicalRecordType.TRADE
            "POSITION" -> CanonicalRecordType.POSITION
            "CASH_MOVEMENT" -> CanonicalRecordType.CASH_MOVEMENT
            "CORPORATE_ACTION" -> CanonicalRecordType.CORPORATE_ACTION
            else -> null
        }
    }

    private fun normalizeHeader(header: String): String {
        return header.trim().lowercase().replace(' ', '_')
    }

    private fun parseCsvLine(line: String): List<String> {
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' && (i + 1 < line.length && line[i + 1] == '"') -> {
                    current.append('"')
                    i += 2
                    continue
                }
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    cells += current.toString()
                    current.clear()
                }
                else -> current.append(ch)
            }
            i++
        }
        cells += current.toString()
        return cells
    }
}
