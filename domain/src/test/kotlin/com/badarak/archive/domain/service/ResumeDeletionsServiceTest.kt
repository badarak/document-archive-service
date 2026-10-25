package com.badarak.archive.domain.service

import com.badarak.archive.domain.fixture.aDocument
import com.badarak.archive.domain.model.ArchivedDocument
import com.badarak.archive.domain.model.DocumentStatus
import com.badarak.archive.domain.model.StalledBacklog
import com.badarak.archive.domain.model.StalledDocumentsPolicy
import com.badarak.archive.domain.port.outbound.DocumentRepository
import com.badarak.archive.domain.port.outbound.ObjectStorage
import com.badarak.archive.domain.port.outbound.ObjectStorageException
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
        every { documents.measureStalled(any(), any()) } returns StalledBacklog.EMPTY
    }

    @Test
    fun `finishes one batch of the deletions stalled for longer than the grace period`() {
        val first = deleting("acme")
        val second = deleting("globex")
        every { documents.findStalled(DocumentStatus.DELETING, now.minus(gracePeriod), 2) } returns listOf(first, second)

        assertEquals(listOf(first.id, second.id), service.resume().completed)

        verify { storage.delete(first.file.objectKey) }
        verify { storage.delete(second.file.objectKey) }
        assertEquals(listOf(first.id, second.id), saved.map { it.id })
        assertEquals(listOf(DocumentStatus.DELETED, DocumentStatus.DELETED), saved.map { it.status })
        assertEquals(now, saved.first().deletedAt)
    }

    @Test
    fun `a deletion another run finished meanwhile is not reported`() {
        val first = deleting("acme")
        val second = deleting("acme")
        every { documents.findStalled(any(), any(), any()) } returns listOf(first, second)
        every { documents.transition(match { it.id == second.id }, DocumentStatus.DELETING) } returns false

        assertEquals(listOf(first.id), service.resume().completed)
    }

    @Test
    fun `nothing is done when no deletion is stalled`() {
        every { documents.findStalled(any(), any(), any()) } returns emptyList()

        assertTrue(service.resume().completed.isEmpty())

        verify(exactly = 0) { storage.delete(any()) }
    }

    @Test
    fun `a deletion whose content cannot be removed stays deleting, without holding back the others`() {
        val first = deleting("acme")
        val second = deleting("acme")
        val third = deleting("acme")
        every { documents.findStalled(any(), any(), any()) } returns listOf(first, second, third)
        every { storage.delete(second.file.objectKey) } throws ObjectStorageException("Object locked", RuntimeException())

        val outcome = service.resume()

        assertEquals(listOf(first.id, third.id), outcome.completed)
        assertEquals(listOf(second.id), outcome.failures.map { it.documentId })
        assertEquals(listOf(first.id, third.id), saved.map { it.id })
    }

    @Test
    fun `reports the documents still stalled once the batch is over`() {
        val backlog = StalledBacklog(250, now.minus(Duration.ofHours(3)))
        every { documents.findStalled(any(), any(), any()) } returns emptyList()
        every { documents.measureStalled(DocumentStatus.DELETING, now.minus(gracePeriod)) } returns backlog

        assertEquals(backlog, service.resume().backlog)
    }

    private fun deleting(tenant: String) = aDocument(DocumentStatus.DELETING, tenant = tenant)
}
