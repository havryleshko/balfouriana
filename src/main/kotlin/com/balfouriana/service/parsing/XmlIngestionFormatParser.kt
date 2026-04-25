package com.balfouriana.service.parsing

import com.balfouriana.domain.CanonicalRecordType
import com.balfouriana.domain.IngestionFileFormat
import com.balfouriana.domain.ParseRejectionCode
import org.springframework.stereotype.Component
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

@Component
class XmlIngestionFormatParser : IngestionFormatParser {
    override fun supports(format: IngestionFileFormat): Boolean = format == IngestionFileFormat.XML

    override fun parse(request: ParseRequest): ParserBatchResult {
        val document = runCatching {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = false
            factory.newDocumentBuilder().parse(ByteArrayInputStream(request.bytes))
        }.getOrElse {
            return ParserBatchResult(
                format = IngestionFileFormat.XML,
                outcomes = listOf(
                    ParserRecordOutcome.Rejected(
                        ParserRejection(0, ParseRejectionCode.UNDECODABLE_CONTENT, "Invalid XML payload", null)
                    )
                )
            )
        }

        val recordNodes = document.getElementsByTagName("record")
        if (recordNodes.length == 0) {
            return ParserBatchResult(
                format = IngestionFileFormat.XML,
                outcomes = listOf(
                    ParserRecordOutcome.Rejected(
                        ParserRejection(0, ParseRejectionCode.STRUCTURALLY_INVALID_RECORD, "XML payload contains no <record> nodes", null)
                    )
                )
            )
        }

        val outcomes = (0 until recordNodes.length).map { idx ->
            val node = recordNodes.item(idx) as? Element
            if (node == null) {
                ParserRecordOutcome.Rejected(
                    ParserRejection(idx + 1, ParseRejectionCode.STRUCTURALLY_INVALID_RECORD, "XML record node is invalid", null)
                )
            } else {
                val fields = mutableMapOf<String, String>()
                val children = node.childNodes
                for (i in 0 until children.length) {
                    val child = children.item(i)
                    if (child is Element) {
                        fields[child.tagName.lowercase()] = child.textContent.trim()
                    }
                }
                val type = resolveType(fields["record_type"])
                if (type == null) {
                    ParserRecordOutcome.Rejected(
                        ParserRejection(idx + 1, ParseRejectionCode.MISSING_REQUIRED_FIELD, "record_type is required", node.textContent)
                    )
                } else {
                    ParserRecordOutcome.Parsed(
                        ParsedStructuredRecord(
                            recordIndex = idx + 1,
                            typeHint = type,
                            fields = fields,
                            rawRecord = node.textContent
                        )
                    )
                }
            }
        }
        return ParserBatchResult(IngestionFileFormat.XML, outcomes)
    }

    private fun resolveType(raw: String?): CanonicalRecordType? {
        return when (raw?.trim()?.uppercase()) {
            "TRADE" -> CanonicalRecordType.TRADE
            "POSITION" -> CanonicalRecordType.POSITION
            "CASH_MOVEMENT" -> CanonicalRecordType.CASH_MOVEMENT
            "CORPORATE_ACTION" -> CanonicalRecordType.CORPORATE_ACTION
            else -> null
        }
    }
}
