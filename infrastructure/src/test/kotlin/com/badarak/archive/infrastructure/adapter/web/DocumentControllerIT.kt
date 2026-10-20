package com.badarak.archive.infrastructure.adapter.web

import com.badarak.archive.domain.model.ArchivedDocument
import com.badarak.archive.domain.model.ArchivingAbandonedException
import com.badarak.archive.domain.model.ContentMismatchException
import com.badarak.archive.domain.model.ContentTypeMismatchException
import com.badarak.archive.domain.model.DocumentFormat
import com.badarak.archive.domain.model.DocumentId
import com.badarak.archive.domain.model.DocumentMetadata
import com.badarak.archive.domain.model.DocumentNotFoundException
import com.badarak.archive.domain.model.DocumentOwner
import com.badarak.archive.domain.model.DocumentPage
import com.badarak.archive.domain.model.DocumentSearchCriteria
import com.badarak.archive.domain.model.DocumentSizePolicy
import com.badarak.archive.domain.model.DocumentStatus
import com.badarak.archive.domain.model.DocumentTooLargeException
import com.badarak.archive.domain.model.DocumentType
import com.badarak.archive.domain.model.EmptyDocumentException
import com.badarak.archive.domain.model.InvalidDocumentException
import com.badarak.archive.domain.model.InvalidStatusTransitionException
import com.badarak.archive.domain.model.ObjectKey
import com.badarak.archive.domain.model.PageRequest
import com.badarak.archive.domain.model.StoredFile
import com.badarak.archive.domain.model.TenantId
import com.badarak.archive.domain.model.UnsupportedDocumentFormatException
import com.badarak.archive.domain.port.inbound.ArchiveDocumentCommand
import com.badarak.archive.domain.port.inbound.ArchiveDocumentUseCase
import com.badarak.archive.domain.port.inbound.DeleteDocumentUseCase
import com.badarak.archive.domain.port.inbound.DocumentDownload
import com.badarak.archive.domain.port.inbound.DocumentQuery
import com.badarak.archive.domain.port.inbound.DocumentSearchQuery
import com.badarak.archive.domain.port.inbound.DownloadDocumentUseCase
import com.badarak.archive.domain.port.inbound.GetDocumentUseCase
import com.badarak.archive.domain.port.inbound.SearchDocumentsUseCase
import com.badarak.archive.domain.port.outbound.DocumentPersistenceException
import com.badarak.archive.domain.port.outbound.MissingObjectException
import com.badarak.archive.domain.port.outbound.ObjectStorageException
import com.badarak.archive.infrastructure.security.TestAuthorizationServer
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import java.io.ByteArrayInputStream
import java.time.Instant

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(DocumentControllerIT.UseCaseStub::class)
class DocumentControllerIT {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var useCase: ArchiveDocumentUseCase

    @Autowired
    private lateinit var getDocument: GetDocumentUseCase

    @Autowired
    private lateinit var downloadDocument: DownloadDocumentUseCase

    @Autowired
    private lateinit var searchDocuments: SearchDocumentsUseCase

    @Autowired
    private lateinit var deleteDocument: DeleteDocumentUseCase

    private val content = "%PDF-1.7\n%âãÏÓ".toByteArray()
    private var received: ArchiveDocumentCommand? = null
    private var receivedBytes: ByteArray? = null

    @BeforeEach
    fun acceptEveryDocument() {
        clearMocks(useCase, getDocument, downloadDocument, searchDocuments, deleteDocument)
        every { useCase.archive(any()) } answers {
            val command = firstArg<ArchiveDocumentCommand>()
            received = command
            receivedBytes = command.file.content.readAllBytes()
            archived(command.metadata)
        }
    }

    @Test
    fun `archives a document and answers 201 with its location and metadata`() {
        post().andExpect {
            status { isCreated() }
            header { string(HttpHeaders.LOCATION, "http://localhost/api/v1/documents/$ID") }
            jsonPath("$.id") { value(ID) }
            jsonPath("$.documentType") { value("INVOICE") }
            jsonPath("$.businessReference") { value("INV-2026-000123") }
            jsonPath("$.fileName") { value("invoice-123.pdf") }
            jsonPath("$.contentType") { value("application/pdf") }
            jsonPath("$.size") { value(content.size) }
            jsonPath("$.status") { value("AVAILABLE") }
            jsonPath("$.createdAt") { value("2026-10-15T09:15:00Z") }
        }
    }

