package com.balfouriana.repository

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("local")
class FlywayMigrationIntegrationTest {
    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `event_store table exists after migration`() {
        val count = jdbcTemplate.queryForObject(
            "select count(*) from event_store",
            Int::class.java
        ) ?: 0
        assertTrue(count >= 0)
    }
}
