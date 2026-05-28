package com.balfouriana.service.filing.validation

data class FilingSchemaValidationError(
    val line: Int?,
    val column: Int?,
    val message: String
)

data class FilingSchemaValidationResult(
    val valid: Boolean,
    val errors: List<FilingSchemaValidationError>
) {
    companion object {
        const val REASON_CODE = "FILING_SCHEMA_VALIDATION_FAILED"

        fun success(): FilingSchemaValidationResult = FilingSchemaValidationResult(valid = true, errors = emptyList())

        fun failure(errors: List<FilingSchemaValidationError>): FilingSchemaValidationResult {
            return FilingSchemaValidationResult(valid = false, errors = errors)
        }
    }

    fun summaryMessage(maxErrors: Int = 5): String {
        if (valid) {
            return "Schema validation passed"
        }
        return errors.take(maxErrors).joinToString("; ") { error ->
            val location = when {
                error.line != null && error.column != null -> "line ${error.line} column ${error.column}: "
                error.line != null -> "line ${error.line}: "
                else -> ""
            }
            "$location${error.message}"
        }
    }
}
