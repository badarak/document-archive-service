package com.badarak.archive.domain.service

import com.badarak.archive.domain.model.ArchivedDocument
import com.badarak.archive.domain.model.DocumentFormatPolicy
import com.badarak.archive.domain.model.DocumentSizePolicy
import com.badarak.archive.domain.model.DocumentStatus
import com.badarak.archive.domain.model.DocumentType
import com.badarak.archive.domain.model.EmptyDocumentException
import com.badarak.archive.domain.model.InvalidDocumentTypeException
import com.badarak.archive.domain.model.ObjectKey
import com.badarak.archive.domain.model.TenantId
import com.badarak.archive.domain.model.UnsupportedDocumentFormatException
import com.badarak.archive.domain.port.inbound.ArchiveDocumentCommand
import com.badarak.archive.domain.port.inbound.AuthenticatedCaller
import com.badarak.archive.domain.port.outbound.DocumentPersistenceException
import com.badarak.archive.domain.port.outbound.DocumentRepository
import com.badarak.archive.domain.port.outbound.ObjectStorage
import com.badarak.archive.domain.port.outbound.ObjectStorageException
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.InputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ArchiveDocumentServiceTest {

    private val now = Instant.parse("2026-10-14T09:15:00Z")
    private val content = "%PDF-1.7\n%âãÏÓ\n1 0 obj".toByteArray()

    private val documents = mockk<DocumentRepository>()
    private val storage = mockk<ObjectStorage>()
    private val service = ArchiveDocumentService(
        documents = documents,
        storage = storage,
        formatPolicy = DocumentFormatPolicy(),
        sizePolicy = DocumentSizePolicy(maxSizeBytes = 1_000),
        clock = Clock.fixed(now, ZoneOffset.UTC),
    )

    private val saved = mutableListOf<ArchivedDocument>()
    private var uploaded: ByteArray? = null

    @BeforeEach
    fun acceptEveryCall() {
        every { documents.save(capture(saved)) } just Runs
        every { storage.store(any(), any(), any(), any()) } answers { uploaded = secondArg<InputStream>().readAllBytes() }
    }

    @Nested
    inner class Success {

        @Test
        fun `records the document as pending, uploads its content, then records it as available`() {
            val document = service.archive(command())

            assertEquals(listOf(DocumentStatus.PENDING, DocumentStatus.AVAILABLE), saved.map { it.status })
            assertEquals(DocumentStatus.AVAILABLE, document.status)
            assertSame(saved.last(), document)
            assertArrayEquals(content, uploaded)
            verify { storage.store(document.objectKey, any(), content.size.toLong(), "application/pdf") }
        }

        @Test
        fun `the document carries the caller, the metadata and a generated object key`() {
            val document = service.archive(command())

            assertEquals(TenantId("acme"), document.tenantId)
            assertEquals("billing-service", document.source)
            assertEquals(DocumentType("INVOICE"), document.documentType)
            assertEquals("INV-2026-000123", document.businessReference)
            assertEquals("Customer invoice", document.description)
            assertEquals(mapOf("customerId" to "CUST-456"), document.tags)
            assertEquals(content.size.toLong(), document.sizeBytes)
            assertTrue(document.objectKey.value.startsWith(ObjectKey.PREFIX))
            assertEquals(now, document.createdAt)
            assertEquals(now, document.updatedAt)
        }

        @Test
        fun `stores the normalised file name and media type`() {
            val document = service.archive(command(fileName = "C:\\docs\\invoice.pdf", contentType = "Application/PDF; charset=binary"))

            assertEquals("invoice.pdf", document.fileName)
            assertEquals("application/pdf", document.contentType)
        }
    }

    @Nested
    inner class Refusal {

        @Test
        fun `an unsupported format leaves no trace`() {
            assertThrows(UnsupportedDocumentFormatException::class.java) { service.archive(command(fileName = "setup.exe")) }

            assertNothingWritten()
        }

        @Test
        fun `an empty document leaves no trace`() {
            assertThrows(EmptyDocumentException::class.java) { service.archive(command(content = ByteArray(0))) }

            assertNothingWritten()
        }

        @Test
        fun `an invalid document type leaves no trace`() {
            assertThrows(InvalidDocumentTypeException::class.java) { service.archive(command(documentType = "invoice")) }

            assertNothingWritten()
        }

        private fun assertNothingWritten() {
            verify(exactly = 0) { documents.save(any()) }
            verify(exactly = 0) { storage.store(any(), any(), any(), any()) }
        }
    }

    @Nested
    inner class Failure {

        private val storageFailure = ObjectStorageException("Storage unavailable", RuntimeException("Connection refused"))
        private val persistenceFailure = DocumentPersistenceException("Database unavailable", RuntimeException("Connection refused"))

        @Test
        fun `nothing is uploaded when the pending document cannot be recorded`() {
            every { documents.save(any()) } throws persistenceFailure

            val exception = assertThrows(DocumentPersistenceException::class.java) { service.archive(command()) }

            assertSame(persistenceFailure, exception)
            verify(exactly = 0) { storage.store(any(), any(), any(), any()) }
        }

        @Test
        fun `a failed upload records the document as failed`() {
            every { storage.store(any(), any(), any(), any()) } throws storageFailure

            val exception = assertThrows(ObjectStorageException::class.java) { service.archive(command()) }

            assertSame(storageFailure, exception)
            assertEquals(listOf(DocumentStatus.PENDING, DocumentStatus.FAILED), saved.map { it.status })
        }

        @Test
        fun `a failed upload leaves the document pending when it cannot be recorded as failed`() {
            every { storage.store(any(), any(), any(), any()) } throws storageFailure
            every { documents.save(match { it.status == DocumentStatus.FAILED }) } throws persistenceFailure

            val exception = assertThrows(ObjectStorageException::class.java) { service.archive(command()) }

            assertEquals(listOf(persistenceFailure), exception.suppressed.toList())
            assertEquals(listOf(DocumentStatus.PENDING), saved.map { it.status })
        }

        @Test
        fun `a failed final save records the document as failed instead of available`() {
            every { documents.save(match { it.status == DocumentStatus.AVAILABLE }) } throws persistenceFailure

            val exception = assertThrows(DocumentPersistenceException::class.java) { service.archive(command()) }

            assertSame(persistenceFailure, exception)
            assertEquals(listOf(DocumentStatus.PENDING, DocumentStatus.FAILED), saved.map { it.status })
        }

        @Test
        fun `a failed final save leaves the document pending when it cannot be recorded as failed`() {
            val failedSave = DocumentPersistenceException("Database still unavailable", RuntimeException("Connection refused"))
            every { documents.save(match { it.status == DocumentStatus.AVAILABLE }) } throws persistenceFailure
            every { documents.save(match { it.status == DocumentStatus.FAILED }) } throws failedSave

            val exception = assertThrows(DocumentPersistenceException::class.java) { service.archive(command()) }

            assertSame(persistenceFailure, exception)
            assertEquals(listOf(failedSave), exception.suppressed.toList())
            assertEquals(listOf(DocumentStatus.PENDING), saved.map { it.status })
        }
    }

    private fun command(
        documentType: String = "INVOICE",
        fileName: String = "invoice-123.pdf",
        contentType: String = "application/pdf",
        content: ByteArray = this.content,
    ) = ArchiveDocumentCommand(
        caller = AuthenticatedCaller(TenantId("acme"), "billing-service"),
        documentType = documentType,
        businessReference = "INV-2026-000123",
        description = "Customer invoice",
        tags = mapOf("customerId" to "CUST-456"),
        fileName = fileName,
        contentType = contentType,
        sizeBytes = content.size.toLong(),
        content = content.inputStream(),
    )
}
