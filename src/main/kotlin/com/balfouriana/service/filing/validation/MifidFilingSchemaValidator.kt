package com.balfouriana.service.filing.validation

import com.balfouriana.domain.FilingOutputFormat
import com.balfouriana.service.filing.template.MifidFilingTemplatePack
import org.springframework.stereotype.Component
import org.xml.sax.SAXParseException
import java.io.ByteArrayInputStream
import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory

@Component
class MifidFilingSchemaValidator : FilingSchemaValidator {
    private val schema by lazy { loadSchema() }

    override fun supports(templateId: String, outputFormat: FilingOutputFormat): Boolean {
        return templateId == MifidFilingTemplatePack.templateVersion.templateId &&
            outputFormat == FilingOutputFormat.XML
    }

    override fun validate(payload: String, templateVersion: String): FilingSchemaValidationResult {
        if (templateVersion != MifidFilingTemplatePack.templateVersion.version) {
            return FilingSchemaValidationResult.failure(
                listOf(
                    FilingSchemaValidationError(
                        line = null,
                        column = null,
                        message = "Unsupported template version $templateVersion for schema ${MifidFilingTemplatePack.SCHEMA_VERSION}"
                    )
                )
            )
        }
        val errors = mutableListOf<FilingSchemaValidationError>()
        val validator = schema.newValidator()
        validator.errorHandler = object : org.xml.sax.ErrorHandler {
            override fun warning(exception: SAXParseException) = Unit

            override fun error(exception: SAXParseException) {
                errors.add(toValidationError(exception))
            }

            override fun fatalError(exception: SAXParseException) {
                errors.add(toValidationError(exception))
            }
        }
        runCatching {
            validator.validate(StreamSource(ByteArrayInputStream(payload.toByteArray(Charsets.UTF_8))))
        }.onFailure { ex ->
            when (ex) {
                is SAXParseException -> errors.add(toValidationError(ex))
                else -> errors.add(
                    FilingSchemaValidationError(
                        line = null,
                        column = null,
                        message = ex.message ?: "Schema validation failed"
                    )
                )
            }
        }
        return if (errors.isEmpty()) {
            FilingSchemaValidationResult.success()
        } else {
            FilingSchemaValidationResult.failure(errors)
        }
    }

    private fun loadSchema(): javax.xml.validation.Schema {
        val factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
        val resource = requireNotNull(javaClass.classLoader.getResource(MifidFilingTemplatePack.SCHEMA_RESOURCE_PATH)) {
            "Missing schema resource ${MifidFilingTemplatePack.SCHEMA_RESOURCE_PATH}"
        }
        return factory.newSchema(resource)
    }

    private fun toValidationError(exception: SAXParseException): FilingSchemaValidationError {
        return FilingSchemaValidationError(
            line = exception.lineNumber.takeIf { it > 0 },
            column = exception.columnNumber.takeIf { it > 0 },
            message = exception.message ?: "Schema validation error"
        )
    }
}
