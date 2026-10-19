package com.badarak.archive.infrastructure.adapter.scheduling

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.scheduling.config.FixedDelayTask
import org.springframework.scheduling.config.ScheduledTaskHolder
import org.springframework.test.context.ActiveProfiles
import java.time.Duration

class DeletionReconciliationSchedulingIT {

    @Nested
    @SpringBootTest
    @ActiveProfiles("test")
    inner class Disabled {

        @Autowired
        private lateinit var scheduler: ObjectProvider<DeletionReconciliationScheduler>

        @Test
        fun `the reconciliation is not scheduled when disabled`() {
            assertTrue(scheduler.ifAvailable == null)
        }
    }

    @Nested
    @SpringBootTest(properties = ["archive.reconciliation.enabled=true", "archive.reconciliation.interval=1h"])
    @ActiveProfiles("test")
    inner class Enabled {

        @Autowired
        private lateinit var tasks: ScheduledTaskHolder

        @Test
        fun `the reconciliation is scheduled at the configured delay when enabled`() {
            val delays = tasks.scheduledTasks.map { it.task }.filterIsInstance<FixedDelayTask>().map { it.intervalDuration }

            assertEquals(listOf(Duration.ofHours(1)), delays)
        }
    }
}
