package com.badarak.archive.infrastructure.adapter.web

import com.badarak.archive.domain.model.ArchivedDocument
import com.badarak.archive.domain.model.DocumentContent
import com.badarak.archive.domain.model.DocumentId
import com.badarak.archive.domain.model.DocumentMetadata
import com.badarak.archive.domain.model.DocumentOwner
import com.badarak.archive.domain.model.DocumentSizePolicy
import com.badarak.archive.domain.model.DocumentType
import com.badarak.archive.domain.model.ObjectKey
import com.badarak.archive.domain.model.TenantId
import com.badarak.archive.domain.port.inbound.ArchiveDocumentCommand
import com.badarak.archive.domain.port.inbound.ArchiveDocumentUseCase
import com.badarak.archive.infrastructure.security.TestAuthorizationServer
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
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

    private fun post(metadata: String = METADATA): ResultActionsDsl = mockMvc.multipart(DOCUMENTS) {
        file(filePart())
        file(metadataPart(metadata))
        header(HttpHeaders.AUTHORIZATION, "Bearer ${token()}")
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
        @DynamicPropertySource
        fun authorizationServerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri") { TestAuthorizationServer.jwksUri }
        }
    }
}
