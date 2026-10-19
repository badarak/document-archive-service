package com.badarak.archive.infrastructure.adapter.web

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
@ActiveProfiles("test")
@TestPropertySource(properties = ["ARCHIVE_OPENAPI_ENABLED=true"])
class OpenApiDocumentationIT {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `the OpenAPI description documents the archiving endpoint without token`() {
        mockMvc.get("/v3/api-docs").andExpect {
            status { isOk() }
            jsonPath("$.paths['/api/v1/documents'].post.summary") { value("Archive a document") }
            jsonPath("$.paths['/api/v1/documents'].post.tags[0]") { value("Documents") }
            jsonPath("$.paths['/api/v1/documents'].post.parameters") { doesNotExist() }
            jsonPath("$.paths['/api/v1/documents'].post.security[0]['bearer-jwt']") { exists() }
            jsonPath("$.paths['/api/v1/documents'].post.requestBody.content['multipart/form-data'].schema.properties.file") { exists() }
            jsonPath("$.paths['/api/v1/documents'].post.requestBody.content['multipart/form-data'].schema.properties.metadata") { exists() }
            jsonPath("$.paths['/api/v1/documents'].post.requestBody.content['multipart/form-data'].encoding.metadata.contentType") {
                value("application/json")
            }
            jsonPath("$.paths['/api/v1/documents'].post.responses['415']") { exists() }
            jsonPath("$.paths['/api/v1/documents'].get.summary") { value("Search the documents") }
            jsonPath("$.paths['/api/v1/documents'].get.parameters[?(@.name == 'size')].schema.maximum") { value(100) }
            jsonPath("$.paths['/api/v1/documents/{id}'].delete.summary") { value("Delete a document") }
            jsonPath("$.paths['/api/v1/documents/{id}'].delete.responses['204']") { exists() }
            jsonPath("$.components.securitySchemes['bearer-jwt'].scheme") { value("bearer") }
        }
    }

    @Test
    fun `the Swagger UI is served without token`() {
        mockMvc.get("/swagger-ui/index.html").andExpect { status { isOk() } }
    }
}
