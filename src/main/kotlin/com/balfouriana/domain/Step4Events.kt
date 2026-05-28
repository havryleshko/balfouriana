package com.balfouriana.domain

import java.util.UUID

enum class FilingOutputFormat {
    XML,
    ISO_20022_XML
}

enum class FilingAcknowledgementStatus {
    ACK,
    NACK,
    UNRESOLVED
}

val UNLINKED_ACK_CORRELATION_ID: UUID = UUID.fromString("00000000-0000-4000-8000-00000000000a")

data class FilingGenerationRequestedEvent(
    override val metadata: EventMetadata,
    val artifactId: UUID,
    val sourceFilingReadyEventId: UUID,
    val sourceFilingReadyFingerprint: String,
    val recordType: CanonicalRecordType,
    val filingTemplateId: String,
    val filingTemplateVersion: String,
    val filingOutputFormat: FilingOutputFormat
) : DomainEvent

data class FilingGeneratedEvent(
    override val metadata: EventMetadata,
    val artifactId: UUID,
    val sourceFilingReadyEventId: UUID,
    val sourceFilingReadyFingerprint: String,
    val recordType: CanonicalRecordType,
    val filingTemplateId: String,
    val filingTemplateVersion: String,
    val filingOutputFormat: FilingOutputFormat,
    val outputFileName: String,
    val outputChecksumSha256: String,
    val outputSizeBytes: Long
) : DomainEvent

data class FilingGenerationFailedEvent(
    override val metadata: EventMetadata,
    val artifactId: UUID,
    val sourceFilingReadyEventId: UUID,
    val sourceFilingReadyFingerprint: String,
    val recordType: CanonicalRecordType,
    val filingTemplateId: String,
    val filingTemplateVersion: String,
    val reasonCode: String,
    val message: String
) : DomainEvent

data class FilingSubmissionRequestedEvent(
    override val metadata: EventMetadata,
    val artifactId: UUID,
    val submissionId: UUID,
    val sourceFilingGeneratedEventId: UUID,
    val recordType: CanonicalRecordType,
    val channel: String,
    val outputFileName: String,
    val outputChecksumSha256: String
) : DomainEvent

data class FilingSubmittedEvent(
    override val metadata: EventMetadata,
    val artifactId: UUID,
    val submissionId: UUID,
    val sourceFilingGeneratedEventId: UUID,
    val recordType: CanonicalRecordType,
    val channel: String,
    val outputFileName: String,
    val outputChecksumSha256: String,
    val remotePath: String
) : DomainEvent

data class FilingSubmissionFailedEvent(
    override val metadata: EventMetadata,
    val artifactId: UUID,
    val submissionId: UUID,
    val sourceFilingGeneratedEventId: UUID,
    val recordType: CanonicalRecordType,
    val channel: String,
    val outputFileName: String,
    val reasonCode: String,
    val message: String
) : DomainEvent

data class FilingAcknowledgementReceivedEvent(
    override val metadata: EventMetadata,
    val submissionId: UUID?,
    val correlationLinked: Boolean,
    val linkedCorrelationId: UUID?,
    val channel: String,
    val acknowledgementStatus: FilingAcknowledgementStatus,
    val externalReference: String,
    val reasonCode: String?,
    val message: String?
) : DomainEvent
