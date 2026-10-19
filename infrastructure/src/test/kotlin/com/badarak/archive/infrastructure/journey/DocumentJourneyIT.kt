package com.badarak.archive.infrastructure.journey

import com.badarak.archive.infrastructure.adapter.persistence.SpringDataArchivedDocumentRepository
import com.badarak.archive.infrastructure.security.TestAuthorizationServer
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import java.time.Instant
import kotlin.random.Random

/**
 * Complete journeys through the HTTP API, the real use cases and the H2 document store, with the object storage held in
 * memory. Each test starts from an empty archive.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(DocumentJourneyIT.Doubles::class)
class DocumentJourneyIT {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var rows: SpringDataArchivedDocumentRepository

    @Autowired
    private lateinit var storage: InMemoryObjectStorage

    @Autowired
    private lateinit var clock: MutableClock

    @AfterEach
    fun emptyTheArchive() {
        rows.deleteAll()
        storage.reset()
        clock.set(START)
    }

    @Nested
    inner class ArchivingThenReading {

        @Test
        fun `an archived document is read back with its metadata`() {
            val id = archive(businessReference = "INV-2026-001")

            read("$DOCUMENTS/$id").andExpect {
                status { isOk() }
                jsonPath("$.id") { value(id) }
                jsonPath("$.documentType") { value("INVOICE") }
                jsonPath("$.businessReference") { value("INV-2026-001") }
                jsonPath("$.fileName") { value("invoice.pdf") }
                jsonPath("$.contentType") { value("application/pdf") }
                jsonPath("$.size") { value(PDF.size) }
                jsonPath("$.status") { value("AVAILABLE") }
                jsonPath("$.createdAt") { value(START.toString()) }
            }
        }

        @Test
        fun `the downloaded content is byte for byte the archived file`() {
            val id = archive()

            val response = read("$DOCUMENTS/$id/content").andExpect {
                status { isOk() }
                content { contentType("application/pdf") }
                header { longValue(HttpHeaders.CONTENT_LENGTH, PDF.size.toLong()) }
                header { string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"invoice.pdf\"; filename*=UTF-8''invoice.pdf") }
            }.andReturn().response

            assertArrayEquals(PDF, response.contentAsByteArray)
        }
    }

    private fun archive(
        tenant: String = ACME,
        documentType: String = "INVOICE",
        businessReference: String? = null,
        source: String = BILLING,
    ): String {
        val metadata = """{"documentType": "$documentType"${businessReference?.let { """, "businessReference": "$it"""" } ?: ""}}"""
        val body = mockMvc.multipart(DOCUMENTS) {
            file(MockMultipartFile("file", "invoice.pdf", "application/pdf", PDF))
            file(MockMultipartFile("metadata", "", "application/json", metadata.toByteArray()))
            header(HttpHeaders.AUTHORIZATION, "Bearer ${token(tenant, WRITE, source)}")
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        return JsonPath.read(body, "$.id")
    }

    private fun read(path: String, tenant: String = ACME): ResultActionsDsl =
        mockMvc.get(path) { header(HttpHeaders.AUTHORIZATION, "Bearer ${token(tenant, READ)}") }

    private fun token(tenant: String, scope: String, source: String = BILLING) =
        TestAuthorizationServer.token(scope = scope, tenant = tenant, client = source)

    @TestConfiguration
    class Doubles {

        @Bean
        @Primary
        fun inMemoryObjectStorage(): InMemoryObjectStorage = InMemoryObjectStorage()

        @Bean
        @Primary
        fun mutableClock(): MutableClock = MutableClock(START)
    }

    companion object {
        private const val DOCUMENTS = "/api/v1/documents"
        private const val READ = "documents:read"
        private const val WRITE = "documents:write"
        private const val ACME = "acme"
        private const val BILLING = "billing-service"
        private val START: Instant = Instant.parse("2026-10-15T09:00:00Z")
        private val PDF: ByteArray = "%PDF-1.7\n".toByteArray() + Random(42).nextBytes(1024 * 1024)

        @JvmStatic
        @DynamicPropertySource
        fun authorizationServerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri") { TestAuthorizationServer.jwksUri }
        }
    }
}
