package com.balfouriana.domain

import java.time.Instant
import java.util.UUID

data class SourceRecordEnvelope(
    val sourceId: String,
    val fileId: UUID,
    val recordIndex: Int,
    val ingestTimestamp: Instant,
    val format: IngestionFileFormat,
    val schemaHint: String?
)
