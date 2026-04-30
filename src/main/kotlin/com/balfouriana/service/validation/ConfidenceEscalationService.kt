package com.balfouriana.service.validation

import com.balfouriana.domain.CanonicalRecordMappedEvent
import com.balfouriana.domain.CanonicalRecordValidatedEvent
import com.balfouriana.domain.ConfidenceEscalationEvaluatedEvent
import com.balfouriana.domain.ConfidenceLevel
import com.balfouriana.domain.ConfidenceSourceStage
import com.balfouriana.domain.EscalationPriority
import com.balfouriana.domain.EventMetadata
import com.balfouriana.domain.RuleExceptionEnvelope
import com.balfouriana.domain.RuleSeverity
import com.balfouriana.domain.RulePackVersion
import com.balfouriana.domain.ValidationExceptionEnvelope
import com.balfouriana.domain.ValidationPackVersion
import com.balfouriana.domain.ValidationSeverity
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

@Service
class ConfidenceEscalationService(
    private val objectMapper: ObjectMapper
) {
    fun forValidation(
        event: CanonicalRecordMappedEvent,
        validationPack: ValidationPackVersion,
        exceptions: List<ValidationExceptionEnvelope>,
        inputFingerprint: String,
        outputFingerprint: String
    ): ConfidenceEscalationEvaluatedEvent {
        val blocking = exceptions.filter { it.severity == ValidationSeverity.ERROR }
        val review = exceptions.filter { it.severity == ValidationSeverity.WARNING }
        val model = modelFrom(blocking.size, review.size)
        val reasonCodes = exceptions.map { it.reasonCode }.toSet()
        val exceptionIds = exceptions.map { it.exceptionId }.toSet()
        val rejectionCategory = (blocking.firstOrNull() ?: review.firstOrNull())?.rejectionCategory ?: "NONE"
        val routingKey = routingKey(event.metadata.regimes.map { it.name }.toSet(), rejectionCategory, model.priority)
        return ConfidenceEscalationEvaluatedEvent(
            metadata = metadata(event.metadata.correlationId, event.metadata.sourceSystem, event.metadata.regimes),
            artifactId = event.artifactId,
            recordType = event.recordType,
            recordIndex = event.envelope.recordIndex,
            sourceStage = ConfidenceSourceStage.STEP2_VALIDATION,
            confidenceLevel = model.level,
            escalationPriority = model.priority,
            routingKey = routingKey,
            blockingCount = blocking.size,
            reviewCount = review.size,
            sourceReasonCodes = reasonCodes,
            sourceExceptionIds = exceptionIds,
            sourcePackId = validationPack.packId,
            sourcePackVersion = validationPack.version,
            sourceEventSchemaVersion = "validation.step2.exception.v1",
            inputFingerprint = inputFingerprint,
            outputFingerprint = fingerprint(outputFingerprint, routingKey, reasonCodes)
        )
    }

    fun forRules(
        event: CanonicalRecordValidatedEvent,
        rulePack: RulePackVersion,
        exceptions: List<RuleExceptionEnvelope>,
        inputFingerprint: String,
        outputFingerprint: String
    ): ConfidenceEscalationEvaluatedEvent {
        val blocking = exceptions.filter { it.severity == RuleSeverity.ERROR }
        val review = exceptions.filter { it.severity == RuleSeverity.WARNING }
        val model = modelFrom(blocking.size, review.size)
        val reasonCodes = exceptions.map { it.reasonCode }.toSet()
        val exceptionIds = exceptions.map { it.exceptionId }.toSet()
        val rejectionCategory = (blocking.firstOrNull() ?: review.firstOrNull())?.rejectionCategory ?: "NONE"
        val routingKey = routingKey(event.metadata.regimes.map { it.name }.toSet(), rejectionCategory, model.priority)
        return ConfidenceEscalationEvaluatedEvent(
            metadata = metadata(event.metadata.correlationId, event.metadata.sourceSystem, event.metadata.regimes),
            artifactId = event.artifactId,
            recordType = event.recordType,
            recordIndex = event.envelope.recordIndex,
            sourceStage = ConfidenceSourceStage.STEP3_RULES,
            confidenceLevel = model.level,
            escalationPriority = model.priority,
            routingKey = routingKey,
            blockingCount = blocking.size,
            reviewCount = review.size,
            sourceReasonCodes = reasonCodes,
            sourceExceptionIds = exceptionIds,
            sourcePackId = rulePack.packId,
            sourcePackVersion = rulePack.version,
            sourceEventSchemaVersion = "rules.step3.exception.v1",
            inputFingerprint = inputFingerprint,
            outputFingerprint = fingerprint(outputFingerprint, routingKey, reasonCodes)
        )
    }

    private fun modelFrom(blockingCount: Int, reviewCount: Int): ConfidenceModel {
        return when {
            blockingCount > 0 -> ConfidenceModel(ConfidenceLevel.LOW, EscalationPriority.P1_BLOCKING)
            reviewCount > 0 -> ConfidenceModel(ConfidenceLevel.MEDIUM, EscalationPriority.P2_REVIEW)
            else -> ConfidenceModel(ConfidenceLevel.HIGH, EscalationPriority.P3_MONITOR)
        }
    }

    private fun routingKey(regimes: Set<String>, category: String, priority: EscalationPriority): String {
        val regime = if (regimes.isEmpty()) "NONE" else regimes.sorted().joinToString("+")
        return "${regime}.${category}.${priority.name}"
    }

    private fun metadata(
        correlationId: UUID,
        sourceSystem: String,
        regimes: Set<com.balfouriana.domain.RegulatoryRegime>
    ): EventMetadata {
        return EventMetadata(
            eventId = UUID.randomUUID(),
            correlationId = correlationId,
            sourceSystem = sourceSystem,
            occurredAt = Instant.now(),
            schemaVersion = "confidence.escalation.v1",
            regimes = regimes
        )
    }

    private fun fingerprint(
        outputFingerprint: String,
        routingKey: String,
        reasonCodes: Set<String>
    ): String {
        val canonical = objectMapper.writeValueAsString(
            mapOf(
                "baseOutputFingerprint" to outputFingerprint,
                "routingKey" to routingKey,
                "reasonCodes" to reasonCodes.sorted()
            )
        )
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

private data class ConfidenceModel(
    val level: ConfidenceLevel,
    val priority: EscalationPriority
)
