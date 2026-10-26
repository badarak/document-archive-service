package com.badarak.archive.infrastructure.adapter.scheduling

import com.badarak.archive.domain.port.inbound.AbandonStalledArchivingsUseCase
import com.badarak.archive.domain.port.inbound.ResumeDeletionsUseCase
import com.badarak.archive.infrastructure.config.ReconciliationProperties
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.time.Clock

class ReconciliationSchedulerConditionTest {

    private val runner = ApplicationContextRunner()
        .withUserConfiguration(ReconciliationScheduler::class.java)
        .withBean(ResumeDeletionsUseCase::class.java, { mockk<ResumeDeletionsUseCase>(relaxed = true) })
        .withBean(AbandonStalledArchivingsUseCase::class.java, { mockk<AbandonStalledArchivingsUseCase>(relaxed = true) })
        .withBean(ReconciliationProperties::class.java, { ReconciliationProperties() })
        .withBean(MeterRegistry::class.java, { SimpleMeterRegistry() })
        .withBean(Clock::class.java, { Clock.systemUTC() })

    @Test
    fun `the reconciliation is scheduled when the property is missing`() {
        runner.run { assertEquals(1, it.getBeansOfType(ReconciliationScheduler::class.java).size) }
    }

    @Test
    fun `the reconciliation is scheduled when enabled`() {
        runner.withPropertyValues("archive.reconciliation.enabled=true")
            .run { assertEquals(1, it.getBeansOfType(ReconciliationScheduler::class.java).size) }
    }

    @Test
    fun `the reconciliation is not scheduled when disabled`() {
        runner.withPropertyValues("archive.reconciliation.enabled=false")
            .run { assertEquals(0, it.getBeansOfType(ReconciliationScheduler::class.java).size) }
    }
}
