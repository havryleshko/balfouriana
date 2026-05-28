package com.balfouriana.service.audit

import com.balfouriana.domain.CanonicalRecordType
import com.balfouriana.domain.EventMetadata
import com.balfouriana.domain.ExceptionResolutionType
import com.balfouriana.domain.FilingAcknowledgementReceivedEvent
import com.balfouriana.domain.FilingAcknowledgementStatus
import com.balfouriana.domain.FilingReadyRecordEvent
import com.balfouriana.domain.FilingSubmittedEvent
import com.balfouriana.domain.IngestionChannel
import com.balfouriana.domain.IngestionFileFormat
import com.balfouriana.domain.RegulatoryRegime
import com.balfouriana.domain.RuleExceptionEnvelope
import com.balfouriana.domain.RuleExceptionRaisedEvent
import com.balfouriana.domain.RulePackVersion
import com.balfouriana.domain.RuleSeverity
import com.balfouriana.domain.SourceRecordEnvelope
import com.balfouriana.repository.EventStoreRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AuditExportIntegrationTest {
    @Autowired
    lateinit var eventStoreRepository: EventStoreRepository

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `resolve then export includes resolution in timeline and summary`() {
        val ruleEvent = ruleException(RuleSeverity.WARNING, "AIFMD_FUND_STRUCTURE_UNKNOWN")
        eventStoreRepository.append(ruleEvent)
        val queueItemId = ruleEvent.exception.exceptionId

        mockMvc.perform(
            post("/demo/exceptions/$queueItemId/resolve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"resolutionType":"ACKNOWLEDGED","resolvedBy":"operator"}""")
        ).andExpect(status().isOk)

        mockMvc.perform(get("/demo/audit/export").param("correlationId", ruleEvent.metadata.correlationId.toString()))
            .andExpect(status().isOk)
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
            .andExpect(jsonPath("$.summary.resolutionCount").value(1))
            .andExpect(jsonPath("$.timeline[?(@.eventType=='ExceptionResolvedEvent')]").exists())
    }

    @Test
    fun `resubmit after linked nack shows two submissions and resubmitted resolution`() {
        val correlationId = UUID.randomUUID()
        val submissionId = UUID.randomUUID()
        val ackEventId = UUID.randomUUID()
        val filingReady = filingReady(correlationId)
        eventStoreRepository.append(filingReady)
        eventStoreRepository.append(
            FilingSubmittedEvent(
                metadata = EventMetadata(
                    eventId = UUID.randomUUID(),
                    correlationId = correlationId,
                    sourceSystem = "integration-test",
                    occurredAt = Instant.parse("2026-05-01T12:00:00Z"),
                    schemaVersion = "filing.step4.sub.ok.v1",
                    regimes = setOf(RegulatoryRegime.MIFID_II)
                ),
                artifactId = filingReady.artifactId,
                submissionId = submissionId,
                sourceFilingGeneratedEventId = UUID.randomUUID(),
                recordType = CanonicalRecordType.TRADE,
                channel = "LOCAL_OUTBOX",
                outputFileName = "x.xml",
                outputChecksumSha256 = "abc",
                remotePath = "file:///out/x.xml"
            )
        )
        eventStoreRepository.append(
            FilingAcknowledgementReceivedEvent(
                metadata = EventMetadata(
                    eventId = ackEventId,
                    correlationId = correlationId,
                    sourceSystem = "integration-test",
                    occurredAt = Instant.parse("2026-05-01T12:05:00Z"),
                    schemaVersion = "filing.step4.ack.rcv.v1",
                    regimes = setOf(RegulatoryRegime.MIFID_II)
                ),
                submissionId = submissionId,
                correlationLinked = true,
                linkedCorrelationId = correlationId,
                channel = "SFTP",
                acknowledgementStatus = FilingAcknowledgementStatus.NACK,
                externalReference = submissionId.toString(),
                reasonCode = "REJECTED",
                message = "bad data"
            )
        )

        mockMvc.perform(
            post("/demo/exceptions/$ackEventId/resubmit")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"resolvedBy":"operator","note":"retry"}""")
        ).andExpect(status().isOk)

        mockMvc.perform(get("/demo/audit/export").param("correlationId", correlationId.toString()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.summary.submissionIds.length()").value(2))
            .andExpect(jsonPath("$.summary.resolutionCount").value(2))
            .andExpect(jsonPath("$.timeline[?(@.eventType == 'ExceptionResolvedEvent')]").exists())
    }

    @Test
    fun `export without filters returns bad request`() {
        mockMvc.perform(get("/demo/audit/export"))
            .andExpect(status().isBadRequest)
    }

    private fun ruleException(severity: RuleSeverity, reasonCode: String): RuleExceptionRaisedEvent {
        val correlationId = UUID.randomUUID()
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

    private fun filingReady(correlationId: UUID): FilingReadyRecordEvent {
        val now = Instant.parse("2026-05-01T10:30:00Z")
        return FilingReadyRecordEvent(
            metadata = EventMetadata(
                eventId = UUID.randomUUID(),
                correlationId = correlationId,
                sourceSystem = "integration-test",
                occurredAt = now,
                schemaVersion = "rules.step3.filing-ready.v1",
                regimes = setOf(RegulatoryRegime.MIFID_II)
            ),
            artifactId = UUID.randomUUID(),
            envelope = SourceRecordEnvelope(
                sourceId = "integration-test",
                sourceSystem = "integration-test",
                ingestionChannel = IngestionChannel.REST,
                fileId = UUID.randomUUID(),
                originalFileName = "trades.csv",
                recordIndex = 1,
                receivedAt = now,
                format = IngestionFileFormat.CSV,
                contentType = "text/csv",
                fileSizeBytes = 100,
                checksumSha256 = "abc",
                schemaHint = null
            ),
            recordType = CanonicalRecordType.TRADE,
            rulePackVersion = RulePackVersion("pack", "v1", Instant.parse("2026-04-28T00:00:00Z")),
            filingReadyFields = mapOf(
                "record_type" to "TRADE",
                "trade_id" to "T-100",
                "instrument_id" to "GB00B03MLX29",
                "trade_date" to "2026-04-22",
                "quantity" to "100",
                "price" to "10.3",
                "currency" to "GBP",
                "buyer_lei" to "5493001KJTIIGC8Y1R12",
                "seller_lei" to "213800D1EI4B9WTWWD28",
                "decision_maker_lei" to "7245008N4E6Y7Z5RAA41",
                "execution_actor_type" to "HUMAN",
                "venue_code" to "XLON",
                "otc_indicator" to "N",
                "waiver_indicator" to "N",
                "short_selling_indicator" to "N",
                "commodity_derivative_indicator" to "N",
                "price_notation" to "MONETARY",
                "price_currency" to "GBP",
                "calculated_notional" to "1030.0",
                "mifid_transaction_side" to "BUYER_SIDE",
                "mifid_execution_mode" to "HUMAN"
            ),
            traceMetadata = emptyMap(),
            inputFingerprint = "in",
            outputFingerprint = "out"
        )
    }
}
