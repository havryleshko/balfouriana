package com.balfouriana.service.filing

interface FilingSubmissionClient {
    fun submit(fileName: String, payload: String): String
}

interface SftpOperations {
    fun upload(
        host: String,
        port: Int,
        username: String,
        password: String,
        privateKeyPath: String,
        remoteDirectory: String,
        fileName: String,
        payload: String
    )
}
