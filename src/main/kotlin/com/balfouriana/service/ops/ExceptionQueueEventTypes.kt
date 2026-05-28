package com.balfouriana.service.ops

object ExceptionQueueEventTypes {
    val ALL = listOf(
        "ValidationExceptionRaisedEvent",
        "RuleExceptionRaisedEvent",
        "FilingGenerationFailedEvent",
        "FilingSubmissionFailedEvent",
        "FilingAcknowledgementReceivedEvent"
    )

    val RESOLUTION = listOf(
        "ExceptionResolvedEvent",
        "ExceptionResubmitRequestedEvent"
    )
}
