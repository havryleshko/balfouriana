package com.balfouriana.service.parsing

import com.balfouriana.domain.CanonicalRecordType
import com.balfouriana.domain.IngestionFileFormat
import com.balfouriana.domain.ParseRejectionCode
import java.util.UUID

data class ParseRequest(
    val artifactId: UUID,
    val sourceId: String,
    val originalFilename: String,
    val bytes: ByteArray,
    val declaredFormat: IngestionFileFormat? = null,
    val schemaHint: String? = null
)

data class ParsedStructuredRecord(
    val recordIndex: Int,
    val typeHint: CanonicalRecordType?,
    val fields: Map<String, String>,
    val rawRecord: String?
)

data class ParserRejection(
    val recordIndex: Int,
    val code: ParseRejectionCode,
    val reason: String,
    val rawRecord: String?
)

sealed interface ParserRecordOutcome {
    data class Parsed(val record: ParsedStructuredRecord) : ParserRecordOutcome
    data class Rejected(val rejection: ParserRejection) : ParserRecordOutcome
}

data class ParserBatchResult(
    val format: IngestionFileFormat,
    val outcomes: List<ParserRecordOutcome>
)

interface IngestionFormatParser {
    fun supports(format: IngestionFileFormat): Boolean
    fun parse(request: ParseRequest): ParserBatchResult
}