    @Test
    fun `passes the caller from the token, the metadata and the uploaded file to the use case`() {
        post().andExpect { status { isCreated() } }

        val command = received!!
        assertEquals(TenantId("acme"), command.caller.tenantId)
        assertEquals("billing-service", command.caller.source)
        assertEquals(DocumentType("INVOICE"), command.metadata.documentType)
        assertEquals("INV-2026-000123", command.metadata.businessReference)
        assertEquals("Customer invoice", command.metadata.description)
        assertEquals(mapOf("customerId" to "CUST-456"), command.metadata.tags)
        assertEquals("invoice-123.pdf", command.file.fileName)
        assertEquals("application/pdf", command.file.contentType)
        assertEquals(content.size.toLong(), command.file.sizeBytes)
        assertArrayEquals(content, receivedBytes)
    }

    @Test
    fun `ignores a source sent in the metadata`() {
        post(metadata = """{"documentType": "INVOICE", "source": "someone-else"}""").andExpect { status { isCreated() } }

        assertEquals("billing-service", received!!.caller.source)
    }

    @Test
    fun `optional metadata may be omitted`() {
        post(metadata = """{"documentType": "INVOICE"}""").andExpect { status { isCreated() } }

        assertEquals(null, received!!.metadata.businessReference)
        assertEquals(emptyMap<String, String>(), received!!.metadata.tags)
    }

    @Test
    fun `a request without file part is refused with 400`() {
        mockMvc.multipart(DOCUMENTS) {
            file(metadataPart(METADATA))
            header(HttpHeaders.AUTHORIZATION, "Bearer ${token()}")
        }.andExpect { status { isBadRequest() } }

        verify(exactly = 0) { useCase.archive(any()) }
    }

    @Test
    fun `a request without metadata part is refused with 400`() {
        mockMvc.multipart(DOCUMENTS) {
            file(filePart())
            header(HttpHeaders.AUTHORIZATION, "Bearer ${token()}")
        }.andExpect { status { isBadRequest() } }

        verify(exactly = 0) { useCase.archive(any()) }
    }

    @Test
    fun `metadata without document type is refused with 400`() {
        post(metadata = """{"businessReference": "INV-2026-000123"}""").andExpect { status { isBadRequest() } }

        verify(exactly = 0) { useCase.archive(any()) }
    }

    @Nested
    inner class Consultation {

        private val query = slot<DocumentQuery>()

        @Test
        fun `an available document is answered with 200 and its metadata, without internal details`() {
            val metadata = DocumentMetadata(DocumentType("INVOICE"), "INV-1", "Invoice", mapOf("k" to "v"))
            every { getDocument.get(capture(query)) } returns archived(metadata)

            mockMvc.get("$DOCUMENTS/$ID") { header(HttpHeaders.AUTHORIZATION, "Bearer ${readToken()}") }.andExpect {
                status { isOk() }
                jsonPath("$.id") { value(ID) }
                jsonPath("$.documentType") { value("INVOICE") }
                jsonPath("$.businessReference") { value("INV-1") }
                jsonPath("$.description") { value("Invoice") }
                jsonPath("$.tags.k") { value("v") }
                jsonPath("$.fileName") { value("invoice-123.pdf") }
                jsonPath("$.status") { value("AVAILABLE") }
                jsonPath("$.updatedAt") { value("2026-10-15T09:15:00Z") }
                jsonPath("$.objectKey") { doesNotExist() }
                jsonPath("$.tenantId") { doesNotExist() }
            }

            assertEquals(DocumentId.parse(ID), query.captured.id)
            assertEquals(TenantId("acme"), query.captured.caller.tenantId)
        }

        @Test
        fun `a document that is not found is answered with 404`() {
            every { getDocument.get(any()) } throws DocumentNotFoundException(DocumentId.parse(ID))

            mockMvc.get("$DOCUMENTS/$ID") { header(HttpHeaders.AUTHORIZATION, "Bearer ${readToken()}") }.andExpect {
                status { isNotFound() }
                content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
                jsonPath("$.code") { value("document-not-found") }
            }
        }

        @Test
        fun `an identifier that is not a UUID is refused with 400`() {
            mockMvc.get("$DOCUMENTS/not-a-uuid") { header(HttpHeaders.AUTHORIZATION, "Bearer ${readToken()}") }.andExpect {
                status { isBadRequest() }
                content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            }

            verify(exactly = 0) { getDocument.get(any()) }
        }

        @Test
        fun `a token without tenant is refused with 403`() {
            val token = TestAuthorizationServer.token(scope = "documents:read", tenant = null)

            mockMvc.get("$DOCUMENTS/$ID") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }.andExpect {
                status { isForbidden() }
                jsonPath("$.code") { value("caller-not-identified") }
            }

            verify(exactly = 0) { getDocument.get(any()) }
        }

