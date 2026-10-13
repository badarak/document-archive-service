package com.badarak.archive.infrastructure.security

import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ApiSecurityIT.DocumentsStubController::class)
class ApiSecurityIT {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `a request without token is rejected with 401`() {
        mockMvc.get(DOCUMENTS).andExpect {
            status { isUnauthorized() }
            header { string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")) }
        }
    }

    @Test
    fun `a token signed by a published key is accepted`() {
        getDocuments(TestIdentityProvider.token()).andExpect { status { isOk() } }
    }

    @Test
    fun `a token signed by an unknown key is rejected with 401`() {
        getDocuments(TestIdentityProvider.token(signedWithPublishedKey = false)).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `a token from another issuer is rejected with 401`() {
        getDocuments(TestIdentityProvider.token(issuer = "https://other-idp.test")).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `a token intended for another audience is rejected with 401`() {
        getDocuments(TestIdentityProvider.token(audience = listOf("billing-service"))).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `a token without audience is rejected with 401`() {
        getDocuments(TestIdentityProvider.token(audience = emptyList())).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `an expired token is rejected with 401`() {
        val expired = TestIdentityProvider.token(expiresAt = Instant.now().minusSeconds(300))

        getDocuments(expired).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `a malformed token is rejected with 401`() {
        getDocuments("not-a-jwt").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `the health endpoint needs no token`() {
        mockMvc.get("/actuator/health").andExpect { status { isOk() } }
        mockMvc.get("/actuator/health/readiness").andExpect { status { isOk() } }
    }

    private fun getDocuments(token: String): ResultActionsDsl = mockMvc.get(DOCUMENTS) {
        header(HttpHeaders.AUTHORIZATION, "Bearer $token")
    }

    /** Stands in for the document API, which does not exist yet. */
    @RestController
    @RequestMapping(DOCUMENTS)
    class DocumentsStubController {

        @GetMapping
        fun search(): String = "found"
    }

    companion object {
        private const val DOCUMENTS = "/api/v1/documents"

        @JvmStatic
        @DynamicPropertySource
        fun identityProviderProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri") { TestIdentityProvider.jwksUri }
        }
    }
}
