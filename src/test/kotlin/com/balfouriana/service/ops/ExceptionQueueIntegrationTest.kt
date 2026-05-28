package com.balfouriana.service.ops

import com.balfouriana.domain.CanonicalRecordType
import com.balfouriana.domain.EventMetadata
import com.balfouriana.domain.ExceptionQueueSeverity
import com.balfouriana.domain.FilingAcknowledgementReceivedEvent
import com.balfouriana.domain.FilingAcknowledgementStatus
import com.balfouriana.domain.RegulatoryRegime
import com.balfouriana.domain.RuleExceptionEnvelope
import com.balfouriana.domain.RuleExceptionRaisedEvent
import com.balfouriana.domain.RulePackVersion
import com.balfouriana.domain.RuleSeverity
import com.balfouriana.domain.UNLINKED_ACK_CORRELATION_ID
import com.balfouriana.repository.EventStoreRepository
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class ExceptionQueueIntegrationTest {
    @Autowired
    lateinit var exceptionQueueQueryService: ExceptionQueueQueryService

    @Autowired
    lateinit var eventStoreRepository: EventStoreRepository

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `blocked breach scenario surfaces blocking queue item`() {
        val correlationId = UUID.randomUUID()
        eventStoreRepository.append(ruleException(correlationId, RuleSeverity.ERROR, "AIFMD_LOAN_CONCENTRATION_BREACH"))

        val items = exceptionQueueQueryService.openExceptions(correlationId = correlationId)
        assertTrue(items.any { it.severity == ExceptionQueueSeverity.BLOCKING })
        assertTrue(items.any { it.reasonCode == "AIFMD_LOAN_CONCENTRATION_BREACH" })
    }

    @Test
    fun `needs review scenario surfaces review queue item`() {
        val correlationId = UUID.randomUUID()
        eventStoreRepository.append(ruleException(correlationId, RuleSeverity.WARNING, "AIFMD_FUND_STRUCTURE_UNKNOWN"))

        val items = exceptionQueueQueryService.openExceptions(correlationId = correlationId)
        assertTrue(items.any { it.severity == ExceptionQueueSeverity.NEEDS_REVIEW })
        assertTrue(items.any { it.reasonCode == "AIFMD_FUND_STRUCTURE_UNKNOWN" })
    }

    @Test
    fun `orphan acknowledgement appears as ops item`() {
        eventStoreRepository.append(
            FilingAcknowledgementReceivedEvent(
                metadata = EventMetadata(
                    eventId = UUID.randomUUID(),
                    correlationId = UNLINKED_ACK_CORRELATION_ID,
                    sourceSystem = "test",
                    occurredAt = Instant.now(),
                    schemaVersion = "filing.step4.ack.rcv.v1",
                    regimes = emptySet()
                ),
                submissionId = null,
                correlationLinked = false,
                linkedCorrelationId = null,
                channel = "SFTP",
                acknowledgementStatus = FilingAcknowledgementStatus.NACK,
                externalReference = "UNKNOWN",
                reasonCode = "REJECTED",
                message = "bad data"
            )
        )

        val items = exceptionQueueQueryService.openExceptions(severity = ExceptionQueueSeverity.OPS)
        assertTrue(items.any { it.message == "bad data" || it.reasonCode == "REJECTED" })
    }

    @Test
    fun `demo exceptions endpoint returns queue items`() {
        val correlationId = UUID.randomUUID()
        eventStoreRepository.append(ruleException(correlationId, RuleSeverity.ERROR, "AIFMD_LOAN_CONCENTRATION_BREACH"))

        mockMvc.perform(get("/demo/exceptions").param("correlationId", correlationId.toString()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].severity").value("BLOCKING"))
    }

    private fun ruleException(
        correlationId: UUID,
        severity: RuleSeverity,
        reasonCode: String
    ): RuleExceptionRaisedEvent {
        return RuleExceptionRaisedEvent(
            metadata = EventMetadata(
                eventId = UUID.randomUUID(),
                correlationId = correlationId,
                sourceSystem = "integration-test",
                occurredAt = Instant.now(),
                schemaVersion = "rules.step3.exception.v1",
                regimes = setOf(RegulatoryRegime.AIFMD_II)
            ),
            artifactId = UUID.randomUUID(),
            recordType = CanonicalRecordType.POSITION,
            recordIndex = 1,
            rulePackVersion = RulePackVersion("step3-aifmd-annex-iv-calcs", "2026.04.30", Instant.parse("2026-04-28T00:00:00Z")),
            exception = RuleExceptionEnvelope(
                exceptionId = UUID.randomUUID(),
                correlationId = correlationId,
                eventId = UUID.randomUUID(),
                regime = RegulatoryRegime.AIFMD_II,
                ruleId = "aifmd.rule",
                severity = severity,
                rejectionCategory = "CALCULATION",
                reasonCode = reasonCode,
                message = reasonCode,
                remediationHint = "fix"
            )
        )
    }
}
