package com.badarak.archive.infrastructure.adapter.web

import jakarta.servlet.http.HttpServletRequest
import java.util.UUID

/** The operations of the document API, recognised from the method and the path of a request, for the logs and the metrics. */
enum class ApiOperation(private val method: String, private val path: Regex) {
    UPLOAD("POST", Regex("${DocumentApi.PATH}/?")),
    SEARCH("GET", Regex("${DocumentApi.PATH}/?")),
    READ("GET", Regex("${DocumentApi.PATH}/([^/]+)")),
    DOWNLOAD("GET", Regex("${DocumentApi.PATH}/([^/]+)/content")),
    DELETE("DELETE", Regex("${DocumentApi.PATH}/([^/]+)")),
    ;

    /** The document named in the path of [request], if it holds one that is a valid identifier. */
    fun documentId(request: HttpServletRequest): UUID? =
        path.matchEntire(pathOf(request))?.groupValues?.getOrNull(1)?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    companion object {

        fun of(request: HttpServletRequest): ApiOperation? =
            entries.firstOrNull { it.method == request.method && it.path.matches(pathOf(request)) }

        private fun pathOf(request: HttpServletRequest): String = request.requestURI.removePrefix(request.contextPath)
    }
}
