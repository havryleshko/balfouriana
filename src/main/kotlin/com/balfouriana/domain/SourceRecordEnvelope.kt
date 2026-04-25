package com.balfouriana.domain

import java.time.Instant
import java.util.UUID

data class SourceRecordEnvelope(
    val sourceId: String,
    val sourceSystem: String,
    val ingestionChannel: IngestionChannel,
    val fileId: UUID,
    val originalFileName: String,
    val recordIndex: Int,
    val receivedAt: Instant,
    val format: IngestionFileFormat,
    val contentType: String,
    val fileSizeBytes: Long,
    val checksumSha256: String,
    val schemaHint: String?
)
