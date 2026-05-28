package com.balfouriana.api

import com.balfouriana.config.DemoProperties
import com.balfouriana.domain.AuditExportBundle
import com.balfouriana.domain.AuditExportFilters
import com.balfouriana.domain.RegulatoryRegime
import com.balfouriana.service.audit.AuditExportBadRequestException
import com.balfouriana.domain.ExceptionQueueSeverity
import com.balfouriana.domain.ExceptionSourceStep
import com.balfouriana.domain.IngestionChannel
import com.balfouriana.service.IngestionReceiveService
import com.balfouriana.service.validation.ValidationAuditQueryService
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import com.balfouriana.service.ops.ExceptionResolutionBadRequestException
import com.balfouriana.service.ops.ExceptionResolutionConflictException
import com.balfouriana.service.ops.ExceptionResolutionNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

@RestController
class DemoController(
    private val demoRunQueryService: DemoRunQueryService,
    private val validationAuditQueryService: ValidationAuditQueryService,
    private val demoProperties: DemoProperties,
    private val ingestionReceiveService: IngestionReceiveService
) {
    @GetMapping("/demo/runs/{correlationId}")
    fun run(@PathVariable correlationId: UUID): DemoRunResponse {
        return demoRunQueryService.run(correlationId)
    }

    @GetMapping("/demo/runs/{correlationId}/summary")
    fun summary(@PathVariable correlationId: UUID): DemoRunSummaryResponse {
        return demoRunQueryService.summary(correlationId)
    }

    @GetMapping("/demo/runs")
    fun recent(@RequestParam(required = false, defaultValue = "20") limit: Int): List<DemoRunRow> {
        return demoRunQueryService.recentRuns(limit)
    }

    @GetMapping("/demo/exceptions")
    fun exceptions(
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) severity: ExceptionQueueSeverity?,
        @RequestParam(required = false) sourceStep: ExceptionSourceStep?,
        @RequestParam(required = false) correlationId: UUID?,
        @RequestParam(required = false, defaultValue = "false") includeResolved: Boolean
    ): List<ExceptionQueueItemResponse> {
        return validationAuditQueryService.openExceptions(
            limit = limit,
            severity = severity,
            sourceStep = sourceStep,
            correlationId = correlationId,
            includeResolved = includeResolved
        ).map { it.toResponse() }
    }

    @PostMapping("/demo/exceptions/{queueItemId}/resolve")
    fun resolveException(
        @PathVariable queueItemId: UUID,
        @RequestBody request: ExceptionResolveRequest
    ): ResponseEntity<ExceptionResolveResponse> {
        return try {
            val resolved = validationAuditQueryService.resolveException(
                queueItemId = queueItemId,
                resolutionType = request.resolutionType,
                note = request.note,
                resolvedBy = request.resolvedBy
            )
            ResponseEntity.ok(
                ExceptionResolveResponse(
                    queueItemId = resolved.queueItemId,
                    resolutionType = resolved.resolutionType,
                    resolvedAt = resolved.metadata.occurredAt,
                    resolvedBy = resolved.resolvedBy
                )
            )
        } catch (e: ExceptionResolutionNotFoundException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        } catch (e: ExceptionResolutionConflictException) {
            ResponseEntity.status(HttpStatus.CONFLICT).build()
        } catch (e: ExceptionResolutionBadRequestException) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST).build()
        }
    }

    @PostMapping("/demo/exceptions/{queueItemId}/resubmit")
    fun resubmitException(
        @PathVariable queueItemId: UUID,
        @RequestBody request: ExceptionResubmitRequest
    ): ResponseEntity<ExceptionResubmitResponse> {
        return try {
            val result = validationAuditQueryService.resubmitException(
                queueItemId = queueItemId,
                note = request.note,
                resolvedBy = request.resolvedBy
            )
            ResponseEntity.ok(
                ExceptionResubmitResponse(
                    correlationId = result.correlationId,
                    submissionId = result.submissionId,
                    filingReadyEventId = result.filingReadyEventId,
                    resolved = result.resolved
                )
            )
        } catch (e: ExceptionResolutionNotFoundException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        } catch (e: ExceptionResolutionConflictException) {
            ResponseEntity.status(HttpStatus.CONFLICT).build()
        } catch (e: ExceptionResolutionBadRequestException) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST).build()
        }
    }

    @GetMapping("/demo/audit/export")
    fun exportAudit(
        @RequestParam(required = false) correlationId: UUID?,
        @RequestParam(required = false) submissionId: UUID?,
        @RequestParam(required = false) artifactId: UUID?,
        @RequestParam(required = false) regime: RegulatoryRegime?,
        @RequestParam(required = false) start: Instant?,
        @RequestParam(required = false) end: Instant?,
        @RequestParam(required = false) limit: Int?
    ): ResponseEntity<AuditExportBundle> {
        return try {
            val bundle = validationAuditQueryService.exportAudit(
                AuditExportFilters(
                    correlationId = correlationId,
                    submissionId = submissionId,
                    artifactId = artifactId,
                    regime = regime,
                    startInclusive = start,
                    endExclusive = end,
                    limit = limit
                )
            )
            val filenameKey = bundle.summary?.correlationId ?: bundle.exportId
            val filename = "audit-$filenameKey-${bundle.generatedAt}.json"
            ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
                .body(bundle)
        } catch (e: AuditExportBadRequestException) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST).build()
        }
    }

    @GetMapping("/demo/scenarios")
    fun scenarios(): List<DemoScenarioFile> {
        val root = Path.of(demoProperties.dataDir)
        if (!Files.exists(root)) {
            return emptyList()
        }
        return Files.walk(root).use { stream ->
            stream
                .filter { Files.isRegularFile(it) && (it.toString().endsWith(".csv") || it.toString().endsWith(".json")) }
                .map { path ->
                    val rel = root.relativize(path).toString().replace("\\", "/")
                    val parts = rel.split("/")
                    DemoScenarioFile(
                        scenario = parts.firstOrNull() ?: "unknown",
                        fileName = path.fileName.toString(),
                        relativePath = rel
                    )
                }
                .sorted(compareBy<DemoScenarioFile> { it.scenario }.thenBy { it.fileName })
                .toList()
        }
    }

    @GetMapping("/demo/scenarios/{scenario}/{fileName}")
    fun scenarioFile(
        @PathVariable scenario: String,
        @PathVariable fileName: String
    ): ResponseEntity<Resource> {
        val target = Path.of(demoProperties.dataDir).resolve(scenario).resolve(fileName).normalize()
        if (!Files.exists(target) || !Files.isRegularFile(target)) {
            return ResponseEntity.notFound().build()
        }
        val resource = FileSystemResource(target)
        val contentType = if (fileName.endsWith(".json")) MediaType.APPLICATION_JSON else MediaType.TEXT_PLAIN
        return ResponseEntity.ok()
            .contentType(contentType)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$fileName\"")
            .body(resource)
    }

    @GetMapping("/demo/scenarios/{scenario}/{fileName}/ingest")
    fun ingestScenario(
        @PathVariable scenario: String,
        @PathVariable fileName: String
    ): IngestionController.IngestionResponse {
        val target = Path.of(demoProperties.dataDir).resolve(scenario).resolve(fileName).normalize()
        require(Files.exists(target) && Files.isRegularFile(target)) { "Scenario file not found" }
        val artifact = ingestionReceiveService.receive(
            bytes = Files.readAllBytes(target),
            originalFilename = "$scenario/$fileName",
            channel = IngestionChannel.REST,
            correlationId = UUID.randomUUID()
        )
        return IngestionController.IngestionResponse(
            artifactId = artifact.artifactId,
            correlationId = artifact.correlationId,
            storedPath = artifact.storedRelativePath
        )
    }
}

data class DemoScenarioFile(
    val scenario: String,
    val fileName: String,
    val relativePath: String
)
