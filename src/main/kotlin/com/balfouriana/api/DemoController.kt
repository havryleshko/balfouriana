package com.balfouriana.api

import com.balfouriana.config.DemoProperties
import com.balfouriana.domain.IngestionChannel
import com.balfouriana.service.IngestionReceiveService
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

@RestController
class DemoController(
    private val demoRunQueryService: DemoRunQueryService,
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
