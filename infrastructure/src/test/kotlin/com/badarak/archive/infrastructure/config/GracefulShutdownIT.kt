package com.badarak.archive.infrastructure.config

import com.badarak.archive.infrastructure.adapter.storage.S3StorageProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.context.LifecycleProperties
import org.springframework.boot.autoconfigure.task.TaskSchedulingProperties
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.Duration

@SpringBootTest
@ActiveProfiles("test")
class GracefulShutdownIT {

    @Autowired
    private lateinit var lifecycle: LifecycleProperties

    @Autowired
    private lateinit var storage: S3StorageProperties

    @Autowired
    private lateinit var scheduling: TaskSchedulingProperties

    @Test
    fun `on shutdown, the requests in progress are waited for longer than the longest storage call`() {
        assertTrue(lifecycle.timeoutPerShutdownPhase > storage.apiCallTimeout) {
            "${lifecycle.timeoutPerShutdownPhase} should exceed ${storage.apiCallTimeout}"
        }
    }

    @Test
    fun `on shutdown, a reconciliation run in progress is waited for`() {
        assertTrue(scheduling.shutdown.isAwaitTermination)
        assertEquals(Duration.ofSeconds(30), scheduling.shutdown.awaitTerminationPeriod)
    }
}
