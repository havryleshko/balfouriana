package com.balfouriana.api

import com.balfouriana.config.CorrelationIdFilter
import com.balfouriana.domain.IngestionChannel
import com.balfouriana.service.IngestionReceiveService
import org.slf4j.MDC
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
class IngestionController(
    private val ingestionReceiveService: IngestionReceiveService
) {

    @PostMapping("/ingest", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun ingest(@RequestParam("file") file: MultipartFile): ResponseEntity<IngestionResponse> {
        if (file.isEmpty) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build()
        }
        val correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_KEY)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: UUID.randomUUID()
        val bytes = file.bytes
        val artifact = try {
            ingestionReceiveService.receive(
                bytes = bytes,
                originalFilename = file.originalFilename ?: "upload.bin",
                channel = IngestionChannel.REST,
                correlationId = correlationId
            )
        } catch (e: IllegalArgumentException) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build()
        }
        return ResponseEntity.ok(
            IngestionResponse(
                artifactId = artifact.artifactId,
                correlationId = artifact.correlationId,
                storedPath = artifact.storedRelativePath
            )
        )
    }

    data class IngestionResponse(
        val artifactId: UUID,
        val correlationId: UUID,
        val storedPath: String
    )
}
