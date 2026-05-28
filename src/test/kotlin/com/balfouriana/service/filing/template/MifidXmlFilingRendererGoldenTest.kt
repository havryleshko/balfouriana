package com.balfouriana.service.filing.template

import com.balfouriana.domain.CanonicalRecordType
import com.balfouriana.domain.EventMetadata
import com.balfouriana.domain.FilingReadyRecordEvent
import com.balfouriana.domain.IngestionChannel
import com.balfouriana.domain.IngestionFileFormat
import com.balfouriana.domain.RegulatoryRegime
import com.balfouriana.domain.RulePackVersion
import com.balfouriana.domain.SourceRecordEnvelope
import com.balfouriana.service.filing.FilingRendererRegistry
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class MifidXmlFilingRendererGoldenTest {
    private val renderer = MifidXmlFilingRenderer()
    private val registry = FilingRendererRegistry(
        listOf(
            renderer,
            com.balfouriana.service.filing.EmirIso20022FilingRenderer(),
            com.balfouriana.service.filing.AifmdXmlFilingRenderer()
        )
    )
    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `renders golden mifid transaction report`() {
        val fixture = readFixture()
        val event = filingReady(fixture["filingReadyFields"] as Map<String, String>)
        val rendered = renderer.render(event)
        val expected = readResource("/filing/mifid/expected-transaction-report.xml")
        assertEquals(normalizeXml(expected), normalizeXml(rendered))
        assertFalse(rendered.contains("<field name="))
    }

    @Test
    fun `registry render is deterministic for golden input`() {
        val fixture = readFixture()
        val event = filingReady(fixture["filingReadyFields"] as Map<String, String>)
        val first = registry.render(event)
        val second = registry.render(event)
        assertEquals(first.payload, second.payload)
        assertEquals("2026.05.18", first.templateVersion)
        assertTrue(first.payload.contains("MiFIRTransactionReport"))
    }

    private fun readFixture(): Map<String, Any> {
        val stream = requireNotNull(javaClass.getResourceAsStream("/filing/mifid/filing-ready-input.json"))
        return objectMapper.readValue(stream)
    }

    private fun readResource(path: String): String {
        val stream = requireNotNull(javaClass.getResourceAsStream(path))
        return stream.bufferedReader().readText()
    }

    private fun normalizeXml(value: String): String {
        return value.replace("\n", "").replace("\r", "").trim()
    }

    private fun filingReady(fields: Map<String, String>): FilingReadyRecordEvent {
        val now = Instant.parse("2026-05-01T10:00:00Z")
        return FilingReadyRecordEvent(
            metadata = EventMetadata(
                eventId = UUID.randomUUID(),
                correlationId = UUID.randomUUID(),
                sourceSystem = "test",
                occurredAt = now,
                schemaVersion = "rules.step3.filing-ready.v1",
                regimes = setOf(RegulatoryRegime.MIFID_II)
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
            rulePackVersion = RulePackVersion("step3-mifid-transaction-rules", "2026.04.30", now),
            filingReadyFields = fields,
            traceMetadata = emptyMap(),
            inputFingerprint = "in",
            outputFingerprint = "out"
        )
    }
}
