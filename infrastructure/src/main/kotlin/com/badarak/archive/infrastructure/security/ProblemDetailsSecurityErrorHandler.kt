package com.badarak.archive.infrastructure.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

/**
 * Answers 401 and 403 as Problem Details documents, like the rest of the API. The standard bearer token handlers
 * still set the status and the WWW-Authenticate header.
 */
@Component
class ProblemDetailsSecurityErrorHandler(private val jsonMapper: JsonMapper) :
    AuthenticationEntryPoint,
    AccessDeniedHandler {

    private val bearerEntryPoint = BearerTokenAuthenticationEntryPoint()
    private val bearerAccessDeniedHandler = BearerTokenAccessDeniedHandler()

    override fun commence(request: HttpServletRequest, response: HttpServletResponse, authException: AuthenticationException) {
        bearerEntryPoint.commence(request, response, authException)
        writeProblem(request, response, HttpStatus.UNAUTHORIZED, "unauthenticated", "A valid bearer token is required")
    }

    override fun handle(request: HttpServletRequest, response: HttpServletResponse, accessDeniedException: AccessDeniedException) {
        bearerAccessDeniedHandler.handle(request, response, accessDeniedException)
        writeProblem(request, response, HttpStatus.FORBIDDEN, "access-denied", "The access token does not grant access to this resource")
    }

    private fun writeProblem(request: HttpServletRequest, response: HttpServletResponse, status: HttpStatus, code: String, detail: String) {
        val problem = linkedMapOf(
            "type" to "about:blank",
            "title" to status.reasonPhrase,
            "status" to status.value(),
            "detail" to detail,
            "instance" to request.requestURI,
            "code" to code,
        )
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        jsonMapper.writeValue(response.outputStream, problem)
    }
}
