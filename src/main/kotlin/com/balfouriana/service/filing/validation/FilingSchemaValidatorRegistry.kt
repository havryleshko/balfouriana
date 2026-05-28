package com.balfouriana.service.filing.validation

import com.balfouriana.config.FilingStep4Properties
import com.balfouriana.service.filing.RenderedFiling
import org.springframework.stereotype.Component

@Component
class FilingSchemaValidatorRegistry(
    private val validators: List<FilingSchemaValidator>,
    private val filingStep4Properties: FilingStep4Properties
) {
    fun validate(rendered: RenderedFiling): FilingSchemaValidationResult? {
        if (!filingStep4Properties.schemaValidation.enabled) {
            return null
        }
        val validator = validators.firstOrNull {
            it.supports(rendered.templateId, rendered.outputFormat)
        } ?: return null
        return validator.validate(rendered.payload, rendered.templateVersion)
    }
}
