package com.balfouriana.service.filing

import com.balfouriana.config.FilingStep4Properties
import com.balfouriana.config.FilingSubmissionMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SftpFilingSubmissionClientTest {
    @Test
    fun `uploads via sftp operations and returns remote path`() {
        var uploadedFileName: String? = null
        val client = SftpFilingSubmissionClient(
            filingStep4Properties = FilingStep4Properties(
                submission = FilingStep4Properties.Submission(
                    mode = FilingSubmissionMode.SFTP,
                    sftp = FilingStep4Properties.Submission.Sftp(
                        host = "sftp.example.com",
                        port = 2222,
                        username = "reporting",
                        password = "secret",
                        remoteDirectory = "/outbound"
                    )
                )
            ),
            sftpOperations = object : SftpOperations {
                override fun upload(
                    host: String,
                    port: Int,
                    username: String,
                    password: String,
                    privateKeyPath: String,
                    remoteDirectory: String,
                    fileName: String,
                    payload: String
                ) {
                    uploadedFileName = fileName
                    assertEquals("sftp.example.com", host)
                    assertEquals(2222, port)
                    assertEquals("reporting", username)
                    assertEquals("/outbound", remoteDirectory)
                    assertEquals("<xml/>", payload)
                }
            }
        )
        val remotePath = client.submit("report.xml", "<xml/>")
        assertEquals("report.xml", uploadedFileName)
        assertTrue(remotePath.startsWith("sftp://sftp.example.com:2222/outbound/report.xml"))
    }

    @Test
    fun `fails fast when host missing`() {
        val client = SftpFilingSubmissionClient(
            filingStep4Properties = FilingStep4Properties(
                submission = FilingStep4Properties.Submission(
                    mode = FilingSubmissionMode.SFTP,
                    sftp = FilingStep4Properties.Submission.Sftp(
                        username = "reporting",
                        password = "secret"
                    )
                )
            ),
            sftpOperations = object : SftpOperations {
                override fun upload(
                    host: String,
                    port: Int,
                    username: String,
                    password: String,
                    privateKeyPath: String,
                    remoteDirectory: String,
                    fileName: String,
                    payload: String
                ) = Unit
            }
        )
        assertThrows(IllegalArgumentException::class.java) {
            client.submit("report.xml", "<xml/>")
        }
    }
}
