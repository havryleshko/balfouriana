package com.balfouriana.service.parsing

import com.balfouriana.domain.CanonicalRecordMappedEvent
import com.balfouriana.domain.EventMetadata
import com.balfouriana.domain.ParseRecordRejectedEvent
import com.balfouriana.domain.RawIngestionArtifact
import com.balfouriana.domain.SourceRecordEnvelope
import com.balfouriana.domain.ValidationExceptionEnvelope
import com.balfouriana.domain.ValidationExceptionRaisedEvent
import com.balfouriana.domain.ValidationPackVersion
import com.balfouriana.domain.ValidationSeverity
import com.balfouriana.repository.EventStoreRepository
import com.balfouriana.service.validation.ValidationAndMappingService
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class IngestionParseAndMapService(
    private val parserRouter: ParserRouter,
    private val canonicalRecordMapper: CanonicalRecordMapper,
    private val validationAndMappingService: ValidationAndMappingService,
    private val eventStoreRepository: EventStoreRepository,
    private val meterRegistry: MeterRegistry
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun process(artifact: RawIngestionArtifact, bytes: ByteArray) {
        val parseStartedAt = System.nanoTime()
        val request = ParseRequest(
            artifactId = artifact.artifactId,
            sourceId = sourceIdFor(artifact),
            sourceSystem = artifact.sourceSystem,
            channel = artifact.channel,
            originalFilename = artifact.originalFilename,
            receivedAt = artifact.receivedAt,
            fileSizeBytes = artifact.byteSize,
            payloadChecksumSha256 = artifact.payloadChecksumSha256,
            bytes = bytes
        )
        val batch = parserRouter.route(request)
        if (batch.outcomes.isEmpty()) {
            eventStoreRepository.append(
                rejectedEvent(
                    artifact = artifact,
                    format = batch.format,
                    rejection = ParserRejection(
                        recordIndex = 0,
                        code = com.balfouriana.domain.ParseRejectionCode.PARSER_FAILURE,
                        reason = "Parser produced no outcomes",
                        rawRecord = null
                    ),
                    schemaHint = request.schemaHint
                )
            )
            meterRegistry.counter("balfouriana.ingestion.parse.rows.rejected", "format", batch.format.name).increment()
            logger.warn("parse_complete fileId={} sourceId={} format={} rowsProcessed=0 rowsEmitted=0 rowsRejected=1 reason=no_outcomes", artifact.artifactId, sourceIdFor(artifact), batch.format)
            return
        }

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
                                    sourceSystem = artifact.sourceSystem,
                                    ingestionChannel = artifact.channel,
                                    fileId = artifact.artifactId,
                                    originalFileName = artifact.originalFilename,
                                    recordIndex = outcome.record.recordIndex,
                                    receivedAt = artifact.receivedAt,
                                    format = batch.format,
                                    contentType = contentTypeFor(batch.format),
                                    fileSizeBytes = artifact.byteSize,
                                    checksumSha256 = artifact.payloadChecksumSha256,
                                    schemaHint = request.schemaHint
                                ),
                                recordType = mapping.recordType,
                                canonicalFields = mapping.canonicalFields
                            )
                            eventStoreRepository.append(event)
                            runCatching { validationAndMappingService.process(event) }
                                .onFailure { ex ->
                                    logger.error("validation_runtime_failure fileId={} recordIndex={} error={}", artifact.artifactId, outcome.record.recordIndex, ex.message ?: ex::class.simpleName, ex)
                                    eventStoreRepository.append(
                                        validationRuntimeExceptionEvent(
                                            event = event,
                                            message = ex.message ?: "Validation processing failed"
                                        )
                                    )
                                }
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
                sourceSystem = artifact.sourceSystem,
                ingestionChannel = artifact.channel,
                fileId = artifact.artifactId,
                originalFileName = artifact.originalFilename,
                recordIndex = rejection.recordIndex,
                receivedAt = artifact.receivedAt,
                format = format,
                contentType = contentTypeFor(format),
                fileSizeBytes = artifact.byteSize,
                checksumSha256 = artifact.payloadChecksumSha256,
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
            sourceSystem = artifact.sourceSystem,
            occurredAt = Instant.now(),
            schemaVersion = schemaVersion,
            regimes = emptySet()
        )
    }

    private fun validationRuntimeExceptionEvent(
        event: CanonicalRecordMappedEvent,
        message: String
    ): ValidationExceptionRaisedEvent {
        return ValidationExceptionRaisedEvent(
            metadata = EventMetadata(
                eventId = UUID.randomUUID(),
                correlationId = event.metadata.correlationId,
                sourceSystem = event.metadata.sourceSystem,
                occurredAt = Instant.now(),
                schemaVersion = "validation.step2.exception.runtime.v1",
                regimes = event.metadata.regimes
            ),
            artifactId = event.artifactId,
            recordType = event.recordType,
            recordIndex = event.envelope.recordIndex,
            validationPack = ValidationPackVersion(
                packId = "step2-runtime",
                version = "runtime-fallback",
                effectiveFrom = Instant.EPOCH
            ),
            exception = ValidationExceptionEnvelope(
                exceptionId = UUID.randomUUID(),
                correlationId = event.metadata.correlationId,
                eventId = event.metadata.eventId,
                regime = event.metadata.regimes.firstOrNull(),
                ruleId = "validation.runtime.failure",
                severity = ValidationSeverity.ERROR,
                rejectionCategory = "VALIDATION_RUNTIME",
                reasonCode = "VALIDATION_RUNTIME_FAILURE",
                message = message,
                remediationHint = "Review validation runtime logs and reprocess record"
            )
        )
    }

    private fun sourceIdFor(artifact: RawIngestionArtifact): String {
        return when (artifact.channel) {
            com.balfouriana.domain.IngestionChannel.SFTP -> "sftp-ingest"
            com.balfouriana.domain.IngestionChannel.REST -> "rest-ingest"
            com.balfouriana.domain.IngestionChannel.DROP_ZONE -> "drop-zone"
        }
    }

    private fun contentTypeFor(format: com.balfouriana.domain.IngestionFileFormat): String {
        return when (format) {
            com.balfouriana.domain.IngestionFileFormat.CSV -> "text/csv"
            com.balfouriana.domain.IngestionFileFormat.JSON -> "application/json"
            com.balfouriana.domain.IngestionFileFormat.FIX -> "text/plain"
            com.balfouriana.domain.IngestionFileFormat.XML -> "application/xml"
            com.balfouriana.domain.IngestionFileFormat.UNKNOWN -> "application/octet-stream"
        }
    }
}
