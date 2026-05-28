package com.balfouriana.service.ops

import com.balfouriana.config.OpsProperties
import com.balfouriana.domain.ExceptionQueueItem
import com.balfouriana.domain.ExceptionQueueSeverity
import com.balfouriana.domain.ExceptionSourceStep
import com.balfouriana.repository.EventStoreRepository
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class ExceptionQueueQueryService(
    private val eventStoreRepository: EventStoreRepository,
    private val exceptionQueueMapper: ExceptionQueueMapper,
    private val opsProperties: OpsProperties
) {
    fun openExceptions(
        limit: Int? = null,
        since: Instant? = null,
        severity: ExceptionQueueSeverity? = null,
        sourceStep: ExceptionSourceStep? = null,
        correlationId: UUID? = null,
        includeResolved: Boolean = false
    ): List<ExceptionQueueItem> {
        val effectiveLimit = limit ?: opsProperties.exceptionQueue.defaultLimit
        val effectiveSince = since ?: Instant.now().minus(opsProperties.exceptionQueue.lookbackHours, ChronoUnit.HOURS)
        val fetchLimit = if (correlationId != null) effectiveLimit * 10 else effectiveLimit * 3
        val records = eventStoreRepository.findByEventTypesSince(
            eventTypes = ExceptionQueueEventTypes.ALL,
            sinceInclusive = effectiveSince,
            limit = fetchLimit.coerceAtLeast(effectiveLimit)
        )
        val items = records
            .mapNotNull { exceptionQueueMapper.map(it) }
            .distinctBy { it.queueItemId }
        val resolvedIds = eventStoreRepository.findResolvedQueueItemIds(items.map { it.queueItemId })
        val projected = if (includeResolved) {
            items.map { item ->
                if (item.queueItemId in resolvedIds) {
                    exceptionQueueMapper.enrichWithResolution(
                        item,
                        eventStoreRepository.findLatestResolutionByQueueItemId(item.queueItemId)
                    )
                } else {
                    item
                }
            }
        } else {
            items.filter { it.queueItemId !in resolvedIds }
        }
        return projected
            .filter { item ->
                (severity == null || item.severity == severity) &&
                    (sourceStep == null || item.sourceStep == sourceStep) &&
                    (correlationId == null || item.correlationId == correlationId)
            }
            .sortedByDescending { it.occurredAt }
            .take(effectiveLimit)
    }

    fun findQueueItemById(queueItemId: UUID): ExceptionQueueItem? {
        for (eventType in ExceptionQueueEventTypes.ALL) {
            val match = eventStoreRepository.findByEventType(eventType)
                .mapNotNull { exceptionQueueMapper.map(it) }
                .firstOrNull { it.queueItemId == queueItemId }
            if (match != null) {
                val resolution = eventStoreRepository.findLatestResolutionByQueueItemId(queueItemId)
                return exceptionQueueMapper.enrichWithResolution(match, resolution)
            }
        }
        return null
    }
}
