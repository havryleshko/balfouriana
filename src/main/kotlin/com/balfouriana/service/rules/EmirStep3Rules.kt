package com.balfouriana.service.rules

import com.balfouriana.domain.CalculationMethodRef
import com.balfouriana.domain.CanonicalRecordValidatedEvent
import com.balfouriana.domain.RuleEvaluationResult
import com.balfouriana.domain.RuleLayer
import com.balfouriana.domain.RuleOutcome
import com.balfouriana.domain.RuleSeverity
import java.math.BigDecimal
import java.math.RoundingMode

class EmirClearedStatusRule : Step3Rule {
    override fun evaluate(event: CanonicalRecordValidatedEvent): RuleEvaluationResult {
        val cleared = event.validatedFields["cleared_status"].orEmpty().uppercase()
        val clearingMemberLei = event.validatedFields["clearing_member_lei"].orEmpty()
        val ok = when (cleared) {
            "Y" -> clearingMemberLei.isNotBlank()
            "N" -> true
            else -> false
        }
        return if (ok) {
            RuleEvaluationResult(
                ruleId = "emir.cleared_status.clearing_member_dependency",
                layer = RuleLayer.CLASSIFICATION,
                outcome = RuleOutcome.PASS,
                reasonCode = "EMIR_OK_CLEARED_STATUS",
                message = "cleared status and clearing member LEI are consistent",
                severity = RuleSeverity.WARNING
            ).withSourceFromCatalog()
        } else {
            RuleEvaluationResult(
                ruleId = "emir.cleared_status.clearing_member_dependency",
                layer = RuleLayer.CLASSIFICATION,
                outcome = RuleOutcome.FAIL,
                reasonCode = "EMIR_INVALID_CLEARED_STATUS",
                message = "cleared status must be Y or N and Y requires clearing member LEI",
                severity = RuleSeverity.ERROR
            )
        }
    }
}

class EmirCollateralPortfolioRule : Step3Rule {
    override fun evaluate(event: CanonicalRecordValidatedEvent): RuleEvaluationResult {
        val portfolio = event.validatedFields["collateral_portfolio_code"].orEmpty()
        return if (portfolio.isNotBlank()) {
            RuleEvaluationResult(
                ruleId = "emir.collateral_portfolio_code.present",
                layer = RuleLayer.CLASSIFICATION,
                outcome = RuleOutcome.PASS,
                reasonCode = "EMIR_OK_COLLATERAL_PORTFOLIO",
                message = "collateral portfolio code is present",
                severity = RuleSeverity.WARNING
            ).withSourceFromCatalog()
        } else {
            RuleEvaluationResult(
                ruleId = "emir.collateral_portfolio_code.present",
                layer = RuleLayer.CLASSIFICATION,
                outcome = RuleOutcome.FAIL,
                reasonCode = "EMIR_MISSING_COLLATERAL_PORTFOLIO",
                message = "collateral portfolio code is required",
                severity = RuleSeverity.ERROR
            )
        }
    }
}

class EmirMarginConsistencyRule : Step3Rule {
    override fun evaluate(event: CanonicalRecordValidatedEvent): RuleEvaluationResult {
        val initial = event.validatedFields["initial_margin_posted"]?.toBigDecimalOrNull()
        val variation = event.validatedFields["variation_margin_posted"]?.toBigDecimalOrNull()
        val ok = initial != null && variation != null && initial >= BigDecimal.ZERO && variation >= BigDecimal.ZERO
        return if (ok) {
            RuleEvaluationResult(
                ruleId = "emir.margin_fields.consistent",
                layer = RuleLayer.CALCULATION,
                outcome = RuleOutcome.PASS,
                reasonCode = "EMIR_OK_MARGIN_FIELDS",
                message = "margin fields are numeric and non-negative",
                severity = RuleSeverity.WARNING
            ).withSourceFromCatalog()
        } else {
            RuleEvaluationResult(
                ruleId = "emir.margin_fields.consistent",
                layer = RuleLayer.CALCULATION,
                outcome = RuleOutcome.FAIL,
                reasonCode = "EMIR_INVALID_MARGIN_FIELDS",
                message = "initial and variation margin must be non-negative numbers",
                severity = RuleSeverity.ERROR
            )
        }
    }
}

