package com.balfouriana.service.validation

import com.balfouriana.domain.AuditExportBundle
import com.balfouriana.domain.AuditExportFilters
import com.balfouriana.domain.ExceptionQueueItem
import com.balfouriana.domain.ExceptionQueueSeverity
import com.balfouriana.domain.ExceptionResolutionType
import com.balfouriana.domain.ExceptionSourceStep
import com.balfouriana.domain.ExceptionResolvedEvent
import com.balfouriana.repository.EventStoreRepository
import com.balfouriana.repository.PersistedEventRecord
import com.balfouriana.service.audit.AuditExportService
import com.balfouriana.service.ops.ExceptionQueueQueryService
import com.balfouriana.service.ops.ExceptionResolutionService
import com.balfouriana.service.ops.ExceptionResubmitResult
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class ValidationAuditQueryService(
    private val eventStoreRepository: EventStoreRepository,
    private val exceptionQueueQueryService: ExceptionQueueQueryService,
    private val exceptionResolutionService: ExceptionResolutionService,
    private val auditExportService: AuditExportService
) {
    fun decisionChainByCorrelationId(correlationId: UUID): List<PersistedEventRecord> {
        return auditExportService.decisionChainByCorrelationId(correlationId)
    }

    fun unresolvedAcknowledgements(limit: Int = 100): List<PersistedEventRecord> {
        return eventStoreRepository.findUnresolvedAcknowledgements(limit)
    }

    fun openExceptions(
        limit: Int? = null,
        since: Instant? = null,
        severity: ExceptionQueueSeverity? = null,
        sourceStep: ExceptionSourceStep? = null,
        correlationId: UUID? = null,
        includeResolved: Boolean = false
    ): List<ExceptionQueueItem> {
        return exceptionQueueQueryService.openExceptions(
            limit = limit,
            since = since,
            severity = severity,
            sourceStep = sourceStep,
            correlationId = correlationId,
            includeResolved = includeResolved
        )
    }

    fun resolveException(
        queueItemId: UUID,
        resolutionType: ExceptionResolutionType,
        note: String?,
        resolvedBy: String
    ): ExceptionResolvedEvent {
        return exceptionResolutionService.resolve(queueItemId, resolutionType, note, resolvedBy)
    }

    fun resubmitException(
        queueItemId: UUID,
        note: String?,
        resolvedBy: String
    ): ExceptionResubmitResult {
        return exceptionResolutionService.resubmitFiling(queueItemId, resolvedBy, note)
    }

    fun exportAudit(filters: AuditExportFilters): AuditExportBundle {
        return auditExportService.export(filters)
    }
}
