package com.balfouriana.service

import com.balfouriana.config.IngestionProperties
import com.balfouriana.domain.IngestionChannel
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.UUID

@Component
class DropZonePoller(
    private val properties: IngestionProperties,
    private val ingestionReceiveService: IngestionReceiveService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${balfouriana.ingestion.drop-zone.poll-interval-ms}")
    fun poll() {
        if (!properties.dropZone.enabled) return
        val root = Path.of(properties.root).toAbsolutePath().normalize()
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
                        log.debug("skip incoming {}: {}", incomingPath, e.toString())
                        return@forEach
                    }
                    try {
                        val bytes = Files.readAllBytes(wip)
                        ingestionReceiveService.receive(
                            bytes = bytes,
                            originalFilename = originalName,
                            channel = IngestionChannel.DROP_ZONE,
                            correlationId = UUID.randomUUID()
                        )
                        Files.deleteIfExists(wip)
                    } catch (e: Exception) {
                        log.warn("drop zone ingest failed for {}", wip, e)
                        try {
                            Files.move(
                                wip,
                                failed.resolve(wip.fileName.toString()),
                                StandardCopyOption.REPLACE_EXISTING
                            )
                        } catch (moveEx: Exception) {
                            log.warn("could not move failed drop file {}", wip, moveEx)
                        }
                    }
                }
        }
    }

    private fun isReady(path: Path): Boolean {
        val lastModified = Files.getLastModifiedTime(path).toInstant()
        val threshold = lastModified.plusMillis(properties.dropZone.stabilityCheckMs)
        return !Instant.now().isBefore(threshold)
    }
}
