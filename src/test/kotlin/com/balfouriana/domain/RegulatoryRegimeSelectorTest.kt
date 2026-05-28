package com.balfouriana.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RegulatoryRegimeSelectorTest {
    @Test
    fun `primary regime follows emir mifid aifmd precedence`() {
        assertEquals(
            RegulatoryRegime.EMIR,
            RegulatoryRegimeSelector.primaryRegime(
                setOf(RegulatoryRegime.AIFMD_II, RegulatoryRegime.MIFID_II, RegulatoryRegime.EMIR)
            )
        )
        assertEquals(
            RegulatoryRegime.MIFID_II,
            RegulatoryRegimeSelector.primaryRegime(setOf(RegulatoryRegime.AIFMD_II, RegulatoryRegime.MIFID_II))
        )
        assertEquals(
            RegulatoryRegime.AIFMD_II,
            RegulatoryRegimeSelector.primaryRegime(setOf(RegulatoryRegime.AIFMD_II))
        )
    }

    @Test
    fun `infers mifid from trade buyer lei`() {
        val regimes = RegulatoryRegimeSelector.inferRegimes(
            mapOf("buyer_lei" to "5493001KJTIIGC8Y1R12"),
            CanonicalRecordType.TRADE
        )
        assertEquals(setOf(RegulatoryRegime.MIFID_II), regimes)
    }

    @Test
    fun `infers emir from cleared status`() {
        val regimes = RegulatoryRegimeSelector.inferRegimes(
            mapOf("cleared_status" to "Y"),
            CanonicalRecordType.TRADE
        )
        assertEquals(setOf(RegulatoryRegime.EMIR), regimes)
    }

    @Test
    fun `infers both emir and mifid when signals present`() {
        val regimes = RegulatoryRegimeSelector.inferRegimes(
            mapOf(
                "buyer_lei" to "5493001KJTIIGC8Y1R12",
                "cleared_status" to "Y"
            ),
            CanonicalRecordType.TRADE
        )
        assertTrue(regimes.contains(RegulatoryRegime.EMIR))
        assertTrue(regimes.contains(RegulatoryRegime.MIFID_II))
    }
}
