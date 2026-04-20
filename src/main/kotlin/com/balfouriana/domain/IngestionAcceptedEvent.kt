package com.balfouriana.domain

data class IngestionAcceptedEvent(
    override val metadata: EventMetadata,
    val fileName: String,
    val payloadChecksum: String
) : DomainEvent
