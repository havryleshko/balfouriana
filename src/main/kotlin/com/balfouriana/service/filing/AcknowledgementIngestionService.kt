package com.balfouriana.service.filing

import com.balfouriana.config.FilingStep4Properties
import com.balfouriana.domain.EventMetadata
import com.balfouriana.domain.FilingAcknowledgementReceivedEvent
import com.balfouriana.domain.FilingAcknowledgementStatus
import com.balfouriana.domain.UNLINKED_ACK_CORRELATION_ID
import com.balfouriana.repository.EventStoreRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class AcknowledgementIngestionService(
    private val eventStoreRepository: EventStoreRepository,
    private val filingStep4Properties: FilingStep4Properties
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun ingestCsv(payload: String, sourceSystem: String = "step4-ack-ingestion"): List<FilingAcknowledgementReceivedEvent> {
        val lines = payload.trim().lines().filter { it.isNotBlank() }
        if (lines.size <= 1) {
            return emptyList()
        }
        val headers = lines.first().split(",").map { it.trim().lowercase() }
        val externalIdx = headers.indexOf("external_reference")
        val statusIdx = headers.indexOf("status")
        val reasonIdx = headers.indexOf("reason_code")
        val messageIdx = headers.indexOf("message")
        return lines.drop(1).mapNotNull { line ->
            val cols = line.split(",")
            if (externalIdx !in cols.indices || statusIdx !in cols.indices) {
                null
            } else {
                persistIfNew(
                    buildEvent(
                        sourceSystem = sourceSystem,
                        externalReference = cols[externalIdx].trim(),
                        rawStatus = cols[statusIdx].trim(),
                        reasonCode = cols.getOrNull(reasonIdx)?.trim()?.ifBlank { null },
                        message = cols.getOrNull(messageIdx)?.trim()?.ifBlank { null }
                    )
                )
            }
        }
    }

    fun ingestXml(payload: String, sourceSystem: String = "step4-ack-ingestion"): FilingAcknowledgementReceivedEvent? {
        val externalReference = Regex("""externalReference="([^"]+)"""").find(payload)?.groupValues?.get(1) ?: return null
        val rawStatus = Regex("""status="([^"]+)"""").find(payload)?.groupValues?.get(1) ?: "UNRESOLVED"
        val reasonCode = Regex("""reasonCode="([^"]+)"""").find(payload)?.groupValues?.get(1)
        val message = Regex("""message="([^"]+)"""").find(payload)?.groupValues?.get(1)
        return persistIfNew(buildEvent(sourceSystem, externalReference, rawStatus, reasonCode, message))
    }

    private fun persistIfNew(event: FilingAcknowledgementReceivedEvent): FilingAcknowledgementReceivedEvent? {
        if (isDuplicate(event)) {
            log.info(
                "skipping duplicate acknowledgement externalReference={} status={}",
                event.externalReference,
                event.acknowledgementStatus
            )
            return null
        }
        eventStoreRepository.append(event)
        return event
    }

    private fun isDuplicate(event: FilingAcknowledgementReceivedEvent): Boolean {
        val submissionId = event.submissionId
        return if (submissionId != null) {
            eventStoreRepository.hasAcknowledgementForSubmission(submissionId, event.acknowledgementStatus)
        } else {
            eventStoreRepository.hasAcknowledgementForExternalReference(
                event.externalReference,
                event.acknowledgementStatus
            )
        }
    }

    private fun buildEvent(
        sourceSystem: String,
        externalReference: String,
        rawStatus: String,
        reasonCode: String?,
        message: String?
    ): FilingAcknowledgementReceivedEvent {
        val submissionId = runCatching { UUID.fromString(externalReference) }.getOrNull()
        val submittedEvent = submissionId?.let { eventStoreRepository.findFilingSubmittedBySubmissionId(it) }
        val status = when (rawStatus.uppercase()) {
            "ACK" -> FilingAcknowledgementStatus.ACK
            "NACK" -> FilingAcknowledgementStatus.NACK
            else -> FilingAcknowledgementStatus.UNRESOLVED
        }
        val linked = submittedEvent != null
        val correlationId = submittedEvent?.metadata?.correlationId ?: UNLINKED_ACK_CORRELATION_ID
        val regimes = submittedEvent?.metadata?.regimes ?: emptySet()
        return FilingAcknowledgementReceivedEvent(
            metadata = EventMetadata(
                eventId = UUID.randomUUID(),
                correlationId = correlationId,
                sourceSystem = sourceSystem,
                occurredAt = Instant.now(),
                schemaVersion = "filing.step4.ack.rcv.v1",
                regimes = regimes
            ),
            submissionId = submissionId,
            correlationLinked = linked,
            linkedCorrelationId = submittedEvent?.metadata?.correlationId,
            channel = filingStep4Properties.acknowledgement.channel,
            acknowledgementStatus = status,
            externalReference = externalReference,
            reasonCode = reasonCode,
            message = message
        )
    }
}
