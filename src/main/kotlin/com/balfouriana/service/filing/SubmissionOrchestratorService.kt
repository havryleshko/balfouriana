package com.balfouriana.service.filing

import com.balfouriana.config.FilingStep4Properties
import com.balfouriana.domain.EventMetadata
import com.balfouriana.domain.FilingGeneratedEvent
import com.balfouriana.domain.FilingGenerationFailedEvent
import com.balfouriana.domain.FilingGenerationRequestedEvent
import com.balfouriana.domain.FilingReadyRecordEvent
import com.balfouriana.domain.FilingSubmissionFailedEvent
import com.balfouriana.domain.FilingSubmissionRequestedEvent
import com.balfouriana.domain.FilingSubmittedEvent
import com.balfouriana.repository.EventStoreRepository
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class SubmissionOrchestratorService(
    private val eventStoreRepository: EventStoreRepository,
    private val filingRendererRegistry: FilingRendererRegistry,
    private val filingSubmissionClient: FilingSubmissionClient,
    private val filingStep4Properties: FilingStep4Properties
) {
    fun process(filingReadyEvent: FilingReadyRecordEvent) {
        if (!filingStep4Properties.enabled) {
            return
        }
        val rendered = runCatching {
            val rendered = filingRendererRegistry.render(filingReadyEvent)
            eventStoreRepository.append(
                FilingGenerationRequestedEvent(
                    metadata = metadata(filingReadyEvent, "filing.step4.gen.req.v1"),
                    artifactId = filingReadyEvent.artifactId,
                    sourceFilingReadyEventId = filingReadyEvent.metadata.eventId,
                    sourceFilingReadyFingerprint = filingReadyEvent.outputFingerprint,
                    recordType = filingReadyEvent.recordType,
                    filingTemplateId = rendered.templateId,
                    filingTemplateVersion = rendered.templateVersion,
                    filingOutputFormat = rendered.outputFormat
                )
            )
            eventStoreRepository.append(
                FilingGeneratedEvent(
                    metadata = metadata(filingReadyEvent, "filing.step4.gen.ok.v1"),
                    artifactId = filingReadyEvent.artifactId,
                    sourceFilingReadyEventId = filingReadyEvent.metadata.eventId,
                    sourceFilingReadyFingerprint = filingReadyEvent.outputFingerprint,
                    recordType = filingReadyEvent.recordType,
                    filingTemplateId = rendered.templateId,
                    filingTemplateVersion = rendered.templateVersion,
                    filingOutputFormat = rendered.outputFormat,
                    outputFileName = rendered.fileName,
                    outputChecksumSha256 = rendered.checksumSha256,
                    outputSizeBytes = rendered.payload.toByteArray().size.toLong()
                )
            )
            rendered
        }.getOrElse { ex ->
            eventStoreRepository.append(
                FilingGenerationFailedEvent(
                    metadata = metadata(filingReadyEvent, "filing.step4.gen.fail.v1"),
                    artifactId = filingReadyEvent.artifactId,
                    sourceFilingReadyEventId = filingReadyEvent.metadata.eventId,
                    sourceFilingReadyFingerprint = filingReadyEvent.outputFingerprint,
                    recordType = filingReadyEvent.recordType,
                    filingTemplateId = "unknown",
                    filingTemplateVersion = "unknown",
                    reasonCode = "FILING_RENDER_FAILED",
                    message = ex.message ?: "Filing rendering failed"
                )
            )
            return
        }

        val submissionId = UUID.randomUUID()
        eventStoreRepository.append(
            FilingSubmissionRequestedEvent(
                metadata = metadata(filingReadyEvent, "filing.step4.sub.req.v1"),
                artifactId = filingReadyEvent.artifactId,
                submissionId = submissionId,
                sourceFilingGeneratedEventId = UUID.randomUUID(),
                recordType = filingReadyEvent.recordType,
                channel = filingStep4Properties.submission.channel,
                outputFileName = rendered.fileName,
                outputChecksumSha256 = rendered.checksumSha256
            )
        )

        runCatching {
            filingSubmissionClient.submit(rendered.fileName, rendered.payload)
        }.onSuccess { remotePath ->
            eventStoreRepository.append(
                FilingSubmittedEvent(
                    metadata = metadata(filingReadyEvent, "filing.step4.sub.ok.v1"),
                    artifactId = filingReadyEvent.artifactId,
                    submissionId = submissionId,
                    sourceFilingGeneratedEventId = UUID.randomUUID(),
                    recordType = filingReadyEvent.recordType,
                    channel = filingStep4Properties.submission.channel,
                    outputFileName = rendered.fileName,
                    outputChecksumSha256 = rendered.checksumSha256,
                    remotePath = remotePath
                )
            )
        }.onFailure { ex ->
            eventStoreRepository.append(
                FilingSubmissionFailedEvent(
                    metadata = metadata(filingReadyEvent, "filing.step4.sub.fail.v1"),
                    artifactId = filingReadyEvent.artifactId,
                    submissionId = submissionId,
                    sourceFilingGeneratedEventId = UUID.randomUUID(),
                    recordType = filingReadyEvent.recordType,
                    channel = filingStep4Properties.submission.channel,
                    outputFileName = rendered.fileName,
                    reasonCode = "FILING_SUBMISSION_FAILED",
                    message = ex.message ?: "Submission failed"
                )
            )
        }
    }

    private fun metadata(event: FilingReadyRecordEvent, schemaVersion: String): EventMetadata {
        return EventMetadata(
            eventId = UUID.randomUUID(),
            correlationId = event.metadata.correlationId,
            sourceSystem = event.metadata.sourceSystem,
            occurredAt = Instant.now(),
            schemaVersion = schemaVersion,
            regimes = event.metadata.regimes
        )
    }
}
