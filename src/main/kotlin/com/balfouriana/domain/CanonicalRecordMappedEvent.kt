package com.balfouriana.domain

import java.util.UUID

data class CanonicalRecordMappedEvent(
    override val metadata: EventMetadata,
    val artifactId: UUID,
    val envelope: SourceRecordEnvelope,
    val recordType: CanonicalRecordType,
    val canonicalFields: Map<String, String>
) : DomainEvent
