package com.balfouriana.domain

import java.time.Instant
import java.util.UUID

data class RawIngestionArtifact(
    val artifactId: UUID,
    val correlationId: UUID,
    val channel: IngestionChannel,
    val originalFilename: String,
    val storedRelativePath: String,
    val byteSize: Long,
    val receivedAt: Instant
)
