package com.balfouriana.service.filing

import com.balfouriana.config.FilingStep4Properties
import com.balfouriana.domain.EventMetadata
import com.balfouriana.domain.FilingAcknowledgementReceivedEvent
import com.balfouriana.domain.FilingAcknowledgementStatus
import com.balfouriana.repository.EventStoreRepository
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class AcknowledgementIngestionService(
    private val eventStoreRepository: EventStoreRepository,
    private val filingStep4Properties: FilingStep4Properties
) {
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
        val events = lines.drop(1).mapNotNull { line ->
            val cols = line.split(",")
            if (externalIdx !in cols.indices || statusIdx !in cols.indices) {
                null
            } else {
                buildEvent(
                    sourceSystem = sourceSystem,
                    externalReference = cols[externalIdx].trim(),
                    rawStatus = cols[statusIdx].trim(),
                    reasonCode = cols.getOrNull(reasonIdx)?.trim()?.ifBlank { null },
                    message = cols.getOrNull(messageIdx)?.trim()?.ifBlank { null }
                )
            }
        }
        events.forEach { eventStoreRepository.append(it) }
        return events
    }

    fun ingestXml(payload: String, sourceSystem: String = "step4-ack-ingestion"): FilingAcknowledgementReceivedEvent? {
        val externalReference = Regex("""externalReference="([^"]+)"""").find(payload)?.groupValues?.get(1) ?: return null
        val rawStatus = Regex("""status="([^"]+)"""").find(payload)?.groupValues?.get(1) ?: "UNRESOLVED"
        val reasonCode = Regex("""reasonCode="([^"]+)"""").find(payload)?.groupValues?.get(1)
        val message = Regex("""message="([^"]+)"""").find(payload)?.groupValues?.get(1)
        val event = buildEvent(sourceSystem, externalReference, rawStatus, reasonCode, message)
        eventStoreRepository.append(event)
        return event
    }

    private fun buildEvent(
        sourceSystem: String,
        externalReference: String,
        rawStatus: String,
        reasonCode: String?,
        message: String?
    ): FilingAcknowledgementReceivedEvent {
        val submissionId = runCatching { UUID.fromString(externalReference) }.getOrNull()
        val linkedRecord = submissionId?.let { id ->
            eventStoreRepository.findByEventType("FilingSubmittedEvent")
                .lastOrNull { it.payload.contains(id.toString()) }
        }
        val status = when (rawStatus.uppercase()) {
            "ACK" -> FilingAcknowledgementStatus.ACK
            "NACK" -> FilingAcknowledgementStatus.NACK
            else -> FilingAcknowledgementStatus.UNRESOLVED
        }
        val correlationId = linkedRecord?.correlationId ?: UUID.randomUUID()
        return FilingAcknowledgementReceivedEvent(
            metadata = EventMetadata(
                eventId = UUID.randomUUID(),
                correlationId = correlationId,
                sourceSystem = sourceSystem,
                occurredAt = Instant.now(),
                schemaVersion = "filing.step4.ack.rcv.v1",
                regimes = linkedRecord?.regimes ?: emptySet()
            ),
            submissionId = submissionId,
            correlationLinked = linkedRecord != null,
            linkedCorrelationId = linkedRecord?.correlationId,
            channel = filingStep4Properties.acknowledgement.channel,
            acknowledgementStatus = status,
            externalReference = externalReference,
            reasonCode = reasonCode,
            message = message
        )
    }
}
