package com.badarak.archive.infrastructure.adapter.scheduling

import com.badarak.archive.domain.port.inbound.ResumeDeletionsUseCase
import com.badarak.archive.domain.port.outbound.ObjectStorageException
import com.badarak.archive.infrastructure.config.ReconciliationProperties
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test

class DeletionReconciliationSchedulerTest {

    private val resumeDeletions = mockk<ResumeDeletionsUseCase>()
    private val scheduler = DeletionReconciliationScheduler(resumeDeletions, ReconciliationProperties())

    @Test
    fun `a run resumes the deletions left in progress`() {
        every { resumeDeletions.resume() } returns 3

        scheduler.run()

        verify(exactly = 1) { resumeDeletions.resume() }
    }

    @Test
    fun `a failed run is absorbed, so that the next one still happens`() {
        every { resumeDeletions.resume() } throws ObjectStorageException("Storage down", RuntimeException())

        assertDoesNotThrow { scheduler.run() }
    }
}
