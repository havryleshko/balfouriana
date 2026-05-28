package com.balfouriana.service.filing.validation

import com.balfouriana.domain.FilingOutputFormat

interface FilingSchemaValidator {
    fun supports(templateId: String, outputFormat: FilingOutputFormat): Boolean
    fun validate(payload: String, templateVersion: String): FilingSchemaValidationResult
}
