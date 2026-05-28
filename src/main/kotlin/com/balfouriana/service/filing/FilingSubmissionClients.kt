package com.balfouriana.service.filing

import com.balfouriana.config.FilingStep4Properties
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.util.Properties

@Component
class JschSftpOperations : SftpOperations {
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
        val jsch = JSch()
        if (privateKeyPath.isNotBlank()) {
            jsch.addIdentity(privateKeyPath)
        }
        val session = jsch.getSession(username, host, port)
        if (password.isNotBlank()) {
            session.setPassword(password)
        }
        session.setConfig(sshConfig())
        session.connect()
        try {
            val channel = session.openChannel("sftp") as ChannelSftp
            channel.connect()
            try {
                runCatching { channel.cd(remoteDirectory) }.getOrElse {
                    channel.mkdir(remoteDirectory)
                    channel.cd(remoteDirectory)
                }
                channel.put(ByteArrayInputStream(payload.toByteArray(Charsets.UTF_8)), fileName)
            } finally {
                channel.disconnect()
            }
        } finally {
            session.disconnect()
        }
    }

    private fun sshConfig(): Properties {
        return Properties().apply {
            setProperty("StrictHostKeyChecking", "no")
        }
    }
}

@Component
class LocalOutboxFilingSubmissionClient(
    private val filingStep4Properties: FilingStep4Properties
) : FilingSubmissionClient {
    override fun submit(fileName: String, payload: String): String {
        val outbox = java.nio.file.Path.of(filingStep4Properties.submission.localOutboxDir)
        java.nio.file.Files.createDirectories(outbox)
        val target = outbox.resolve(fileName)
        java.nio.file.Files.writeString(target, payload, java.nio.charset.StandardCharsets.UTF_8)
        return target.toUri().toString()
    }
}

@Component
class SftpFilingSubmissionClient(
    private val filingStep4Properties: FilingStep4Properties,
    private val sftpOperations: SftpOperations
) : FilingSubmissionClient {
    override fun submit(fileName: String, payload: String): String {
        val sftp = filingStep4Properties.submission.sftp
        require(sftp.host.isNotBlank()) { "SFTP host is required when submission mode is SFTP" }
        require(sftp.username.isNotBlank()) { "SFTP username is required when submission mode is SFTP" }
        require(sftp.password.isNotBlank() || sftp.privateKeyPath.isNotBlank()) {
            "SFTP password or private key path is required when submission mode is SFTP"
        }
        sftpOperations.upload(
            host = sftp.host,
            port = sftp.port,
            username = sftp.username,
            password = sftp.password,
            privateKeyPath = sftp.privateKeyPath,
            remoteDirectory = sftp.remoteDirectory,
            fileName = fileName,
            payload = payload
        )
        return "sftp://${sftp.host}:${sftp.port}${sftp.remoteDirectory.trimEnd('/')}/$fileName"
    }
}
