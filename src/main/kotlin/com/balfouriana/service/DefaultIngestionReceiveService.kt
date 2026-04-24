package com.balfouriana.service

import com.balfouriana.config.IngestionProperties
import com.balfouriana.domain.EventMetadata
import com.balfouriana.domain.FileReceivedEvent
import com.balfouriana.domain.IngestionChannel
import com.balfouriana.domain.RawIngestionArtifact
import com.balfouriana.repository.EventStoreRepository
import com.balfouriana.repository.IngestionArtifactRepository
import com.balfouriana.service.parsing.IngestionParseAndMapService
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.UUID

@Service
class DefaultIngestionReceiveService(
    private val properties: IngestionProperties,
    private val eventStoreRepository: EventStoreRepository,
    private val ingestionArtifactRepository: IngestionArtifactRepository,
    private val ingestionParseAndMapService: IngestionParseAndMapService
) : IngestionReceiveService {

    override fun receive(
        bytes: ByteArray,
        originalFilename: String,
        channel: IngestionChannel,
        correlationId: UUID
    ): RawIngestionArtifact {
        require(bytes.isNotEmpty()) { "empty file" }
        require(bytes.size <= properties.rest.maxFileSizeBytes) { "file exceeds max size" }

        val root = Path.of(properties.root).toAbsolutePath().normalize()
        val incoming = root.resolve("incoming")
        val processing = root.resolve("processing")
        val failed = root.resolve("failed")
        val received = root.resolve("received")
        listOf(incoming, processing, failed, received).forEach { Files.createDirectories(it) }

        val safeName = sanitizeFilename(originalFilename)
        val artifactId = UUID.randomUUID()
        val relativeStored = "received/${artifactId}_$safeName"
        val target = root.resolve(relativeStored)

        val tmp = Files.createTempFile(received, ".upload-", ".tmp")
        try {
            Files.write(tmp, bytes)
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: Exception) {
            Files.deleteIfExists(tmp)
            Files.deleteIfExists(target)
            throw e
        }

        val receivedAt = Instant.now()
        val sourceSystem = when (channel) {
            IngestionChannel.REST -> "rest-ingest"
            IngestionChannel.DROP_ZONE -> "drop-zone"
        }
        val eventId = UUID.randomUUID()
        val metadata = EventMetadata(
            eventId = eventId,
            correlationId = correlationId,
            sourceSystem = sourceSystem,
            occurredAt = receivedAt,
            schemaVersion = properties.receiveSchemaVersion,
            regimes = emptySet()
        )
        val domainEvent = FileReceivedEvent(
            metadata = metadata,
            channel = channel,
            artifactId = artifactId,
            originalFilename = originalFilename,
            storedRelativePath = relativeStored,
            byteSize = bytes.size.toLong()
        )
        try {
            ingestionArtifactRepository.insert(
                artifactId = artifactId,
                correlationId = correlationId,
                channel = channel,
                originalFilename = originalFilename,
                storedPath = relativeStored,
                byteSize = bytes.size.toLong(),
                receivedAt = receivedAt
            )
            eventStoreRepository.append(domainEvent)
        } catch (e: Exception) {
            Files.deleteIfExists(target)
            throw e
        }

        val artifact = RawIngestionArtifact(
            artifactId = artifactId,
            correlationId = correlationId,
            channel = channel,
            originalFilename = originalFilename,
            storedRelativePath = relativeStored,
            byteSize = bytes.size.toLong(),
            receivedAt = receivedAt
        )
        ingestionParseAndMapService.process(artifact, bytes)
        return artifact
    }

    private fun sanitizeFilename(name: String): String {
        val base = Path.of(name).fileName.toString().ifBlank { "unnamed" }
        val cleaned = base.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(200)
        return if (cleaned.isBlank()) "unnamed" else cleaned
    }
}
