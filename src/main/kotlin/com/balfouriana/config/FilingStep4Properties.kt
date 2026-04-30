package com.balfouriana.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@ConfigurationProperties(prefix = "balfouriana.filing.step4")
data class FilingStep4Properties(
    val enabled: Boolean = true,
    val submission: Submission = Submission(),
    val acknowledgement: Acknowledgement = Acknowledgement()
) {
    data class Submission(
        val channel: String = "SFTP",
        val localOutboxDir: String = "./data/filing/outbox",
        val remoteBasePath: String = "/outbound"
    )

    data class Acknowledgement(
        val channel: String = "SFTP"
    )
}

@Configuration
@EnableConfigurationProperties(FilingStep4Properties::class)
class FilingStep4PropertiesConfiguration
