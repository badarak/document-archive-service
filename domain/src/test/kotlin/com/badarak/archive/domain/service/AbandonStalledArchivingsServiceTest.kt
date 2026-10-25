package com.badarak.archive.domain.service

import com.badarak.archive.domain.fixture.aDocument
import com.badarak.archive.domain.model.ArchivedDocument
import com.badarak.archive.domain.model.DocumentStatus
import com.badarak.archive.domain.model.StalledDocumentsPolicy
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
import org.junit.jupiter.api.Assertions.assertTrue
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

        assertEquals(listOf(first.id, second.id), service.abandon().completed)

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
    fun `an archiving another run abandoned meanwhile is not reported`() {
        val first = pending("acme")
        val second = pending("acme")
        every { documents.findStalled(any(), any(), any()) } returns listOf(first, second)
        every { documents.transition(match { it.id == second.id }, DocumentStatus.PENDING) } returns false

        assertEquals(listOf(first.id), service.abandon().completed)
    }

    @Test
    fun `nothing is done when no archiving is stalled`() {
        every { documents.findStalled(any(), any(), any()) } returns emptyList()

        assertTrue(service.abandon().completed.isEmpty())

        verify(exactly = 0) { storage.delete(any()) }
    }

    @Test
    fun `a document whose content cannot be removed stays pending, without holding back the others`() {
        val first = pending("acme")
        val second = pending("acme")
        val third = pending("acme")
        every { documents.findStalled(any(), any(), any()) } returns listOf(first, second, third)
        every { storage.delete(second.file.objectKey) } throws ObjectStorageException("Object locked", RuntimeException())

        val outcome = service.abandon()

        assertEquals(listOf(first.id, third.id), outcome.completed)
        assertEquals(listOf(second.id), outcome.failures.map { it.documentId })
        assertEquals(listOf(first.id, third.id), saved.map { it.id })
    }

    @Test
    fun `a document that cannot be recorded as failed is abandoned again at the next call`() {
        val stalled = pending("acme")
        every { documents.findStalled(any(), any(), any()) } returns listOf(stalled)
        every { documents.transition(any(), any()) } throws DocumentPersistenceException("Database down", RuntimeException()) andThen true

        assertEquals(listOf(stalled.id), service.abandon().failures.map { it.documentId })
        assertEquals(listOf(stalled.id), service.abandon().completed)

        verify(exactly = 2) { storage.delete(stalled.file.objectKey) }
    }

    private fun pending(tenant: String) = aDocument(DocumentStatus.PENDING, tenant = tenant)
}
