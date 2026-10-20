package com.badarak.archive.domain.service

import com.badarak.archive.domain.model.ArchivedDocument
import com.badarak.archive.domain.model.DocumentId
import com.badarak.archive.domain.model.DocumentMetadata
import com.badarak.archive.domain.model.DocumentNotFoundException
import com.badarak.archive.domain.model.DocumentOwner
import com.badarak.archive.domain.model.DocumentStatus
import com.badarak.archive.domain.model.DocumentType
import com.badarak.archive.domain.model.ObjectKey
import com.badarak.archive.domain.model.StoredFile
import com.badarak.archive.domain.model.TenantId
import com.badarak.archive.domain.port.inbound.AuthenticatedCaller
import com.badarak.archive.domain.port.inbound.DocumentQuery
import com.badarak.archive.domain.port.outbound.DocumentPersistenceException
import com.badarak.archive.domain.port.outbound.DocumentRepository
import com.badarak.archive.domain.port.outbound.ObjectStorage
import com.badarak.archive.domain.port.outbound.ObjectStorageException
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class DeleteDocumentServiceTest {

    private val documents = mockk<DocumentRepository>()
    private val storage = mockk<ObjectStorage>()
    private val now = Instant.parse("2026-10-18T10:00:00Z")
    private val service = DeleteDocumentService(documents, storage, Clock.fixed(now, ZoneOffset.UTC))

    private val id = DocumentId.generate()
    private val key = ObjectKey.generate()
    private val query = DocumentQuery(AuthenticatedCaller(TenantId("acme"), "billing-service"), id)
    private val saved = mutableListOf<ArchivedDocument>()

    @BeforeEach
    fun acceptEveryWrite() {
        every { documents.create(capture(saved)) } just runs
        every { documents.transition(capture(saved), any()) } returns true
        every { storage.delete(any()) } just runs
    }

    @Test
    fun `an available document is recorded as deleting, its content is removed, then it is recorded as deleted`() {
        every { documents.findByIdAndTenant(id, TenantId("acme")) } returns document(DocumentStatus.AVAILABLE)

        service.delete(query)

        verifyOrder {
            documents.transition(match { it.status == DocumentStatus.DELETING }, DocumentStatus.AVAILABLE)
            storage.delete(key)
            documents.transition(match { it.status == DocumentStatus.DELETED }, DocumentStatus.DELETING)
        }
        assertEquals(listOf(DocumentStatus.DELETING, DocumentStatus.DELETED), saved.map { it.status })
        assertEquals(now, saved.last().deletedAt)
        assertEquals(now, saved.last().updatedAt)
    }

    @Test
    fun `a document left deleting is finished`() {
        every { documents.findByIdAndTenant(id, TenantId("acme")) } returns document(DocumentStatus.DELETING)

        service.delete(query)

        verify { storage.delete(key) }
        assertEquals(listOf(DocumentStatus.DELETED), saved.map { it.status })
    }

    @Test
    fun `deleting a deleted document succeeds without touching the storage`() {
        every { documents.findByIdAndTenant(id, TenantId("acme")) } returns document(DocumentStatus.DELETED)

        service.delete(query)

        verify(exactly = 0) { storage.delete(any()) }
        verify(exactly = 0) { documents.transition(any(), any()) }
    }

    @Test
    fun `a document that does not exist for the caller's tenant is not found`() {
        every { documents.findByIdAndTenant(id, TenantId("acme")) } returns null

        val exception = assertThrows(DocumentNotFoundException::class.java) { service.delete(query) }

        assertEquals(id, exception.id)
        verify(exactly = 0) { storage.delete(any()) }
    }

    @ParameterizedTest
    @EnumSource(DocumentStatus::class, names = ["PENDING", "FAILED"])
    fun `a document that was never available is not found`(status: DocumentStatus) {
        every { documents.findByIdAndTenant(id, TenantId("acme")) } returns document(status)

        assertThrows(DocumentNotFoundException::class.java) { service.delete(query) }

        verify(exactly = 0) { storage.delete(any()) }
        verify(exactly = 0) { documents.transition(any(), any()) }
    }

    @Test
    fun `a deletion that another one recorded as deleting first still finishes the document`() {
        every { documents.findByIdAndTenant(id, TenantId("acme")) } returns document(DocumentStatus.AVAILABLE)
        every { documents.transition(match { it.status == DocumentStatus.DELETING }, DocumentStatus.AVAILABLE) } returns false

        service.delete(query)

        verify { storage.delete(key) }
        verify { documents.transition(match { it.status == DocumentStatus.DELETED }, DocumentStatus.DELETING) }
    }

    @Test
    fun `when the storage fails, the document stays deleting and the error is rethrown`() {
        val failure = ObjectStorageException("Storage down", RuntimeException())
        every { documents.findByIdAndTenant(id, TenantId("acme")) } returns document(DocumentStatus.AVAILABLE)
        every { storage.delete(key) } throws failure

        assertSame(failure, assertThrows(ObjectStorageException::class.java) { service.delete(query) })

        assertEquals(listOf(DocumentStatus.DELETING), saved.map { it.status })
    }

    @Test
    fun `when the document cannot be recorded as deleting, its content is kept`() {
        every { documents.findByIdAndTenant(id, TenantId("acme")) } returns document(DocumentStatus.AVAILABLE)
        every { documents.transition(any(), any()) } throws DocumentPersistenceException("Database down", RuntimeException())

        assertThrows(DocumentPersistenceException::class.java) { service.delete(query) }

        verify(exactly = 0) { storage.delete(any()) }
    }

    private fun document(status: DocumentStatus) = ArchivedDocument(
        id = id,
        owner = DocumentOwner(TenantId("acme"), "billing-service"),
        metadata = DocumentMetadata(DocumentType("INVOICE"), null, null, emptyMap()),
        file = StoredFile("invoice.pdf", "application/pdf", 1, key),
        status = status,
        createdAt = Instant.parse("2026-10-15T09:15:00Z"),
        updatedAt = Instant.parse("2026-10-15T09:15:00Z"),
        deletedAt = Instant.parse("2026-10-15T09:15:00Z").takeIf { status == DocumentStatus.DELETED },
    )
}
