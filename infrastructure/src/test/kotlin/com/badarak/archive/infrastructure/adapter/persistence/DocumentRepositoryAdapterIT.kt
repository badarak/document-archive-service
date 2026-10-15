package com.badarak.archive.infrastructure.adapter.persistence

import com.badarak.archive.domain.model.ArchivedDocument
import com.badarak.archive.domain.model.DocumentContent
import com.badarak.archive.domain.model.DocumentId
import com.badarak.archive.domain.model.DocumentMetadata
import com.badarak.archive.domain.model.DocumentOwner
import com.badarak.archive.domain.model.DocumentSizePolicy
import com.badarak.archive.domain.model.DocumentStatus
import com.badarak.archive.domain.model.DocumentType
import com.badarak.archive.domain.model.ObjectKey
import com.badarak.archive.domain.model.TenantId
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.Instant

@SpringBootTest
@ActiveProfiles("test")
class DocumentRepositoryAdapterIT {

    @Autowired
    private lateinit var adapter: DocumentRepositoryAdapter

    @Autowired
    private lateinit var repository: SpringDataArchivedDocumentRepository

    private val createdAt = Instant.parse("2026-10-14T09:15:00.123456Z")
    private val later = Instant.parse("2026-10-14T09:15:05.654321Z")

    @AfterEach
    fun deleteDocuments() {
        repository.deleteAll()
    }

    @Test
    fun `a new document is stored with all its metadata`() {
        val document = pendingDocument()

        adapter.save(document)

        val stored = repository.findById(document.id.value).orElseThrow()
        assertEquals("acme", stored.tenantId)
        assertEquals("INVOICE", stored.documentType)
        assertEquals("BILLING-SERVICE", stored.source)
        assertEquals("INV-2026-000123", stored.businessReference)
        assertEquals("Customer invoice", stored.description)
        assertEquals(mapOf("customerId" to "CUST-456"), stored.tags)
        assertEquals("invoice-123.pdf", stored.fileName)
        assertEquals("application/pdf", stored.contentType)
        assertEquals(248_120, stored.sizeBytes)
        assertEquals(document.content.objectKey.value, stored.objectKey)
        assertEquals("PENDING", stored.status)
        assertEquals(createdAt, stored.createdAt)
        assertEquals(createdAt, stored.updatedAt)
    }

    @Test
    fun `saving a document again replaces its state`() {
        val pending = pendingDocument()
        adapter.save(pending)

        adapter.save(pending.markAvailable(later))

        val stored = repository.findById(pending.id.value).orElseThrow()
        assertEquals(1, repository.count())
        assertEquals("AVAILABLE", stored.status)
        assertEquals(createdAt, stored.createdAt)
        assertEquals(later, stored.updatedAt)
    }

    @Test
    fun `a stored document is read back with all its fields for its tenant`() {
        val document = pendingDocument().markAvailable(later)
        adapter.save(document)

        val found = adapter.findByIdAndTenant(document.id, TenantId("acme"))!!

        assertEquals(document.id, found.id)
        assertEquals(document.owner, found.owner)
        assertEquals(document.metadata.documentType, found.metadata.documentType)
        assertEquals(document.metadata.businessReference, found.metadata.businessReference)
        assertEquals(document.metadata.description, found.metadata.description)
        assertEquals(document.metadata.tags, found.metadata.tags)
        assertEquals(document.content, found.content)
        assertEquals(DocumentStatus.AVAILABLE, found.status)
        assertEquals(createdAt, found.createdAt)
        assertEquals(later, found.updatedAt)
    }

    @Test
    fun `a document of another tenant is not found`() {
        val document = pendingDocument()
        adapter.save(document)

        assertNull(adapter.findByIdAndTenant(document.id, TenantId("globex")))
    }

    @Test
    fun `an unknown document is not found`() {
        assertNull(adapter.findByIdAndTenant(DocumentId.generate(), TenantId("acme")))
    }

    private fun pendingDocument(): ArchivedDocument = ArchivedDocument.create(
        id = DocumentId.generate(),
        owner = DocumentOwner(TenantId("acme"), "BILLING-SERVICE"),
        metadata = DocumentMetadata(DocumentType("INVOICE"), "INV-2026-000123", "Customer invoice", mapOf("customerId" to "CUST-456")),
        content = DocumentContent("invoice-123.pdf", "application/pdf", 248_120, ObjectKey.generate()),
        sizePolicy = DocumentSizePolicy(),
        now = createdAt,
    )
}