        private fun readToken() = TestAuthorizationServer.token(scope = "documents:read")
    }

    @Nested
    inner class Download {

        private val query = slot<DocumentQuery>()

        @Test
        fun `streams the content as an attachment with its type, size and file name`() {
            val stream = TrackedStream(content)
            every { downloadDocument.download(capture(query)) } returns DocumentDownload(stored("facture-é.pdf"), stream)

            val response = mockMvc.get("$DOCUMENTS/$ID/content") { header(HttpHeaders.AUTHORIZATION, "Bearer ${readToken()}") }
                .andExpect {
                    status { isOk() }
                    content { contentType("application/pdf") }
                    header { longValue(HttpHeaders.CONTENT_LENGTH, content.size.toLong()) }
                    header { string(HttpHeaders.CONTENT_DISPOSITION, DISPOSITION) }
                }
                .andReturn()

            assertArrayEquals(content, response.response.contentAsByteArray)
            assertTrue(stream.closed)
            assertEquals(DocumentId.parse(ID), query.captured.id)
            assertEquals(TenantId("acme"), query.captured.caller.tenantId)
        }

        @Test
        fun `a document that is not found is answered with 404, whatever the accepted type`() {
            every { downloadDocument.download(any()) } throws DocumentNotFoundException(DocumentId.parse(ID))

            mockMvc.get("$DOCUMENTS/$ID/content") {
                header(HttpHeaders.AUTHORIZATION, "Bearer ${readToken()}")
                accept = MediaType.APPLICATION_PDF
            }.andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("document-not-found") }
            }
        }

        @Test
        fun `a content missing from the storage is answered with 500, without internal details`() {
            val key = ObjectKey("documents/secret-key")
            every { downloadDocument.download(any()) } throws MissingObjectException(key, RuntimeException("NoSuchKey"))

            mockMvc.get("$DOCUMENTS/$ID/content") { header(HttpHeaders.AUTHORIZATION, "Bearer ${readToken()}") }.andExpect {
                status { isInternalServerError() }
                jsonPath("$.code") { value("content-missing") }
                jsonPath("$.detail") { value("The content of this document cannot be found") }
            }
        }

        @Test
        fun `an unreachable storage is answered with 503`() {
            every { downloadDocument.download(any()) } throws ObjectStorageException("Bucket 'secret' unreachable", RuntimeException())

            mockMvc.get("$DOCUMENTS/$ID/content") { header(HttpHeaders.AUTHORIZATION, "Bearer ${readToken()}") }.andExpect {
                status { isServiceUnavailable() }
                jsonPath("$.code") { value("archive-unavailable") }
            }
        }

        private fun stored(fileName: String) = ArchivedDocument(
            id = DocumentId.parse(ID),
            owner = DocumentOwner(TenantId("acme"), "billing-service"),
            metadata = DocumentMetadata(DocumentType("INVOICE"), null, null, emptyMap()),
            file = StoredFile(fileName, "application/pdf", content.size.toLong(), ObjectKey.generate()),
            status = DocumentStatus.AVAILABLE,
            createdAt = Instant.parse("2026-10-15T09:15:00Z"),
            updatedAt = Instant.parse("2026-10-15T09:15:00Z"),
        )

        private fun readToken() = TestAuthorizationServer.token(scope = "documents:read")
    }

    @Nested
    inner class Search {

        private val query = slot<DocumentSearchQuery>()

        @BeforeEach
        fun answerAnEmptyPage() {
            every { searchDocuments.search(capture(query)) } answers { DocumentPage(emptyList(), firstArg<DocumentSearchQuery>().page, 0) }
        }

        @Test
        fun `a page of documents is answered with 200, its documents and its counts`() {
            val document = archived(DocumentMetadata(DocumentType("INVOICE"), "INV-1", null, emptyMap()))
            every { searchDocuments.search(any()) } returns DocumentPage(listOf(document), PageRequest(1, 2), 3)

            search("page=1&size=2").andExpect {
                status { isOk() }
                jsonPath("$.content[0].id") { value(ID) }
                jsonPath("$.content[0].documentType") { value("INVOICE") }
                jsonPath("$.content[0].fileName") { value("invoice-123.pdf") }
                jsonPath("$.content[0].size") { value(content.size) }
                jsonPath("$.content[0].status") { value("AVAILABLE") }
                jsonPath("$.content[0].objectKey") { doesNotExist() }
                jsonPath("$.page") { value(1) }
                jsonPath("$.size") { value(2) }
                jsonPath("$.totalElements") { value(3) }
                jsonPath("$.totalPages") { value(2) }
            }
        }

        @Test
        fun `passes the caller from the token, every filter and the page to the use case`() {
            val query = "documentType=INVOICE&source=billing-service&businessReference=INV-1" +
                "&createdFrom=2026-10-01T00:00:00Z&createdTo=2026-10-15T16:30:00+02:00&page=3&size=50"

            search(query).andExpect { status { isOk() } }

            val received = this.query.captured
            assertEquals(TenantId("acme"), received.caller.tenantId)
            assertEquals(
                DocumentSearchCriteria(
                    DocumentType("INVOICE"),
                    "billing-service",
                    "INV-1",
                    Instant.parse("2026-10-01T00:00:00Z"),
                    Instant.parse("2026-10-15T14:30:00Z"),
                ),
                received.criteria,
            )
            assertEquals(PageRequest(3, 50), received.page)
        }

        @Test
        fun `without parameter, every document is searched in pages of 20 from the first one`() {
            search().andExpect {
                status { isOk() }
                jsonPath("$.content") { isEmpty() }
                jsonPath("$.page") { value(0) }
                jsonPath("$.size") { value(20) }
                jsonPath("$.totalPages") { value(0) }
            }

            assertEquals(DocumentSearchCriteria(), query.captured.criteria)
            assertEquals(PageRequest(), query.captured.page)
        }

        @Test
        fun `a tenant sent by the client is ignored in favour of the token's`() {
            search("tenantId=globex&tenant_id=globex").andExpect { status { isOk() } }

            assertEquals(TenantId("acme"), query.captured.caller.tenantId)
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(
            strings = [
                "size=0",
                "size=101",
                "page=-1",
                "source=",
                "businessReference= ",
                "createdFrom=2026-10-15T00:00:00Z&createdTo=2026-10-14T00:00:00Z",
            ],
        )
        fun `invalid search parameters are refused with 400 before the use case`(parameters: String) {
            search(parameters).andExpect {
                status { isBadRequest() }
                content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
                jsonPath("$.code") { value("invalid-search") }
            }

            verify(exactly = 0) { searchDocuments.search(any()) }
        }

        @Test
        fun `a malformed document type is refused with 400`() {
            search("documentType=invoice").andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("invalid-document-type") }
            }
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = ["createdFrom=2026-10-15T14:30:00", "createdTo=yesterday", "page=first", "size=ten"])
        fun `a parameter that cannot be read is refused with 400`(parameters: String) {
            search(parameters).andExpect {
                status { isBadRequest() }
                content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            }

            verify(exactly = 0) { searchDocuments.search(any()) }
        }

        @Test
        fun `an unavailable document store is answered with 503`() {
            every { searchDocuments.search(any()) } throws DocumentPersistenceException("Database down", RuntimeException())

            search().andExpect {
                status { isServiceUnavailable() }
                jsonPath("$.code") { value("archive-unavailable") }
            }
        }

        @Test
        fun `a token without tenant is refused with 403`() {
            val token = TestAuthorizationServer.token(scope = "documents:read", tenant = null)

            mockMvc.get(DOCUMENTS) { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }.andExpect {
                status { isForbidden() }
                jsonPath("$.code") { value("caller-not-identified") }
            }

            verify(exactly = 0) { searchDocuments.search(any()) }
        }

        private fun search(parameters: String = ""): ResultActionsDsl = mockMvc.get(DOCUMENTS) {
            header(HttpHeaders.AUTHORIZATION, "Bearer ${TestAuthorizationServer.token(scope = "documents:read")}")
            parameters.split("&").filter { it.isNotEmpty() }.forEach { param(it.substringBefore("="), it.substringAfter("=")) }
        }
    }

    @Nested
    inner class Deletion {

        private val query = slot<DocumentQuery>()

        @Test
        fun `a deleted document is answered with 204 and no body`() {
            every { deleteDocument.delete(capture(query)) } just runs

            delete().andExpect {
                status { isNoContent() }
                content { string("") }
            }

            assertEquals(DocumentId.parse(ID), query.captured.id)
            assertEquals(TenantId("acme"), query.captured.caller.tenantId)
        }

        @Test
        fun `a document that is not found is answered with 404`() {
            every { deleteDocument.delete(any()) } throws DocumentNotFoundException(DocumentId.parse(ID))

            delete().andExpect {
                status { isNotFound() }
                content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
                jsonPath("$.code") { value("document-not-found") }
            }
        }

        @Test
        fun `an unreachable storage is answered with 503, without internal details`() {
            every { deleteDocument.delete(any()) } throws ObjectStorageException("Bucket 'secret' unreachable", RuntimeException())

            delete().andExpect {
                status { isServiceUnavailable() }
                jsonPath("$.code") { value("archive-unavailable") }
                jsonPath("$.detail") { value("The archive is temporarily unavailable, retry later") }
            }
        }

        @Test
        fun `an identifier that is not a UUID is refused with 400`() {
            mockMvc.delete("$DOCUMENTS/not-a-uuid") { header(HttpHeaders.AUTHORIZATION, "Bearer ${deleteToken()}") }.andExpect {
                status { isBadRequest() }
                content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            }

            verify(exactly = 0) { deleteDocument.delete(any()) }
        }

        @Test
        fun `a token without tenant is refused with 403`() {
            delete(TestAuthorizationServer.token(scope = "documents:delete", tenant = null)).andExpect {
                status { isForbidden() }
                jsonPath("$.code") { value("caller-not-identified") }
            }

            verify(exactly = 0) { deleteDocument.delete(any()) }
        }

        private fun delete(token: String = deleteToken()): ResultActionsDsl =
            mockMvc.delete("$DOCUMENTS/$ID") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }

        private fun deleteToken() = TestAuthorizationServer.token(scope = "documents:delete")
    }

    @Nested
    inner class Errors {

        @ParameterizedTest(name = "{1} {2}")
        @MethodSource("com.badarak.archive.infrastructure.adapter.web.DocumentControllerIT#archiveErrors")
        fun `an archiving error is answered as a problem with its status and code`(error: RuntimeException, status: Int, code: String) {
            every { useCase.archive(any()) } throws error

            post().andExpect {
                status { isEqualTo(status) }
                content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
                jsonPath("$.status") { value(status) }
                jsonPath("$.code") { value(code) }
                jsonPath("$.detail") { exists() }
            }
        }

        @Test
        fun `an unavailable archive is answered without internal details`() {
            val failure = ObjectStorageException("Could not store object in bucket 'secret-bucket'", RuntimeException())
            every { useCase.archive(any()) } throws failure

            post().andExpect {
                status { isServiceUnavailable() }
                jsonPath("$.detail") { value("The archive is temporarily unavailable, retry later") }
            }
        }

        @Test
        fun `an unexpected error is answered with 500, without internal details`() {
            every { getDocument.get(any()) } throws IllegalStateException("Connection to jdbc:postgresql://db-internal:5432 lost")

            mockMvc.get("$DOCUMENTS/$ID") {
                header(HttpHeaders.AUTHORIZATION, "Bearer ${TestAuthorizationServer.token(scope = "documents:read")}")
            }.andExpect {
                status { isInternalServerError() }
                content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
                jsonPath("$.code") { value("internal-error") }
                jsonPath("$.detail") { value("An unexpected error occurred") }
            }
        }

        @Test
        fun `an invalid document type is refused with 400 before the use case`() {
            post(metadata = """{"documentType": "invoice"}""").andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("invalid-document-type") }
            }

            verify(exactly = 0) { useCase.archive(any()) }
        }

        @Test
        fun `a token without tenant is refused with 403`() {
            post(token = TestAuthorizationServer.token(scope = "documents:write", tenant = null)).andExpect {
                status { isForbidden() }
                content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
                jsonPath("$.code") { value("caller-not-identified") }
            }

            verify(exactly = 0) { useCase.archive(any()) }
        }

        @Test
        fun `a token without source is refused with 403`() {
            post(token = TestAuthorizationServer.token(scope = "documents:write", client = null)).andExpect {
                status { isForbidden() }
                jsonPath("$.code") { value("caller-not-identified") }
            }
        }

        @Test
        fun `a missing part is answered as a problem`() {
            mockMvc.multipart(DOCUMENTS) {
                file(filePart())
                header(HttpHeaders.AUTHORIZATION, "Bearer ${token()}")
            }.andExpect {
                status { isBadRequest() }
                content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            }
        }
    }

    private fun post(metadata: String = METADATA, token: String = token()): ResultActionsDsl = mockMvc.multipart(DOCUMENTS) {
        file(filePart())
        file(metadataPart(metadata))
        header(HttpHeaders.AUTHORIZATION, "Bearer $token")
    }

    private fun filePart() = MockMultipartFile("file", "invoice-123.pdf", "application/pdf", content)

    private fun metadataPart(json: String) = MockMultipartFile("metadata", "", "application/json", json.toByteArray())

    private fun token() = TestAuthorizationServer.token(scope = "documents:write")

    private fun archived(metadata: DocumentMetadata): ArchivedDocument = ArchivedDocument.create(
        id = DocumentId.parse(ID),
        owner = DocumentOwner(TenantId("acme"), "billing-service"),
        metadata = metadata,
        file = StoredFile("invoice-123.pdf", "application/pdf", content.size.toLong(), ObjectKey.generate()),
        sizePolicy = DocumentSizePolicy(),
        now = Instant.parse("2026-10-15T09:15:00Z"),
    ).markAvailable(Instant.parse("2026-10-15T09:15:00Z"))

    @TestConfiguration
    class UseCaseStub {

        @Bean
        @Primary
        fun archiveDocumentUseCaseStub(): ArchiveDocumentUseCase = mockk()

        @Bean
        @Primary
        fun getDocumentUseCaseStub(): GetDocumentUseCase = mockk()

        @Bean
        @Primary
        fun downloadDocumentUseCaseStub(): DownloadDocumentUseCase = mockk()

        @Bean
        @Primary
        fun searchDocumentsUseCaseStub(): SearchDocumentsUseCase = mockk()

        @Bean
        @Primary
        fun deleteDocumentUseCaseStub(): DeleteDocumentUseCase = mockk()
    }

    companion object {
        private const val DOCUMENTS = "/api/v1/documents"
        private const val DISPOSITION = "attachment; filename=\"facture-e.pdf\"; filename*=UTF-8''facture-%C3%A9.pdf"
        private const val ID = "7d7d57e1-4c50-4cb9-98b8-c5b60e8a2f01"
        private const val METADATA = """
            {"documentType": "INVOICE", "businessReference": "INV-2026-000123",
             "description": "Customer invoice", "tags": {"customerId": "CUST-456"}}
        """

        @JvmStatic
        fun archiveErrors(): List<Arguments> = listOf(
            Arguments.of(InvalidDocumentException("fileName", "must contain a file name"), 400, "invalid-document"),
            Arguments.of(EmptyDocumentException(0), 400, "empty-document"),
            Arguments.of(DocumentTooLargeException(60_000_000, 52_428_800), 413, "document-too-large"),
            Arguments.of(UnsupportedDocumentFormatException("setup.exe", listOf("pdf")), 415, "unsupported-format"),
            Arguments.of(ContentTypeMismatchException("text/plain", DocumentFormat.PDF), 415, "content-type-mismatch"),
            Arguments.of(ContentMismatchException(DocumentFormat.PDF), 415, "content-mismatch"),
            Arguments.of(ObjectStorageException("Storage down", RuntimeException()), 503, "archive-unavailable"),
            Arguments.of(DocumentPersistenceException("Database down", RuntimeException()), 503, "archive-unavailable"),
            Arguments.of(ArchivingAbandonedException(DocumentId.generate()), 503, "archive-unavailable"),
            Arguments.of(InvalidStatusTransitionException(DocumentStatus.AVAILABLE, DocumentStatus.FAILED), 500, "internal-error"),
        )

        @JvmStatic
        @DynamicPropertySource
        fun authorizationServerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri") { TestAuthorizationServer.jwksUri }
        }
    }
}

private class TrackedStream(bytes: ByteArray) : ByteArrayInputStream(bytes) {
    var closed = false

    override fun close() {
        closed = true
        super.close()
    }
}
