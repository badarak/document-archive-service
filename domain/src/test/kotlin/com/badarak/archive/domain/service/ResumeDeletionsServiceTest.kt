package com.badarak.archive.domain.service

import com.badarak.archive.domain.model.ArchivedDocument
import com.badarak.archive.domain.model.DocumentId
import com.badarak.archive.domain.model.DocumentLifecycle
import com.badarak.archive.domain.model.DocumentMetadata
import com.badarak.archive.domain.model.DocumentOwner
import com.badarak.archive.domain.model.DocumentStatus
import com.badarak.archive.domain.model.DocumentType
import com.badarak.archive.domain.model.ObjectKey
import com.badarak.archive.domain.model.StalledDocumentsPolicy
import com.badarak.archive.domain.model.StoredFile
import com.badarak.archive.domain.model.TenantId
import com.badarak.archive.domain.port.outbound.DocumentRepository
import com.badarak.archive.domain.port.outbound.ObjectStorage
import com.badarak.archive.domain.port.outbound.ObjectStorageException
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class ResumeDeletionsServiceTest {

    private val documents = mockk<DocumentRepository>()
    private val storage = mockk<ObjectStorage>()
    private val now = Instant.parse("2026-10-19T10:00:00Z")
    private val gracePeriod = Duration.ofMinutes(15)
    private val policy = StalledDocumentsPolicy(gracePeriod, 2)
    private val service = ResumeDeletionsService(documents, storage, Clock.fixed(now, ZoneOffset.UTC), policy)
    private val saved = mutableListOf<ArchivedDocument>()

    @BeforeEach
    fun acceptEveryWrite() {
        every { documents.create(capture(saved)) } just runs
        every { documents.transition(capture(saved), any()) } returns true
        every { storage.delete(any()) } just runs
    }

    @Test
    fun `finishes one batch of the deletions stalled for longer than the grace period`() {
        val first = deleting("acme")
        val second = deleting("globex")
        every { documents.findStalled(DocumentStatus.DELETING, now.minus(gracePeriod), 2) } returns listOf(first, second)

        assertEquals(2, service.resume())

        verify { storage.delete(first.file.objectKey) }
        verify { storage.delete(second.file.objectKey) }
        assertEquals(listOf(first.id, second.id), saved.map { it.id })
        assertEquals(listOf(DocumentStatus.DELETED, DocumentStatus.DELETED), saved.map { it.status })
        assertEquals(now, saved.first().deletedAt)
    }

    @Test
    fun `a deletion another run finished meanwhile is not counted`() {
        val first = deleting("acme")
        val second = deleting("acme")
        every { documents.findStalled(any(), any(), any()) } returns listOf(first, second)
        every { documents.transition(match { it.id == second.id }, DocumentStatus.DELETING) } returns false

        assertEquals(1, service.resume())
    }

    @Test
    fun `nothing is done when no deletion is stalled`() {
        every { documents.findStalled(any(), any(), any()) } returns emptyList()

        assertEquals(0, service.resume())

        verify(exactly = 0) { storage.delete(any()) }
    }

    @Test
    fun `stops at the first storage failure, keeping the deletions already finished`() {
        val first = deleting("acme")
        val second = deleting("acme")
        val third = deleting("acme")
        every { documents.findStalled(any(), any(), any()) } returns listOf(first, second, third)
        every { storage.delete(second.file.objectKey) } throws ObjectStorageException("Storage down", RuntimeException())

        assertThrows(ObjectStorageException::class.java) { service.resume() }

        assertEquals(listOf(first.id), saved.map { it.id })
        verify(exactly = 0) { storage.delete(third.file.objectKey) }
    }

    private fun deleting(tenant: String) = ArchivedDocument(
        id = DocumentId.generate(),
        owner = DocumentOwner(TenantId(tenant), "billing-service"),
        metadata = DocumentMetadata(DocumentType("INVOICE"), null, null, emptyMap()),
        file = StoredFile("invoice.pdf", "application/pdf", 1, ObjectKey.generate()),
        lifecycle = DocumentLifecycle(
            status = DocumentStatus.DELETING,
            createdAt = Instant.parse("2026-10-15T09:15:00Z"),
            updatedAt = Instant.parse("2026-10-15T09:15:00Z"),
        ),
    )
}
