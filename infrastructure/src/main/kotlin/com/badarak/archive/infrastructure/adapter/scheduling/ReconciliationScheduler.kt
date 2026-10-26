package com.badarak.archive.infrastructure.adapter.scheduling

import com.badarak.archive.domain.model.DocumentStatus
import com.badarak.archive.domain.port.inbound.AbandonStalledArchivingsUseCase
import com.badarak.archive.domain.port.inbound.ReconciliationOutcome
import com.badarak.archive.domain.port.inbound.ResumeDeletionsUseCase
import com.badarak.archive.infrastructure.config.ReconciliationProperties
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.SchedulingConfigurer
import org.springframework.scheduling.config.ScheduledTaskRegistrar
import java.time.Clock

/**
 * Resumes the deletions left in progress, then abandons the archivings left pending, at a fixed delay after each run.
 * Every instance of the service runs it, with no lock between them: both steps are idempotent, so concurrent runs only
 * repeat some storage calls. A step that fails is logged and retried at the next run, without preventing the other one.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnBooleanProperty("archive.reconciliation.enabled", matchIfMissing = true)
class ReconciliationScheduler(
    private val resumeDeletions: ResumeDeletionsUseCase,
    private val abandonArchivings: AbandonStalledArchivingsUseCase,
    private val properties: ReconciliationProperties,
    registry: MeterRegistry,
    clock: Clock,
) : SchedulingConfigurer {

    private val log = LoggerFactory.getLogger(javaClass)
    private val metrics = ReconciliationMetrics(registry, clock)

    override fun configureTasks(taskRegistrar: ScheduledTaskRegistrar) {
        taskRegistrar.addFixedDelayTask(::run, properties.interval)
    }

    fun run() {
        reconcile(DocumentStatus.DELETING, DELETIONS) { resumeDeletions.resume() }?.let { outcome ->
            if (outcome.completed.isNotEmpty()) log.info("Reconciliation finished {} {}", outcome.completed.size, DELETIONS)
        }
        reconcile(DocumentStatus.PENDING, ARCHIVINGS) { abandonArchivings.abandon() }?.completed?.forEach {
            log.warn("Reconciliation abandoned the archiving of document {}, left pending beyond its grace period", it)
        }
    }

    private fun reconcile(status: DocumentStatus, subject: String, step: () -> ReconciliationOutcome): ReconciliationOutcome? = try {
        step().also {
            report(subject, it)
            metrics.record(status, it)
        }
    } catch (e: RuntimeException) {
        metrics.failed(status)
        log.warn("Reconciliation of the {} interrupted, retried at the next run: {}", subject, e.message, e)
        null
    }

    private fun report(subject: String, outcome: ReconciliationOutcome) {
        outcome.failures.forEach {
            log.warn(
                "Reconciliation of document {} among the {} failed, retried at the next run: {}",
                it.documentId,
                subject,
                it.cause.message,
                it.cause,
            )
        }
        if (outcome.interrupted) {
            log.warn("Reconciliation of the {} interrupted after several failures in a row, retried at the next run", subject)
        }
    }

    private companion object {
        const val DELETIONS = "deletions left in progress"
        const val ARCHIVINGS = "archivings left pending"
    }
}
