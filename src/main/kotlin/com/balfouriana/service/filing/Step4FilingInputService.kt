package com.balfouriana.service.filing

import com.balfouriana.domain.DomainEvent
import com.balfouriana.domain.FilingReadyRecordEvent
import com.balfouriana.repository.EventStoreRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class Step4FilingInputService(
    private val eventStoreRepository: EventStoreRepository,
    private val objectMapper: ObjectMapper
) {
    fun filingReadyByCorrelationId(correlationId: UUID): List<FilingReadyRecordEvent> {
        return eventStoreRepository.findByCorrelationId(correlationId)
            .filter { it.eventType == "FilingReadyRecordEvent" }
            .map { objectMapper.readValue<DomainEvent>(it.payload) as FilingReadyRecordEvent }
    }

    fun filingReadyByOccurredAtWindow(startInclusive: Instant, endExclusive: Instant): List<FilingReadyRecordEvent> {
        return eventStoreRepository.findByOccurredAtBetween(startInclusive, endExclusive)
            .filter { it.eventType == "FilingReadyRecordEvent" }
            .map { objectMapper.readValue<DomainEvent>(it.payload) as FilingReadyRecordEvent }
    }
}
