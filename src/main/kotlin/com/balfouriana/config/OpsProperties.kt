package com.balfouriana.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@ConfigurationProperties(prefix = "balfouriana.ops")
data class OpsProperties(
    val exceptionQueue: ExceptionQueue = ExceptionQueue(),
    val auditExport: AuditExport = AuditExport()
) {
    data class ExceptionQueue(
        val lookbackHours: Long = 168,
        val defaultLimit: Int = 100
    )

    data class AuditExport(
        val defaultLimit: Int = 500,
        val maxLimit: Int = 5000,
        val defaultLookbackHours: Long = 168
    )
}

@Configuration
@EnableConfigurationProperties(OpsProperties::class)
class OpsPropertiesConfiguration
