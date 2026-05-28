package com.balfouriana.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@ConfigurationProperties(prefix = "balfouriana.filing.step4")
data class FilingStep4Properties(
    val enabled: Boolean = true,
    val schemaValidation: SchemaValidation = SchemaValidation(),
    val submission: Submission = Submission(),
    val acknowledgement: Acknowledgement = Acknowledgement()
) {
    data class SchemaValidation(
        val enabled: Boolean = true
    )

    data class Submission(
        val mode: FilingSubmissionMode = FilingSubmissionMode.LOCAL_OUTBOX,
        val channel: String = "LOCAL_OUTBOX",
        val localOutboxDir: String = "./data/filing/outbox",
        val remoteBasePath: String = "/outbound",
        val sftp: Sftp = Sftp()
    ) {
        data class Sftp(
            val host: String = "",
            val port: Int = 22,
            val username: String = "",
            val password: String = "",
            val privateKeyPath: String = "",
            val remoteDirectory: String = "/outbound"
        )
    }

    data class Acknowledgement(
        val channel: String = "SFTP",
        val dropZone: DropZone = DropZone()
    ) {
        data class DropZone(
            val enabled: Boolean = true,
            val root: String = "./data/filing/ack",
            val pollIntervalMs: Long = 5000,
            val stabilityCheckMs: Long = 2000
        )
    }
}

@Configuration
@EnableConfigurationProperties(FilingStep4Properties::class)
class FilingStep4PropertiesConfiguration
