package com.badarak.archive.infrastructure.adapter.scheduling

import com.badarak.archive.domain.port.inbound.AbandonStalledArchivingsUseCase
import com.badarak.archive.domain.port.inbound.ResumeDeletionsUseCase
import com.badarak.archive.domain.port.outbound.DocumentPersistenceException
import com.badarak.archive.domain.port.outbound.ObjectStorageException
import com.badarak.archive.infrastructure.config.ReconciliationProperties
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test

class ReconciliationSchedulerTest {

    private val resumeDeletions = mockk<ResumeDeletionsUseCase>()
    private val abandonArchivings = mockk<AbandonStalledArchivingsUseCase>()
    private val scheduler = ReconciliationScheduler(resumeDeletions, abandonArchivings, ReconciliationProperties())

    @Test
    fun `a run resumes the deletions left in progress and abandons the archivings left pending`() {
        every { resumeDeletions.resume() } returns 3
        every { abandonArchivings.abandon() } returns 2

        scheduler.run()

        verify(exactly = 1) { resumeDeletions.resume() }
        verify(exactly = 1) { abandonArchivings.abandon() }
    }

    @Test
    fun `a failed deletion step does not prevent the archiving step, and the run is absorbed`() {
        every { resumeDeletions.resume() } throws ObjectStorageException("Storage down", RuntimeException())
        every { abandonArchivings.abandon() } returns 0

        assertDoesNotThrow { scheduler.run() }

        verify(exactly = 1) { abandonArchivings.abandon() }
    }

    @Test
    fun `a failed archiving step is absorbed, so that the next run still happens`() {
        every { resumeDeletions.resume() } returns 0
        every { abandonArchivings.abandon() } throws DocumentPersistenceException("Database down", RuntimeException())

        assertDoesNotThrow { scheduler.run() }
    }
}
