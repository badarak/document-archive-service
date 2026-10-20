package com.badarak.archive.infrastructure.adapter.web

import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.containsInAnyOrder
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
            jsonPath("$.paths['/api/v1/documents'].get.parameters[*].name") {
                value(containsInAnyOrder("documentType", "source", "businessReference", "createdFrom", "createdTo", "page", "size"))
            }
            jsonPath("$.paths['/api/v1/documents'].get.parameters[?(@.name == 'createdFrom')].in") { value(contains("query")) }
            jsonPath("$.paths['/api/v1/documents'].get.parameters[?(@.name == 'documentType')].description") {
                value(contains("Exact document type, such as INVOICE"))
            }
            jsonPath("$.paths['/api/v1/documents/{id}'].delete.summary") { value("Delete a document") }
            jsonPath("$.paths['/api/v1/documents/{id}'].delete.responses['204']") { exists() }
            jsonPath("$.components.securitySchemes['bearer-jwt'].scheme") { value("bearer") }
        }
    }

    @Test
    fun `every error answer is described as Problem Details, with the 401 and 500 answers shared by every operation`() {
        val document = "$.paths['/api/v1/documents/{id}']"
        mockMvc.get("/v3/api-docs").andExpect {
            status { isOk() }
            jsonPath("$.components.schemas.ProblemDetail") { exists() }
            jsonPath("$document.get.responses['404'].content['application/problem+json'].schema['\$ref']") { value(PROBLEM_DETAIL) }
            jsonPath("$document.get.responses['404'].content['application/json']") { doesNotExist() }
            jsonPath("$document.get.responses['200'].content['application/json'].schema['\$ref']") {
                value("#/components/schemas/ArchivedDocument")
            }
            jsonPath("$document.delete.responses['401'].description") { value("Missing, invalid or expired access token") }
            jsonPath("$document.delete.responses['401'].content['application/problem+json'].schema['\$ref']") { value(PROBLEM_DETAIL) }
            jsonPath("$document.delete.responses['500'].description") { value("Unexpected error") }
            jsonPath("$['paths']['/api/v1/documents/{id}/content'].get.responses['500'].description") {
                value("The document's content is missing from the storage")
            }
            jsonPath("$['paths']['/api/v1/documents/{id}/content'].get.responses['403'].content['application/problem+json']") { exists() }
        }
    }

    @Test
    fun `the Swagger UI is served without token`() {
        mockMvc.get("/swagger-ui/index.html").andExpect { status { isOk() } }
    }

    companion object {
        private const val PROBLEM_DETAIL = "#/components/schemas/ProblemDetail"
    }
}
