package com.balfouriana.service.validation

import com.balfouriana.domain.CanonicalRecordMappedEvent
import com.balfouriana.domain.RegulatoryRegime
import com.balfouriana.domain.ValidationPackVersion
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class ValidationPackRegistry {
    private val defaultPack = ValidationPack(
        version = ValidationPackVersion(
            packId = "step2-core",
            version = "2026.04.19",
            effectiveFrom = Instant.parse("2026-04-19T00:00:00Z")
        ),
        regime = null,
        rules = listOf(
            RequiredFieldRule("required.record_type", "record_type"),
            RequiredFieldRule("required.instrument_id", "instrument_id"),
            UppercaseCurrencyRule(),
            PositiveNumberRule("positive.quantity", "quantity"),
            PositiveNumberRule("positive.price", "price"),
            TradeDateIsoRule()
        )
    )

    fun select(event: CanonicalRecordMappedEvent): ValidationPack {
        val regime = primaryRegime(event)
        if (regime == null) {
            return defaultPack
        }
        return defaultPack.copy(regime = regime)
    }

    private fun primaryRegime(event: CanonicalRecordMappedEvent): RegulatoryRegime? {
        return event.metadata.regimes.sortedBy { it.name }.firstOrNull()
    }
}
