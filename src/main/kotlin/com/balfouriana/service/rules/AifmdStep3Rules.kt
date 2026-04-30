package com.balfouriana.service.rules

import com.balfouriana.domain.CalculationMethodRef
import com.balfouriana.domain.CanonicalRecordValidatedEvent
import com.balfouriana.domain.RuleEvaluationResult
import com.balfouriana.domain.RuleLayer
import com.balfouriana.domain.RuleOutcome
import com.balfouriana.domain.RuleSeverity
import java.math.BigDecimal
import java.math.RoundingMode

private val SCALE = RoundingMode.HALF_UP

private fun ratioPercent(numerator: BigDecimal, denominator: BigDecimal): String {
    if (denominator <= BigDecimal.ZERO) return "0"
    return numerator.divide(denominator, 8, SCALE).multiply(BigDecimal("100")).stripTrailingZeros().toPlainString()
}

class AifmdCommitmentLeverageInputsRule : Step3Rule {
    override fun evaluate(event: CanonicalRecordValidatedEvent): RuleEvaluationResult {
        val exposure = event.validatedFields["aifmd_commitment_exposure"]?.toBigDecimalOrNull()
        val nav = event.validatedFields["aifmd_nav"]?.toBigDecimalOrNull()
        val ok = exposure != null && nav != null && nav > BigDecimal.ZERO
        return (
            if (ok) {
                RuleEvaluationResult(
                    ruleId = "aifmd.commitment_leverage.inputs",
                    layer = RuleLayer.CALCULATION,
                    outcome = RuleOutcome.PASS,
                    reasonCode = "AIFMD_OK_COMMITMENT_INPUTS",
                    message = "commitment exposure and NAV are present",
                    severity = RuleSeverity.WARNING
                )
            } else {
                RuleEvaluationResult(
                    ruleId = "aifmd.commitment_leverage.inputs",
                    layer = RuleLayer.CALCULATION,
                    outcome = RuleOutcome.FAIL,
                    reasonCode = "AIFMD_MISSING_COMMITMENT_INPUTS",
                    message = "aifmd_commitment_exposure and positive aifmd_nav are required",
                    severity = RuleSeverity.ERROR
                )
            }
            ).withSourceFromCatalog()
    }
}

class AifmdGrossLeverageInputsRule : Step3Rule {
    override fun evaluate(event: CanonicalRecordValidatedEvent): RuleEvaluationResult {
        val gross = event.validatedFields["aifmd_gross_exposure"]?.toBigDecimalOrNull()
        val nav = event.validatedFields["aifmd_nav"]?.toBigDecimalOrNull()
        val ok = gross != null && nav != null && nav > BigDecimal.ZERO
        return (
            if (ok) {
                RuleEvaluationResult(
                    ruleId = "aifmd.gross_leverage.inputs",
                    layer = RuleLayer.CALCULATION,
                    outcome = RuleOutcome.PASS,
                    reasonCode = "AIFMD_OK_GROSS_INPUTS",
                    message = "gross exposure and NAV are present",
                    severity = RuleSeverity.WARNING
                )
            } else {
                RuleEvaluationResult(
                    ruleId = "aifmd.gross_leverage.inputs",
                    layer = RuleLayer.CALCULATION,
                    outcome = RuleOutcome.FAIL,
                    reasonCode = "AIFMD_MISSING_GROSS_INPUTS",
                    message = "aifmd_gross_exposure and positive aifmd_nav are required",
                    severity = RuleSeverity.ERROR
                )
            }
            ).withSourceFromCatalog()
    }
}

