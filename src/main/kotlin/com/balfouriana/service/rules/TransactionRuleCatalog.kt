package com.balfouriana.service.rules

import com.balfouriana.domain.RegulatoryRegime
import com.balfouriana.domain.RuleEvaluationResult
import com.balfouriana.domain.RuleSeverity
import java.time.Instant

data class RegulatorySourceRef(
    val authority: String,
    val reference: String,
    val publishedAt: Instant
)

data class TransactionRuleDescriptor(
    val ruleId: String,
    val regime: RegulatoryRegime,
    val source: RegulatorySourceRef,
    val effectiveFrom: Instant,
    val defaultSeverity: RuleSeverity,
    val expectedOutputFields: Set<String>,
    val blocksFilingReady: Boolean
)

object TransactionRuleCatalog {
    val mifidRegulatorySource = RegulatorySourceRef(
        authority = "ESMA/FCA",
        reference = "MiFIR transaction reporting RTS 22 core field logic",
        publishedAt = Instant.parse("2026-04-19T00:00:00Z")
    )

    private val mifidSource get() = mifidRegulatorySource

    val emirRegulatorySource = RegulatorySourceRef(
        authority = "ESMA",
        reference = "EMIR Refit reconciliation and reporting field logic",
        publishedAt = Instant.parse("2026-04-27T00:00:00Z")
    )

    private val emirSource get() = emirRegulatorySource

    val aifmdAnnexSource = RegulatorySourceRef(
        authority = "ESMA",
        reference = "AIFMD II Annex IV leverage and reporting",
        publishedAt = Instant.parse("2026-04-16T00:00:00Z")
    )

    val step3FoundationSource = RegulatorySourceRef(
        authority = "internal",
        reference = "balfouriana step3 foundation",
        publishedAt = Instant.parse("2026-04-28T00:00:00Z")
    )

    private val internalStep3Source get() = step3FoundationSource

