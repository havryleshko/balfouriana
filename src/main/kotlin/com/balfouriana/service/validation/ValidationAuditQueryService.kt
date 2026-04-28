package com.balfouriana.service.validation

import com.balfouriana.repository.EventStoreRepository
import com.balfouriana.repository.PersistedEventRecord
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ValidationAuditQueryService(
    private val eventStoreRepository: EventStoreRepository
) {
    fun decisionChainByCorrelationId(correlationId: UUID): List<PersistedEventRecord> {
        return eventStoreRepository.findByCorrelationId(correlationId)
            .filter {
                it.eventType == "ValidationDecisionEvent" ||
                    it.eventType == "ValidationExceptionRaisedEvent" ||
                    it.eventType == "CanonicalRecordValidatedEvent" ||
                    it.eventType == "RuleDecisionEvent" ||
                    it.eventType == "RuleExceptionRaisedEvent" ||
                    it.eventType == "CalculationAppliedEvent" ||
                    it.eventType == "FilingReadyRecordEvent"
            }
    }
}
