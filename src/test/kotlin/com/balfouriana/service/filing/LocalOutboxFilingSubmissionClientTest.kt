package com.balfouriana.service.filing

import com.balfouriana.config.FilingStep4Properties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class LocalOutboxFilingSubmissionClientTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `writes payload to configured outbox directory`() {
        val client = LocalOutboxFilingSubmissionClient(
            FilingStep4Properties(
                submission = FilingStep4Properties.Submission(
                    localOutboxDir = tempDir.toString()
                )
            )
        )
        val location = client.submit("report.xml", "<xml/>")
        val target = tempDir.resolve("report.xml")
        assertTrue(Files.exists(target))
        assertEquals("<xml/>", Files.readString(target))
        assertTrue(location.contains("report.xml"))
    }
}
