package com.balfouriana.service.rules

import com.balfouriana.domain.CalculationAppliedEvent
import com.balfouriana.domain.CanonicalRecordValidatedEvent
import com.balfouriana.domain.EventMetadata
import com.balfouriana.domain.FilingReadyRecordEvent
import com.balfouriana.domain.RuleDecisionEvent
import com.balfouriana.domain.RuleExceptionEnvelope
import com.balfouriana.domain.RuleExceptionRaisedEvent
import com.balfouriana.domain.RuleOutcome
import com.balfouriana.domain.RuleSeverity
import com.balfouriana.repository.EventStoreRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

@Service
class RuleEngineService(
    private val rulePackRegistry: RulePackRegistry,
    private val eventStoreRepository: EventStoreRepository,
    private val objectMapper: ObjectMapper
) {
    fun process(event: CanonicalRecordValidatedEvent): RuleEngineProcessingResult {
        val pack = rulePackRegistry.select(event)
        val inputFingerprint = fingerprint(event.validatedFields + mapOf("_rule_pack_version" to pack.version.version))
        val decisionEvents = mutableListOf<RuleDecisionEvent>()
        val exceptions = mutableListOf<RuleExceptionEnvelope>()

        pack.rules.forEach { rule ->
            val result = rule.evaluate(event)
            val exception = if (result.outcome == RuleOutcome.FAIL || result.outcome == RuleOutcome.NEEDS_REVIEW) {
                RuleExceptionEnvelope(
                    exceptionId = UUID.randomUUID(),
                    correlationId = event.metadata.correlationId,
                    eventId = event.metadata.eventId,
                    regime = event.metadata.regimes.firstOrNull(),
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
                RuleDecisionEvent(
                    metadata = eventMetadata(event, "rules.step3.decision.v1"),
                    artifactId = event.artifactId,
                    recordType = event.recordType,
                    recordIndex = event.envelope.recordIndex,
                    rulePackVersion = pack.version,
                    ruleResult = result,
                    inputFingerprint = inputFingerprint,
                    outputFingerprint = null,
                    exceptionId = exception?.exceptionId
                )
            )
        }

        val hasBlockingErrors = exceptions.any { it.severity == RuleSeverity.ERROR }
        if (hasBlockingErrors) {
            val outputFingerprint = fingerprint(event.validatedFields)
            decisionEvents.map { it.copy(outputFingerprint = outputFingerprint) }
                .forEach { eventStoreRepository.append(it) }
            exceptions.map { envelope ->
                RuleExceptionRaisedEvent(
                    metadata = eventMetadata(event, "rules.step3.exception.v1"),
                    artifactId = event.artifactId,
                    recordType = event.recordType,
                    recordIndex = event.envelope.recordIndex,
                    rulePackVersion = pack.version,
                    exception = envelope
                )
            }.forEach { eventStoreRepository.append(it) }
            return RuleEngineProcessingResult(
                decisionCount = decisionEvents.size,
                calculationCount = 0,
                exceptionCount = exceptions.size,
                emittedFilingReadyEvent = false,
                outputFingerprint = outputFingerprint
            )
        }

        val calculationOutputs = pack.calculations.map { it.apply(event) }
        val calculatedFields = calculationOutputs.flatMap { it.calculatedFields.entries }
            .associate { it.key to it.value }
        val calculationMetadata = calculationOutputs.flatMap { it.metadata.entries }
            .associate { it.key to it.value }
        val filingReadyFields = event.validatedFields + calculatedFields
        val outputFingerprint = fingerprint(filingReadyFields)

        decisionEvents.map { it.copy(outputFingerprint = outputFingerprint) }
            .forEach { eventStoreRepository.append(it) }
        exceptions.map { envelope ->
            RuleExceptionRaisedEvent(
                metadata = eventMetadata(event, "rules.step3.exception.v1"),
                artifactId = event.artifactId,
                recordType = event.recordType,
                recordIndex = event.envelope.recordIndex,
                rulePackVersion = pack.version,
                exception = envelope
            )
        }.forEach { eventStoreRepository.append(it) }
        calculationOutputs.forEach { output ->
            eventStoreRepository.append(
                CalculationAppliedEvent(
                    metadata = eventMetadata(event, "rules.step3.calculation.v1"),
                    artifactId = event.artifactId,
                    recordType = event.recordType,
                    recordIndex = event.envelope.recordIndex,
                    rulePackVersion = pack.version,
                    calculationMethod = output.methodRef,
                    calculatedFields = output.calculatedFields,
                    calculationMetadata = output.metadata,
                    inputFingerprint = inputFingerprint,
                    outputFingerprint = outputFingerprint
                )
            )
        }
        eventStoreRepository.append(
            FilingReadyRecordEvent(
                metadata = eventMetadata(event, "rules.step3.filing-ready.v1"),
                artifactId = event.artifactId,
                envelope = event.envelope,
                recordType = event.recordType,
                rulePackVersion = pack.version,
                filingReadyFields = filingReadyFields,
                traceMetadata = mapOf("sourceValidatedSchemaVersion" to event.metadata.schemaVersion) + calculationMetadata,
                inputFingerprint = inputFingerprint,
                outputFingerprint = outputFingerprint
            )
        )

        return RuleEngineProcessingResult(
            decisionCount = decisionEvents.size,
            calculationCount = calculationOutputs.size,
            exceptionCount = exceptions.size,
            emittedFilingReadyEvent = true,
            outputFingerprint = outputFingerprint
        )
    }

    private fun eventMetadata(event: CanonicalRecordValidatedEvent, schemaVersion: String): EventMetadata {
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
            "PRICE_MISSING_OR_NON_POSITIVE" -> "Provide a positive price and re-run Step 3"
            "RECORD_TYPE_MISMATCH" -> "Reconcile canonical mapping and source record_type"
            else -> "Review rule decision and correct source data before reprocessing"
        }
    }

    private fun fingerprint(fields: Map<String, String>): String {
        val canonical = objectMapper.writeValueAsString(fields.toSortedMap())
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

data class RuleEngineProcessingResult(
    val decisionCount: Int,
    val calculationCount: Int,
    val exceptionCount: Int,
    val emittedFilingReadyEvent: Boolean,
    val outputFingerprint: String
)
