package com.balfouriana.service.filing.template

import com.balfouriana.domain.FilingOutputFormat
import com.balfouriana.domain.FilingReadyRecordEvent
import com.balfouriana.domain.RegulatoryRegime
import com.balfouriana.service.filing.FilingRenderer
import org.springframework.stereotype.Component

@Component
class MifidXmlFilingRenderer : FilingRenderer {
    override val templateId: String = MifidFilingTemplatePack.version.templateId
    override val templateVersion: String = MifidFilingTemplatePack.version.version
    override val outputFormat: FilingOutputFormat = FilingOutputFormat.XML

    override fun supports(regime: RegulatoryRegime): Boolean = regime == RegulatoryRegime.MIFID_II

    override fun render(event: FilingReadyRecordEvent): String {
        val pack = MifidFilingTemplatePack.version
        val elements = MifidFilingTemplatePack.canonicalToXmlElement.mapNotNull { (canonicalKey, xmlElement) ->
            val value = event.filingReadyFields[canonicalKey]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            "<$xmlElement>${escapeXml(value)}</$xmlElement>"
        }.joinToString("")
        return buildString {
            append("<MiFIRTransactionReport")
            append(" templateVersion=\"${escapeXml(pack.version)}\"")
            append(" authorityRef=\"${escapeXml(pack.authorityRef)}\"")
            append(" authority=\"${escapeXml(pack.authority)}\"")
            append(">")
            append("<Transaction>")
            append(elements)
            append("</Transaction>")
            append("</MiFIRTransactionReport>")
        }
    }
}

internal fun escapeXml(value: String): String {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
