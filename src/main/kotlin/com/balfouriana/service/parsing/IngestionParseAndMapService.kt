package com.balfouriana.service.parsing

import com.balfouriana.domain.CanonicalRecordMappedEvent
import com.balfouriana.domain.EventMetadata
import com.balfouriana.domain.ParseRecordRejectedEvent
import com.balfouriana.domain.RawIngestionArtifact
import com.balfouriana.domain.SourceRecordEnvelope
import com.balfouriana.repository.EventStoreRepository
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class IngestionParseAndMapService(
    private val parserRouter: ParserRouter,
    private val canonicalRecordMapper: CanonicalRecordMapper,
    private val eventStoreRepository: EventStoreRepository,
    private val meterRegistry: MeterRegistry
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun process(artifact: RawIngestionArtifact, bytes: ByteArray) {
        val parseStartedAt = System.nanoTime()
        val request = ParseRequest(
            artifactId = artifact.artifactId,
            sourceId = sourceIdFor(artifact),
            originalFilename = artifact.originalFilename,
            bytes = bytes
        )
        val batch = parserRouter.route(request)

        var emitted = 0
        var rejected = 0

        batch.outcomes.forEach { outcome ->
            when (outcome) {
                is ParserRecordOutcome.Parsed -> {
                    when (val mapping = canonicalRecordMapper.map(outcome.record)) {
                        is MappingOutcome.Mapped -> {
                            emitted++
                            val event = CanonicalRecordMappedEvent(
                                metadata = eventMetadata(artifact, "ingestion.parse.v1"),
                                artifactId = artifact.artifactId,
                                envelope = SourceRecordEnvelope(
                                    sourceId = sourceIdFor(artifact),
                                    fileId = artifact.artifactId,
                                    recordIndex = outcome.record.recordIndex,
                                    ingestTimestamp = artifact.receivedAt,
                                    format = batch.format,
                                    schemaHint = request.schemaHint
                                ),
                                recordType = mapping.recordType,
                                canonicalFields = mapping.canonicalFields
                            )
                            eventStoreRepository.append(event)
                        }
                        is MappingOutcome.Rejected -> {
                            rejected++
                            eventStoreRepository.append(
                                rejectedEvent(
                                    artifact = artifact,
                                    format = batch.format,
                                    rejection = mapping.rejection,
                                    schemaHint = request.schemaHint
                                )
                            )
                        }
                    }
                }
                is ParserRecordOutcome.Rejected -> {
                    rejected++
                    eventStoreRepository.append(
                        rejectedEvent(
                            artifact = artifact,
                            format = batch.format,
                            rejection = outcome.rejection,
                            schemaHint = request.schemaHint
                        )
                    )
                }
            }
        }

        val durationSeconds = (System.nanoTime() - parseStartedAt) / 1_000_000_000.0
        meterRegistry.counter("balfouriana.ingestion.parse.rows.processed", "format", batch.format.name).increment(batch.outcomes.size.toDouble())
        meterRegistry.counter("balfouriana.ingestion.parse.rows.emitted", "format", batch.format.name).increment(emitted.toDouble())
        meterRegistry.counter("balfouriana.ingestion.parse.rows.rejected", "format", batch.format.name).increment(rejected.toDouble())
        meterRegistry.timer("balfouriana.ingestion.parse.duration", "format", batch.format.name).record(java.time.Duration.ofNanos((durationSeconds * 1_000_000_000L).toLong()))

        logger.info(
            "parse_complete fileId={} sourceId={} format={} rowsProcessed={} rowsEmitted={} rowsRejected={} durationSeconds={}",
            artifact.artifactId,
            sourceIdFor(artifact),
            batch.format,
            batch.outcomes.size,
            emitted,
            rejected,
            "%.4f".format(durationSeconds)
        )
    }

    private fun rejectedEvent(
        artifact: RawIngestionArtifact,
        format: com.balfouriana.domain.IngestionFileFormat,
        rejection: ParserRejection,
        schemaHint: String?
    ): ParseRecordRejectedEvent {
        return ParseRecordRejectedEvent(
            metadata = eventMetadata(artifact, "ingestion.parse.rejection.v1"),
            artifactId = artifact.artifactId,
            envelope = SourceRecordEnvelope(
                sourceId = sourceIdFor(artifact),
                fileId = artifact.artifactId,
                recordIndex = rejection.recordIndex,
                ingestTimestamp = artifact.receivedAt,
                format = format,
                schemaHint = schemaHint
            ),
            code = rejection.code,
            reason = rejection.reason,
            rawRecord = rejection.rawRecord
        )
    }

    private fun eventMetadata(artifact: RawIngestionArtifact, schemaVersion: String): EventMetadata {
        return EventMetadata(
            eventId = UUID.randomUUID(),
            correlationId = artifact.correlationId,
            sourceSystem = sourceIdFor(artifact),
            occurredAt = Instant.now(),
            schemaVersion = schemaVersion,
            regimes = emptySet()
        )
    }

    private fun sourceIdFor(artifact: RawIngestionArtifact): String {
        return when (artifact.channel) {
            com.balfouriana.domain.IngestionChannel.REST -> "rest-ingest"
            com.balfouriana.domain.IngestionChannel.DROP_ZONE -> "drop-zone"
        }
    }
}