class AifmdLofLeverageCapRule : Step3Rule {
    override fun evaluate(event: CanonicalRecordValidatedEvent): RuleEvaluationResult {
        val structure = event.validatedFields["aifmd_fund_structure"].orEmpty().uppercase()
        val exposure = event.validatedFields["aifmd_commitment_exposure"]?.toBigDecimalOrNull()
        val nav = event.validatedFields["aifmd_nav"]?.toBigDecimalOrNull()
        if (exposure == null || nav == null || nav <= BigDecimal.ZERO) {
            return RuleEvaluationResult(
                ruleId = "aifmd.lof_leverage_cap",
                layer = RuleLayer.CALCULATION,
                outcome = RuleOutcome.FAIL,
                reasonCode = "AIFMD_LOF_INPUTS_INCOMPLETE",
                message = "LOF cap check requires commitment exposure and positive NAV",
                severity = RuleSeverity.ERROR
            ).withSourceFromCatalog()
        }
        val ratio = exposure.divide(nav, 8, SCALE)
        val cap = when (structure) {
            "OPEN_ENDED" -> BigDecimal("1.75")
            "CLOSED_ENDED" -> BigDecimal("3.00")
            else -> return RuleEvaluationResult(
                ruleId = "aifmd.lof_leverage_cap",
                layer = RuleLayer.CALCULATION,
                outcome = RuleOutcome.NEEDS_REVIEW,
                reasonCode = "AIFMD_FUND_STRUCTURE_UNKNOWN",
                message = "aifmd_fund_structure must be OPEN_ENDED or CLOSED_ENDED for LOF cap",
                severity = RuleSeverity.WARNING
            ).withSourceFromCatalog()
        }
        return if (ratio <= cap) {
            RuleEvaluationResult(
                ruleId = "aifmd.lof_leverage_cap",
                layer = RuleLayer.CALCULATION,
                outcome = RuleOutcome.PASS,
                reasonCode = "AIFMD_OK_LOF_CAP",
                message = "commitment leverage within LOF cap for fund structure",
                severity = RuleSeverity.WARNING
            ).withSourceFromCatalog()
        } else {
            RuleEvaluationResult(
                ruleId = "aifmd.lof_leverage_cap",
                layer = RuleLayer.CALCULATION,
                outcome = RuleOutcome.FAIL,
                reasonCode = "AIFMD_LOF_CAP_BREACH",
                message = "commitment leverage exceeds LOF cap for fund structure",
                severity = RuleSeverity.ERROR
            ).withSourceFromCatalog()
        }
    }
}

class AifmdDelegationInputsRule : Step3Rule {
    override fun evaluate(event: CanonicalRecordValidatedEvent): RuleEvaluationResult {
        val delegated = event.validatedFields["aifmd_delegated_nav"]?.toBigDecimalOrNull()
        val total = event.validatedFields["aifmd_total_nav"]?.toBigDecimalOrNull()
        val ok = delegated != null && total != null && total > BigDecimal.ZERO && delegated >= BigDecimal.ZERO && delegated <= total
        return (
            if (ok) {
                RuleEvaluationResult(
                    ruleId = "aifmd.delegation_pct.inputs",
                    layer = RuleLayer.CLASSIFICATION,
                    outcome = RuleOutcome.PASS,
                    reasonCode = "AIFMD_OK_DELEGATION_INPUTS",
                    message = "delegation NAV inputs are consistent",
                    severity = RuleSeverity.WARNING
                )
            } else {
                RuleEvaluationResult(
                    ruleId = "aifmd.delegation_pct.inputs",
                    layer = RuleLayer.CLASSIFICATION,
                    outcome = RuleOutcome.FAIL,
                    reasonCode = "AIFMD_INVALID_DELEGATION_INPUTS",
                    message = "aifmd_delegated_nav and positive aifmd_total_nav are required with delegated <= total",
                    severity = RuleSeverity.ERROR
                )
            }
            ).withSourceFromCatalog()
    }
}

class AifmdFteSplitInputsRule : Step3Rule {
    override fun evaluate(event: CanonicalRecordValidatedEvent): RuleEvaluationResult {
        val internal = event.validatedFields["aifmd_internal_fte"]?.toBigDecimalOrNull()
        val delegated = event.validatedFields["aifmd_delegated_fte"]?.toBigDecimalOrNull()
        val ok = internal != null && delegated != null && internal >= BigDecimal.ZERO && delegated >= BigDecimal.ZERO && internal + delegated > BigDecimal.ZERO
        return (
            if (ok) {
                RuleEvaluationResult(
                    ruleId = "aifmd.fte_split.inputs",
                    layer = RuleLayer.CLASSIFICATION,
                    outcome = RuleOutcome.PASS,
                    reasonCode = "AIFMD_OK_FTE_INPUTS",
                    message = "FTE inputs are present and positive in sum",
                    severity = RuleSeverity.WARNING
                )
            } else {
                RuleEvaluationResult(
                    ruleId = "aifmd.fte_split.inputs",
                    layer = RuleLayer.CLASSIFICATION,
                    outcome = RuleOutcome.FAIL,
                    reasonCode = "AIFMD_INVALID_FTE_INPUTS",
                    message = "non-negative aifmd_internal_fte and aifmd_delegated_fte with positive sum are required",
                    severity = RuleSeverity.ERROR
                )
            }
            ).withSourceFromCatalog()
    }
}

