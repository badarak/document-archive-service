package com.badarak.archive.domain.service

import com.badarak.archive.domain.model.ArchivedDocument
import com.badarak.archive.domain.model.DocumentId
import com.badarak.archive.domain.port.inbound.ReconciliationFailure
import com.badarak.archive.domain.port.inbound.ReconciliationOutcome

/**
 * Moves on a batch of stalled documents one by one, shared by the resumption of the deletions and the abandonment of the
 * archivings. A document whose step fails is reported and skipped, so that a single document refused durably, by an
 * object lock for instance, never blocks the ones behind it. After [MAX_CONSECUTIVE_FAILURES] failures in a row, the
 * batch stops: the failure is then most likely global, and the next documents would only repeat failing calls.
 */
internal object StalledDocumentsReconciliation {

    const val MAX_CONSECUTIVE_FAILURES = 3

    fun reconcile(stalled: List<ArchivedDocument>, step: (ArchivedDocument) -> Boolean): ReconciliationOutcome {
        val completed = mutableListOf<DocumentId>()
        val failures = mutableListOf<ReconciliationFailure>()
        var consecutiveFailures = 0
        for (document in stalled) {
            try {
                if (step(document)) completed += document.id
                consecutiveFailures = 0
            } catch (e: RuntimeException) {
                failures += ReconciliationFailure(document.id, e)
                if (++consecutiveFailures == MAX_CONSECUTIVE_FAILURES) return ReconciliationOutcome(completed, failures, interrupted = true)
            }
        }
        return ReconciliationOutcome(completed, failures, interrupted = false)
    }
}
