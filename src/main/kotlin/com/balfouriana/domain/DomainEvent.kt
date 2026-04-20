package com.balfouriana.domain

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = IngestionAcceptedEvent::class, name = "ingestionAccepted"),
    JsonSubTypes.Type(value = PipelineCheckpointEvent::class, name = "pipelineCheckpoint")
)
sealed interface DomainEvent {
    val metadata: EventMetadata
}
