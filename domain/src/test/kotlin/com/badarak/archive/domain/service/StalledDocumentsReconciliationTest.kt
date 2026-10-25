package com.badarak.archive.domain.service

import com.badarak.archive.domain.fixture.aDocument
import com.badarak.archive.domain.model.ArchivedDocument
import com.badarak.archive.domain.model.DocumentStatus
import com.badarak.archive.domain.model.StalledBacklog
import com.badarak.archive.domain.port.inbound.ReconciliationFailure
import com.badarak.archive.domain.port.inbound.ReconciliationOutcome
import com.badarak.archive.domain.port.outbound.ObjectStorageException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class StalledDocumentsReconciliationTest {

    private val handled = mutableListOf<ArchivedDocument>()

    @Test
    fun `every document of the batch is moved on`() {
        val batch = stalled(2)

        val outcome = StalledDocumentsReconciliation.reconcile(batch, measured(), succeeding())

        assertEquals(ReconciliationOutcome(batch.map { it.id }, emptyList(), interrupted = false, backlog = BACKLOG), outcome)
    }

    @Test
    fun `a document another run moved on meanwhile is not reported`() {
        val batch = stalled(2)

        val outcome = StalledDocumentsReconciliation.reconcile(batch, measured()) { it != batch.first() }

        assertEquals(listOf(batch.last().id), outcome.completed)
        assertTrue(outcome.failures.isEmpty())
    }

    @Test
    fun `a document that fails is reported, without holding back the ones behind it`() {
        val batch = stalled(3)
        val refused = ObjectStorageException("Object locked", RuntimeException())

        val outcome = StalledDocumentsReconciliation.reconcile(batch, measured(), failingFor(batch[0] to refused))

        assertEquals(listOf(batch[1].id, batch[2].id), outcome.completed)
        assertEquals(listOf(ReconciliationFailure(batch[0].id, refused)), outcome.failures)
        assertFalse(outcome.interrupted)
    }

    @Test
    fun `the batch stops after the maximum number of failures in a row`() {
        val batch = stalled(StalledDocumentsReconciliation.MAX_CONSECUTIVE_FAILURES + 1)

        val outcome = StalledDocumentsReconciliation.reconcile(batch, measured()) { throw down() }

        assertTrue(outcome.interrupted)
        assertEquals(batch.dropLast(1).map { it.id }, outcome.failures.map { it.documentId })
        assertTrue(outcome.completed.isEmpty())
    }

    @Test
    fun `a document moved on resets the count of failures in a row`() {
        val batch = stalled(5)

        val outcome = StalledDocumentsReconciliation.reconcile(batch, measured()) { document ->
            handled += document
            if (document == batch[2]) true else throw down()
        }

        assertFalse(outcome.interrupted)
        assertEquals(batch, handled)
        assertEquals(listOf(batch[2].id), outcome.completed)
        assertEquals(4, outcome.failures.size)
    }

    @Test
    fun `an empty batch does nothing`() {
        val outcome = StalledDocumentsReconciliation.reconcile(emptyList(), measured(), succeeding())

        assertEquals(ReconciliationOutcome(emptyList(), emptyList(), interrupted = false, backlog = BACKLOG), outcome)
    }

    @Test
    fun `the backlog is measured once the batch is over, even when it is interrupted`() {
        val events = mutableListOf<String>()
        val batch = stalled(StalledDocumentsReconciliation.MAX_CONSECUTIVE_FAILURES)

        val outcome = StalledDocumentsReconciliation.reconcile(batch, { BACKLOG.also { events += "measured" } }) {
            events += "step"
            throw down()
        }

        assertTrue(outcome.interrupted)
        assertEquals(BACKLOG, outcome.backlog)
        assertEquals(List(batch.size) { "step" } + "measured", events)
    }

    private fun measured(): () -> StalledBacklog = { BACKLOG }

    private fun stalled(count: Int) = List(count) { aDocument(DocumentStatus.PENDING) }

    private fun succeeding(): (ArchivedDocument) -> Boolean = { true }

    private fun failingFor(failure: Pair<ArchivedDocument, RuntimeException>): (ArchivedDocument) -> Boolean =
        { if (it == failure.first) throw failure.second else true }

    private fun down() = ObjectStorageException("Storage down", RuntimeException())

    private companion object {
        val BACKLOG = StalledBacklog(7, Instant.parse("2026-10-19T09:00:00Z"))
    }
}
