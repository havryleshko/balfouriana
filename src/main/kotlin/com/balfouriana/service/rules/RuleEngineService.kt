package com.balfouriana.service.rules

import com.balfouriana.domain.CalculationAppliedEvent
import com.balfouriana.domain.CanonicalRecordValidatedEvent
import com.balfouriana.domain.EventMetadata
import com.balfouriana.domain.FilingReadyRecordEvent
import com.balfouriana.domain.RuleDecisionEvent
import com.balfouriana.domain.RuleExceptionEnvelope
import com.balfouriana.domain.RuleExceptionRaisedEvent
import com.balfouriana.domain.RegulatoryRegime
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
        val calcIdsJoined = calculationOutputs.joinToString(",") { it.methodRef.calculationId }
        val blockingErrorCount = exceptions.count { it.severity == RuleSeverity.ERROR }
        val needsReviewCount = exceptions.count { it.severity == RuleSeverity.WARNING }

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
        val traceBase = mapOf(
            "sourceValidatedSchemaVersion" to event.metadata.schemaVersion,
            "step3_rule_pack_id" to pack.version.packId,
            "step3_rule_pack_version" to pack.version.version,
            "step3_applied_calculation_ids" to calcIdsJoined
        ) + calculationMetadata
        val traceAifmd = if (pack.regime == RegulatoryRegime.AIFMD_II) {
            mapOf(
                "aifmd_review_required" to (needsReviewCount > 0).toString(),
                "aifmd_blocking_error_count" to blockingErrorCount.toString(),
                "aifmd_needs_review_count" to needsReviewCount.toString(),
                "aifmd_primary_metric" to "aifmd_commitment_leverage_ratio",
                "aifmd_primary_metric_value" to calculatedFields.getOrDefault("aifmd_commitment_leverage_ratio", "")
            )
        } else {
            emptyMap()
        }
        eventStoreRepository.append(
            FilingReadyRecordEvent(
                metadata = eventMetadata(event, "rules.step3.filing-ready.v1"),
                artifactId = event.artifactId,
                envelope = event.envelope,
                recordType = event.recordType,
                rulePackVersion = pack.version,
                filingReadyFields = filingReadyFields,
                traceMetadata = traceBase + traceAifmd,
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
            "MIFID_MISSING_LEI_ROLES" -> "Populate buyer, seller, and decision maker LEIs"
            "MIFID_UNKNOWN_EXECUTION_ACTOR" -> "Set execution_actor_type to HUMAN or ALGORITHM"
            "MIFID_VENUE_OTC_WAIVER_INCONSISTENT" -> "Provide venue_code and valid OTC/waiver indicators"
            "MIFID_INVALID_SHORT_SELLING" -> "Set short_selling_indicator to Y or N"
            "MIFID_INVALID_COMMODITY_DERIVATIVE" -> "Set commodity_derivative_indicator to Y or N"
            "MIFID_INVALID_PRICE_NOTATION" -> "Provide price_notation and ISO-3 price_currency"
            "EMIR_INVALID_CLEARED_STATUS" -> "Set cleared_status to Y/N and include clearing_member_lei when Y"
            "EMIR_MISSING_COLLATERAL_PORTFOLIO" -> "Provide collateral_portfolio_code"
            "EMIR_INVALID_MARGIN_FIELDS" -> "Provide non-negative numeric initial/variation margin fields"
            "EMIR_INVALID_LIFECYCLE_SEQUENCE" -> "Provide lifecycle_event_type and positive lifecycle_sequence"
            "EMIR_INVALID_VALUATION_UPDATE" -> "Provide valuation_amount and valuation_timestamp"
            "EMIR_MISSING_RECONCILIATION_REFERENCE" -> "Provide reconciliation_reference for strict matching"
            "AIFMD_MISSING_COMMITMENT_INPUTS" -> "Provide aifmd_commitment_exposure and positive aifmd_nav"
            "AIFMD_MISSING_GROSS_INPUTS" -> "Provide aifmd_gross_exposure and positive aifmd_nav"
            "AIFMD_LOF_INPUTS_INCOMPLETE" -> "Provide commitment exposure and NAV for LOF cap check"
            "AIFMD_FUND_STRUCTURE_UNKNOWN" -> "Set aifmd_fund_structure to OPEN_ENDED or CLOSED_ENDED"
            "AIFMD_LOF_CAP_BREACH" -> "Reduce leverage or confirm fund structure against LOF limits"
            "AIFMD_INVALID_DELEGATION_INPUTS" -> "Provide consistent aifmd_delegated_nav and aifmd_total_nav"
            "AIFMD_INVALID_FTE_INPUTS" -> "Provide non-negative FTE counts with positive sum"
            "AIFMD_LMT_INDICATOR_MISSING" -> "Set aifmd_lmt_used to Y or N"
            "AIFMD_LMT_LIMIT_INVALID" -> "When LMT is used, provide positive aifmd_lmt_limit_amount"
            "AIFMD_LOAN_CONCENTRATION_INPUTS" -> "Provide loan exposure totals for concentration check"
            "AIFMD_LOAN_CONCENTRATION_BREACH" -> "Reduce largest borrower exposure below 20% of total"
            "AIFMD_RISK_RETENTION_INPUTS" -> "Provide retained and securitized amounts"
            "AIFMD_RISK_RETENTION_BREACH" -> "Increase retained amount to at least 5% of securitized total"
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
