package com.badarak.archive.domain.service

import com.badarak.archive.domain.model.DocumentStatus
import com.badarak.archive.domain.model.StalledDocumentsPolicy
import com.badarak.archive.domain.port.inbound.AbandonStalledArchivingsUseCase
import com.badarak.archive.domain.port.inbound.ReconciliationOutcome
import com.badarak.archive.domain.port.outbound.DocumentRepository
import com.badarak.archive.domain.port.outbound.ObjectStorage
import java.time.Clock

class AbandonStalledArchivingsService(
    private val documents: DocumentRepository,
    storage: ObjectStorage,
    private val clock: Clock,
    private val policy: StalledDocumentsPolicy,
) : AbandonStalledArchivingsUseCase {

    private val failure = ArchivingFailure(documents, storage, clock)

    override fun abandon(): ReconciliationOutcome {
        val stalledBefore = policy.stalledBefore(clock.instant())
        return StalledDocumentsReconciliation.reconcile(
            stalled = documents.findStalled(DocumentStatus.PENDING, stalledBefore, policy.batchSize),
            backlog = { documents.measureStalled(DocumentStatus.PENDING, stalledBefore) },
            step = failure::record,
        )
    }
}
