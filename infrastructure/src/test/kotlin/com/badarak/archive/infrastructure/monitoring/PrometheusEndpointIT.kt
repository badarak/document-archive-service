package com.badarak.archive.infrastructure.monitoring

import com.badarak.archive.infrastructure.security.TestAuthorizationServer
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PrometheusEndpointIT {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `the metrics are scraped with the metrics scope, tagged with the application and never with an identifier`() {
        mockMvc.get(DOCUMENTS)

        scrape(TestAuthorizationServer.token(scope = METRICS)).andExpect {
            status { isOk() }
            content { string(containsString("archive_search_seconds_count{")) }
            content { string(containsString("application=\"document-archive-service\"")) }
            content { string(not(containsString("documentId"))) }
            content { string(not(containsString("tenantId"))) }
        }
    }

    @Test
    fun `scraping without token is refused with 401`() {
        mockMvc.get(PROMETHEUS).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `scraping with a token without the metrics scope is refused with 403`() {
        val documentsOnly = TestAuthorizationServer.token(scope = "documents:read documents:write documents:delete")

        scrape(documentsOnly).andExpect { status { isForbidden() } }
    }

    private fun scrape(token: String): ResultActionsDsl = mockMvc.get(PROMETHEUS) { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }

    companion object {
        private const val PROMETHEUS = "/actuator/prometheus"
        private const val DOCUMENTS = "/api/v1/documents"
        private const val METRICS = "metrics:read"

        @JvmStatic
        @DynamicPropertySource
        fun authorizationServerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri") { TestAuthorizationServer.jwksUri }
        }
    }
}
