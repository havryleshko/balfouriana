package com.balfouriana.service.filing

import com.balfouriana.domain.CanonicalRecordType
import com.balfouriana.domain.EventMetadata
import com.balfouriana.domain.FilingOutputFormat
import com.balfouriana.domain.FilingReadyRecordEvent
import com.balfouriana.domain.IngestionChannel
import com.balfouriana.domain.IngestionFileFormat
import com.balfouriana.domain.RegulatoryRegime
import com.balfouriana.domain.RulePackVersion
import com.balfouriana.domain.SourceRecordEnvelope
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class FilingRendererRegistryTest {
    private val registry = FilingRendererRegistry(
        listOf(
            EmirIso20022FilingRenderer(),
            MifidXmlFilingRenderer(),
            AifmdXmlFilingRenderer()
        )
    )

    @Test
    fun `renders mifid filing deterministically`() {
        val event = filingReady(setOf(RegulatoryRegime.MIFID_II))
        val first = registry.render(event)
        val second = registry.render(event)
        assertEquals("step4-mifid-xml", first.templateId)
        assertEquals(FilingOutputFormat.XML, first.outputFormat)
        assertEquals(first.payload, second.payload)
        assertEquals(first.checksumSha256, second.checksumSha256)
    }

    @Test
    fun `renders emir filing with iso output format`() {
        val event = filingReady(setOf(RegulatoryRegime.EMIR))
        val rendered = registry.render(event)
        assertEquals("step4-emir-iso20022-xml", rendered.templateId)
        assertEquals(FilingOutputFormat.ISO_20022_XML, rendered.outputFormat)
        assertTrue(rendered.payload.contains("EmirIso20022Report"))
    }

    private fun filingReady(regimes: Set<RegulatoryRegime>): FilingReadyRecordEvent {
        val now = Instant.parse("2026-05-01T10:00:00Z")
        return FilingReadyRecordEvent(
            metadata = EventMetadata(
                eventId = UUID.randomUUID(),
                correlationId = UUID.randomUUID(),
                sourceSystem = "test",
                occurredAt = now,
                schemaVersion = "rules.step3.filing-ready.v1",
                regimes = regimes
            ),
            artifactId = UUID.randomUUID(),
            envelope = SourceRecordEnvelope(
                sourceId = "test",
                sourceSystem = "test",
                ingestionChannel = IngestionChannel.REST,
                fileId = UUID.randomUUID(),
                originalFileName = "x.csv",
                recordIndex = 1,
                receivedAt = now,
                format = IngestionFileFormat.CSV,
                contentType = "text/csv",
                fileSizeBytes = 100,
                checksumSha256 = "abc",
                schemaHint = null
            ),
            recordType = CanonicalRecordType.TRADE,
            rulePackVersion = RulePackVersion("pack", "v1", Instant.parse("2026-04-28T00:00:00Z")),
            filingReadyFields = mapOf("a" to "1", "b" to "2"),
            traceMetadata = mapOf("x" to "y"),
            inputFingerprint = "in",
            outputFingerprint = "out"
        )
    }
}
