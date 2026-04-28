package com.balfouriana.service.rules

import com.balfouriana.domain.CalculationMethodRef
import com.balfouriana.domain.CanonicalRecordValidatedEvent
import com.balfouriana.domain.RuleEvaluationResult
import com.balfouriana.domain.RuleLayer
import com.balfouriana.domain.RuleOutcome
import com.balfouriana.domain.RuleSeverity
import java.math.BigDecimal
import java.math.RoundingMode

class RecordTypeMustMatchFieldRule : Step3Rule {
    override fun evaluate(event: CanonicalRecordValidatedEvent): RuleEvaluationResult {
        val field = event.validatedFields["record_type"].orEmpty().trim()
        return if (field == event.recordType.name) {
            RuleEvaluationResult(
                ruleId = "step3.record_type.matches",
                layer = RuleLayer.CLASSIFICATION,
                outcome = RuleOutcome.PASS,
                reasonCode = "OK",
                message = "record_type matches mapped record type",
                severity = RuleSeverity.WARNING
            )
        } else {
            RuleEvaluationResult(
                ruleId = "step3.record_type.matches",
                layer = RuleLayer.CLASSIFICATION,
                outcome = RuleOutcome.NEEDS_REVIEW,
                reasonCode = "RECORD_TYPE_MISMATCH",
                message = "record_type field differs from canonical record type",
                severity = RuleSeverity.WARNING
            )
        }
    }
}

class PriceMustBePositiveForReadinessRule : Step3Rule {
    override fun evaluate(event: CanonicalRecordValidatedEvent): RuleEvaluationResult {
        val price = event.validatedFields["price"]?.toBigDecimalOrNull()
        return if (price != null && price > BigDecimal.ZERO) {
            RuleEvaluationResult(
                ruleId = "step3.price.positive_for_readiness",
                layer = RuleLayer.READINESS,
                outcome = RuleOutcome.PASS,
                reasonCode = "OK",
                message = "price is positive and eligible for filing-ready output",
                severity = RuleSeverity.WARNING
            )
        } else {
            RuleEvaluationResult(
                ruleId = "step3.price.positive_for_readiness",
                layer = RuleLayer.READINESS,
                outcome = RuleOutcome.FAIL,
                reasonCode = "PRICE_MISSING_OR_NON_POSITIVE",
                message = "price must be greater than zero for filing-ready output",
                severity = RuleSeverity.ERROR
            )
        }
    }
}

class NotionalCalculation : Step3Calculation {
    override fun apply(event: CanonicalRecordValidatedEvent): CalculationOutput {
        val quantity = event.validatedFields["quantity"]?.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val price = event.validatedFields["price"]?.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val notional = quantity.multiply(price).setScale(8, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
        return CalculationOutput(
            methodRef = CalculationMethodRef(
                calculationId = "step3.notional",
                methodVersion = "2026.04.28"
            ),
            calculatedFields = mapOf("calculated_notional" to notional),
            metadata = mapOf("rounding" to "HALF_UP", "scale" to "8")
        )
    }
}
