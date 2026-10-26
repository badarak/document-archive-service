package com.badarak.archive.infrastructure.adapter.scheduling

import com.badarak.archive.domain.model.DocumentStatus
import com.badarak.archive.domain.port.inbound.AbandonStalledArchivingsUseCase
import com.badarak.archive.domain.port.inbound.ReconciliationOutcome
import com.badarak.archive.domain.port.inbound.ResumeDeletionsUseCase
import com.badarak.archive.domain.port.outbound.DocumentPersistenceException
import com.badarak.archive.domain.port.outbound.ObjectStorageException
import com.badarak.archive.infrastructure.config.ReconciliationProperties
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.slf4j.MDC
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

    /**
     * Runs both steps, logging with `operation=RECONCILIATION` in the MDC and, as key-value pairs, the status the documents
     * are left in, the document concerned, the outcome and the error code, like the log lines of the API operations.
     */
    fun run() {
        MDC.putCloseable(OPERATION, RECONCILIATION).use {
            reconcile(DocumentStatus.DELETING, DELETIONS) { resumeDeletions.resume() }?.let { outcome ->
                if (outcome.completed.isNotEmpty()) {
                    log.atInfo()
                        .addKeyValue(STATUS, tag(DocumentStatus.DELETING))
                        .addKeyValue(OUTCOME, "finished")
                        .addKeyValue("count", outcome.completed.size)
                        .log("Reconciliation finished {} {}", outcome.completed.size, DELETIONS)
                }
            }
            reconcile(DocumentStatus.PENDING, ARCHIVINGS) { abandonArchivings.abandon() }?.completed?.forEach {
                log.atWarn()
                    .addKeyValue(STATUS, tag(DocumentStatus.PENDING))
                    .addKeyValue(DOCUMENT_ID, it)
                    .addKeyValue(OUTCOME, "abandoned")
                    .log("Reconciliation abandoned the archiving of document {}, left pending beyond its grace period", it)
            }
        }
    }

    private fun reconcile(status: DocumentStatus, subject: String, step: () -> ReconciliationOutcome): ReconciliationOutcome? = try {
        step().also {
            report(status, subject, it)
            metrics.record(status, it)
        }
    } catch (e: RuntimeException) {
        metrics.failed(status)
        log.atWarn()
            .setCause(e)
            .addKeyValue(STATUS, tag(status))
            .addKeyValue(OUTCOME, "failed")
            .addKeyValue(ERROR_CODE, errorCode(e))
            .log("Reconciliation of the {} interrupted, retried at the next run: {}", subject, e.message)
        null
    }

    private fun report(status: DocumentStatus, subject: String, outcome: ReconciliationOutcome) {
        outcome.failures.forEach {
            log.atWarn()
                .setCause(it.cause)
                .addKeyValue(STATUS, tag(status))
                .addKeyValue(DOCUMENT_ID, it.documentId)
                .addKeyValue(OUTCOME, "failed")
                .addKeyValue(ERROR_CODE, errorCode(it.cause))
                .log(
                    "Reconciliation of document {} among the {} failed, retried at the next run: {}",
                    it.documentId,
                    subject,
                    it.cause.message,
                )
        }
        if (outcome.interrupted) {
            log.atWarn()
                .addKeyValue(STATUS, tag(status))
                .addKeyValue(OUTCOME, "interrupted")
                .log("Reconciliation of the {} interrupted after several failures in a row, retried at the next run", subject)
        }
    }

    private fun tag(status: DocumentStatus) = status.name.lowercase()

    private fun errorCode(failure: RuntimeException): String = when (failure) {
        is ObjectStorageException -> "storage-failure"
        is DocumentPersistenceException -> "persistence-failure"
        else -> "internal-error"
    }

    private companion object {
        const val DELETIONS = "deletions left in progress"
        const val ARCHIVINGS = "archivings left pending"
        const val OPERATION = "operation"
        const val RECONCILIATION = "RECONCILIATION"
        const val STATUS = "status"
        const val DOCUMENT_ID = "documentId"
        const val OUTCOME = "outcome"
        const val ERROR_CODE = "errorCode"
    }
}
