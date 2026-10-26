package com.badarak.archive.infrastructure.adapter.web

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail

class TraceIdsTest {

    @AfterEach
    fun clearTheMdc() {
        MDC.clear()
    }

    @Test
    fun `an error answer carries the trace of the current request, the one its logs carry`() {
        MDC.put("traceId", "4bf92f3577b34da6a3ce929d0e0e4736")
        val problem = ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE)

        problem.withTraceId()

        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", problem.properties?.get("traceId"))
    }

    @Test
    fun `nothing is added outside a trace`() {
        val problem = ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE)

        problem.withTraceId()

        assertNull(problem.properties?.get("traceId"))
    }
}