class AifmdLmtUsageRule : Step3Rule {
    override fun evaluate(event: CanonicalRecordValidatedEvent): RuleEvaluationResult {
        val used = event.validatedFields["aifmd_lmt_used"].orEmpty().uppercase()
        val limit = event.validatedFields["aifmd_lmt_limit_amount"]?.toBigDecimalOrNull()
        return when {
            used.isBlank() -> RuleEvaluationResult(
                ruleId = "aifmd.lmt_usage.consistent",
                layer = RuleLayer.CLASSIFICATION,
                outcome = RuleOutcome.NEEDS_REVIEW,
                reasonCode = "AIFMD_LMT_INDICATOR_MISSING",
                message = "aifmd_lmt_used should be Y or N",
                severity = RuleSeverity.WARNING
            ).withSourceFromCatalog()
            used == "N" -> RuleEvaluationResult(
                ruleId = "aifmd.lmt_usage.consistent",
                layer = RuleLayer.CLASSIFICATION,
                outcome = RuleOutcome.PASS,
                reasonCode = "AIFMD_OK_LMT_NOT_USED",
                message = "LMT not used",
                severity = RuleSeverity.WARNING
            ).withSourceFromCatalog()
            used == "Y" && limit != null && limit > BigDecimal.ZERO -> RuleEvaluationResult(
                ruleId = "aifmd.lmt_usage.consistent",
                layer = RuleLayer.CLASSIFICATION,
                outcome = RuleOutcome.PASS,
                reasonCode = "AIFMD_OK_LMT_USED",
                message = "LMT limit amount present",
                severity = RuleSeverity.WARNING
            ).withSourceFromCatalog()
            else -> RuleEvaluationResult(
                ruleId = "aifmd.lmt_usage.consistent",
                layer = RuleLayer.CLASSIFICATION,
                outcome = RuleOutcome.FAIL,
                reasonCode = "AIFMD_LMT_LIMIT_INVALID",
                message = "when aifmd_lmt_used is Y, positive aifmd_lmt_limit_amount is required",
                severity = RuleSeverity.ERROR
            ).withSourceFromCatalog()
        }
    }
}

class AifmdLoanConcentrationRule : Step3Rule {
    override fun evaluate(event: CanonicalRecordValidatedEvent): RuleEvaluationResult {
        val largest = event.validatedFields["aifmd_largest_borrower_exposure"]?.toBigDecimalOrNull()
        val total = event.validatedFields["aifmd_total_loan_exposure"]?.toBigDecimalOrNull()
        if (largest == null || total == null || total <= BigDecimal.ZERO) {
            return RuleEvaluationResult(
                ruleId = "aifmd.loan_concentration.limit",
                layer = RuleLayer.CALCULATION,
                outcome = RuleOutcome.FAIL,
                reasonCode = "AIFMD_LOAN_CONCENTRATION_INPUTS",
                message = "positive aifmd_total_loan_exposure and aifmd_largest_borrower_exposure are required",
                severity = RuleSeverity.ERROR
            ).withSourceFromCatalog()
        }
        val ratio = largest.divide(total, 8, SCALE)
        return if (ratio <= BigDecimal("0.20")) {
            RuleEvaluationResult(
                ruleId = "aifmd.loan_concentration.limit",
                layer = RuleLayer.CALCULATION,
                outcome = RuleOutcome.PASS,
                reasonCode = "AIFMD_OK_LOAN_CONCENTRATION",
                message = "largest borrower within 20% concentration limit",
                severity = RuleSeverity.WARNING
            ).withSourceFromCatalog()
        } else {
            RuleEvaluationResult(
                ruleId = "aifmd.loan_concentration.limit",
                layer = RuleLayer.CALCULATION,
                outcome = RuleOutcome.FAIL,
                reasonCode = "AIFMD_LOAN_CONCENTRATION_BREACH",
                message = "largest borrower exceeds 20% of total loan exposure",
                severity = RuleSeverity.ERROR
            ).withSourceFromCatalog()
        }
    }
}

