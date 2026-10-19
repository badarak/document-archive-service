package com.badarak.archive.infrastructure.journey

import com.badarak.archive.infrastructure.adapter.persistence.SpringDataArchivedDocumentRepository
import com.badarak.archive.infrastructure.security.TestAuthorizationServer
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
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

    @Nested
    inner class Searching {

        @Test
        fun `each filter narrows the search, newest documents first`() {
            val firstInvoice = archiveAt(START, "INVOICE", "INV-1", BILLING)
            val contract = archiveAt(START.plusSeconds(3600), "CONTRACT", "CTR-1", SALES)
            val secondInvoice = archiveAt(START.plusSeconds(7200), "INVOICE", "INV-2", BILLING)
            val salesInvoice = archiveAt(START.plusSeconds(10800), "INVOICE", "INV-3", SALES)

            assertEquals(listOf(salesInvoice, secondInvoice, firstInvoice), search("documentType=INVOICE"))
            assertEquals(listOf(salesInvoice, contract), search("source=$SALES"))
            assertEquals(listOf(secondInvoice), search("businessReference=INV-2"))
            assertEquals(listOf(secondInvoice, contract), search("createdFrom=2026-10-15T10:00:00Z&createdTo=2026-10-15T11:00:00Z"))
            assertEquals(listOf(secondInvoice, firstInvoice), search("documentType=INVOICE&source=$BILLING"))
        }

        @Test
        fun `the documents are returned page by page with their total`() {
            val newestFirst = (0L..4L).map { archiveAt(START.plusSeconds(it * 60)) }.reversed()

            val body = read("$DOCUMENTS?page=1&size=2").andExpect {
                status { isOk() }
                jsonPath("$.page") { value(1) }
                jsonPath("$.size") { value(2) }
                jsonPath("$.totalElements") { value(5) }
                jsonPath("$.totalPages") { value(3) }
            }.andReturn().response.contentAsString

            assertEquals(newestFirst.subList(2, 4), JsonPath.read<List<String>>(body, "$.content[*].id"))
        }

        private fun archiveAt(
            createdAt: Instant,
            documentType: String = "INVOICE",
            reference: String? = null,
            source: String = BILLING,
        ): String {
            clock.set(createdAt)
            return archive(documentType = documentType, businessReference = reference, source = source)
        }
    }

    private fun search(parameters: String, tenant: String = ACME): List<String> {
        val body = read("$DOCUMENTS?$parameters", tenant).andExpect { status { isOk() } }.andReturn().response.contentAsString
        return JsonPath.read(body, "$.content[*].id")
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
        private const val SALES = "sales-service"
        private val START: Instant = Instant.parse("2026-10-15T09:00:00Z")
        private val PDF: ByteArray = "%PDF-1.7\n".toByteArray() + Random(42).nextBytes(1024 * 1024)

        @JvmStatic
        @DynamicPropertySource
        fun authorizationServerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri") { TestAuthorizationServer.jwksUri }
        }
    }
}
