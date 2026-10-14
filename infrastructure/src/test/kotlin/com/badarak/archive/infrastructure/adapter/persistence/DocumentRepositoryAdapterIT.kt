package com.badarak.archive.infrastructure.adapter.persistence

import com.badarak.archive.domain.model.ArchivedDocument
import com.badarak.archive.domain.model.DocumentId
import com.badarak.archive.domain.model.DocumentSizePolicy
import com.badarak.archive.domain.model.DocumentType
import com.badarak.archive.domain.model.ObjectKey
import com.badarak.archive.domain.model.TenantId
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
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
        assertEquals(document.objectKey.value, stored.objectKey)
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

    private fun pendingDocument(): ArchivedDocument {
        val id = DocumentId.generate()
        return ArchivedDocument.create(
            id = id,
            tenantId = TenantId("acme"),
            documentType = DocumentType("INVOICE"),
            source = "BILLING-SERVICE",
            businessReference = "INV-2026-000123",
            description = "Customer invoice",
            tags = mapOf("customerId" to "CUST-456"),
            fileName = "invoice-123.pdf",
            contentType = "application/pdf",
            sizeBytes = 248_120,
            objectKey = ObjectKey.generate(),
            sizePolicy = DocumentSizePolicy(),
            now = createdAt,
        )
    }
}
