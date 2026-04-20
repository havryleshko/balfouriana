package com.balfouriana.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@ConfigurationProperties(prefix = "balfouriana.runtime")
data class AppRuntimeProperties(
    val serviceIdentity: String = "balfouriana-core"
)

@Configuration
@EnableConfigurationProperties(AppRuntimeProperties::class)
class RuntimePropertiesConfiguration
