package com.balfouriana.domain

object RegulatoryRegimeSelector {
    private val PRECEDENCE = listOf(
        RegulatoryRegime.EMIR,
        RegulatoryRegime.MIFID_II,
        RegulatoryRegime.AIFMD_II
    )

    fun primaryRegime(regimes: Set<RegulatoryRegime>): RegulatoryRegime? {
        return PRECEDENCE.firstOrNull { regimes.contains(it) }
    }

    fun inferRegimes(canonicalFields: Map<String, String>, recordType: CanonicalRecordType): Set<RegulatoryRegime> {
        val inferred = linkedSetOf<RegulatoryRegime>()
        if (canonicalFields["cleared_status"].orEmpty().isNotBlank()) {
            inferred.add(RegulatoryRegime.EMIR)
        }
        when (recordType) {
            CanonicalRecordType.TRADE -> {
                if (canonicalFields["buyer_lei"].orEmpty().isNotBlank()) {
                    inferred.add(RegulatoryRegime.MIFID_II)
                }
            }
            CanonicalRecordType.POSITION -> {
                if (canonicalFields["aifmd_nav"].orEmpty().isNotBlank()) {
                    inferred.add(RegulatoryRegime.AIFMD_II)
                }
            }
            CanonicalRecordType.CASH_MOVEMENT, CanonicalRecordType.CORPORATE_ACTION -> Unit
        }
        return inferred
    }
}
