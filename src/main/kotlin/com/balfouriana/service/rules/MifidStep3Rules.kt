package com.balfouriana.service.rules

import com.balfouriana.domain.CalculationMethodRef
import com.balfouriana.domain.CanonicalRecordValidatedEvent
import com.balfouriana.domain.RuleEvaluationResult
import com.balfouriana.domain.RuleLayer
import com.balfouriana.domain.RuleOutcome
import com.balfouriana.domain.RuleSeverity

class MifidLeiRolesRule : Step3Rule {
    override fun evaluate(event: CanonicalRecordValidatedEvent): RuleEvaluationResult {
        val buyerLei = event.validatedFields["buyer_lei"].orEmpty()
        val sellerLei = event.validatedFields["seller_lei"].orEmpty()
        val decisionMakerLei = event.validatedFields["decision_maker_lei"].orEmpty()
        val ok = buyerLei.isNotBlank() && sellerLei.isNotBlank() && decisionMakerLei.isNotBlank()
        return if (ok) {
            RuleEvaluationResult(
                ruleId = "mifid.buyer_seller_decision_maker_lei.required",
                layer = RuleLayer.CLASSIFICATION,
                outcome = RuleOutcome.PASS,
                reasonCode = "MIFID_OK_LEI_ROLES",
                message = "buyer, seller, and decision maker LEIs are present",
                severity = RuleSeverity.WARNING
            ).withSourceFromCatalog()
        } else {
            RuleEvaluationResult(
                ruleId = "mifid.buyer_seller_decision_maker_lei.required",
                layer = RuleLayer.CLASSIFICATION,
                outcome = RuleOutcome.FAIL,
                reasonCode = "MIFID_MISSING_LEI_ROLES",
                message = "MiFID requires buyer, seller, and decision maker LEIs",
                severity = RuleSeverity.ERROR
            )
        }
    }
}

class MifidExecutionActorRule : Step3Rule {
    override fun evaluate(event: CanonicalRecordValidatedEvent): RuleEvaluationResult {
        val actor = event.validatedFields["execution_actor_type"].orEmpty().uppercase()
        val ok = actor == "HUMAN" || actor == "ALGORITHM"
        return if (ok) {
            RuleEvaluationResult(
                ruleId = "mifid.execution_actor.classified",
                layer = RuleLayer.CLASSIFICATION,
                outcome = RuleOutcome.PASS,
                reasonCode = "MIFID_OK_EXECUTION_ACTOR",
                message = "execution actor classified as HUMAN or ALGORITHM",
                severity = RuleSeverity.WARNING
            ).withSourceFromCatalog()
        } else {
            RuleEvaluationResult(
                ruleId = "mifid.execution_actor.classified",
                layer = RuleLayer.CLASSIFICATION,
                outcome = RuleOutcome.NEEDS_REVIEW,
                reasonCode = "MIFID_UNKNOWN_EXECUTION_ACTOR",
                message = "execution actor is missing or outside supported values",
                severity = RuleSeverity.WARNING
            )
        }
    }
}

class MifidVenueOtcWaiverRule : Step3Rule {
    override fun evaluate(event: CanonicalRecordValidatedEvent): RuleEvaluationResult {
        val venue = event.validatedFields["venue_code"].orEmpty()
        val otc = event.validatedFields["otc_indicator"].orEmpty().uppercase()
        val waiver = event.validatedFields["waiver_indicator"].orEmpty().uppercase()
        val otcFlagValid = otc == "Y" || otc == "N"
        val waiverValid = waiver == "Y" || waiver == "N"
        val consistency = venue.isNotBlank() && otcFlagValid && waiverValid
        return if (consistency) {
            RuleEvaluationResult(
                ruleId = "mifid.venue_otc_waiver_consistent",
                layer = RuleLayer.CLASSIFICATION,
                outcome = RuleOutcome.PASS,
                reasonCode = "MIFID_OK_VENUE_OTC_WAIVER",
                message = "venue, OTC and waiver indicators are consistent",
                severity = RuleSeverity.WARNING
            ).withSourceFromCatalog()
        } else {
            RuleEvaluationResult(
                ruleId = "mifid.venue_otc_waiver_consistent",
                layer = RuleLayer.CLASSIFICATION,
                outcome = RuleOutcome.FAIL,
                reasonCode = "MIFID_VENUE_OTC_WAIVER_INCONSISTENT",
                message = "venue, OTC indicator, and waiver indicator must be present and valid",
                severity = RuleSeverity.ERROR
            )
        }
    }
}

