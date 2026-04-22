package com.balfouriana.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@ConfigurationProperties(prefix = "balfouriana.ingestion")
data class IngestionProperties(
    val root: String = "./data/ingest",
    val receiveSchemaVersion: String = "ingestion.receive.v1",
    val rest: Rest = Rest(),
    val dropZone: DropZone = DropZone()
) {
    data class Rest(
        val maxFileSizeBytes: Long = 52_428_800,
        val apiKey: String? = null
    )

    data class DropZone(
        val enabled: Boolean = true,
        val pollIntervalMs: Long = 5000,
        val stabilityCheckMs: Long = 2000
    )
}

@Configuration
@EnableConfigurationProperties(IngestionProperties::class)
class IngestionPropertiesConfiguration
