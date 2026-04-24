package com.balfouriana.domain

enum class ParseRejectionCode {
    UNSUPPORTED_FORMAT,
    MALFORMED_RECORD,
    MISSING_REQUIRED_FIELD,
    INVALID_DATE_FORMAT,
    INVALID_NUMBER_FORMAT,
    MAPPING_FAILED
}
