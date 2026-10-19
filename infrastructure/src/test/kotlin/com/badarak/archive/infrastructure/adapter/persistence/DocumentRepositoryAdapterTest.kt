package com.badarak.archive.infrastructure.adapter.persistence

import com.badarak.archive.domain.model.ArchivedDocument
import com.badarak.archive.domain.model.DocumentId
import com.badarak.archive.domain.model.DocumentMetadata
import com.badarak.archive.domain.model.DocumentOwner
import com.badarak.archive.domain.model.DocumentSearchCriteria
import com.badarak.archive.domain.model.DocumentSizePolicy
import com.badarak.archive.domain.model.DocumentStatus
import com.badarak.archive.domain.model.DocumentType
import com.badarak.archive.domain.model.InvalidDocumentTypeException
import com.badarak.archive.domain.model.ObjectKey
import com.badarak.archive.domain.model.PageRequest
import com.badarak.archive.domain.model.StoredFile
import com.badarak.archive.domain.model.TenantId
import com.badarak.archive.domain.port.outbound.DocumentPersistenceException
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import java.time.Instant
import java.util.UUID

class DocumentRepositoryAdapterTest {

    private val repository = mockk<SpringDataArchivedDocumentRepository>()
    private val adapter = DocumentRepositoryAdapter(repository)

    @Test
    fun `a database failure becomes a document persistence error`() {
        val failure = DataAccessResourceFailureException("Connection refused")
        every { repository.save(any()) } throws failure
        val document = document()

        val exception = assertThrows(DocumentPersistenceException::class.java) { adapter.save(document) }

        assertSame(failure, exception.cause)
        assertEquals("Could not save document ${document.id}", exception.message)
    }

    @Test
    fun `a database failure during a search becomes a document persistence error`() {
        val failure = DataAccessResourceFailureException("Connection refused")
        every { repository.findAll(any<Specification<ArchivedDocumentEntity>>(), any<Pageable>()) } throws failure

        val exception = assertThrows(DocumentPersistenceException::class.java) {
            adapter.search(TenantId("acme"), DocumentStatus.AVAILABLE, DocumentSearchCriteria(), PageRequest())
        }

        assertSame(failure, exception.cause)
    }

    @Test
    fun `a database failure while looking for stalled documents becomes a document persistence error`() {
        val failure = DataAccessResourceFailureException("Connection refused")
        every { repository.findByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(any(), any(), any()) } throws failure

        val exception = assertThrows(DocumentPersistenceException::class.java) {
            adapter.findStalled(DocumentStatus.DELETING, Instant.parse("2026-10-14T09:15:00Z"), 10)
        }

        assertSame(failure, exception.cause)
    }

    @Test
    fun `a stored row that breaks a domain rule becomes a document persistence error`() {
        val row = entity(documentType = "invoice")
        every { repository.findByIdAndTenantId(row.id, "acme") } returns row

        val exception = assertThrows(DocumentPersistenceException::class.java) {
            adapter.findByIdAndTenant(DocumentId(row.id), TenantId("acme"))
        }

        assertEquals("Document ${row.id} is stored in an invalid state", exception.message)
        assertTrue(exception.cause is InvalidDocumentTypeException)
    }

    @Test
    fun `a stored row with an unknown status becomes a document persistence error`() {
        val row = entity(status = "ARCHIVED")
        every { repository.findByIdAndTenantId(row.id, "acme") } returns row

        assertThrows(DocumentPersistenceException::class.java) { adapter.findByIdAndTenant(DocumentId(row.id), TenantId("acme")) }
    }

    private fun entity(documentType: String = "INVOICE", status: String = "AVAILABLE") = ArchivedDocumentEntity(
        id = UUID.randomUUID(),
        tenantId = "acme",
        documentType = documentType,
        source = "billing-service",
        businessReference = null,
        description = null,
        tags = emptyMap(),
        fileName = "invoice.pdf",
        contentType = "application/pdf",
        sizeBytes = 1,
        objectKey = "documents/${UUID.randomUUID()}",
        status = status,
        createdAt = Instant.parse("2026-10-14T09:15:00Z"),
        updatedAt = Instant.parse("2026-10-14T09:15:00Z"),
    )

    private fun document(): ArchivedDocument = ArchivedDocument.create(
        id = DocumentId.generate(),
        owner = DocumentOwner(TenantId("acme"), "billing-service"),
        metadata = DocumentMetadata(DocumentType("INVOICE"), null, null, emptyMap()),
        file = StoredFile("invoice.pdf", "application/pdf", 1, ObjectKey.generate()),
        sizePolicy = DocumentSizePolicy(),
        now = Instant.parse("2026-10-14T09:15:00Z"),
    )
}
