package com.balfouriana.service.parsing

import com.balfouriana.domain.IngestionFileFormat
import com.balfouriana.domain.ParseRejectionCode
import org.springframework.stereotype.Component

@Component
class ParserRouter(
    private val fileFormatDetector: FileFormatDetector,
    private val parsers: List<IngestionFormatParser>
) {
    fun route(request: ParseRequest): ParserBatchResult {
        if (request.bytes.isEmpty()) {
            return ParserBatchResult(
                format = IngestionFileFormat.UNKNOWN,
                outcomes = listOf(
                    ParserRecordOutcome.Rejected(
                        ParserRejection(
                            recordIndex = 0,
                            code = ParseRejectionCode.EMPTY_PAYLOAD,
                            reason = "Payload is empty for file ${request.originalFilename}",
                            rawRecord = null
                        )
                    )
                )
            )
        }
        val format = fileFormatDetector.detect(request)
        val parser = parsers.firstOrNull { it.supports(format) }
        if (parser == null) {
            return ParserBatchResult(
                format = IngestionFileFormat.UNKNOWN,
                outcomes = listOf(
                    ParserRecordOutcome.Rejected(
                        ParserRejection(
                            recordIndex = 0,
                            code = ParseRejectionCode.UNSUPPORTED_FORMAT,
                            reason = "Unsupported ingestion format for file ${request.originalFilename}",
                            rawRecord = null
                        )
                    )
                )
            )
        }
        return runCatching { parser.parse(request) }.getOrElse { ex ->
            ParserBatchResult(
                format = format,
                outcomes = listOf(
                    ParserRecordOutcome.Rejected(
                        ParserRejection(
                            recordIndex = 0,
                            code = ParseRejectionCode.PARSER_FAILURE,
                            reason = "Parser failure: ${ex.message ?: ex::class.simpleName ?: "unknown"}",
                            rawRecord = null
                        )
                    )
                )
            )
        }
    }
}
