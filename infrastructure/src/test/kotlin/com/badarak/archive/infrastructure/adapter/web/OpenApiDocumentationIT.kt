package com.badarak.archive.infrastructure.adapter.web

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiDocumentationIT {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `the OpenAPI description documents the archiving endpoint without token`() {
        mockMvc.get("/v3/api-docs").andExpect {
            status { isOk() }
            jsonPath("$.paths['/api/v1/documents'].post.summary") { value("Archive a document") }
            jsonPath("$.paths['/api/v1/documents'].post.responses['415']") { exists() }
            jsonPath("$.components.securitySchemes['bearer-jwt'].scheme") { value("bearer") }
        }
    }

    @Test
    fun `the Swagger UI is served without token`() {
        mockMvc.get("/swagger-ui/index.html").andExpect { status { isOk() } }
    }
}
