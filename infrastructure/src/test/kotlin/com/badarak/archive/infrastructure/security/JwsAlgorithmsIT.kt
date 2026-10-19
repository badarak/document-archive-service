package com.badarak.archive.infrastructure.security

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = ["ARCHIVE_JWT_ALGORITHMS=ES256"])
class JwsAlgorithmsIT {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `a token signed with an algorithm left out of the configured ones is rejected with 401`() {
        val token = TestAuthorizationServer.token(scope = "documents:read")

        mockMvc.get("/api/v1/documents") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
            .andExpect { status { isUnauthorized() } }
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun authorizationServerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri") { TestAuthorizationServer.jwksUri }
        }
    }
}