    val rules: Map<String, TransactionRuleDescriptor> = listOf(
        TransactionRuleDescriptor(
            ruleId = "step3.record_type.matches",
            regime = RegulatoryRegime.MIFID_II,
            source = internalStep3Source,
            effectiveFrom = Instant.parse("2026-04-28T00:00:00Z"),
            defaultSeverity = RuleSeverity.WARNING,
            expectedOutputFields = setOf("record_type"),
            blocksFilingReady = false
        ),
        TransactionRuleDescriptor(
            ruleId = "step3.price.positive_for_readiness",
            regime = RegulatoryRegime.MIFID_II,
            source = internalStep3Source,
            effectiveFrom = Instant.parse("2026-04-28T00:00:00Z"),
            defaultSeverity = RuleSeverity.ERROR,
            expectedOutputFields = setOf("price"),
            blocksFilingReady = true
        ),
        TransactionRuleDescriptor(
            ruleId = "mifid.buyer_seller_decision_maker_lei.required",
            regime = RegulatoryRegime.MIFID_II,
            source = mifidSource,
            effectiveFrom = Instant.parse("2026-04-28T00:00:00Z"),
            defaultSeverity = RuleSeverity.ERROR,
            expectedOutputFields = setOf("buyer_lei", "seller_lei", "decision_maker_lei"),
            blocksFilingReady = true
        ),
        TransactionRuleDescriptor(
            ruleId = "mifid.execution_actor.classified",
            regime = RegulatoryRegime.MIFID_II,
            source = mifidSource,
            effectiveFrom = Instant.parse("2026-04-28T00:00:00Z"),
            defaultSeverity = RuleSeverity.WARNING,
            expectedOutputFields = setOf("execution_actor_type"),
            blocksFilingReady = false
        ),
        TransactionRuleDescriptor(
            ruleId = "mifid.venue_otc_waiver_consistent",
            regime = RegulatoryRegime.MIFID_II,
            source = mifidSource,
            effectiveFrom = Instant.parse("2026-04-28T00:00:00Z"),
            defaultSeverity = RuleSeverity.ERROR,
            expectedOutputFields = setOf("venue_code", "otc_indicator", "waiver_indicator"),
            blocksFilingReady = true
        ),
        TransactionRuleDescriptor(
            ruleId = "mifid.short_sell_indicator.valid",
            regime = RegulatoryRegime.MIFID_II,
            source = mifidSource,
            effectiveFrom = Instant.parse("2026-04-28T00:00:00Z"),
            defaultSeverity = RuleSeverity.ERROR,
            expectedOutputFields = setOf("short_selling_indicator"),
            blocksFilingReady = true
        ),
        TransactionRuleDescriptor(
            ruleId = "mifid.commodity_derivative_indicator.valid",
            regime = RegulatoryRegime.MIFID_II,
            source = mifidSource,
            effectiveFrom = Instant.parse("2026-04-28T00:00:00Z"),
            defaultSeverity = RuleSeverity.ERROR,
            expectedOutputFields = setOf("commodity_derivative_indicator"),
            blocksFilingReady = true
        ),
        TransactionRuleDescriptor(
            ruleId = "mifid.price_notation_conditions.consistent",
            regime = RegulatoryRegime.MIFID_II,
            source = mifidSource,
            effectiveFrom = Instant.parse("2026-04-28T00:00:00Z"),
            defaultSeverity = RuleSeverity.ERROR,
            expectedOutputFields = setOf("price_notation", "price_currency"),
            blocksFilingReady = true
        ),
        TransactionRuleDescriptor(
            ruleId = "emir.cleared_status.clearing_member_dependency",
            regime = RegulatoryRegime.EMIR,
            source = emirSource,
            effectiveFrom = Instant.parse("2026-04-28T00:00:00Z"),
            defaultSeverity = RuleSeverity.ERROR,
            expectedOutputFields = setOf("cleared_status", "clearing_member_lei"),
            blocksFilingReady = true
        ),
        TransactionRuleDescriptor(
            ruleId = "emir.collateral_portfolio_code.present",
            regime = RegulatoryRegime.EMIR,
            source = emirSource,
            effectiveFrom = Instant.parse("2026-04-28T00:00:00Z"),
            defaultSeverity = RuleSeverity.ERROR,
            expectedOutputFields = setOf("collateral_portfolio_code"),
            blocksFilingReady = true
        ),
        TransactionRuleDescriptor(
            ruleId = "emir.margin_fields.consistent",
            regime = RegulatoryRegime.EMIR,
            source = emirSource,
            effectiveFrom = Instant.parse("2026-04-28T00:00:00Z"),
            defaultSeverity = RuleSeverity.ERROR,
            expectedOutputFields = setOf("initial_margin_posted", "variation_margin_posted"),
            blocksFilingReady = true
        ),
        TransactionRuleDescriptor(
            ruleId = "emir.lifecycle_sequence.valid",
            regime = RegulatoryRegime.EMIR,
            source = emirSource,
            effectiveFrom = Instant.parse("2026-04-28T00:00:00Z"),
            defaultSeverity = RuleSeverity.ERROR,
            expectedOutputFields = setOf("lifecycle_event_type", "lifecycle_sequence"),
            blocksFilingReady = true
        ),
        TransactionRuleDescriptor(
            ruleId = "emir.valuation_update.valid",
            regime = RegulatoryRegime.EMIR,
            source = emirSource,
            effectiveFrom = Instant.parse("2026-04-28T00:00:00Z"),
            defaultSeverity = RuleSeverity.ERROR,
            expectedOutputFields = setOf("valuation_amount", "valuation_timestamp"),
            blocksFilingReady = true
        ),
        TransactionRuleDescriptor(
            ruleId = "emir.reconciliation_reference.present",
            regime = RegulatoryRegime.EMIR,
            source = emirSource,
            effectiveFrom = Instant.parse("2026-04-28T00:00:00Z"),
            defaultSeverity = RuleSeverity.ERROR,
            expectedOutputFields = setOf("reconciliation_reference"),
            blocksFilingReady = true
        ),
        TransactionRuleDescriptor(
            ruleId = "aifmd.commitment_leverage.inputs",
            regime = RegulatoryRegime.AIFMD_II,
            source = aifmdAnnexSource,
            effectiveFrom = Instant.parse("2026-04-28T00:00:00Z"),
            defaultSeverity = RuleSeverity.ERROR,
            expectedOutputFields = setOf("aifmd_commitment_exposure", "aifmd_nav"),
            blocksFilingReady = true
        ),
        TransactionRuleDescriptor(
            ruleId = "aifmd.gross_leverage.inputs",
            regime = RegulatoryRegime.AIFMD_II,
            source = aifmdAnnexSource,
            effectiveFrom = Instant.parse("2026-04-28T00:00:00Z"),
            defaultSeverity = RuleSeverity.ERROR,
            expectedOutputFields = setOf("aifmd_gross_exposure", "aifmd_nav"),
            blocksFilingReady = true
        ),
        TransactionRuleDescriptor(
            ruleId = "aifmd.lof_leverage_cap",
            regime = RegulatoryRegime.AIFMD_II,
            source = aifmdAnnexSource,
            effectiveFrom = Instant.parse("2026-04-28T00:00:00Z"),
            defaultSeverity = RuleSeverity.ERROR,
            expectedOutputFields = setOf(
                "aifmd_fund_structure",
                "aifmd_commitment_exposure",
                "aifmd_nav"
            ),
            blocksFilingReady = true
        ),
        TransactionRuleDescriptor(
            ruleId = "aifmd.delegation_pct.inputs",
            regime = RegulatoryRegime.AIFMD_II,
            source = aifmdAnnexSource,
            effectiveFrom = Instant.parse("2026-04-28T00:00:00Z"),
            defaultSeverity = RuleSeverity.ERROR,
            expectedOutputFields = setOf("aifmd_delegated_nav", "aifmd_total_nav"),
            blocksFilingReady = true
        ),
        TransactionRuleDescriptor(
            ruleId = "aifmd.fte_split.inputs",
            regime = RegulatoryRegime.AIFMD_II,
            source = aifmdAnnexSource,
            effectiveFrom = Instant.parse("2026-04-28T00:00:00Z"),
            defaultSeverity = RuleSeverity.ERROR,
            expectedOutputFields = setOf("aifmd_internal_fte", "aifmd_delegated_fte"),
            blocksFilingReady = true
        ),
        TransactionRuleDescriptor(
            ruleId = "aifmd.lmt_usage.consistent",
            regime = RegulatoryRegime.AIFMD_II,
            source = aifmdAnnexSource,
            effectiveFrom = Instant.parse("2026-04-28T00:00:00Z"),
            defaultSeverity = RuleSeverity.WARNING,
            expectedOutputFields = setOf("aifmd_lmt_used", "aifmd_lmt_limit_amount"),
            blocksFilingReady = false
        ),
        TransactionRuleDescriptor(
            ruleId = "aifmd.loan_concentration.limit",
            regime = RegulatoryRegime.AIFMD_II,
            source = aifmdAnnexSource,
            effectiveFrom = Instant.parse("2026-04-28T00:00:00Z"),
            defaultSeverity = RuleSeverity.ERROR,
            expectedOutputFields = setOf("aifmd_largest_borrower_exposure", "aifmd_total_loan_exposure"),
            blocksFilingReady = true
        ),
        TransactionRuleDescriptor(
            ruleId = "aifmd.risk_retention.minimum",
            regime = RegulatoryRegime.AIFMD_II,
            source = aifmdAnnexSource,
            effectiveFrom = Instant.parse("2026-04-28T00:00:00Z"),
            defaultSeverity = RuleSeverity.ERROR,
            expectedOutputFields = setOf("aifmd_retained_amount", "aifmd_securitized_total"),
            blocksFilingReady = true
        )
    ).associateBy { it.ruleId }

    fun regulatoryMetadata(source: RegulatorySourceRef): Map<String, String> {
        return mapOf(
            "regulatory_source_authority" to source.authority,
            "regulatory_source_reference" to source.reference,
            "regulatory_source_published_at" to source.publishedAt.toString()
        )
    }
}

fun RuleEvaluationResult.withSourceFromCatalog(): RuleEvaluationResult {
    val d = TransactionRuleCatalog.rules[ruleId] ?: return this
    return copy(
        sourceAuthority = d.source.authority,
        sourceReference = d.source.reference,
        sourcePublishedAt = d.source.publishedAt.toString()
    )
}
