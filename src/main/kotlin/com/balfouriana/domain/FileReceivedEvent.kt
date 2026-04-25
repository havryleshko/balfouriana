package com.balfouriana.domain

import java.util.UUID

data class FileReceivedEvent(
    override val metadata: EventMetadata,
    val channel: IngestionChannel,
    val artifactId: UUID,
    val originalFilename: String,
    val storedRelativePath: String,
    val byteSize: Long,
    val payloadChecksumSha256: String
) : DomainEvent
