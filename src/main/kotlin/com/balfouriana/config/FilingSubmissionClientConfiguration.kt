package com.balfouriana.service.filing

import com.balfouriana.config.FilingStep4Properties
import com.balfouriana.config.FilingSubmissionMode
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class FilingSubmissionClientConfiguration(
    private val filingStep4Properties: FilingStep4Properties,
    private val localOutboxFilingSubmissionClient: LocalOutboxFilingSubmissionClient,
    private val sftpFilingSubmissionClient: SftpFilingSubmissionClient
) {
    @Bean
    fun filingSubmissionClient(): FilingSubmissionClient {
        return when (filingStep4Properties.submission.mode) {
            FilingSubmissionMode.LOCAL_OUTBOX -> localOutboxFilingSubmissionClient
            FilingSubmissionMode.SFTP -> sftpFilingSubmissionClient
        }
    }
}
