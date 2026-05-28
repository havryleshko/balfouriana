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
import com.balfouriana.service.filing.validation.FilingSchemaValidationResult
import com.balfouriana.service.filing.validation.FilingSchemaValidatorRegistry
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class SubmissionOrchestratorService(
    private val eventStoreRepository: EventStoreRepository,
    private val filingRendererRegistry: FilingRendererRegistry,
    private val filingSchemaValidatorRegistry: FilingSchemaValidatorRegistry,
    private val filingSubmissionClient: FilingSubmissionClient,
    private val filingStep4Properties: FilingStep4Properties
) {
    fun process(filingReadyEvent: FilingReadyRecordEvent, forceResubmit: Boolean = false) {
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

        val schemaValidation = filingSchemaValidatorRegistry.validate(rendered)
        if (schemaValidation != null && !schemaValidation.valid) {
            eventStoreRepository.append(
                FilingGenerationFailedEvent(
                    metadata = metadata(filingReadyEvent, "filing.step4.gen.fail.v1"),
                    artifactId = filingReadyEvent.artifactId,
                    sourceFilingReadyEventId = filingReadyEvent.metadata.eventId,
                    sourceFilingReadyFingerprint = filingReadyEvent.outputFingerprint,
                    recordType = filingReadyEvent.recordType,
                    filingTemplateId = rendered.templateId,
                    filingTemplateVersion = rendered.templateVersion,
                    reasonCode = FilingSchemaValidationResult.REASON_CODE,
                    message = schemaValidation.summaryMessage()
                )
            )
            return
        }

        val generatedEvent = FilingGeneratedEvent(
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
        eventStoreRepository.append(generatedEvent)

        if (!forceResubmit && eventStoreRepository.hasSuccessfulSubmission(
                filingReadyEvent.metadata.correlationId,
                rendered.checksumSha256,
                rendered.templateVersion
            )
        ) {
            return
        }

        val submissionId = UUID.randomUUID()
        val generatedEventId = generatedEvent.metadata.eventId
        eventStoreRepository.append(
            FilingSubmissionRequestedEvent(
                metadata = metadata(filingReadyEvent, "filing.step4.sub.req.v1"),
                artifactId = filingReadyEvent.artifactId,
                submissionId = submissionId,
                sourceFilingGeneratedEventId = generatedEventId,
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
                    sourceFilingGeneratedEventId = generatedEventId,
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
                    sourceFilingGeneratedEventId = generatedEventId,
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
