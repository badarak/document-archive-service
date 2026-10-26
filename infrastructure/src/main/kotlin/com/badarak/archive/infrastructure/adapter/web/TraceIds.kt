package com.badarak.archive.infrastructure.adapter.web

import org.slf4j.MDC
import org.springframework.http.ProblemDetail

/**
 * Adds the trace of the current request to an error answer, as its `traceId` property: the identifier Micrometer Tracing
 * puts in the MDC for the logs, so that the answer and the log lines of a request share it. Nothing is added outside a
 * trace.
 */
fun ProblemDetail.withTraceId() {
    MDC.get(TRACE_ID)?.let { setProperty(TRACE_ID, it) }
}

private const val TRACE_ID = "traceId"
