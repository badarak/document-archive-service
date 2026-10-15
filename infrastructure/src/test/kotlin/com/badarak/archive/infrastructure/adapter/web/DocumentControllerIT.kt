package com.badarak.archive.infrastructure.adapter.web

import com.badarak.archive.domain.model.ArchivedDocument
import com.badarak.archive.domain.model.ContentMismatchException
import com.badarak.archive.domain.model.ContentTypeMismatchException
import com.badarak.archive.domain.model.DocumentContent
import com.badarak.archive.domain.model.DocumentFormat
import com.badarak.archive.domain.model.DocumentId
import com.badarak.archive.domain.model.DocumentMetadata
import com.badarak.archive.domain.model.DocumentOwner
import com.badarak.archive.domain.model.DocumentSizePolicy
import com.badarak.archive.domain.model.DocumentStatus
import com.badarak.archive.domain.model.DocumentTooLargeException
import com.badarak.archive.domain.model.DocumentType
import com.badarak.archive.domain.model.EmptyDocumentException
import com.badarak.archive.domain.model.InvalidDocumentException
import com.badarak.archive.domain.model.InvalidStatusTransitionException
import com.badarak.archive.domain.model.ObjectKey
import com.badarak.archive.domain.model.TenantId
import com.badarak.archive.domain.model.UnsupportedDocumentFormatException
import com.badarak.archive.domain.port.inbound.ArchiveDocumentCommand
import com.badarak.archive.domain.port.inbound.ArchiveDocumentUseCase
import com.badarak.archive.domain.port.outbound.DocumentPersistenceException
import com.badarak.archive.domain.port.outbound.ObjectStorageException
import com.badarak.archive.infrastructure.security.TestAuthorizationServer
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
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
import org.springframework.test.web.servlet.multipart
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

    private val content = "%PDF-1.7\n%âãÏÓ".toByteArray()
    private var received: ArchiveDocumentCommand? = null
    private var receivedBytes: ByteArray? = null

    @BeforeEach
    fun acceptEveryDocument() {
        clearMocks(useCase)
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
        content = DocumentContent("invoice-123.pdf", "application/pdf", content.size.toLong(), ObjectKey.generate()),
        sizePolicy = DocumentSizePolicy(),
        now = Instant.parse("2026-10-15T09:15:00Z"),
    ).markAvailable(Instant.parse("2026-10-15T09:15:00Z"))

    @TestConfiguration
    class UseCaseStub {

        @Bean
        @Primary
        fun archiveDocumentUseCaseStub(): ArchiveDocumentUseCase = mockk()
    }

    companion object {
        private const val DOCUMENTS = "/api/v1/documents"
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
            Arguments.of(InvalidStatusTransitionException(DocumentStatus.AVAILABLE, DocumentStatus.FAILED), 500, "internal-error"),
        )

        @JvmStatic
        @DynamicPropertySource
        fun authorizationServerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri") { TestAuthorizationServer.jwksUri }
        }
    }
}