class AifmdRiskRetentionRule : Step3Rule {
    override fun evaluate(event: CanonicalRecordValidatedEvent): RuleEvaluationResult {
        val retained = event.validatedFields["aifmd_retained_amount"]?.toBigDecimalOrNull()
        val securitized = event.validatedFields["aifmd_securitized_total"]?.toBigDecimalOrNull()
        if (retained == null || securitized == null || securitized <= BigDecimal.ZERO) {
            return RuleEvaluationResult(
                ruleId = "aifmd.risk_retention.minimum",
                layer = RuleLayer.CALCULATION,
                outcome = RuleOutcome.FAIL,
                reasonCode = "AIFMD_RISK_RETENTION_INPUTS",
                message = "positive aifmd_securitized_total and aifmd_retained_amount are required",
                severity = RuleSeverity.ERROR
            ).withSourceFromCatalog()
        }
        val ratio = retained.divide(securitized, 8, SCALE)
        return if (ratio >= BigDecimal("0.05")) {
            RuleEvaluationResult(
                ruleId = "aifmd.risk_retention.minimum",
                layer = RuleLayer.CALCULATION,
                outcome = RuleOutcome.PASS,
                reasonCode = "AIFMD_OK_RISK_RETENTION",
                message = "retained amount meets 5% minimum of securitized total",
                severity = RuleSeverity.WARNING
            ).withSourceFromCatalog()
        } else {
            RuleEvaluationResult(
                ruleId = "aifmd.risk_retention.minimum",
                layer = RuleLayer.CALCULATION,
                outcome = RuleOutcome.FAIL,
                reasonCode = "AIFMD_RISK_RETENTION_BREACH",
                message = "retained amount below 5% of securitized total",
                severity = RuleSeverity.ERROR
            ).withSourceFromCatalog()
        }
    }
}

class AifmdCommitmentLeverageCalculation : Step3Calculation {
    override fun apply(event: CanonicalRecordValidatedEvent): CalculationOutput {
        val exposure = event.validatedFields["aifmd_commitment_exposure"]?.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val nav = event.validatedFields["aifmd_nav"]?.toBigDecimalOrNull() ?: BigDecimal.ONE
        val ratio = ratioPercent(exposure, nav)
        return CalculationOutput(
            methodRef = CalculationMethodRef("aifmd.commitment_leverage.calc", "2026.04.30"),
            calculatedFields = mapOf("aifmd_commitment_leverage_ratio" to ratio),
            metadata = TransactionRuleCatalog.regulatoryMetadata(TransactionRuleCatalog.aifmdAnnexSource)
        )
    }
}

class AifmdGrossLeverageCalculation : Step3Calculation {
    override fun apply(event: CanonicalRecordValidatedEvent): CalculationOutput {
        val gross = event.validatedFields["aifmd_gross_exposure"]?.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val nav = event.validatedFields["aifmd_nav"]?.toBigDecimalOrNull() ?: BigDecimal.ONE
        val ratio = ratioPercent(gross, nav)
        return CalculationOutput(
            methodRef = CalculationMethodRef("aifmd.gross_leverage.calc", "2026.04.30"),
            calculatedFields = mapOf("aifmd_gross_leverage_ratio" to ratio),
            metadata = TransactionRuleCatalog.regulatoryMetadata(TransactionRuleCatalog.aifmdAnnexSource)
        )
    }
}

class AifmdDelegationPercentageCalculation : Step3Calculation {
    override fun apply(event: CanonicalRecordValidatedEvent): CalculationOutput {
        val delegated = event.validatedFields["aifmd_delegated_nav"]?.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val total = event.validatedFields["aifmd_total_nav"]?.toBigDecimalOrNull() ?: BigDecimal.ONE
        val pct = ratioPercent(delegated, total)
        return CalculationOutput(
            methodRef = CalculationMethodRef("aifmd.delegation_pct.calc", "2026.04.30"),
            calculatedFields = mapOf("aifmd_delegation_pct" to pct),
            metadata = TransactionRuleCatalog.regulatoryMetadata(TransactionRuleCatalog.aifmdAnnexSource)
        )
    }
}

class AifmdFteSplitCalculation : Step3Calculation {
    override fun apply(event: CanonicalRecordValidatedEvent): CalculationOutput {
        val internal = event.validatedFields["aifmd_internal_fte"]?.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val delegated = event.validatedFields["aifmd_delegated_fte"]?.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val sum = internal.add(delegated).max(BigDecimal.ONE)
        val internalPct = ratioPercent(internal, sum)
        val delegatedPct = ratioPercent(delegated, sum)
        return CalculationOutput(
            methodRef = CalculationMethodRef("aifmd.fte_split.calc", "2026.04.30"),
            calculatedFields = mapOf(
                "aifmd_internal_fte_pct" to internalPct,
                "aifmd_delegated_fte_pct" to delegatedPct
            ),
            metadata = TransactionRuleCatalog.regulatoryMetadata(TransactionRuleCatalog.aifmdAnnexSource)
        )
    }
}
