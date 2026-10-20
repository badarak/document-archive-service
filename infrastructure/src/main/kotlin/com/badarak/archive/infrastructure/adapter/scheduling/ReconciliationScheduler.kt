package com.badarak.archive.infrastructure.adapter.scheduling

import com.badarak.archive.domain.port.inbound.AbandonStalledArchivingsUseCase
import com.badarak.archive.domain.port.inbound.ResumeDeletionsUseCase
import com.badarak.archive.infrastructure.config.ReconciliationProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.SchedulingConfigurer
import org.springframework.scheduling.config.ScheduledTaskRegistrar

/**
 * Resumes the deletions left in progress, then abandons the archivings left pending, at a fixed delay after each run.
 * Every instance of the service runs it, with no lock between them: both steps are idempotent, so concurrent runs only
 * repeat some storage calls. A step that fails is logged and retried at the next run, without preventing the other one.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnBooleanProperty("archive.reconciliation.enabled")
class ReconciliationScheduler(
    private val resumeDeletions: ResumeDeletionsUseCase,
    private val abandonArchivings: AbandonStalledArchivingsUseCase,
    private val properties: ReconciliationProperties,
) : SchedulingConfigurer {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun configureTasks(taskRegistrar: ScheduledTaskRegistrar) {
        taskRegistrar.addFixedDelayTask(::run, properties.interval)
    }

    fun run() {
        reconcile("deletions left in progress", "finished") { resumeDeletions.resume() }
        reconcile("archivings left pending", "abandoned") { abandonArchivings.abandon() }
    }

    private fun reconcile(subject: String, outcome: String, step: () -> Int) {
        try {
            val count = step()
            if (count > 0) log.info("Reconciliation {} {} {}", outcome, count, subject)
        } catch (e: RuntimeException) {
            log.warn("Reconciliation of the {} interrupted, retried at the next run: {}", subject, e.message, e)
        }
    }
}
