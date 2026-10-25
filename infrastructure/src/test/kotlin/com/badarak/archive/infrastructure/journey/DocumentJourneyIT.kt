package com.badarak.archive.infrastructure.journey

import com.badarak.archive.domain.port.inbound.AbandonStalledArchivingsUseCase
import com.badarak.archive.domain.port.inbound.ResumeDeletionsUseCase
import com.badarak.archive.infrastructure.adapter.persistence.SpringDataArchivedDocumentRepository
import com.badarak.archive.infrastructure.security.TestAuthorizationServer
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import java.time.Duration
import java.time.Instant
import java.util.UUID
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

    @Autowired
    private lateinit var resumeDeletions: ResumeDeletionsUseCase

    @Autowired
    private lateinit var abandonArchivings: AbandonStalledArchivingsUseCase

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

    @Nested
    inner class Deleting {

        @Test
        fun `a deleted document can no longer be read, downloaded or found, and its content is gone`() {
            val id = archive()
            val deletedAt = START.plusSeconds(60)
            clock.set(deletedAt)

            delete(id).andExpect { status { isNoContent() } }

            read("$DOCUMENTS/$id").andExpect { status { isNotFound() } }
            read("$DOCUMENTS/$id/content").andExpect { status { isNotFound() } }
            assertEquals(emptyList<String>(), search("documentType=INVOICE"))
            assertTrue(storage.keys().isEmpty())
            val row = row(id)
            assertEquals("DELETED", row.status)
            assertEquals(deletedAt, row.deletedAt)
        }

        @Test
        fun `deleting a document again succeeds`() {
            val id = archive()
            delete(id).andExpect { status { isNoContent() } }

            delete(id).andExpect { status { isNoContent() } }

            assertEquals("DELETED", row(id).status)
        }
    }

    @Nested
    inner class TenantIsolation {

        @Test
        fun `a document of another tenant cannot be read, downloaded, found or deleted`() {
            val id = archive(tenant = ACME, businessReference = "INV-1")

            read("$DOCUMENTS/$id", GLOBEX).andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("document-not-found") }
            }
            read("$DOCUMENTS/$id/content", GLOBEX).andExpect { status { isNotFound() } }
            assertEquals(emptyList<String>(), search("businessReference=INV-1", GLOBEX))
            delete(id, GLOBEX).andExpect { status { isNotFound() } }

            read("$DOCUMENTS/$id", ACME).andExpect { status { isOk() } }
            assertEquals(listOf(id), search("businessReference=INV-1", ACME))
            assertEquals(1, storage.keys().size)
        }

        @Test
        fun `a document of another tenant answers like a document that does not exist`() {
            val id = archive(tenant = ACME)

            val foreign = read("$DOCUMENTS/$id", GLOBEX).andReturn().response
            val unknown = read("$DOCUMENTS/${UUID.randomUUID()}", GLOBEX).andReturn().response

            assertEquals(unknown.status, foreign.status)
            assertEquals(JsonPath.read<String>(unknown.contentAsString, "$.code"), JsonPath.read(foreign.contentAsString, "$.code"))
        }
    }

    @Nested
    inner class StorageOutage {

        @Test
        fun `a download during an outage is answered with 503, while the metadata stay readable`() {
            val id = archive()
            storage.unavailable = true

            read("$DOCUMENTS/$id/content").andExpect {
                status { isServiceUnavailable() }
                jsonPath("$.code") { value("archive-unavailable") }
            }
            read("$DOCUMENTS/$id").andExpect { status { isOk() } }
        }

        @Test
        fun `a deletion interrupted by an outage leaves the document unreadable, and replaying it once the storage is back finishes it`() {
            val id = archive()
            storage.unavailable = true

            delete(id).andExpect {
                status { isServiceUnavailable() }
                jsonPath("$.code") { value("archive-unavailable") }
            }

            assertEquals("DELETING", row(id).status)
            read("$DOCUMENTS/$id").andExpect { status { isNotFound() } }
            assertEquals(emptyList<String>(), search("documentType=INVOICE"))
            assertEquals(1, storage.keys().size)

            storage.unavailable = false
            delete(id).andExpect { status { isNoContent() } }

            assertEquals("DELETED", row(id).status)
            assertTrue(storage.keys().isEmpty())
        }

        @Test
        fun `a document left deleting is finished by a new deletion`() {
            val id = archive()
            rows.save(row(id).apply { status = "DELETING" })

            delete(id).andExpect { status { isNoContent() } }

            assertEquals("DELETED", row(id).status)
            assertTrue(storage.keys().isEmpty())
        }
    }

    @Nested
    inner class Reconciliation {

        @Test
        fun `deletions interrupted by an outage are finished by the reconciliation once their grace period is over`() {
            val acmeDocument = archive(tenant = ACME)
            val globexDocument = archive(tenant = GLOBEX)
            storage.unavailable = true
            delete(acmeDocument, ACME).andExpect { status { isServiceUnavailable() } }
            delete(globexDocument, GLOBEX).andExpect { status { isServiceUnavailable() } }
            storage.unavailable = false

            clock.set(START.plus(Duration.ofMinutes(10)))
            assertTrue(resumeDeletions.resume().completed.isEmpty())
            assertEquals("DELETING", row(acmeDocument).status)

            clock.set(START.plus(Duration.ofMinutes(16)))
            assertEquals(2, resumeDeletions.resume().completed.size)

            assertEquals("DELETED", row(acmeDocument).status)
            assertEquals("DELETED", row(globexDocument).status)
            assertTrue(storage.keys().isEmpty())
            read("$DOCUMENTS/$acmeDocument").andExpect { status { isNotFound() } }
        }

        @Test
        fun `a reconciliation during an outage leaves the deletions for the next run`() {
            val id = archive()
            storage.unavailable = true
            delete(id).andExpect { status { isServiceUnavailable() } }
            clock.set(START.plus(Duration.ofMinutes(16)))

            assertEquals(listOf(id), resumeDeletions.resume().failures.map { it.documentId.value.toString() })

            assertEquals("DELETING", row(id).status)
            storage.unavailable = false
            assertEquals(listOf(id), resumeDeletions.resume().completed.map { it.value.toString() })
            assertEquals("DELETED", row(id).status)
        }

        @Test
        fun `an archiving refused by an outage stays pending, then is abandoned once the storage is back and its grace period is over`() {
            storage.unavailable = true

            upload().andExpect {
                status { isServiceUnavailable() }
                jsonPath("$.code") { value("archive-unavailable") }
            }

            val pending = rows.findAll().single()
            assertEquals("PENDING", pending.status)
            storage.unavailable = false

            clock.set(START.plus(Duration.ofMinutes(10)))
            assertTrue(abandonArchivings.abandon().completed.isEmpty())

            clock.set(START.plus(Duration.ofMinutes(16)))
            assertEquals(1, abandonArchivings.abandon().completed.size)

            assertEquals("FAILED", row(pending.getId().toString()).status)
            assertTrue(storage.keys().isEmpty())
        }

        @Test
        fun `an archiving interrupted after its upload is abandoned with its content`() {
            val id = archive()
            rows.save(row(id).apply { status = "PENDING" })
            clock.set(START.plus(Duration.ofMinutes(16)))

            assertEquals(listOf(id), abandonArchivings.abandon().completed.map { it.value.toString() })

            assertEquals("FAILED", row(id).status)
            assertTrue(storage.keys().isEmpty())
            read("$DOCUMENTS/$id").andExpect { status { isNotFound() } }
        }
    }

    private fun row(id: String) = rows.findById(UUID.fromString(id)).orElseThrow()

    private fun delete(id: String, tenant: String = ACME): ResultActionsDsl =
        mockMvc.delete("$DOCUMENTS/$id") { header(HttpHeaders.AUTHORIZATION, "Bearer ${token(tenant, DELETE)}") }

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
        val response = upload(tenant, documentType, businessReference, source).andExpect { status { isCreated() } }.andReturn().response
        return JsonPath.read(response.contentAsString, "$.id")
    }

    private fun upload(
        tenant: String = ACME,
        documentType: String = "INVOICE",
        businessReference: String? = null,
        source: String = BILLING,
    ): ResultActionsDsl {
        val metadata = """{"documentType": "$documentType"${businessReference?.let { """, "businessReference": "$it"""" } ?: ""}}"""
        return mockMvc.multipart(DOCUMENTS) {
            file(MockMultipartFile("file", "invoice.pdf", "application/pdf", PDF))
            file(MockMultipartFile("metadata", "", "application/json", metadata.toByteArray()))
            header(HttpHeaders.AUTHORIZATION, "Bearer ${token(tenant, WRITE, source)}")
        }
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
        private const val DELETE = "documents:delete"
        private const val ACME = "acme"
        private const val GLOBEX = "globex"
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
