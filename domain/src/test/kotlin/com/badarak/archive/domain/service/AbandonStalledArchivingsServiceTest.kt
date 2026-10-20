package com.badarak.archive.domain.service

import com.badarak.archive.domain.model.ArchivedDocument
import com.badarak.archive.domain.model.DocumentId
import com.badarak.archive.domain.model.DocumentMetadata
import com.badarak.archive.domain.model.DocumentOwner
import com.badarak.archive.domain.model.DocumentStatus
import com.badarak.archive.domain.model.DocumentType
import com.badarak.archive.domain.model.ObjectKey
import com.badarak.archive.domain.model.StalledDocumentsPolicy
import com.badarak.archive.domain.model.StoredFile
import com.badarak.archive.domain.model.TenantId
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
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class AbandonStalledArchivingsServiceTest {

    private val documents = mockk<DocumentRepository>()
    private val storage = mockk<ObjectStorage>()
    private val now = Instant.parse("2026-10-19T10:00:00Z")
    private val gracePeriod = Duration.ofMinutes(15)
    private val policy = StalledDocumentsPolicy(gracePeriod, 2)
    private val service = AbandonStalledArchivingsService(documents, storage, Clock.fixed(now, ZoneOffset.UTC), policy)
    private val saved = mutableListOf<ArchivedDocument>()

    @BeforeEach
    fun acceptEveryWrite() {
        every { documents.create(capture(saved)) } just runs
        every { documents.transition(capture(saved), any()) } returns true
        every { storage.delete(any()) } just runs
    }

    @Test
    fun `abandons one batch of the archivings stalled for longer than the grace period`() {
        val first = pending("acme")
        val second = pending("globex")
        every { documents.findStalled(DocumentStatus.PENDING, now.minus(gracePeriod), 2) } returns listOf(first, second)

        assertEquals(2, service.abandon())

        assertEquals(listOf(first.id, second.id), saved.map { it.id })
        assertEquals(listOf(DocumentStatus.FAILED, DocumentStatus.FAILED), saved.map { it.status })
        assertEquals(now, saved.first().updatedAt)
    }

    @Test
    fun `removes the content before recording the document as failed`() {
        val stalled = pending("acme")
        every { documents.findStalled(any(), any(), any()) } returns listOf(stalled)

        service.abandon()

        verifyOrder {
            storage.delete(stalled.file.objectKey)
            documents.transition(match { it.status == DocumentStatus.FAILED }, DocumentStatus.PENDING)
        }
    }

    @Test
    fun `an archiving another run abandoned meanwhile is not counted`() {
        val first = pending("acme")
        val second = pending("acme")
        every { documents.findStalled(any(), any(), any()) } returns listOf(first, second)
        every { documents.transition(match { it.id == second.id }, DocumentStatus.PENDING) } returns false

        assertEquals(1, service.abandon())
    }

    @Test
    fun `nothing is done when no archiving is stalled`() {
        every { documents.findStalled(any(), any(), any()) } returns emptyList()

        assertEquals(0, service.abandon())

        verify(exactly = 0) { storage.delete(any()) }
    }

    @Test
    fun `a document whose content cannot be removed stays pending, and the batch stops there`() {
        val first = pending("acme")
        val second = pending("acme")
        val third = pending("acme")
        every { documents.findStalled(any(), any(), any()) } returns listOf(first, second, third)
        every { storage.delete(second.file.objectKey) } throws ObjectStorageException("Storage down", RuntimeException())

        assertThrows(ObjectStorageException::class.java) { service.abandon() }

        assertEquals(listOf(first.id), saved.map { it.id })
        verify(exactly = 0) { storage.delete(third.file.objectKey) }
    }

    @Test
    fun `a document that cannot be recorded as failed is abandoned again at the next call`() {
        val stalled = pending("acme")
        every { documents.findStalled(any(), any(), any()) } returns listOf(stalled)
        every { documents.transition(any(), any()) } throws DocumentPersistenceException("Database down", RuntimeException()) andThen true

        assertThrows(DocumentPersistenceException::class.java) { service.abandon() }
        assertEquals(1, service.abandon())

        verify(exactly = 2) { storage.delete(stalled.file.objectKey) }
    }

    private fun pending(tenant: String) = ArchivedDocument(
        id = DocumentId.generate(),
        owner = DocumentOwner(TenantId(tenant), "billing-service"),
        metadata = DocumentMetadata(DocumentType("INVOICE"), null, null, emptyMap()),
        file = StoredFile("invoice.pdf", "application/pdf", 1, ObjectKey.generate()),
        status = DocumentStatus.PENDING,
        createdAt = Instant.parse("2026-10-15T09:15:00Z"),
        updatedAt = Instant.parse("2026-10-15T09:15:00Z"),
    )
}
