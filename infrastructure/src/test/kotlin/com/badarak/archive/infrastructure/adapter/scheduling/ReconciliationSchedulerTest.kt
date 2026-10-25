package com.badarak.archive.infrastructure.adapter.scheduling

import com.badarak.archive.domain.model.DocumentId
import com.badarak.archive.domain.model.StalledBacklog
import com.badarak.archive.domain.port.inbound.AbandonStalledArchivingsUseCase
import com.badarak.archive.domain.port.inbound.ReconciliationFailure
import com.badarak.archive.domain.port.inbound.ReconciliationOutcome
import com.badarak.archive.domain.port.inbound.ResumeDeletionsUseCase
import com.badarak.archive.domain.port.outbound.DocumentPersistenceException
import com.badarak.archive.domain.port.outbound.ObjectStorageException
import com.badarak.archive.infrastructure.config.ReconciliationProperties
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

@ExtendWith(OutputCaptureExtension::class)
class ReconciliationSchedulerTest {

    private val resumeDeletions = mockk<ResumeDeletionsUseCase>()
    private val abandonArchivings = mockk<AbandonStalledArchivingsUseCase>()
    private val now = Instant.parse("2026-10-19T10:00:00Z")
    private val registry = SimpleMeterRegistry()
    private val scheduler =
        ReconciliationScheduler(resumeDeletions, abandonArchivings, ReconciliationProperties(), registry, Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `a run resumes the deletions left in progress and abandons the archivings left pending`() {
        every { resumeDeletions.resume() } returns outcome(completed = List(3) { DocumentId.generate() })
        every { abandonArchivings.abandon() } returns outcome(completed = List(2) { DocumentId.generate() })

        scheduler.run()

        verify(exactly = 1) { resumeDeletions.resume() }
        verify(exactly = 1) { abandonArchivings.abandon() }
    }

    @Test
    fun `every abandoned archiving is logged as a warning with its document`(output: CapturedOutput) {
        val abandoned = DocumentId.generate()
        every { resumeDeletions.resume() } returns outcome()
        every { abandonArchivings.abandon() } returns outcome(completed = listOf(abandoned))

        scheduler.run()

        assertThat(output.out, containsString("WARN"))
        assertThat(output.out, containsString("Reconciliation abandoned the archiving of document $abandoned"))
    }

    @Test
    fun `every document that failed is logged with its cause, and so is an interrupted batch`(output: CapturedOutput) {
        val failed = DocumentId.generate()
        every { resumeDeletions.resume() } returns outcome(
            failures = listOf(ReconciliationFailure(failed, ObjectStorageException("Object locked", RuntimeException()))),
            interrupted = true,
        )
        every { abandonArchivings.abandon() } returns outcome()

        scheduler.run()

        assertThat(output.out, containsString("Reconciliation of document $failed among the deletions left in progress failed"))
        assertThat(output.out, containsString("Object locked"))
        assertThat(output.out, containsString("deletions left in progress interrupted after several failures in a row"))
    }

    @Test
    fun `a failed deletion step does not prevent the archiving step, and the run is absorbed`() {
        every { resumeDeletions.resume() } throws DocumentPersistenceException("Database down", RuntimeException())
        every { abandonArchivings.abandon() } returns outcome()

        assertDoesNotThrow { scheduler.run() }

        verify(exactly = 1) { abandonArchivings.abandon() }
    }

    @Test
    fun `a failed archiving step is absorbed, so that the next run still happens`() {
        every { resumeDeletions.resume() } returns outcome()
        every { abandonArchivings.abandon() } throws DocumentPersistenceException("Database down", RuntimeException())

        assertDoesNotThrow { scheduler.run() }
    }

    @Test
    fun `each run publishes the backlog left, and the age of its oldest document`() {
        every { resumeDeletions.resume() } returns outcome(backlog = StalledBacklog(4, now.minus(Duration.ofMinutes(40))))
        every { abandonArchivings.abandon() } returns outcome()

        scheduler.run()

        assertEquals(4.0, gauge("archive.reconciliation.backlog", "deleting"))
        assertEquals(2400.0, gauge("archive.reconciliation.backlog.oldest.age", "deleting"))
        assertEquals(0.0, gauge("archive.reconciliation.backlog", "pending"))
        assertEquals(0.0, gauge("archive.reconciliation.backlog.oldest.age", "pending"))
    }

    @Test
    fun `each run counts the documents moved on and those that failed, and how the run ended`() {
        val failure = ReconciliationFailure(DocumentId.generate(), ObjectStorageException("Object locked", RuntimeException()))
        every { resumeDeletions.resume() } throws DocumentPersistenceException("Database down", RuntimeException())
        every { abandonArchivings.abandon() } returns outcome(completed = List(2) { DocumentId.generate() }, failures = listOf(failure))

        scheduler.run()
        every { abandonArchivings.abandon() } returns outcome(failures = List(3) { failure }, interrupted = true)
        scheduler.run()

        assertEquals(2.0, counter("archive.reconciliation.documents", "pending", "completed"))
        assertEquals(4.0, counter("archive.reconciliation.documents", "pending", "failed"))
        assertEquals(1.0, counter("archive.reconciliation.runs", "pending", "completed"))
        assertEquals(1.0, counter("archive.reconciliation.runs", "pending", "interrupted"))
        assertEquals(2.0, counter("archive.reconciliation.runs", "deleting", "failed"))
    }

    private fun gauge(name: String, status: String): Double = registry.get(name).tag("status", status).gauge().value()

    private fun counter(name: String, status: String, outcome: String): Double =
        registry.get(name).tag("status", status).tag("outcome", outcome).counter().count()

    private fun outcome(
        completed: List<DocumentId> = emptyList(),
        failures: List<ReconciliationFailure> = emptyList(),
        interrupted: Boolean = false,
        backlog: StalledBacklog = StalledBacklog.EMPTY,
    ) = ReconciliationOutcome(completed, failures, interrupted, backlog)
}
