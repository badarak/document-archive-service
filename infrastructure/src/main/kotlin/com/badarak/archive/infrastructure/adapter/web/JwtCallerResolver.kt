package com.badarak.archive.infrastructure.adapter.web

import com.badarak.archive.domain.model.TenantId
import com.badarak.archive.domain.port.inbound.AuthenticatedCaller
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.core.MethodParameter
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

/**
 * Identifies the caller of a request from its access token, and hands it to any controller parameter of type
 * [AuthenticatedCaller]: no endpoint can forget to read the tenant from the token. A token without the tenant or source
 * claim is refused with [MissingCallerClaimException].
 */
@Component
@EnableConfigurationProperties(CallerClaimsProperties::class)
class JwtCallerResolver(private val claims: CallerClaimsProperties) : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter): Boolean = parameter.parameterType == AuthenticatedCaller::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): AuthenticatedCaller {
        val authentication = webRequest.userPrincipal as? JwtAuthenticationToken
            ?: throw IllegalStateException("The request reached a document endpoint without a JWT authentication")
        return resolve(authentication.token).also {
            webRequest.setAttribute(ApiOperationFilter.TENANT, it.tenantId.value, RequestAttributes.SCOPE_REQUEST)
        }
    }

    fun resolve(jwt: Jwt): AuthenticatedCaller = AuthenticatedCaller(
        tenantId = TenantId(requiredClaim(jwt, claims.tenant)),
        source = requiredClaim(jwt, claims.source),
    )

    private fun requiredClaim(jwt: Jwt, name: String): String =
        jwt.getClaimAsString(name)?.takeIf { it.isNotBlank() } ?: throw MissingCallerClaimException(name)
}

class MissingCallerClaimException(val claim: String) : RuntimeException("The access token has no '$claim' claim")
