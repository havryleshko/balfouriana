package com.balfouriana.service.rules

import com.balfouriana.domain.RegulatoryRegime
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TransactionRuleCatalogTest {
    @Test
    fun `contains mifid and emir rule descriptors with source metadata`() {
        val descriptors = TransactionRuleCatalog.rules.values
        assertTrue(descriptors.any { it.regime == RegulatoryRegime.MIFID_II })
        assertTrue(descriptors.any { it.regime == RegulatoryRegime.EMIR })
        assertTrue(descriptors.any { it.regime == RegulatoryRegime.AIFMD_II })
        assertTrue(descriptors.all { it.source.reference.isNotBlank() })
        assertTrue(descriptors.all { it.expectedOutputFields.isNotEmpty() })
        assertTrue(descriptors.any { it.regime == RegulatoryRegime.AIFMD_II && it.blocksFilingReady })
        assertTrue(descriptors.any { it.regime == RegulatoryRegime.AIFMD_II && !it.blocksFilingReady })
    }

    @Test
    fun `all reason-bearing descriptors are uniquely keyed`() {
        assertFalse(TransactionRuleCatalog.rules.isEmpty())
        assertTrue(TransactionRuleCatalog.rules.keys.size == TransactionRuleCatalog.rules.values.map { it.ruleId }.toSet().size)
    }
}
