package com.balfouriana.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@ConfigurationProperties(prefix = "balfouriana.demo")
data class DemoProperties(
    val dataDir: String = "./balfouriana-demo-data"
)

@Configuration
@EnableConfigurationProperties(DemoProperties::class)
class DemoPropertiesConfiguration
