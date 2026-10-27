package com.badarak.archive.infrastructure.adapter.scheduling

import com.badarak.archive.domain.model.DocumentStatus
import com.badarak.archive.domain.port.inbound.ReconciliationOutcome
import com.badarak.archive.domain.port.outbound.DocumentPersistenceException
import com.badarak.archive.domain.port.outbound.ObjectStorageException
import org.slf4j.LoggerFactory
import org.slf4j.MDC

/**
 * Logs and measures what one reconciliation step did, around the call of its use case, which it leaves unchanged: the
 * outcome is returned and a failure passed on. The lines carry `operation=RECONCILIATION` in the MDC and, as key-value
 * pairs, the status the documents are left in, the document, the outcome and an error code, like the log lines of the API
 * operations. Every abandoned archiving is a warning, since it always follows an incident; finished deletions are counted.
 */
class ReconciliationReport(private val metrics: ReconciliationMetrics) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun of(status: DocumentStatus, step: () -> ReconciliationOutcome): ReconciliationOutcome =
        MDC.putCloseable(OPERATION, RECONCILIATION).use {
            try {
                step().also {
                    report(status, it)
                    metrics.record(status, it)
                }
            } catch (e: RuntimeException) {
                metrics.failed(status)
                log.atWarn()
                    .setCause(e)
                    .addKeyValue(STATUS, tag(status))
                    .addKeyValue(OUTCOME, "failed")
                    .addKeyValue(ERROR_CODE, errorCode(e))
                    .log("Reconciliation of the {} interrupted, retried at the next run: {}", subject(status), e.message)
                throw e
            }
        }

    private fun report(status: DocumentStatus, outcome: ReconciliationOutcome) {
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
                    subject(status),
                    it.cause.message,
                )
        }
        if (outcome.interrupted) {
            log.atWarn()
                .addKeyValue(STATUS, tag(status))
                .addKeyValue(OUTCOME, "interrupted")
                .log("Reconciliation of the {} interrupted after several failures in a row, retried at the next run", subject(status))
        }
        if (status == DocumentStatus.PENDING) {
            outcome.completed.forEach {
                log.atWarn()
                    .addKeyValue(STATUS, tag(status))
                    .addKeyValue(DOCUMENT_ID, it)
                    .addKeyValue(OUTCOME, "abandoned")
                    .log("Reconciliation abandoned the archiving of document {}, left pending beyond its grace period", it)
            }
        } else if (outcome.completed.isNotEmpty()) {
            log.atInfo()
                .addKeyValue(STATUS, tag(status))
                .addKeyValue(OUTCOME, "finished")
                .addKeyValue("count", outcome.completed.size)
                .log("Reconciliation finished {} {}", outcome.completed.size, subject(status))
        }
    }

    private fun subject(status: DocumentStatus) = if (status == DocumentStatus.PENDING) ARCHIVINGS else DELETIONS

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
