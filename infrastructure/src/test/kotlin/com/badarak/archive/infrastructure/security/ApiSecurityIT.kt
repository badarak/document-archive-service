package com.badarak.archive.infrastructure.security

import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockHttpServletRequestDsl
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
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

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource(
        "GET, /api/v1/documents",
        "GET, /api/v1/documents/7d7d57e1-4c50-4cb9-98b8-c5b60e8a2f01",
        "GET, /api/v1/documents/7d7d57e1-4c50-4cb9-98b8-c5b60e8a2f01/content",
        "POST, /api/v1/documents",
        "DELETE, /api/v1/documents/7d7d57e1-4c50-4cb9-98b8-c5b60e8a2f01",
    )
    fun `every operation of the document API rejects a request without token with 401`(method: HttpMethod, path: String) {
        mockMvc.perform(request(method, path)).andExpect(status().isUnauthorized)
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
    fun `a token that is not valid yet is rejected with 401`() {
        val early = TestAuthorizationServer.token(scope = READ, notBefore = Instant.now().plusSeconds(300))

        getDocuments(early).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `an unsigned token is rejected with 401`() {
        getDocuments(TestAuthorizationServer.unsignedToken(scope = READ)).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `a token signed with an algorithm that is not accepted is rejected with 401`() {
        getDocuments(TestAuthorizationServer.hmacToken(scope = READ)).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `a token sent in the URL instead of the Authorization header is rejected with 401`() {
        mockMvc.get(DOCUMENTS) { param("access_token", TestAuthorizationServer.token(scope = READ)) }
            .andExpect { status { isUnauthorized() } }
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
        mockMvc.post(DOCUMENTS) { bearer(TestAuthorizationServer.token(scope = DELETE)) }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `a token with the write scope reaches the archiving endpoint, which then refuses a request that is not multipart`() {
        mockMvc.post(DOCUMENTS) { bearer(TestAuthorizationServer.token(scope = WRITE)) }.andExpect { status { isUnsupportedMediaType() } }
    }

    @Test
    fun `deleting a document requires the delete scope`() {
        val document = "$DOCUMENTS/${UUID.randomUUID()}"

        mockMvc.delete(document) { bearer(TestAuthorizationServer.token(scope = READ)) }.andExpect { status { isForbidden() } }
        mockMvc.delete(document) { bearer(TestAuthorizationServer.token(scope = WRITE)) }.andExpect { status { isForbidden() } }
        mockMvc.delete(document) { bearer(TestAuthorizationServer.token(scope = DELETE)) }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `reading documents is refused to the write and delete scopes`() {
        getDocuments(TestAuthorizationServer.token(scope = "$WRITE $DELETE")).andExpect { status { isForbidden() } }
    }

    @Test
    fun `other methods on the document API are closed even with every scope`() {
        val token = TestAuthorizationServer.token(scope = "$READ $WRITE $DELETE")
        val document = "$DOCUMENTS/${UUID.randomUUID()}"

        mockMvc.put(document) { bearer(token) }.andExpect { status { isForbidden() } }
        mockMvc.patch(document) { bearer(token) }.andExpect { status { isForbidden() } }
        mockMvc.delete(DOCUMENTS) { bearer(token) }.andExpect { status { isForbidden() } }
        mockMvc.post("$document/content") { bearer(token) }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `a path outside the document API is closed even with every scope`() {
        val token = TestAuthorizationServer.token(scope = "$READ $WRITE $DELETE")

        mockMvc.get("/api/v1/other") { bearer(token) }.andExpect { status { isForbidden() } }
        mockMvc.get("/actuator/env") { bearer(token) }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `the OpenAPI description and the Swagger UI are not published by default`() {
        mockMvc.get("/v3/api-docs").andExpect { status { isNotFound() } }
        mockMvc.get("/swagger-ui/index.html").andExpect { status { isNotFound() } }
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
        private const val DELETE = "documents:delete"

        @JvmStatic
        @DynamicPropertySource
        fun authorizationServerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri") { TestAuthorizationServer.jwksUri }
        }
    }
}
