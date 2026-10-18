package com.badarak.archive.infrastructure.security

import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockHttpServletRequestDsl
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.Instant
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiSecurityIT {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `a request without token is rejected with 401`() {
        mockMvc.get(DOCUMENTS).andExpect {
            status { isUnauthorized() }
            header { string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")) }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.status") { value(401) }
            jsonPath("$.code") { value("unauthenticated") }
            jsonPath("$.instance") { value(DOCUMENTS) }
        }
    }

    @Test
    fun `a token signed by the authorization server is accepted`() {
        getDocuments(TestAuthorizationServer.token(scope = READ)).andExpect { status { isOk() } }
    }

    @Test
    fun `a token signed with a key the server does not publish is rejected with 401`() {
        getDocuments(TestAuthorizationServer.token(signedByServer = false)).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `a token from another issuer is rejected with 401`() {
        getDocuments(TestAuthorizationServer.token(issuer = "https://other-auth.test")).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `a token intended for another audience is rejected with 401`() {
        getDocuments(TestAuthorizationServer.token(audience = listOf("billing-service"))).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `a token without audience is rejected with 401`() {
        getDocuments(TestAuthorizationServer.token(audience = emptyList())).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `an invalid token is answered as a problem`() {
        getDocuments(TestAuthorizationServer.token(signedByServer = false)).andExpect {
            status { isUnauthorized() }
            header { string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer error=\"invalid_token\"")) }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("unauthenticated") }
        }
    }

    @Test
    fun `an expired token is rejected with 401`() {
        val expired = TestAuthorizationServer.token(expiresAt = Instant.now().minusSeconds(300))

        getDocuments(expired).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `a malformed token is rejected with 401`() {
        getDocuments("not-a-jwt").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `a valid token without scope is rejected with 403`() {
        getDocuments(TestAuthorizationServer.token()).andExpect {
            status { isForbidden() }
            header { string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")) }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.status") { value(403) }
            jsonPath("$.code") { value("access-denied") }
        }
    }

    @Test
    fun `reading documents requires the read scope`() {
        getDocuments(TestAuthorizationServer.token(scope = WRITE)).andExpect { status { isForbidden() } }
        getDocuments(TestAuthorizationServer.token(scope = "profile $READ")).andExpect { status { isOk() } }
        mockMvc.get("$DOCUMENTS/${UUID.randomUUID()}") { bearer(TestAuthorizationServer.token(scope = READ)) }
            .andExpect { status { isNotFound() } }
        mockMvc.get("$DOCUMENTS/${UUID.randomUUID()}/content") { bearer(TestAuthorizationServer.token(scope = WRITE)) }
            .andExpect { status { isForbidden() } }
        mockMvc.get("$DOCUMENTS/${UUID.randomUUID()}/content") { bearer(TestAuthorizationServer.token(scope = READ)) }
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `archiving a document requires the write scope`() {
        mockMvc.post(DOCUMENTS) { bearer(TestAuthorizationServer.token(scope = READ)) }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `a token with the write scope reaches the archiving endpoint, which then refuses a request that is not multipart`() {
        mockMvc.post(DOCUMENTS) { bearer(TestAuthorizationServer.token(scope = WRITE)) }.andExpect { status { isUnsupportedMediaType() } }
    }

    @Test
    fun `deleting a document requires the write scope`() {
        val document = "$DOCUMENTS/${UUID.randomUUID()}"

        mockMvc.delete(document) { bearer(TestAuthorizationServer.token(scope = READ)) }.andExpect { status { isForbidden() } }
        mockMvc.delete(document) { bearer(TestAuthorizationServer.token(scope = WRITE)) }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `a path outside the document API is closed even with every scope`() {
        val token = TestAuthorizationServer.token(scope = "$READ $WRITE")

        mockMvc.get("/api/v1/other") { bearer(token) }.andExpect { status { isForbidden() } }
        mockMvc.get("/actuator/env") { bearer(token) }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `the health endpoint needs no token`() {
        mockMvc.get("/actuator/health").andExpect { status { isOk() } }
        mockMvc.get("/actuator/health/readiness").andExpect { status { isOk() } }
    }

    private fun getDocuments(token: String): ResultActionsDsl = mockMvc.get(DOCUMENTS) { bearer(token) }

    private fun MockHttpServletRequestDsl.bearer(token: String) {
        header(HttpHeaders.AUTHORIZATION, "Bearer $token")
    }

    companion object {
        private const val DOCUMENTS = "/api/v1/documents"
        private const val READ = "documents:read"
        private const val WRITE = "documents:write"

        @JvmStatic
        @DynamicPropertySource
        fun authorizationServerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri") { TestAuthorizationServer.jwksUri }
        }
    }
}