class EmirLifecycleSequenceRule : Step3Rule {
    override fun evaluate(event: CanonicalRecordValidatedEvent): RuleEvaluationResult {
        val eventType = event.validatedFields["lifecycle_event_type"].orEmpty()
        val sequence = event.validatedFields["lifecycle_sequence"]?.toIntOrNull()
        val ok = eventType.isNotBlank() && sequence != null && sequence > 0
        return if (ok) {
            RuleEvaluationResult(
                ruleId = "emir.lifecycle_sequence.valid",
                layer = RuleLayer.CLASSIFICATION,
                outcome = RuleOutcome.PASS,
                reasonCode = "EMIR_OK_LIFECYCLE_SEQUENCE",
                message = "lifecycle event type and sequence are valid",
                severity = RuleSeverity.WARNING
            ).withSourceFromCatalog()
        } else {
            RuleEvaluationResult(
                ruleId = "emir.lifecycle_sequence.valid",
                layer = RuleLayer.CLASSIFICATION,
                outcome = RuleOutcome.FAIL,
                reasonCode = "EMIR_INVALID_LIFECYCLE_SEQUENCE",
                message = "lifecycle event type is required and sequence must be positive",
                severity = RuleSeverity.ERROR
            )
        }
    }
}

class EmirValuationUpdateRule : Step3Rule {
    override fun evaluate(event: CanonicalRecordValidatedEvent): RuleEvaluationResult {
        val valuationAmount = event.validatedFields["valuation_amount"]?.toBigDecimalOrNull()
        val valuationTimestamp = event.validatedFields["valuation_timestamp"].orEmpty()
        val ok = valuationAmount != null && valuationTimestamp.isNotBlank()
        return if (ok) {
            RuleEvaluationResult(
                ruleId = "emir.valuation_update.valid",
                layer = RuleLayer.CALCULATION,
                outcome = RuleOutcome.PASS,
                reasonCode = "EMIR_OK_VALUATION_UPDATE",
                message = "valuation amount and valuation timestamp are present",
                severity = RuleSeverity.WARNING
            ).withSourceFromCatalog()
        } else {
            RuleEvaluationResult(
                ruleId = "emir.valuation_update.valid",
                layer = RuleLayer.CALCULATION,
                outcome = RuleOutcome.FAIL,
                reasonCode = "EMIR_INVALID_VALUATION_UPDATE",
                message = "valuation amount and valuation timestamp are required",
                severity = RuleSeverity.ERROR
            )
        }
    }
}

class EmirReconciliationReferenceRule : Step3Rule {
    override fun evaluate(event: CanonicalRecordValidatedEvent): RuleEvaluationResult {
        val reference = event.validatedFields["reconciliation_reference"].orEmpty()
        return if (reference.isNotBlank()) {
            RuleEvaluationResult(
                ruleId = "emir.reconciliation_reference.present",
                layer = RuleLayer.READINESS,
                outcome = RuleOutcome.PASS,
                reasonCode = "EMIR_OK_RECONCILIATION_REFERENCE",
                message = "reconciliation reference is present",
                severity = RuleSeverity.WARNING
            ).withSourceFromCatalog()
        } else {
            RuleEvaluationResult(
                ruleId = "emir.reconciliation_reference.present",
                layer = RuleLayer.READINESS,
                outcome = RuleOutcome.FAIL,
                reasonCode = "EMIR_MISSING_RECONCILIATION_REFERENCE",
                message = "reconciliation reference is required for strict matching",
                severity = RuleSeverity.ERROR
            ).withSourceFromCatalog()
        }
    }
}

class EmirDerivedFieldsCalculation : Step3Calculation {
    override fun apply(event: CanonicalRecordValidatedEvent): CalculationOutput {
        val initial = event.validatedFields["initial_margin_posted"]?.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val variation = event.validatedFields["variation_margin_posted"]?.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val totalMargin = initial.add(variation).setScale(8, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
        return CalculationOutput(
            methodRef = CalculationMethodRef(
                calculationId = "emir.transaction.derived_fields",
                methodVersion = "2026.04.30"
            ),
            calculatedFields = mapOf(
                "emir_total_margin" to totalMargin,
                "emir_reconciliation_scope" to "PHASE2_STRICT"
            ),
            metadata = mapOf(
                "source_ref" to "EMIR Refit Phase II",
                "source_date" to "2026-04-27"
            ) + TransactionRuleCatalog.regulatoryMetadata(TransactionRuleCatalog.emirRegulatorySource)
        )
    }
}
