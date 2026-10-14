package com.badarak.archive.infrastructure.security

import com.badarak.archive.domain.model.TenantId
import com.badarak.archive.domain.port.inbound.AuthenticatedCaller
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component

@Component
@EnableConfigurationProperties(CallerClaimsProperties::class)
class JwtCallerResolver(private val claims: CallerClaimsProperties) {

    fun resolve(jwt: Jwt): AuthenticatedCaller = AuthenticatedCaller(
        tenantId = TenantId(requiredClaim(jwt, claims.tenant)),
        source = requiredClaim(jwt, claims.source),
    )

    private fun requiredClaim(jwt: Jwt, name: String): String =
        jwt.getClaimAsString(name)?.takeIf { it.isNotBlank() } ?: throw MissingCallerClaimException(name)
}

class MissingCallerClaimException(val claim: String) : RuntimeException("The access token has no '$claim' claim")
