package com.badarak.archive.infrastructure.adapter.web

import com.badarak.archive.domain.model.InvalidTenantIdException
import com.badarak.archive.domain.model.TenantId
import com.badarak.archive.domain.port.inbound.AuthenticatedCaller
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.context.request.ServletWebRequest

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

    @Test
    fun `hands the caller of a JWT authenticated request to a parameter of type AuthenticatedCaller`() {
        val servletRequest = MockHttpServletRequest()
        servletRequest.userPrincipal = JwtAuthenticationToken(jwt("tenant_id" to "acme", "azp" to "billing-service"))
        val request = ServletWebRequest(servletRequest)

        assertTrue(resolver.supportsParameter(parameter(0)))
        assertEquals(AuthenticatedCaller(TenantId("acme"), "billing-service"), resolver.resolveArgument(parameter(0), null, request, null))
    }

    @Test
    fun `leaves the other parameters to the other resolvers`() {
        assertFalse(resolver.supportsParameter(parameter(1)))
    }

    @Test
    fun `refuses a request that is not authenticated with a JWT`() {
        val request = ServletWebRequest(MockHttpServletRequest())

        assertThrows(IllegalStateException::class.java) { resolver.resolveArgument(parameter(0), null, request, null) }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun endpoint(caller: AuthenticatedCaller, id: String) = Unit

    private fun parameter(index: Int): MethodParameter {
        val endpoint = javaClass.getDeclaredMethod("endpoint", AuthenticatedCaller::class.java, String::class.java)
        return MethodParameter(endpoint, index)
    }

    private fun jwt(vararg claims: Pair<String, String>): Jwt = Jwt.withTokenValue("token")
        .header("alg", "RS256")
        .subject("service-account")
        .apply { claims.forEach { (name, value) -> claim(name, value) } }
        .build()
}
