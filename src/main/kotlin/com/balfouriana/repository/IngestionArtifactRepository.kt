package com.balfouriana.repository

import com.balfouriana.domain.IngestionChannel
import java.time.Instant
import java.util.UUID

interface IngestionArtifactRepository {
    fun insert(
        artifactId: UUID,
        correlationId: UUID,
        channel: IngestionChannel,
        originalFilename: String,
        storedPath: String,
        byteSize: Long,
        receivedAt: Instant
    )
}
