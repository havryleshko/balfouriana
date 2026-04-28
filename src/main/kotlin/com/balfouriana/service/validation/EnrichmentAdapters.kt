package com.balfouriana.service.validation

import com.balfouriana.domain.CanonicalRecordMappedEvent
import com.balfouriana.domain.ValidationExceptionEnvelope
import com.balfouriana.domain.ValidationSeverity
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

data class EnrichmentResult(
    val fields: Map<String, String>,
    val metadata: Map<String, String>,
    val exception: ValidationExceptionEnvelope?
)

interface EnrichmentAdapter {
    fun enrich(event: CanonicalRecordMappedEvent): EnrichmentResult
}

@Component
class LeiEnrichmentAdapter : EnrichmentAdapter {
    override fun enrich(event: CanonicalRecordMappedEvent): EnrichmentResult {
        val lei = event.canonicalFields["counterparty_lei"].orEmpty()
        if (lei.isBlank()) {
            return EnrichmentResult(
                fields = emptyMap(),
                metadata = mapOf(
                    "lei.source" to "none",
                    "lei.checkedAt" to Instant.now().toString()
                ),
                exception = null
            )
        }
        if (lei.length == 20 && lei.all { it.isLetterOrDigit() }) {
            return EnrichmentResult(
                fields = mapOf("counterparty_lei_status" to "ACTIVE"),
                metadata = mapOf(
                    "lei.source" to "gleif-cache",
                    "lei.checkedAt" to Instant.now().toString()
                ),
                exception = null
            )
        }
        return EnrichmentResult(
            fields = emptyMap(),
            metadata = mapOf(
                "lei.source" to "gleif-cache",
                "lei.checkedAt" to Instant.now().toString()
            ),
            exception = ValidationExceptionEnvelope(
                exceptionId = UUID.randomUUID(),
                correlationId = event.metadata.correlationId,
                eventId = event.metadata.eventId,
                regime = event.metadata.regimes.firstOrNull(),
                ruleId = "enrichment.lei.format",
                severity = ValidationSeverity.ERROR,
                rejectionCategory = "LEI",
                reasonCode = "INVALID_LEI",
                message = "LEI must be 20 alphanumeric characters",
                remediationHint = "Provide an active legal entity identifier"
            )
        )
    }
}

@Component
class InstrumentEnrichmentAdapter : EnrichmentAdapter {
    override fun enrich(event: CanonicalRecordMappedEvent): EnrichmentResult {
        val instrumentId = event.canonicalFields["instrument_id"].orEmpty()
        if (instrumentId.isBlank()) {
            return EnrichmentResult(
                fields = emptyMap(),
                metadata = mapOf(
                    "instrument.source" to "firds-cache",
                    "instrument.checkedAt" to Instant.now().toString()
                ),
                exception = ValidationExceptionEnvelope(
                    exceptionId = UUID.randomUUID(),
                    correlationId = event.metadata.correlationId,
                    eventId = event.metadata.eventId,
                    regime = event.metadata.regimes.firstOrNull(),
                    ruleId = "enrichment.instrument.present",
                    severity = ValidationSeverity.ERROR,
                    rejectionCategory = "INSTRUMENT",
                    reasonCode = "MISSING_INSTRUMENT_ID",
                    message = "instrument_id is required for enrichment",
                    remediationHint = "Provide ISIN or instrument identifier"
                )
            )
        }
        val cfi = when {
            instrumentId.startsWith("GB") -> "ESXXXX"
            instrumentId.startsWith("US") -> "ESXXXX"
            else -> "OTXXXX"
        }
        return EnrichmentResult(
            fields = mapOf("derived_cfi" to cfi),
            metadata = mapOf(
                "instrument.source" to "firds-cache",
                "instrument.checkedAt" to Instant.now().toString()
            ),
            exception = null
        )
    }
}

@Component
class VenueMicEnrichmentAdapter : EnrichmentAdapter {
    private val normalized = mapOf(
        "xlon" to "XLON",
        "xoff" to "XOFF"
    )

    override fun enrich(event: CanonicalRecordMappedEvent): EnrichmentResult {
        val venue = event.canonicalFields["venue"].orEmpty()
        if (venue.isBlank()) {
            return EnrichmentResult(
                fields = emptyMap(),
                metadata = mapOf(
                    "venue.source" to "mic-map",
                    "venue.checkedAt" to Instant.now().toString()
                ),
                exception = ValidationExceptionEnvelope(
                    exceptionId = UUID.randomUUID(),
                    correlationId = event.metadata.correlationId,
                    eventId = event.metadata.eventId,
                    regime = event.metadata.regimes.firstOrNull(),
                    ruleId = "enrichment.venue.present",
                    severity = ValidationSeverity.WARNING,
                    rejectionCategory = "VENUE",
                    reasonCode = "MISSING_VENUE",
                    message = "Venue missing; requires review",
                    remediationHint = "Provide MIC venue code"
                )
            )
        }
        val mic = normalized[venue.lowercase()] ?: venue.uppercase()
        return EnrichmentResult(
            fields = mapOf("venue_mic" to mic),
            metadata = mapOf(
                "venue.source" to "mic-map",
                "venue.checkedAt" to Instant.now().toString()
            ),
            exception = null
        )
    }
}
