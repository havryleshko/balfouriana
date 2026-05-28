package com.balfouriana.service.filing.validation

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MifidFilingSchemaValidatorTest {
    private val validator = MifidFilingSchemaValidator()

    @Test
    fun `validates golden transaction report`() {
        val payload = readResource("/filing/mifid/expected-transaction-report.xml")
        val result = validator.validate(payload, "2026.05.18")
        assertTrue(result.valid, result.summaryMessage())
    }

    @Test
    fun `rejects missing transaction element`() {
        val payload = """
            <MiFIRTransactionReport templateVersion="2026.05.18" authorityRef="MiFIR RTS 22 transaction reporting" authority="FCA"/>
        """.trimIndent()
        val result = validator.validate(payload, "2026.05.18")
        assertFalse(result.valid)
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun `rejects unknown root element`() {
        val payload = "<InvalidRoot><Transaction/></InvalidRoot>"
        val result = validator.validate(payload, "2026.05.18")
        assertFalse(result.valid)
    }

    @Test
    fun `rejects unsupported template version`() {
        val payload = readResource("/filing/mifid/expected-transaction-report.xml")
        val result = validator.validate(payload, "2026.01.01")
        assertFalse(result.valid)
    }

    private fun readResource(path: String): String {
        val stream = requireNotNull(javaClass.getResourceAsStream(path))
        return stream.bufferedReader().readText()
    }
}
