package com.badarak.archive.infrastructure.adapter.web

import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test", "local")
@TestPropertySource(
    properties = [
        "ARCHIVE_JWT_ISSUER=https://auth.test/realms/archive",
        "ARCHIVE_KEYCLOAK_ACME_CLIENT_SECRET=acme-secret",
    ],
)
class OpenApiLocalProfileIT {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `with the local profile, the Swagger UI can also get a token itself with the client credentials grant`() {
        val flow = "$.components.securitySchemes.oauth2.flows.clientCredentials"
        mockMvc.get("/v3/api-docs").andExpect {
            status { isOk() }
            jsonPath("$.components.securitySchemes.oauth2.type") { value("oauth2") }
            jsonPath("$flow.tokenUrl") { value("https://auth.test/realms/archive/protocol/openid-connect/token") }
            jsonPath("$flow.scopes.keys()") { value(containsInAnyOrder("documents:read", "documents:write", "documents:delete")) }
            jsonPath("$.components.securitySchemes['bearer-jwt'].scheme") { value("bearer") }
            jsonPath("$.security[*]") {
                value(containsInAnyOrder(mapOf("bearer-jwt" to emptyList<String>()), mapOf("oauth2" to emptyList<String>())))
            }
        }
    }

    @Test
    fun `the Swagger UI is filled in with the calling service of the acme tenant`() {
        mockMvc.get("/swagger-ui/swagger-initializer.js").andExpect {
            status { isOk() }
            content { string(containsString("\"clientId\":\"billing-service\"")) }
            content { string(containsString("\"clientSecret\":\"acme-secret\"")) }
        }
    }
}
