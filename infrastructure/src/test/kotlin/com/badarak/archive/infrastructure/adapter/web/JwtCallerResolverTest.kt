package com.badarak.archive.infrastructure.adapter.web

import com.badarak.archive.domain.model.InvalidTenantIdException
import com.badarak.archive.domain.model.TenantId
import com.badarak.archive.domain.port.inbound.AuthenticatedCaller
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.Jwt

class JwtCallerResolverTest {

    private val resolver = JwtCallerResolver(CallerClaimsProperties(tenant = "tenant_id", source = "azp"))

    @Test
    fun `identifies the caller from the tenant and source claims`() {
        val caller = resolver.resolve(jwt("tenant_id" to "acme", "azp" to "billing-service"))

        assertEquals(AuthenticatedCaller(TenantId("acme"), "billing-service"), caller)
    }

    @Test
    fun `reads the claims named in the configuration`() {
        val custom = JwtCallerResolver(CallerClaimsProperties(tenant = "org", source = "client_id"))

        val caller = custom.resolve(jwt("org" to "acme", "client_id" to "billing-service"))

        assertEquals(AuthenticatedCaller(TenantId("acme"), "billing-service"), caller)
    }

    @Test
    fun `rejects a token without tenant`() {
        val exception = assertThrows(MissingCallerClaimException::class.java) { resolver.resolve(jwt("azp" to "billing-service")) }

        assertEquals("tenant_id", exception.claim)
    }

    @Test
    fun `rejects a token whose tenant is blank`() {
        assertThrows(MissingCallerClaimException::class.java) { resolver.resolve(jwt("tenant_id" to " ", "azp" to "billing-service")) }
    }

    @Test
    fun `rejects a token without source`() {
        val exception = assertThrows(MissingCallerClaimException::class.java) { resolver.resolve(jwt("tenant_id" to "acme")) }

        assertEquals("azp", exception.claim)
    }

    @Test
    fun `rejects a tenant the domain does not accept`() {
        val tooLong = "t".repeat(TenantId.MAX_LENGTH + 1)

        assertThrows(InvalidTenantIdException::class.java) { resolver.resolve(jwt("tenant_id" to tooLong, "azp" to "billing-service")) }
    }

    private fun jwt(vararg claims: Pair<String, String>): Jwt = Jwt.withTokenValue("token")
        .header("alg", "RS256")
        .subject("service-account")
        .apply { claims.forEach { (name, value) -> claim(name, value) } }
        .build()
}
