package com.balfouriana.service.validation

import com.balfouriana.domain.CanonicalRecordMappedEvent
import com.balfouriana.domain.CanonicalRecordValidatedEvent
import com.balfouriana.domain.EventMetadata
import com.balfouriana.domain.ValidationDecisionEvent
import com.balfouriana.domain.ValidationExceptionEnvelope
import com.balfouriana.domain.ValidationExceptionRaisedEvent
import com.balfouriana.domain.ValidationOutcome
import com.balfouriana.domain.ValidationSeverity
import com.balfouriana.repository.EventStoreRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

@Service
class ValidationAndMappingService(
    private val validationPackRegistry: ValidationPackRegistry,
    private val leiEnrichmentAdapter: LeiEnrichmentAdapter,
    private val instrumentEnrichmentAdapter: InstrumentEnrichmentAdapter,
    private val venueMicEnrichmentAdapter: VenueMicEnrichmentAdapter,
    private val eventStoreRepository: EventStoreRepository,
    private val objectMapper: ObjectMapper
) {
    fun process(event: CanonicalRecordMappedEvent): ValidationProcessingResult {
        val pack = validationPackRegistry.select(event)
        val inputFingerprint = fingerprint(event.canonicalFields)
        val decisionEvents = mutableListOf<ValidationDecisionEvent>()
        val exceptions = mutableListOf<ValidationExceptionEnvelope>()

        pack.rules.forEach { rule ->
            val result = rule.evaluate(event)
            val exception = if (result.outcome == ValidationOutcome.FAIL || result.outcome == ValidationOutcome.NEEDS_REVIEW) {
                ValidationExceptionEnvelope(
                    exceptionId = UUID.randomUUID(),
                    correlationId = event.metadata.correlationId,
                    eventId = event.metadata.eventId,
                    regime = pack.regime,
                    ruleId = result.ruleId,
                    severity = result.severity,
                    rejectionCategory = result.layer.name,
                    reasonCode = result.reasonCode,
                    message = result.message,
                    remediationHint = remediationHintFor(result.reasonCode)
                )
            } else {
                null
            }
            if (exception != null) {
                exceptions.add(exception)
            }
            decisionEvents.add(
                ValidationDecisionEvent(
                    metadata = decisionMetadata(event, "validation.step2.decision.v1"),
                    artifactId = event.artifactId,
                    recordType = event.recordType,
                    recordIndex = event.envelope.recordIndex,
                    validationPack = pack.version,
                    ruleResult = result,
                    inputFingerprint = inputFingerprint,
                    outputFingerprint = null,
                    exceptionId = exception?.exceptionId
                )
            )
        }

        val enrichmentResults = listOf(
            leiEnrichmentAdapter.enrich(event),
            instrumentEnrichmentAdapter.enrich(event),
            venueMicEnrichmentAdapter.enrich(event)
        )

        val enrichmentFields = enrichmentResults.flatMap { it.fields.entries }.associate { it.key to it.value }
        val enrichmentMetadata = enrichmentResults.flatMap { it.metadata.entries }.associate { it.key to it.value }
        enrichmentResults.mapNotNull { it.exception }.forEach { exceptions.add(it) }

        val validatedFields = event.canonicalFields + enrichmentFields
        val outputFingerprint = fingerprint(validatedFields)
        val exceptionIds = exceptions.map { it.exceptionId }.toSet()

        val finalizedDecisions = decisionEvents.map {
            it.copy(outputFingerprint = outputFingerprint)
        }
        finalizedDecisions.forEach { eventStoreRepository.append(it) }

        val exceptionEvents = exceptions.map { envelope ->
            ValidationExceptionRaisedEvent(
                metadata = decisionMetadata(event, "validation.step2.exception.v1"),
                artifactId = event.artifactId,
                recordType = event.recordType,
                recordIndex = event.envelope.recordIndex,
                validationPack = pack.version,
                exception = envelope
            )
        }
        exceptionEvents.forEach { eventStoreRepository.append(it) }

        val hasBlockingErrors = exceptions.any { it.severity == ValidationSeverity.ERROR }
        if (!hasBlockingErrors) {
            val validatedEvent = CanonicalRecordValidatedEvent(
                metadata = decisionMetadata(event, "validation.step2.validated.v1"),
                artifactId = event.artifactId,
                envelope = event.envelope,
                recordType = event.recordType,
                validationPack = pack.version,
                validatedFields = validatedFields,
                enrichmentMetadata = enrichmentMetadata
            )
            eventStoreRepository.append(validatedEvent)
            return ValidationProcessingResult(
                decisionCount = finalizedDecisions.size,
                exceptionCount = exceptionEvents.size,
                emittedValidatedEvent = true,
                outputFingerprint = outputFingerprint
            )
        }

        return ValidationProcessingResult(
            decisionCount = finalizedDecisions.size,
            exceptionCount = exceptionEvents.size,
            emittedValidatedEvent = false,
            outputFingerprint = outputFingerprint,
            blockingExceptionIds = exceptionIds
        )
    }

    private fun decisionMetadata(event: CanonicalRecordMappedEvent, schemaVersion: String): EventMetadata {
        return EventMetadata(
            eventId = UUID.randomUUID(),
            correlationId = event.metadata.correlationId,
            sourceSystem = event.metadata.sourceSystem,
            occurredAt = Instant.now(),
            schemaVersion = schemaVersion,
            regimes = event.metadata.regimes
        )
    }

    private fun remediationHintFor(reasonCode: String): String {
        return when (reasonCode) {
            "MISSING_REQUIRED_FIELD" -> "Populate required source field and resubmit"
            "INVALID_NUMERIC_FORMAT" -> "Use numeric format accepted by schema"
            "NON_POSITIVE_VALUE" -> "Provide value greater than zero where required"
            "INVALID_DATE_FORMAT" -> "Use ISO-8601 date format (yyyy-MM-dd)"
            else -> "Review source record and correct field values"
        }
    }

    private fun fingerprint(fields: Map<String, String>): String {
        val canonical = objectMapper.writeValueAsString(fields.toSortedMap())
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

data class ValidationProcessingResult(
    val decisionCount: Int,
    val exceptionCount: Int,
    val emittedValidatedEvent: Boolean,
    val outputFingerprint: String,
    val blockingExceptionIds: Set<UUID> = emptySet()
)
