package com.balfouriana.service.filing

import com.balfouriana.config.FilingStep4Properties
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

interface FilingSubmissionClient {
    fun submit(fileName: String, payload: String): String
}

@Component
class SftpFilingSubmissionClient(
    private val filingStep4Properties: FilingStep4Properties
) : FilingSubmissionClient {
    override fun submit(fileName: String, payload: String): String {
        val outbox = Path.of(filingStep4Properties.submission.localOutboxDir)
        Files.createDirectories(outbox)
        val target = outbox.resolve(fileName)
        Files.writeString(target, payload, StandardCharsets.UTF_8)
        return "sftp://${filingStep4Properties.submission.remoteBasePath}/$fileName"
    }
}
