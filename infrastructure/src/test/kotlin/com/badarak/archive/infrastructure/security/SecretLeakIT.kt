package com.badarak.archive.infrastructure.security

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart

private const val SECRET = "sentinel-secret-7f3a9c"

/**
 * The secrets of the service, set here to a sentinel value, never reach a log line nor an answer, even when the storage
 * they open fails and its error is logged with its stack trace.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension::class)
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:secrets;MODE=PostgreSQL;DEFAULT_NULL_ORDERING=HIGH;INIT=CREATE SCHEMA IF NOT EXISTS archive",
        "spring.datasource.password=$SECRET",
        "spring.liquibase.password=$SECRET",
        "archive.storage.s3.endpoint=http://127.0.0.1:9",
        "archive.storage.s3.secret-key=$SECRET",
    ],
)
class SecretLeakIT {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `a storage failure is logged and answered without any secret`(output: CapturedOutput) {
        val answer = mockMvc.multipart("/api/v1/documents") {
            file(MockMultipartFile("file", "invoice.pdf", "application/pdf", "%PDF-1.7\n1 0 obj\n%%EOF\n".toByteArray()))
            file(MockMultipartFile("metadata", "", "application/json", """{"documentType": "INVOICE"}""".toByteArray()))
            header(HttpHeaders.AUTHORIZATION, "Bearer ${TestAuthorizationServer.token(scope = "documents:write")}")
        }.andReturn().response

        assertTrue(answer.status == 503, "${answer.status}")
        assertTrue(output.out.contains("Archive unavailable"), "The failure should be logged")
        assertFalse(answer.contentAsString.contains(SECRET))
        assertFalse(output.all.contains(SECRET))
    }

    @Test
    fun `the actuator never shows a secret, even to a caller with every scope`(output: CapturedOutput) {
        val token = TestAuthorizationServer.token(scope = "documents:read documents:write documents:delete metrics:read")

        listOf("/actuator/env", "/actuator/configprops", "/actuator/prometheus").forEach { path ->
            val answer = mockMvc.get(path) { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }.andReturn().response

            assertFalse(answer.contentAsString.contains(SECRET), path)
        }
        assertFalse(output.all.contains(SECRET))
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun authorizationServerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri") { TestAuthorizationServer.jwksUri }
        }
    }
}
