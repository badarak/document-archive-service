package com.badarak.archive.infrastructure.adapter.scheduling

import com.badarak.archive.domain.port.inbound.ResumeDeletionsUseCase
import com.badarak.archive.infrastructure.config.ReconciliationProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.SchedulingConfigurer
import org.springframework.scheduling.config.ScheduledTaskRegistrar

/**
 * Resumes the deletions left in progress at a fixed delay after each run. Every instance of the service runs it, with no
 * lock between them: resuming a deletion is idempotent, so concurrent runs only repeat some storage calls. A run that
 * fails is logged and retried at the next one.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnBooleanProperty("archive.reconciliation.enabled")
class DeletionReconciliationScheduler(
    private val resumeDeletions: ResumeDeletionsUseCase,
    private val properties: ReconciliationProperties,
) : SchedulingConfigurer {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun configureTasks(taskRegistrar: ScheduledTaskRegistrar) {
        taskRegistrar.addFixedDelayTask(::run, properties.interval)
    }

    fun run() {
        try {
            val finished = resumeDeletions.resume()
            if (finished > 0) log.info("Finished {} deletions left in progress", finished)
        } catch (e: RuntimeException) {
            log.warn("Deletion reconciliation interrupted, retried at the next run: {}", e.message, e)
        }
    }
}
