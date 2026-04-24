package com.balfouriana.domain

import java.util.UUID

data class ParseRecordRejectedEvent(
    override val metadata: EventMetadata,
    val artifactId: UUID,
    val envelope: SourceRecordEnvelope,
    val code: ParseRejectionCode,
    val reason: String,
    val rawRecord: String?
) : DomainEvent
