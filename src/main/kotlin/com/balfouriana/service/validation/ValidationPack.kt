package com.balfouriana.service.validation

import com.balfouriana.domain.CanonicalRecordMappedEvent
import com.balfouriana.domain.RegulatoryRegime
import com.balfouriana.domain.ValidationPackVersion
import com.balfouriana.domain.ValidationRuleResult

fun interface ValidationRule {
    fun evaluate(event: CanonicalRecordMappedEvent): ValidationRuleResult
}

data class ValidationPack(
    val version: ValidationPackVersion,
    val regime: RegulatoryRegime?,
    val rules: List<ValidationRule>
)