class MifidShortSellingRule : Step3Rule {
    override fun evaluate(event: CanonicalRecordValidatedEvent): RuleEvaluationResult {
        val indicator = event.validatedFields["short_selling_indicator"].orEmpty().uppercase()
        val ok = indicator == "Y" || indicator == "N"
        return if (ok) {
            RuleEvaluationResult(
                ruleId = "mifid.short_sell_indicator.valid",
                layer = RuleLayer.CLASSIFICATION,
                outcome = RuleOutcome.PASS,
                reasonCode = "MIFID_OK_SHORT_SELLING",
                message = "short selling indicator is valid",
                severity = RuleSeverity.WARNING
            ).withSourceFromCatalog()
        } else {
            RuleEvaluationResult(
                ruleId = "mifid.short_sell_indicator.valid",
                layer = RuleLayer.CLASSIFICATION,
                outcome = RuleOutcome.FAIL,
                reasonCode = "MIFID_INVALID_SHORT_SELLING",
                message = "short selling indicator must be Y or N",
                severity = RuleSeverity.ERROR
            )
        }
    }
}

class MifidCommodityDerivativeRule : Step3Rule {
    override fun evaluate(event: CanonicalRecordValidatedEvent): RuleEvaluationResult {
        val indicator = event.validatedFields["commodity_derivative_indicator"].orEmpty().uppercase()
        val ok = indicator == "Y" || indicator == "N"
        return if (ok) {
            RuleEvaluationResult(
                ruleId = "mifid.commodity_derivative_indicator.valid",
                layer = RuleLayer.CLASSIFICATION,
                outcome = RuleOutcome.PASS,
                reasonCode = "MIFID_OK_COMMODITY_DERIVATIVE",
                message = "commodity derivative indicator is valid",
                severity = RuleSeverity.WARNING
            ).withSourceFromCatalog()
        } else {
            RuleEvaluationResult(
                ruleId = "mifid.commodity_derivative_indicator.valid",
                layer = RuleLayer.CLASSIFICATION,
                outcome = RuleOutcome.FAIL,
                reasonCode = "MIFID_INVALID_COMMODITY_DERIVATIVE",
                message = "commodity derivative indicator must be Y or N",
                severity = RuleSeverity.ERROR
            )
        }
    }
}

class MifidPriceNotationRule : Step3Rule {
    override fun evaluate(event: CanonicalRecordValidatedEvent): RuleEvaluationResult {
        val notation = event.validatedFields["price_notation"].orEmpty().uppercase()
        val currency = event.validatedFields["price_currency"].orEmpty()
        val ok = notation.isNotBlank() && currency.length == 3
        return if (ok) {
            RuleEvaluationResult(
                ruleId = "mifid.price_notation_conditions.consistent",
                layer = RuleLayer.CALCULATION,
                outcome = RuleOutcome.PASS,
                reasonCode = "MIFID_OK_PRICE_NOTATION",
                message = "price notation and price currency are consistent",
                severity = RuleSeverity.WARNING
            ).withSourceFromCatalog()
        } else {
            RuleEvaluationResult(
                ruleId = "mifid.price_notation_conditions.consistent",
                layer = RuleLayer.CALCULATION,
                outcome = RuleOutcome.FAIL,
                reasonCode = "MIFID_INVALID_PRICE_NOTATION",
                message = "price notation must be present and price currency must be ISO-3",
                severity = RuleSeverity.ERROR
            ).withSourceFromCatalog()
        }
    }
}

class MifidDerivedFieldsCalculation : Step3Calculation {
    override fun apply(event: CanonicalRecordValidatedEvent): CalculationOutput {
        val buyerLei = event.validatedFields["buyer_lei"].orEmpty()
        val sellerLei = event.validatedFields["seller_lei"].orEmpty()
        val executionActor = event.validatedFields["execution_actor_type"].orEmpty().uppercase()
        return CalculationOutput(
            methodRef = CalculationMethodRef(
                calculationId = "mifid.transaction.derived_fields",
                methodVersion = "2026.04.30"
            ),
            calculatedFields = mapOf(
                "mifid_transaction_side" to if (buyerLei <= sellerLei) "BUYER_SIDE" else "SELLER_SIDE",
                "mifid_execution_mode" to if (executionActor == "ALGORITHM") "ALGO" else "HUMAN"
            ),
            metadata = mapOf(
                "source_ref" to "MiFIR RTS22",
                "source_date" to "2026-04-19"
            ) + TransactionRuleCatalog.regulatoryMetadata(TransactionRuleCatalog.mifidRegulatorySource)
        )
    }
}
