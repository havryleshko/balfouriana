package com.balfouriana.service.rules

import com.balfouriana.domain.CanonicalRecordValidatedEvent
import com.balfouriana.domain.CalculationMethodRef
import com.balfouriana.domain.RegulatoryRegime
import com.balfouriana.domain.RuleEvaluationResult
import com.balfouriana.domain.RulePackVersion

fun interface Step3Rule {
    fun evaluate(event: CanonicalRecordValidatedEvent): RuleEvaluationResult
}

data class CalculationOutput(
    val methodRef: CalculationMethodRef,
    val calculatedFields: Map<String, String>,
    val metadata: Map<String, String>
)

fun interface Step3Calculation {
    fun apply(event: CanonicalRecordValidatedEvent): CalculationOutput
}

data class RulePack(
    val version: RulePackVersion,
    val regime: RegulatoryRegime?,
    val rules: List<Step3Rule>,
    val calculations: List<Step3Calculation>
)
