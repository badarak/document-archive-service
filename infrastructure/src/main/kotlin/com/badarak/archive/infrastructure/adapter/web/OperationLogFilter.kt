package com.badarak.archive.infrastructure.adapter.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.slf4j.event.Level
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * Writes one log line per operation of the document API once its answer is complete, the streaming of a download
 * included: the HTTP status, the outcome, the error code and the duration, as key-value pairs for the structured logs. The
 * operation and the document are in the MDC for the whole request, so that every other line it logs carries them too. It
 * runs before Spring Security, so that a refused token is logged as well.
 *
 * Nothing else from the request is logged, never the content, the token, the file name nor the business reference; the
 * tenant only when [OperationLogProperties.includeTenant] allows it.
 */
@Component
@Order(SecurityFilterProperties.DEFAULT_FILTER_ORDER - 1)
@EnableConfigurationProperties(OperationLogProperties::class)
class OperationLogFilter(private val properties: OperationLogProperties) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val operation = ApiOperation.of(request) ?: return chain.doFilter(request, response)
        val start = System.nanoTime()
        val documentId = operation.documentId(request)
        MDC.put(OPERATION, operation.name)
        documentId?.let { MDC.put(DOCUMENT_ID, it.toString()) }
        var failure: Throwable? = null
        try {
            chain.doFilter(request, response)
        } catch (e: Throwable) {
            failure = e
            throw e
        } finally {
            val durationMs = (System.nanoTime() - start) / 1_000_000
            if (documentId == null) createdDocument(response)?.let { MDC.put(DOCUMENT_ID, it.toString()) }
            log(operation, request, response, durationMs, failure)
            MDC.remove(OPERATION)
            MDC.remove(DOCUMENT_ID)
        }
    }

    private fun log(
        operation: ApiOperation,
        request: HttpServletRequest,
        response: HttpServletResponse,
        durationMs: Long,
        failure: Throwable?,
    ) {
        val outcome = OperationOutcome.of(response.status, failure)
        val errorCode = request.getAttribute(ERROR_CODE) as String? ?: INTERNAL_ERROR.takeIf { outcome == OperationOutcome.SERVER_ERROR }
        val tenant = request.getAttribute(TENANT).takeIf { properties.includeTenant }
        log.atLevel(if (outcome == OperationOutcome.SERVER_ERROR) Level.ERROR else Level.INFO)
            .addKeyValue("status", response.status)
            .addKeyValue("outcome", outcome.value)
            .addKeyValue("durationMs", durationMs)
            .apply { errorCode?.let { addKeyValue("errorCode", it) } }
            .apply { tenant?.let { addKeyValue("tenantId", it) } }
            .log("{} answered {}{} in {} ms", operation, response.status, errorCode?.let { " ($it)" }.orEmpty(), durationMs)
    }

    private fun createdDocument(response: HttpServletResponse): UUID? =
        response.getHeader(HttpHeaders.LOCATION)?.substringAfterLast('/')?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    companion object {
        /** Request attribute holding the `code` of the error answer, set by whoever writes it. */
        const val ERROR_CODE = "archive.errorCode"

        /** Request attribute holding the tenant of the caller, set once the access token is read. */
        const val TENANT = "archive.tenantId"

        private const val OPERATION = "operation"
        private const val DOCUMENT_ID = "documentId"
        private const val INTERNAL_ERROR = "internal-error"
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
