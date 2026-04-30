package com.balfouriana.service.rules

import com.balfouriana.domain.CanonicalRecordType
import com.balfouriana.domain.CanonicalRecordValidatedEvent
import com.balfouriana.domain.EventMetadata
import com.balfouriana.domain.IngestionChannel
import com.balfouriana.domain.IngestionFileFormat
import com.balfouriana.domain.RegulatoryRegime
import com.balfouriana.domain.SourceRecordEnvelope
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class RulePackRegistryTest {
    private val registry = RulePackRegistry()

    @Test
    fun `selects regime-specific pack for mifid event`() {
        val event = validatedEvent(
            regimes = setOf(RegulatoryRegime.MIFID_II),
            occurredAt = Instant.parse("2026-04-29T00:00:00Z")
        )
        val pack = registry.select(event)
        assertEquals("step3-mifid-transaction-rules", pack.version.packId)
    }

    @Test
    fun `falls back to default pack before effective date`() {
        val event = validatedEvent(
            regimes = setOf(RegulatoryRegime.MIFID_II),
            occurredAt = Instant.parse("2026-04-27T00:00:00Z")
        )
        val pack = registry.select(event)
        assertEquals("step3-core-default", pack.version.packId)
    }

    @Test
    fun `selects emir pack when both emir and mifid are present`() {
        val event = validatedEvent(
            regimes = setOf(RegulatoryRegime.MIFID_II, RegulatoryRegime.EMIR),
            occurredAt = Instant.parse("2026-04-29T00:00:00Z")
        )
        val pack = registry.select(event)
        assertEquals("step3-emir-transaction-rules", pack.version.packId)
    }

    @Test
    fun `selects aifmd pack when only aifmd is present`() {
        val event = validatedEvent(
            regimes = setOf(RegulatoryRegime.AIFMD_II),
            occurredAt = Instant.parse("2026-04-29T00:00:00Z")
        )
        assertEquals("step3-aifmd-annex-iv-calcs", registry.select(event).version.packId)
    }

    @Test
    fun `selects emir over aifmd when both present`() {
        val event = validatedEvent(
            regimes = setOf(RegulatoryRegime.AIFMD_II, RegulatoryRegime.EMIR),
            occurredAt = Instant.parse("2026-04-29T00:00:00Z")
        )
        assertEquals("step3-emir-transaction-rules", registry.select(event).version.packId)
    }

    @Test
    fun `selects mifid over aifmd when both present without emir`() {
        val event = validatedEvent(
            regimes = setOf(RegulatoryRegime.AIFMD_II, RegulatoryRegime.MIFID_II),
            occurredAt = Instant.parse("2026-04-29T00:00:00Z")
        )
        assertEquals("step3-mifid-transaction-rules", registry.select(event).version.packId)
    }

    private fun validatedEvent(regimes: Set<RegulatoryRegime>, occurredAt: Instant): CanonicalRecordValidatedEvent {
        return CanonicalRecordValidatedEvent(
            metadata = EventMetadata(
                eventId = UUID.randomUUID(),
                correlationId = UUID.randomUUID(),
                sourceSystem = "test",
                occurredAt = occurredAt,
                schemaVersion = "validation.step2.validated.v1",
                regimes = regimes
            ),
            artifactId = UUID.randomUUID(),
            envelope = SourceRecordEnvelope(
                sourceId = "test",
                sourceSystem = "test",
                ingestionChannel = IngestionChannel.REST,
                fileId = UUID.randomUUID(),
                originalFileName = "test.csv",
                recordIndex = 1,
                receivedAt = occurredAt,
                format = IngestionFileFormat.CSV,
                contentType = "text/csv",
                fileSizeBytes = 100,
                checksumSha256 = "abc",
                schemaHint = null
            ),
            recordType = CanonicalRecordType.TRADE,
            validationPack = com.balfouriana.domain.ValidationPackVersion("step2-core", "2026.04.19", Instant.parse("2026-04-19T00:00:00Z")),
            validatedFields = mapOf("record_type" to "TRADE", "quantity" to "10", "price" to "2.5"),
            enrichmentMetadata = emptyMap()
        )
    }
}
