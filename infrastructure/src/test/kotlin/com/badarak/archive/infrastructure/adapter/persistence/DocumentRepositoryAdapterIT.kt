package com.badarak.archive.infrastructure.adapter.persistence

import com.badarak.archive.domain.model.ArchivedDocument
import com.badarak.archive.domain.model.DocumentId
import com.badarak.archive.domain.model.DocumentMetadata
import com.badarak.archive.domain.model.DocumentOwner
import com.badarak.archive.domain.model.DocumentSearchCriteria
import com.badarak.archive.domain.model.DocumentSizePolicy
import com.badarak.archive.domain.model.DocumentStatus
import com.badarak.archive.domain.model.DocumentType
import com.badarak.archive.domain.model.ObjectKey
import com.badarak.archive.domain.model.PageRequest
import com.badarak.archive.domain.model.StoredFile
import com.badarak.archive.domain.model.TenantId
import com.badarak.archive.domain.port.outbound.DocumentPersistenceException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
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
    private val deletedAt = Instant.parse("2026-10-16T10:00:00.000001Z")

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
        assertEquals(document.file.objectKey.value, stored.objectKey)
        assertEquals("PENDING", stored.status)
        assertEquals(createdAt, stored.createdAt)
        assertEquals(createdAt, stored.updatedAt)
        assertNull(stored.deletedAt)
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
    fun `a created document is stored with its initial state`() {
        val document = pendingDocument()

        adapter.create(document)

        val stored = repository.findById(document.id.value).orElseThrow()
        assertEquals("PENDING", stored.status)
        assertEquals(document.file.objectKey.value, stored.objectKey)
        assertEquals(createdAt, stored.updatedAt)
    }

    @Test
    fun `creating a document never replaces an existing one`() {
        val document = pendingDocument()
        adapter.create(document)

        assertThrows(DocumentPersistenceException::class.java) { adapter.create(document.markAvailable(later)) }

        assertEquals("PENDING", repository.findById(document.id.value).orElseThrow().status)
    }

    @Test
    fun `a transition from the stored status is recorded with its dates`() {
        val document = pendingDocument().markAvailable(later).markDeleting(later)
        adapter.create(document)
        val deleted = document.markDeleted(deletedAt)

        assertTrue(adapter.transition(deleted, DocumentStatus.DELETING))

        val stored = repository.findById(document.id.value).orElseThrow()
        assertEquals("DELETED", stored.status)
        assertEquals(deletedAt, stored.updatedAt)
        assertEquals(deletedAt, stored.deletedAt)
        assertEquals(createdAt, stored.createdAt)
    }

    @Test
    fun `a transition from another status than the stored one is not recorded`() {
        val pending = pendingDocument()
        adapter.create(pending)
        assertTrue(adapter.transition(pending.markFailed(later), DocumentStatus.PENDING))

        assertFalse(adapter.transition(pending.markAvailable(deletedAt), DocumentStatus.PENDING))

        val stored = repository.findById(pending.id.value).orElseThrow()
        assertEquals("FAILED", stored.status)
        assertEquals(later, stored.updatedAt)
    }

    @Test
    fun `a transition of an unknown document is not recorded`() {
        assertFalse(adapter.transition(pendingDocument().markAvailable(later), DocumentStatus.PENDING))

        assertEquals(0, repository.count())
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
        assertEquals(document.file, found.file)
        assertEquals(DocumentStatus.AVAILABLE, found.status)
        assertEquals(createdAt, found.createdAt)
        assertEquals(later, found.updatedAt)
    }

    @Test
    fun `a deleted document is stored and read back with its deletion date`() {
        val deleted = pendingDocument().markAvailable(later).markDeleting(later).markDeleted(deletedAt)
        adapter.save(deleted)

        val found = adapter.findByIdAndTenant(deleted.id, TenantId("acme"))!!

        assertEquals(DocumentStatus.DELETED, found.status)
        assertEquals(deletedAt, found.deletedAt)
        assertEquals(deletedAt, repository.findById(deleted.id.value).orElseThrow().deletedAt)
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

    @Test
    fun `a search returns only the documents of the tenant in the requested status`() {
        val expected = stored()
        stored(tenant = "globex")
        stored(status = DocumentStatus.PENDING)

        val page = adapter.search(TenantId("acme"), DocumentStatus.AVAILABLE, DocumentSearchCriteria(), PageRequest())

        assertEquals(listOf(expected.id), page.documents.map { it.id })
        assertEquals(1, page.totalElements)
        assertEquals(expected.file, page.documents.single().file)
    }

    @Test
    fun `a search filters on the document type, the source and the business reference`() {
        val expected = stored(type = "CONTRACT", source = "sales-service", reference = "CTR-1")
        stored(type = "INVOICE", source = "sales-service", reference = "CTR-1")
        stored(type = "CONTRACT", source = "billing-service", reference = "CTR-1")
        stored(type = "CONTRACT", source = "sales-service", reference = "CTR-2")
        val criteria = DocumentSearchCriteria(DocumentType("CONTRACT"), "sales-service", "CTR-1")

        val page = adapter.search(TenantId("acme"), DocumentStatus.AVAILABLE, criteria, PageRequest())

        assertEquals(listOf(expected.id), page.documents.map { it.id })
    }

    @Test
    fun `a search includes the documents created on both bounds of the period`() {
        stored(createdAt = createdAt.minusNanos(1_000))
        val first = stored(createdAt = createdAt)
        val last = stored(createdAt = later)
        stored(createdAt = later.plusNanos(1_000))
        val criteria = DocumentSearchCriteria(createdFrom = createdAt, createdTo = later)

        val page = adapter.search(TenantId("acme"), DocumentStatus.AVAILABLE, criteria, PageRequest())

        assertEquals(listOf(last.id, first.id), page.documents.map { it.id })
    }

    @Test
    fun `a search returns the newest documents first, then orders by descending identifier`() {
        val oldest = stored(createdAt = createdAt)
        val sameInstant = List(3) { stored(createdAt = later) }

        val page = adapter.search(TenantId("acme"), DocumentStatus.AVAILABLE, DocumentSearchCriteria(), PageRequest())

        val expected = sameInstant.sortedByDescending { it.id.value.toString() }.map { it.id } + oldest.id
        assertEquals(expected, page.documents.map { it.id })
    }

    @Test
    fun `a search returns the requested page and the number of matching documents`() {
        val documents = List(5) { stored(createdAt = createdAt.plusSeconds(it.toLong())) }.reversed()

        val second = adapter.search(TenantId("acme"), DocumentStatus.AVAILABLE, DocumentSearchCriteria(), PageRequest(1, 2))
        val beyond = adapter.search(TenantId("acme"), DocumentStatus.AVAILABLE, DocumentSearchCriteria(), PageRequest(3, 2))

        assertEquals(documents.subList(2, 4).map { it.id }, second.documents.map { it.id })
        assertEquals(5, second.totalElements)
        assertEquals(3, second.totalPages)
        assertEquals(PageRequest(1, 2), second.request)
        assertTrue(beyond.documents.isEmpty())
        assertEquals(5, beyond.totalElements)
    }

    @Test
    fun `stalled documents are found whatever their tenant, oldest update first, before the given instant only`() {
        val oldest = stored(tenant = "acme", status = DocumentStatus.DELETING, createdAt = createdAt)
        val other = stored(tenant = "globex", status = DocumentStatus.DELETING, createdAt = createdAt.plusSeconds(1))
        stored(tenant = "acme", status = DocumentStatus.DELETING, createdAt = createdAt.plusSeconds(2))
        stored(tenant = "acme", status = DocumentStatus.AVAILABLE, createdAt = createdAt)

        val stalled = adapter.findStalled(DocumentStatus.DELETING, createdAt.plusSeconds(2), 10)

        assertEquals(listOf(oldest.id, other.id), stalled.map { it.id })
        assertEquals(TenantId("globex"), stalled.last().owner.tenantId)
    }

    @Test
    fun `no more stalled documents than the limit are found`() {
        val oldest = stored(status = DocumentStatus.DELETING, createdAt = createdAt)
        stored(status = DocumentStatus.DELETING, createdAt = createdAt.plusSeconds(1))

        assertEquals(listOf(oldest.id), adapter.findStalled(DocumentStatus.DELETING, later.plusSeconds(60), 1).map { it.id })
    }

    private fun stored(
        tenant: String = "acme",
        status: DocumentStatus = DocumentStatus.AVAILABLE,
        type: String = "INVOICE",
        source: String = "billing-service",
        reference: String = "INV-1",
        createdAt: Instant = this.createdAt,
    ): ArchivedDocument = ArchivedDocument(
        id = DocumentId.generate(),
        owner = DocumentOwner(TenantId(tenant), source),
        metadata = DocumentMetadata(DocumentType(type), reference, null, emptyMap()),
        file = StoredFile("invoice.pdf", "application/pdf", 1, ObjectKey.generate()),
        status = status,
        createdAt = createdAt,
        updatedAt = createdAt,
    ).also { adapter.save(it) }

    private fun pendingDocument(): ArchivedDocument = ArchivedDocument.create(
        id = DocumentId.generate(),
        owner = DocumentOwner(TenantId("acme"), "BILLING-SERVICE"),
        metadata = DocumentMetadata(DocumentType("INVOICE"), "INV-2026-000123", "Customer invoice", mapOf("customerId" to "CUST-456")),
        file = StoredFile("invoice-123.pdf", "application/pdf", 248_120, ObjectKey.generate()),
        sizePolicy = DocumentSizePolicy(),
        now = createdAt,
    )
}
