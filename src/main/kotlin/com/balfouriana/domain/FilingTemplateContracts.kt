package com.balfouriana.domain

import java.time.Instant

data class FilingTemplateVersion(
    val templateId: String,
    val version: String,
    val effectiveFrom: Instant,
    val authority: String,
    val authorityRef: String,
    val publishedAt: String
)
