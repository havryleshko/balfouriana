package com.balfouriana.service.rules

import com.balfouriana.domain.CanonicalRecordValidatedEvent
import com.balfouriana.domain.RegulatoryRegime
import com.balfouriana.domain.RegulatoryRegimeSelector
import com.balfouriana.domain.RulePackVersion
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class RulePackRegistry {
    private val defaultPack: RulePack = RulePack(
        version = RulePackVersion(
            packId = "step3-core-default",
            version = "2026.04.30",
            effectiveFrom = Instant.parse("2026-04-28T00:00:00Z")
        ),
        regime = null,
        rules = listOf(
            RecordTypeMustMatchFieldRule(),
            PriceMustBePositiveForReadinessRule()
        ),
        calculations = listOf(
            NotionalCalculation()
        )
    )

    private val mifidPack: RulePack = RulePack(
        version = RulePackVersion(
            packId = "step3-mifid-transaction-rules",
            version = "2026.04.30",
            effectiveFrom = Instant.parse("2026-04-28T00:00:00Z")
        ),
        regime = RegulatoryRegime.MIFID_II,
        rules = listOf(
            RecordTypeMustMatchFieldRule(),
            PriceMustBePositiveForReadinessRule(),
            MifidLeiRolesRule(),
            MifidExecutionActorRule(),
            MifidVenueOtcWaiverRule(),
            MifidShortSellingRule(),
            MifidCommodityDerivativeRule(),
            MifidPriceNotationRule()
        ),
        calculations = listOf(
            NotionalCalculation(),
            MifidDerivedFieldsCalculation()
        )
    )

    private val emirPack: RulePack = RulePack(
        version = RulePackVersion(
            packId = "step3-emir-transaction-rules",
            version = "2026.04.30",
            effectiveFrom = Instant.parse("2026-04-28T00:00:00Z")
        ),
        regime = RegulatoryRegime.EMIR,
        rules = listOf(
            RecordTypeMustMatchFieldRule(),
            PriceMustBePositiveForReadinessRule(),
            EmirClearedStatusRule(),
            EmirCollateralPortfolioRule(),
            EmirMarginConsistencyRule(),
            EmirLifecycleSequenceRule(),
            EmirValuationUpdateRule(),
            EmirReconciliationReferenceRule()
        ),
        calculations = listOf(
            NotionalCalculation(),
            EmirDerivedFieldsCalculation()
        )
    )

    private val aifmdPack: RulePack = RulePack(
        version = RulePackVersion(
            packId = "step3-aifmd-annex-iv-calcs",
            version = "2026.04.30",
            effectiveFrom = Instant.parse("2026-04-28T00:00:00Z")
        ),
        regime = RegulatoryRegime.AIFMD_II,
        rules = listOf(
            RecordTypeMustMatchFieldRule(),
            PriceMustBePositiveForReadinessRule(),
            AifmdCommitmentLeverageInputsRule(),
            AifmdGrossLeverageInputsRule(),
            AifmdLofLeverageCapRule(),
            AifmdDelegationInputsRule(),
            AifmdFteSplitInputsRule(),
            AifmdLmtUsageRule(),
            AifmdLoanConcentrationRule(),
            AifmdRiskRetentionRule()
        ),
        calculations = listOf(
            NotionalCalculation(),
            AifmdCommitmentLeverageCalculation(),
            AifmdGrossLeverageCalculation(),
            AifmdDelegationPercentageCalculation(),
            AifmdFteSplitCalculation()
        )
    )

    private val regimePacks: Map<RegulatoryRegime, List<RulePack>> = listOf(mifidPack, emirPack, aifmdPack)
        .groupBy { it.regime!! }
        .mapValues { (_, packs) -> packs.sortedByDescending { it.version.effectiveFrom } }

    fun select(event: CanonicalRecordValidatedEvent): RulePack {
        val regime = primaryRegime(event)
        val occurredAt = event.metadata.occurredAt
        val regimePack = regime?.let { chooseRegimePack(it, occurredAt) }
        return regimePack ?: defaultPack
    }

    private fun primaryRegime(event: CanonicalRecordValidatedEvent): RegulatoryRegime? {
        return RegulatoryRegimeSelector.primaryRegime(event.metadata.regimes)
    }

    private fun chooseRegimePack(regime: RegulatoryRegime, occurredAt: Instant): RulePack? {
        return regimePacks[regime]
            ?.firstOrNull { occurredAt >= it.version.effectiveFrom }
    }
}
