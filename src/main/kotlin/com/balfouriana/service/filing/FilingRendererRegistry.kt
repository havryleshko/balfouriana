package com.balfouriana.service.filing

import com.balfouriana.domain.FilingOutputFormat
import com.balfouriana.domain.FilingReadyRecordEvent
import com.balfouriana.domain.RegulatoryRegime
import com.balfouriana.domain.RegulatoryRegimeSelector
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

data class RenderedFiling(
    val templateId: String,
    val templateVersion: String,
    val outputFormat: FilingOutputFormat,
    val fileName: String,
    val payload: String,
    val checksumSha256: String
)

interface FilingRenderer {
    val templateId: String
    val templateVersion: String
    val outputFormat: FilingOutputFormat
    fun supports(regime: RegulatoryRegime): Boolean
    fun render(event: FilingReadyRecordEvent): String
}

@Component
class FilingRendererRegistry(
    private val renderers: List<FilingRenderer>
) {
    fun render(event: FilingReadyRecordEvent): RenderedFiling {
        val regime = primaryRegime(event)
        val renderer = renderers.firstOrNull { it.supports(regime) }
            ?: throw IllegalStateException("No renderer registered for regime $regime")
        val payload = renderer.render(event)
        val checksum = sha256(payload)
        val extension = when (renderer.outputFormat) {
            FilingOutputFormat.XML -> "xml"
            FilingOutputFormat.ISO_20022_XML -> "xml"
        }
        val fileName = "${renderer.templateId}_${event.metadata.correlationId}_${event.envelope.recordIndex}.$extension"
        return RenderedFiling(
            templateId = renderer.templateId,
            templateVersion = renderer.templateVersion,
            outputFormat = renderer.outputFormat,
            fileName = fileName,
            payload = payload,
            checksumSha256 = checksum
        )
    }

    private fun primaryRegime(event: FilingReadyRecordEvent): RegulatoryRegime {
        return RegulatoryRegimeSelector.primaryRegime(event.metadata.regimes)
            ?: throw IllegalStateException("Filing-ready event has no supported regime")
    }

    private fun sha256(payload: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

@Component
class AifmdXmlFilingRenderer : FilingRenderer {
    override val templateId: String = "step4-aifmd-annex-iv-xml"
    override val templateVersion: String = "2026.05.01"
    override val outputFormat: FilingOutputFormat = FilingOutputFormat.XML

    override fun supports(regime: RegulatoryRegime): Boolean = regime == RegulatoryRegime.AIFMD_II

    override fun render(event: FilingReadyRecordEvent): String {
        val fieldXml = event.filingReadyFields.toSortedMap()
            .entries
            .joinToString("") { "<metric code=\"${escapeXml(it.key)}\">${escapeXml(it.value)}</metric>" }
        return "<aifmdAnnexIvReport templateVersion=\"$templateVersion\">$fieldXml</aifmdAnnexIvReport>"
    }
}

@Component
class EmirIso20022FilingRenderer : FilingRenderer {
    override val templateId: String = "step4-emir-iso20022-xml"
    override val templateVersion: String = "2026.05.01"
    override val outputFormat: FilingOutputFormat = FilingOutputFormat.ISO_20022_XML

    override fun supports(regime: RegulatoryRegime): Boolean = regime == RegulatoryRegime.EMIR

    override fun render(event: FilingReadyRecordEvent): String {
        val fieldXml = event.filingReadyFields.toSortedMap()
            .entries
            .joinToString("") { "<RptgAttr name=\"${escapeXml(it.key)}\">${escapeXml(it.value)}</RptgAttr>" }
        return "<EmirIso20022Report templateVersion=\"$templateVersion\">$fieldXml</EmirIso20022Report>"
    }
}

private fun escapeXml(value: String): String {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
