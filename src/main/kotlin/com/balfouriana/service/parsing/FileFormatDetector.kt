package com.balfouriana.service.parsing

import com.balfouriana.domain.IngestionFileFormat
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import kotlin.math.min

@Component
class FileFormatDetector {
    fun detect(request: ParseRequest): IngestionFileFormat {
        request.declaredFormat?.let { declared ->
            if (declared != IngestionFileFormat.UNKNOWN) {
                return declared
            }
        }

        val byExtension = detectByExtension(request.originalFilename)
        if (byExtension != IngestionFileFormat.UNKNOWN) {
            return byExtension
        }
        return detectBySignature(request.bytes)
    }

    private fun detectByExtension(filename: String): IngestionFileFormat {
        val lower = filename.lowercase()
        return when {
            lower.endsWith(".csv") -> IngestionFileFormat.CSV
            lower.endsWith(".json") -> IngestionFileFormat.JSON
            lower.endsWith(".fix") || lower.endsWith(".txt") -> IngestionFileFormat.FIX
            lower.endsWith(".xml") -> IngestionFileFormat.XML
            else -> IngestionFileFormat.UNKNOWN
        }
    }

    private fun detectBySignature(bytes: ByteArray): IngestionFileFormat {
        if (bytes.isEmpty()) {
            return IngestionFileFormat.UNKNOWN
        }
        val prefix = bytes.copyOfRange(0, min(bytes.size, 256)).toString(StandardCharsets.UTF_8).trimStart()
        if (prefix.startsWith("{") || prefix.startsWith("[")) {
            return IngestionFileFormat.JSON
        }
        if (prefix.startsWith("<")) {
            return IngestionFileFormat.XML
        }
        if (prefix.contains("=") && prefix.contains("|")) {
            return IngestionFileFormat.FIX
        }
        if (prefix.contains(",")) {
            return IngestionFileFormat.CSV
        }
        return IngestionFileFormat.UNKNOWN
    }
}
