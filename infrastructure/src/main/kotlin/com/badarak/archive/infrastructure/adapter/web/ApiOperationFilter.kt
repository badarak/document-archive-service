package com.badarak.archive.infrastructure.adapter.web

import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * Observes each operation of the document API until its answer is complete, the streaming of a download included. It
 * runs before Spring Security, so that a refused token counts too.
 *
 * - **Log**: one line per operation, with the operation, the document, the HTTP status, the outcome, the error code and
 * the duration as key-value pairs. The other lines of the request share its `traceId` with it. Nothing else from the
 * request is logged, never the content, the token, the file name nor the business reference; the tenant only when
 * [OperationLogProperties.includeTenant] allows it. The line is an ERROR for a server error, a WARN for the client errors
 * that point to a caller to fix (401, 403, 413, 415), and an INFO otherwise.
 * - **Metrics**: an observation named after the operation (`archive.upload`, `archive.download`…), hence a timer that
 * counts and times the operations, tagged with their `outcome` and `error.code` only: never an identifier.
 */
@Component
@Order(SecurityFilterProperties.DEFAULT_FILTER_ORDER - 1)
@EnableConfigurationProperties(OperationLogProperties::class)
class ApiOperationFilter(private val properties: OperationLogProperties, private val observations: ObservationRegistry) :
    OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val operation = ApiOperation.of(request) ?: return chain.doFilter(request, response)
        val start = System.nanoTime()
        val observation = Observation.createNotStarted("archive.${operation.name.lowercase()}", observations).start()
        var failure: Throwable? = null
        try {
            observation.openScope().use { chain.doFilter(request, response) }
        } catch (e: Throwable) {
            failure = e
            observation.error(e)
            throw e
        } finally {
            val durationMs = (System.nanoTime() - start) / 1_000_000
            val outcome = OperationOutcome.of(response.status, failure)
            val errorCode = errorCode(request, outcome)
            observation.lowCardinalityKeyValue("outcome", outcome.value)
                .lowCardinalityKeyValue("error.code", errorCode ?: NONE)
                .stop()
            log(operation, request, response, outcome, errorCode, durationMs)
        }
    }

    private fun log(
        operation: ApiOperation,
        request: HttpServletRequest,
        response: HttpServletResponse,
        outcome: OperationOutcome,
        errorCode: String?,
        durationMs: Long,
    ) {
        val status = response.status
        val documentId = operation.documentId(request) ?: createdDocument(response)
        val tenant = request.getAttribute(TENANT).takeIf { properties.includeTenant }
        log.atLevel(level(status, outcome))
            .addKeyValue("operation", operation.name)
            .apply { documentId?.let { addKeyValue("documentId", it) } }
            .addKeyValue("status", status)
            .addKeyValue("outcome", outcome.value)
            .addKeyValue("durationMs", durationMs)
            .apply { errorCode?.let { addKeyValue("errorCode", it) } }
            .apply { tenant?.let { addKeyValue("tenantId", it) } }
            .log("{} answered {}{} in {} ms", operation, status, errorCode?.let { " ($it)" }.orEmpty(), durationMs)
    }

    private fun level(status: Int, outcome: OperationOutcome): Level = when {
        outcome == OperationOutcome.SERVER_ERROR -> Level.ERROR
        HttpStatus.resolve(status) in CALLER_TO_FIX -> Level.WARN
        else -> Level.INFO
    }

    private fun errorCode(request: HttpServletRequest, outcome: OperationOutcome): String? =
        request.getAttribute(ERROR_CODE) as String? ?: INTERNAL_ERROR.takeIf { outcome == OperationOutcome.SERVER_ERROR }

    private fun createdDocument(response: HttpServletResponse): UUID? =
        response.getHeader(HttpHeaders.LOCATION)?.substringAfterLast('/')?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    companion object {
        /** Request attribute holding the `code` of the error answer, set by whoever writes it. */
        const val ERROR_CODE = "archive.errorCode"

        /** Request attribute holding the tenant of the caller, set once the access token is read. */
        const val TENANT = "archive.tenantId"

        private const val INTERNAL_ERROR = "internal-error"
        private const val NONE = "none"
        private val CALLER_TO_FIX = setOf(
            HttpStatus.UNAUTHORIZED,
            HttpStatus.FORBIDDEN,
            HttpStatus.CONTENT_TOO_LARGE,
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        )
    }
}

/** How an operation ended, from its HTTP status, or as a server error when it failed after its answer started. */
enum class OperationOutcome(val value: String) {
    SUCCESS("success"),
    CLIENT_ERROR("client-error"),
    SERVER_ERROR("server-error"),
    ;

    companion object {
        fun of(status: Int, failure: Throwable?): OperationOutcome = when {
            failure != null || status >= 500 -> SERVER_ERROR
            status >= 400 -> CLIENT_ERROR
            else -> SUCCESS
        }
    }
}
