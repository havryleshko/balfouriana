package com.balfouriana.service.validation

import com.balfouriana.domain.CanonicalRecordMappedEvent
import com.balfouriana.domain.ValidationLayer
import com.balfouriana.domain.ValidationOutcome
import com.balfouriana.domain.ValidationRuleResult
import com.balfouriana.domain.ValidationSeverity
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeParseException

class RequiredFieldRule(
    private val ruleId: String,
    private val field: String
) : ValidationRule {
    override fun evaluate(event: CanonicalRecordMappedEvent): ValidationRuleResult {
        val value = event.canonicalFields[field].orEmpty()
        if (value.isNotBlank()) {
            return ValidationRuleResult(
                ruleId = ruleId,
                layer = ValidationLayer.SCHEMA,
                outcome = ValidationOutcome.PASS,
                reasonCode = "OK",
                message = "Field present",
                severity = ValidationSeverity.WARNING
            )
        }
        return ValidationRuleResult(
            ruleId = ruleId,
            layer = ValidationLayer.SCHEMA,
            outcome = ValidationOutcome.FAIL,
            reasonCode = "MISSING_REQUIRED_FIELD",
            message = "Missing required field: $field",
            severity = ValidationSeverity.ERROR
        )
    }
}

class UppercaseCurrencyRule : ValidationRule {
    override fun evaluate(event: CanonicalRecordMappedEvent): ValidationRuleResult {
        val currency = event.canonicalFields["currency"].orEmpty()
        if (currency.isBlank()) {
            return ValidationRuleResult(
                ruleId = "currency.present",
                layer = ValidationLayer.BUSINESS,
                outcome = ValidationOutcome.FAIL,
                reasonCode = "MISSING_CURRENCY",
                message = "Currency is required",
                severity = ValidationSeverity.ERROR
            )
        }
        if (currency.matches(Regex("^[A-Z]{3}$"))) {
            return ValidationRuleResult(
                ruleId = "currency.uppercase_iso",
                layer = ValidationLayer.BUSINESS,
                outcome = ValidationOutcome.PASS,
                reasonCode = "OK",
                message = "Currency format valid",
                severity = ValidationSeverity.WARNING
            )
        }
        return ValidationRuleResult(
            ruleId = "currency.uppercase_iso",
            layer = ValidationLayer.BUSINESS,
            outcome = ValidationOutcome.FAIL,
            reasonCode = "INVALID_CURRENCY_FORMAT",
            message = "Currency must be 3 uppercase letters",
            severity = ValidationSeverity.ERROR
        )
    }
}

class PositiveNumberRule(
    private val ruleId: String,
    private val field: String
) : ValidationRule {
    override fun evaluate(event: CanonicalRecordMappedEvent): ValidationRuleResult {
        val value = event.canonicalFields[field].orEmpty()
        if (value.isBlank()) {
            return ValidationRuleResult(
                ruleId = ruleId,
                layer = ValidationLayer.BUSINESS,
                outcome = ValidationOutcome.NEEDS_REVIEW,
                reasonCode = "MISSING_OPTIONAL_NUMERIC",
                message = "Field is blank: $field",
                severity = ValidationSeverity.WARNING
            )
        }
        val numeric = value.toBigDecimalOrNull()
        if (numeric == null) {
            return ValidationRuleResult(
                ruleId = ruleId,
                layer = ValidationLayer.BUSINESS,
                outcome = ValidationOutcome.FAIL,
                reasonCode = "INVALID_NUMERIC_FORMAT",
                message = "Invalid number in field: $field",
                severity = ValidationSeverity.ERROR
            )
        }
        if (numeric > BigDecimal.ZERO) {
            return ValidationRuleResult(
                ruleId = ruleId,
                layer = ValidationLayer.BUSINESS,
                outcome = ValidationOutcome.PASS,
                reasonCode = "OK",
                message = "Numeric value is positive",
                severity = ValidationSeverity.WARNING
            )
        }
        return ValidationRuleResult(
            ruleId = ruleId,
            layer = ValidationLayer.BUSINESS,
            outcome = ValidationOutcome.FAIL,
            reasonCode = "NON_POSITIVE_VALUE",
            message = "Value must be greater than zero in field: $field",
            severity = ValidationSeverity.ERROR
        )
    }
}

class TradeDateIsoRule : ValidationRule {
    override fun evaluate(event: CanonicalRecordMappedEvent): ValidationRuleResult {
        val value = event.canonicalFields["trade_date"].orEmpty()
        if (value.isBlank()) {
            return ValidationRuleResult(
                ruleId = "trade_date.optional_iso",
                layer = ValidationLayer.BUSINESS,
                outcome = ValidationOutcome.NEEDS_REVIEW,
                reasonCode = "TRADE_DATE_MISSING",
                message = "trade_date is missing",
                severity = ValidationSeverity.WARNING
            )
        }
        return try {
            LocalDate.parse(value)
            ValidationRuleResult(
                ruleId = "trade_date.optional_iso",
                layer = ValidationLayer.BUSINESS,
                outcome = ValidationOutcome.PASS,
                reasonCode = "OK",
                message = "trade_date format valid",
                severity = ValidationSeverity.WARNING
            )
        } catch (_: DateTimeParseException) {
            ValidationRuleResult(
                ruleId = "trade_date.optional_iso",
                layer = ValidationLayer.BUSINESS,
                outcome = ValidationOutcome.FAIL,
                reasonCode = "INVALID_DATE_FORMAT",
                message = "trade_date must be ISO-8601 date",
                severity = ValidationSeverity.ERROR
            )
        }
    }
}
