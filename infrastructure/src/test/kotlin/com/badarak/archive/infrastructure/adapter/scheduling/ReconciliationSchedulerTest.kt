package com.badarak.archive.infrastructure.adapter.scheduling

import com.badarak.archive.domain.model.DocumentId
import com.badarak.archive.domain.port.inbound.AbandonStalledArchivingsUseCase
import com.badarak.archive.domain.port.inbound.ReconciliationFailure
import com.badarak.archive.domain.port.inbound.ReconciliationOutcome
import com.badarak.archive.domain.port.inbound.ResumeDeletionsUseCase
import com.badarak.archive.domain.port.outbound.DocumentPersistenceException
import com.badarak.archive.domain.port.outbound.ObjectStorageException
import com.badarak.archive.infrastructure.config.ReconciliationProperties
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension

@ExtendWith(OutputCaptureExtension::class)
class ReconciliationSchedulerTest {

    private val resumeDeletions = mockk<ResumeDeletionsUseCase>()
    private val abandonArchivings = mockk<AbandonStalledArchivingsUseCase>()
    private val scheduler = ReconciliationScheduler(resumeDeletions, abandonArchivings, ReconciliationProperties())

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

    private fun outcome(
        completed: List<DocumentId> = emptyList(),
        failures: List<ReconciliationFailure> = emptyList(),
        interrupted: Boolean = false,
    ) = ReconciliationOutcome(completed, failures, interrupted)
}
