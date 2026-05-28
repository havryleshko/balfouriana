package com.balfouriana.service.filing

import com.balfouriana.config.FilingStep4Properties
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.UUID

@Component
class AcknowledgementDropZonePoller(
    private val filingStep4Properties: FilingStep4Properties,
    private val acknowledgementIngestionService: AcknowledgementIngestionService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${balfouriana.filing.step4.acknowledgement.drop-zone.poll-interval-ms}")
    fun poll() {
        if (!filingStep4Properties.enabled) return
        if (!filingStep4Properties.acknowledgement.dropZone.enabled) return
        val root = Path.of(filingStep4Properties.acknowledgement.dropZone.root).toAbsolutePath().normalize()
        val incoming = root.resolve("incoming")
        val processing = root.resolve("processing")
        val failed = root.resolve("failed")
        if (!Files.isDirectory(incoming)) return
        Files.createDirectories(processing)
        Files.createDirectories(failed)

        Files.list(incoming).use { stream ->
            stream.filter { Files.isRegularFile(it) }
                .forEach { incomingPath ->
                    if (!isReady(incomingPath)) return@forEach
                    val originalName = incomingPath.fileName.toString()
                    val wip = processing.resolve("${UUID.randomUUID()}_$originalName")
                    try {
                        Files.move(incomingPath, wip, StandardCopyOption.ATOMIC_MOVE)
                    } catch (e: Exception) {
                        log.debug("skip ack incoming {}: {}", incomingPath, e.toString())
                        return@forEach
                    }
                    try {
                        val payload = Files.readString(wip)
                        val sourceSystem = "step4-ack-drop-zone:$originalName"
                        val ingested = when {
                            originalName.lowercase().endsWith(".csv") -> {
                                val lines = payload.trim().lines().filter { it.isNotBlank() }
                                if (lines.size <= 1) {
                                    false
                                } else {
                                    acknowledgementIngestionService.ingestCsv(payload, sourceSystem)
                                    true
                                }
                            }
                            originalName.lowercase().endsWith(".xml") -> {
                                if (Regex("""externalReference="([^"]+)"""").containsMatchIn(payload)) {
                                    acknowledgementIngestionService.ingestXml(payload, sourceSystem)
                                    true
                                } else {
                                    false
                                }
                            }
                            else -> false
                        }
                        if (!ingested) {
                            moveToFailed(wip, failed)
                        } else {
                            Files.deleteIfExists(wip)
                        }
                    } catch (e: Exception) {
                        log.warn("ack drop zone ingest failed for {}", wip, e)
                        moveToFailed(wip, failed)
                    }
                }
        }
    }

    private fun moveToFailed(wip: Path, failed: Path) {
        try {
            Files.move(wip, failed.resolve(wip.fileName.toString()), StandardCopyOption.REPLACE_EXISTING)
        } catch (moveEx: Exception) {
            log.warn("could not move failed ack file {}", wip, moveEx)
        }
    }

    private fun isReady(path: Path): Boolean {
        val lastModified = Files.getLastModifiedTime(path).toInstant()
        val threshold = lastModified.plusMillis(filingStep4Properties.acknowledgement.dropZone.stabilityCheckMs)
        return !Instant.now().isBefore(threshold)
    }
}
