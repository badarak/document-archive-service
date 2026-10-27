package com.badarak.archive.infrastructure.adapter.scheduling

import com.badarak.archive.domain.port.inbound.AbandonStalledArchivingsUseCase
import com.badarak.archive.domain.port.inbound.ReconciliationOutcome
import com.badarak.archive.domain.port.inbound.ResumeDeletionsUseCase
import com.badarak.archive.infrastructure.config.ReconciliationProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.SchedulingConfigurer
import org.springframework.scheduling.config.ScheduledTaskRegistrar

/**
 * Resumes the deletions left in progress, then abandons the archivings left pending, at a fixed delay after each run.
 * Every instance of the service runs it, with no lock between them: both steps are idempotent, so concurrent runs only
 * repeat some storage calls. A step that fails is retried at the next run, without preventing the other one; what each
 * step did, its failure included, is logged and measured around its use case (see [ReconciliationReport]).
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnBooleanProperty("archive.reconciliation.enabled", matchIfMissing = true)
class ReconciliationScheduler(
    private val resumeDeletions: ResumeDeletionsUseCase,
    private val abandonArchivings: AbandonStalledArchivingsUseCase,
    private val properties: ReconciliationProperties,
) : SchedulingConfigurer {

    override fun configureTasks(taskRegistrar: ScheduledTaskRegistrar) {
        taskRegistrar.addFixedDelayTask(::run, properties.interval)
    }

    fun run() {
        retriedAtTheNextRun { resumeDeletions.resume() }
        retriedAtTheNextRun { abandonArchivings.abandon() }
    }

    private fun retriedAtTheNextRun(step: () -> ReconciliationOutcome) {
        try {
            step()
        } catch (_: RuntimeException) {
        }
    }
}
