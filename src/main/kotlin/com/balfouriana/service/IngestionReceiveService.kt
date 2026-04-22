package com.balfouriana.service

import com.balfouriana.domain.IngestionChannel
import com.balfouriana.domain.RawIngestionArtifact
import java.util.UUID

interface IngestionReceiveService {
    fun receive(
        bytes: ByteArray,
        originalFilename: String,
        channel: IngestionChannel,
        correlationId: UUID
    ): RawIngestionArtifact
}
