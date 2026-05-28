package com.balfouriana.service.filing

import com.balfouriana.domain.CanonicalRecordType
import com.balfouriana.domain.EventMetadata
import com.balfouriana.domain.FilingSubmittedEvent
import com.balfouriana.domain.RegulatoryRegime
import com.balfouriana.repository.EventStoreRepository
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

@SpringBootTest
@ActiveProfiles("local")
class AcknowledgementDropZonePollerIntegrationTest {
    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var eventStoreRepository: EventStoreRepository

    companion object {
        private val ackRoot: Path = run {
            val p = Files.createTempDirectory("ack-drop")
            Files.createDirectories(p.resolve("incoming"))
            p
        }

        @JvmStatic
        @DynamicPropertySource
        fun registerProps(registry: DynamicPropertyRegistry) {
            registry.add("balfouriana.filing.step4.acknowledgement.drop-zone.root") { ackRoot.toString() }
            registry.add("balfouriana.filing.step4.acknowledgement.drop-zone.enabled") { "true" }
            registry.add("balfouriana.filing.step4.acknowledgement.drop-zone.poll-interval-ms") { "100" }
            registry.add("balfouriana.filing.step4.acknowledgement.drop-zone.stability-check-ms") { "0" }
        }
    }

    @Test
    fun `ack drop zone picks up csv and links to submission`() {
        val submissionId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        eventStoreRepository.append(
            FilingSubmittedEvent(
                metadata = EventMetadata(
                    eventId = UUID.randomUUID(),
                    correlationId = correlationId,
                    sourceSystem = "integration-test",
                    occurredAt = Instant.parse("2026-05-01T12:00:00Z"),
                    schemaVersion = "filing.step4.submitted.v1",
                    regimes = setOf(RegulatoryRegime.MIFID_II)
                ),
                artifactId = UUID.randomUUID(),
                submissionId = submissionId,
                sourceFilingGeneratedEventId = UUID.randomUUID(),
                recordType = CanonicalRecordType.TRADE,
                channel = "LOCAL_OUTBOX",
                outputFileName = "x.xml",
                outputChecksumSha256 = "abc",
                remotePath = "file:///out/x.xml"
            )
        )
        val incoming = ackRoot.resolve("incoming").resolve("ack-${UUID.randomUUID()}.csv")
        Files.writeString(
            incoming,
            "external_reference,status,reason_code,message\n$submissionId,ACK,,accepted"
        )
        val before = jdbcTemplate.queryForObject(
            "select count(*) from event_store where event_type = 'FilingAcknowledgementReceivedEvent'",
            Int::class.java
        ) ?: 0
        var after = before
        var waited = 0
        while (waited < 15_000 && after <= before) {
            Thread.sleep(200)
            waited += 200
            after = jdbcTemplate.queryForObject(
                "select count(*) from event_store where event_type = 'FilingAcknowledgementReceivedEvent'",
                Int::class.java
            ) ?: 0
        }
        assertTrue(after > before, "expected FilingAcknowledgementReceivedEvent within timeout")
        val linked = eventStoreRepository.findByCorrelationId(correlationId)
        assertTrue(linked.any { it.eventType == "FilingAcknowledgementReceivedEvent" })
    }
}
